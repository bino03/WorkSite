package com.management.managementapi.service.attendance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.management.managementapi.dto.attendance.response.ClockCodeResponseDTO;
import com.management.managementapi.dto.attendance.response.ClockPreviewDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.attendance.TimeEntryMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EnterpriseClockCode;
import com.management.managementapi.model.attendance.RetiredClockCode;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.EnterpriseClockCodeRepository;
import com.management.managementapi.repository.attendance.RetiredClockCodeRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;

/**
 * picagem por QR (fase 5 da assiduidade). O QR de cada obra leva um URL com um código
 * opaco; o funcionário lê-o com a câmara do telemóvel, entra com a sua conta
 * ({@code EMPLOYEE}) e confirma. A direção não se escolhe — é o contrário da última
 * picagem do dia, que é o que torna a picagem um toque só.
 *
 * <p>O instante é o do servidor, não o do telemóvel: um relógio de telemóvel
 * adiantado não pode mudar as horas de ninguém.
 */
@Service
@Transactional
public class ClockService {

    /** Duas leituras do mesmo QR em menos disto são um duplo toque, não duas picagens. */
    static final Duration MIN_GAP = Duration.ofMinutes(1);

    private static final EnumSet<EnterPriseStatus> CLOSED =
            EnumSet.of(EnterPriseStatus.deleted, EnterPriseStatus.archived);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(ClockService.class);

    private final EnterpriseClockCodeRepository codeRepository;
    private final RetiredClockCodeRepository retiredRepository;
    private final RetiredClockCodeAlertService alertService;
    private final EnterpriseRepository enterpriseRepository;
    private final EmploymentRepository employmentRepository;
    private final ProfileRepository profileRepository;
    private final TimeEntryRepository entryRepository;
    private final TimeEntryService timeEntryService;
    private final TimeEntryMapper mapper;
    private final AttendanceZone zone;
    private final String frontendUrl;

    public ClockService(EnterpriseClockCodeRepository codeRepository,
                        RetiredClockCodeRepository retiredRepository,
                        RetiredClockCodeAlertService alertService,
                        EnterpriseRepository enterpriseRepository,
                        EmploymentRepository employmentRepository,
                        ProfileRepository profileRepository,
                        TimeEntryRepository entryRepository,
                        TimeEntryService timeEntryService,
                        TimeEntryMapper mapper,
                        AttendanceZone zone,
                        @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.codeRepository = codeRepository;
        this.retiredRepository = retiredRepository;
        this.alertService = alertService;
        this.enterpriseRepository = enterpriseRepository;
        this.employmentRepository = employmentRepository;
        this.profileRepository = profileRepository;
        this.entryRepository = entryRepository;
        this.timeEntryService = timeEntryService;
        this.mapper = mapper;
        this.zone = zone;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    // ── Admin: o QR de cada obra ──────────────────────────────

    /**
     * O código da obra, ou vazio se ainda não tem. Ler não cria (decisão de 2026-11-20):
     * o QR define-se de propósito, no drawer da obra, e não por alguém ter espreitado.
     */
    @Transactional(readOnly = true)
    public Optional<ClockCodeResponseDTO> codeFor(UUID enterpriseId) {
        return codeRepository.findByEnterpriseId(enterpriseId).map(this::toResponse);
    }

    /**
     * Cria o QR da obra, ou substitui o atual: o antigo deixa de picar logo, e tem de se
     * imprimir o novo. É a mesma operação — "definir um QR novo" — tenha ou não havido um.
     */
    public ClockCodeResponseDTO generate(UUID enterpriseId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        EnterpriseClockCode code = codeRepository.findByEnterpriseId(enterpriseId).orElseGet(() -> {
            EnterpriseClockCode created = new EnterpriseClockCode();
            created.setEnterprise(resolveEnterprise(enterpriseId));
            return created;
        });
        if (code.getCode() != null) {
            // Guardado antes de sobrepor: é o que distingue, mais tarde, um QR antigo de um inventado.
            retiredRepository.save(RetiredClockCode.of(code, now));
        }
        code.regenerate(newCode(), now);
        return toResponse(codeRepository.save(code));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID enterpriseId) {
        String url = codeFor(enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLOCK_CODE_NOT_CREATED))
                .url();
        try {
            // Correção H: o QR fica colado numa obra, ao pó e à chuva — aguenta ~30% estragado.
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 800, 800,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Não foi possível gerar o QR da obra " + enterpriseId, e);
        }
    }

    // ── Funcionário: picar ────────────────────────────────────

    @Transactional(readOnly = true)
    public ClockPreviewDTO preview(String code, UUID profileId) {
        // O aviso de QR antigo dispara aqui, não no POST: o /picar chama o preview logo
        // ao ler o QR, e um QR antigo nunca chega a confirmar (decidido a 2026-11-23).
        Enterprise enterprise = resolveOpenEnterprise(code, profileId);
        requireEmployed(profileId);
        List<TimeEntry> today = todayEntries(profileId);
        return new ClockPreviewDTO(enterprise.getId(), enterprise.getName(), nextDirection(today),
                mapper.toResponses(today, zone.zoneId()));
    }

    public TimeEntry clock(String code, TimeDirection expected, UUID profileId) {
        Enterprise enterprise = resolveOpenEnterprise(code, null);
        requireEmployed(profileId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        List<TimeEntry> today = todayEntries(profileId);

        if (nextDirection(today) != expected) {
            throw new BusinessException(ErrorCode.CLOCK_STALE_DIRECTION);
        }
        if (!today.isEmpty()
                && Duration.between(today.get(today.size() - 1).getHappenedAt(), now).compareTo(MIN_GAP) < 0) {
            throw new BusinessException(ErrorCode.CLOCK_TOO_SOON);
        }

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_PROFILE_NOT_FOUND, "Perfil " + profileId + " não encontrado"));
        return timeEntryService.registerByClock(profile, enterprise, expected, now);
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> today(UUID profileId) {
        return mapper.toResponses(todayEntries(profileId), zone.zoneId());
    }

    // ── Regras ────────────────────────────────────────────────

    /** O contrário da última picagem do dia; o dia começa sempre por uma entrada. */
    static TimeDirection nextDirection(List<TimeEntry> today) {
        if (today.isEmpty()) {
            return TimeDirection.IN;
        }
        return today.get(today.size() - 1).getDirection() == TimeDirection.IN
                ? TimeDirection.OUT
                : TimeDirection.IN;
    }

    private List<TimeEntry> todayEntries(UUID profileId) {
        LocalDate today = zone.today();
        return entryRepository.findForProfileBetween(profileId, zone.startOfDay(today), zone.startOfNextDay(today));
    }

    /**
     * A obra do código, se está aberta. Um código que já foi de uma obra e foi substituído
     * dá {@code ATT_026}; com {@code alertFor}, a tentativa fica registada e os ADMIN são
     * avisados. Vem antes da ficha de emprego de propósito: quem tenta picar com um QR
     * antigo é sinal para o escritório, tenha ou não ficha.
     */
    private Enterprise resolveOpenEnterprise(String code, UUID alertFor) {
        Optional<EnterpriseClockCode> current = codeRepository.findByCode(code);
        if (current.isEmpty()) {
            RetiredClockCode retired = retiredRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CLOCK_CODE_INVALID));
            if (alertFor != null) {
                alertRetired(retired, alertFor);
            }
            throw new BusinessException(ErrorCode.CLOCK_CODE_RETIRED);
        }
        Enterprise enterprise = current.get().getEnterprise();
        if (enterprise.getStatus() != null && CLOSED.contains(enterprise.getStatus())) {
            throw new BusinessException(ErrorCode.CLOCK_ENTERPRISE_CLOSED);
        }
        return enterprise;
    }

    /**
     * O aviso nunca pode trocar o erro que o funcionário vê: se a escrita falhar (ex.: duas
     * leituras em simultâneo a bater na unique do dedupe), regista-se e segue o ATT_026.
     */
    private void alertRetired(RetiredClockCode retired, UUID profileId) {
        try {
            alertService.recordAttempt(retired, profileId);
        } catch (RuntimeException e) {
            log.warn("[picagem] aviso de QR antigo não gravado (código {}, perfil {}): {}",
                    retired.getId(), profileId, e.getMessage());
        }
    }

    /**
     * Só pica quem tem ficha de emprego em vigor hoje. Ao contrário do registo pelo
     * admin (que aceita picagens sem ficha), aqui a ficha é o que diz que esta conta
     * é de um trabalhador com contrato, e é ela que dá o horário contra o qual as
     * horas se contam.
     */
    private void requireEmployed(UUID profileId) {
        Employment employment = employmentRepository.findByProfileId(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CLOCK_NOT_EMPLOYED));
        LocalDate today = zone.today();
        boolean active = !employment.getHiredAt().isAfter(today)
                && (employment.getEndedAt() == null || !employment.getEndedAt().isBefore(today));
        if (!active) {
            throw new BusinessException(ErrorCode.CLOCK_NOT_EMPLOYED);
        }
    }

    private Enterprise resolveEnterprise(UUID enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));
    }

    private ClockCodeResponseDTO toResponse(EnterpriseClockCode code) {
        Enterprise enterprise = code.getEnterprise();
        return new ClockCodeResponseDTO(enterprise.getId(), enterprise.getName(),
                frontendUrl + "/picar?c=" + code.getCode(), code.getGeneratedAt());
    }

    /** 24 bytes aleatórios em base64 url-safe = 32 caracteres, sem padding. */
    private static String newCode() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
