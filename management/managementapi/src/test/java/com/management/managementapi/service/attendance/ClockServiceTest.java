package com.management.managementapi.service.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
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
 * A picagem por QR tem três guardas que, se se perderem, ninguém nota até ao fim do
 * mês: a direção conferida (um duplo toque não pode virar entrada+saída), o intervalo
 * mínimo, e a ficha de emprego.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClockServiceTest {

    @Mock private EnterpriseClockCodeRepository codeRepository;
    @Mock private RetiredClockCodeRepository retiredRepository;
    @Mock private RetiredClockCodeAlertService alertService;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private EmploymentRepository employmentRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private TimeEntryRepository entryRepository;
    @Mock private TimeEntryService timeEntryService;
    @Mock private TimeEntryMapper mapper;

    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");
    private static final UUID ANA = UUID.randomUUID();
    private static final String CODE = "abcdefghijklmnopqrstuvwxyz012345";

    private Enterprise obra;
    private final List<TimeEntry> hoje = new ArrayList<>();

    private ClockService service() {
        return new ClockService(codeRepository, retiredRepository, alertService, enterpriseRepository,
                employmentRepository, profileRepository, entryRepository, timeEntryService, mapper, ZONE,
                "https://worksite.example/");
    }

    @BeforeEach
    void setUp() {
        obra = new Enterprise();
        obra.setId(UUID.randomUUID());
        obra.setName("Villa Petrus");
        obra.setStatus(EnterPriseStatus.under_construction);

        EnterpriseClockCode code = new EnterpriseClockCode();
        code.setEnterprise(obra);
        code.regenerate(CODE, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
        when(codeRepository.findByCode(any())).thenAnswer(call ->
                CODE.equals(call.getArgument(0)) ? Optional.of(code) : Optional.empty());

        Employment employment = new Employment();
        employment.setHiredAt(LocalDate.of(2020, 1, 1));
        when(employmentRepository.findByProfileId(ANA)).thenReturn(Optional.of(employment));

        Profile ana = new Profile();
        ana.setId(ANA);
        ana.setName("Ana");
        when(profileRepository.findById(ANA)).thenReturn(Optional.of(ana));

        hoje.clear();
        when(entryRepository.findForProfileBetween(eq(ANA), any(), any())).thenReturn(hoje);
    }

    private static TimeEntry entry(TimeDirection direction, OffsetDateTime at) {
        TimeEntry entry = new TimeEntry();
        entry.setDirection(direction);
        entry.setHappenedAt(at);
        return entry;
    }

    @Test
    @DisplayName("o dia começa por uma entrada, e cada leitura é o contrário da anterior")
    void direcaoAlterna() {
        assertThat(ClockService.nextDirection(List.of())).isEqualTo(TimeDirection.IN);
        OffsetDateTime cedo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3);
        assertThat(ClockService.nextDirection(List.of(entry(TimeDirection.IN, cedo))))
                .isEqualTo(TimeDirection.OUT);
        assertThat(ClockService.nextDirection(List.of(entry(TimeDirection.IN, cedo),
                entry(TimeDirection.OUT, cedo.plusHours(1))))).isEqualTo(TimeDirection.IN);
    }

    @Test
    @DisplayName("picar regista na obra do QR, com a direção prevista, pelo próprio")
    void picaNaObraDoQr() {
        service().clock(CODE, TimeDirection.IN, ANA);

        verify(timeEntryService).registerByClock(any(Profile.class), eq(obra), eq(TimeDirection.IN),
                any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("uma página desatualizada (ou um duplo toque) não regista a picagem contrária")
    void direcaoDesatualizadaRecusada() {
        hoje.add(entry(TimeDirection.IN, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)));

        assertThatThrownBy(() -> service().clock(CODE, TimeDirection.IN, ANA))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_STALE_DIRECTION);
        verify(timeEntryService, never()).registerByClock(any(), any(), any(), any());
    }

    @Test
    @DisplayName("duas leituras em menos de um minuto são uma só")
    void leituraRepetidaRecusada() {
        hoje.add(entry(TimeDirection.IN, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(20)));

        assertThatThrownBy(() -> service().clock(CODE, TimeDirection.OUT, ANA))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_TOO_SOON);
    }

    @Test
    @DisplayName("um código inventado não pica, e não avisa ninguém")
    void codigoInvalido() {
        assertThatThrownBy(() -> service().preview("codigo-inventado-que-nunca-existiu", ANA))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_CODE_INVALID);
        verify(alertService, never()).recordAttempt(any(), any());
    }

    private RetiredClockCode retiredCode(String value) {
        EnterpriseClockCode old = new EnterpriseClockCode();
        old.setEnterprise(obra);
        old.regenerate(value, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        RetiredClockCode retired = RetiredClockCode.of(old, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
        when(retiredRepository.findByCode(value)).thenReturn(Optional.of(retired));
        return retired;
    }

    @Test
    @DisplayName("ler um QR substituído dá ATT_026 e avisa os ADMIN — mesmo sem ficha de emprego")
    void qrAntigoNoPreviewAvisa() {
        RetiredClockCode retired = retiredCode("codigo-antigo-que-foi-substituido");
        UUID semFicha = UUID.randomUUID();
        when(employmentRepository.findByProfileId(semFicha)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().preview("codigo-antigo-que-foi-substituido", semFicha))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_CODE_RETIRED);
        verify(alertService).recordAttempt(retired, semFicha);
    }

    @Test
    @DisplayName("confirmar com um QR substituído dá ATT_026 sem novo aviso (o aviso é do preview)")
    void qrAntigoNoPostNaoAvisa() {
        retiredCode("codigo-antigo-que-foi-substituido");

        assertThatThrownBy(() -> service().clock("codigo-antigo-que-foi-substituido", TimeDirection.IN, ANA))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_CODE_RETIRED);
        verify(alertService, never()).recordAttempt(any(), any());
    }

    @Test
    @DisplayName("se o aviso falhar a gravar, o funcionário vê na mesma o ATT_026")
    void avisoQueFalhaNaoTrocaOErro() {
        RetiredClockCode retired = retiredCode("codigo-antigo-que-foi-substituido");
        when(alertService.recordAttempt(retired, ANA)).thenThrow(new IllegalStateException("unique"));

        assertThatThrownBy(() -> service().preview("codigo-antigo-que-foi-substituido", ANA))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_CODE_RETIRED);
    }

    @Test
    @DisplayName("sem ficha de emprego, ou com ela terminada, não pica")
    void semFichaNaoPica() {
        UUID semFicha = UUID.randomUUID();
        when(employmentRepository.findByProfileId(semFicha)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().clock(CODE, TimeDirection.IN, semFicha))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_NOT_EMPLOYED);

        Employment terminada = new Employment();
        terminada.setHiredAt(LocalDate.of(2020, 1, 1));
        terminada.setEndedAt(LocalDate.of(2021, 1, 1));
        when(employmentRepository.findByProfileId(ANA)).thenReturn(Optional.of(terminada));
        assertThatThrownBy(() -> service().clock(CODE, TimeDirection.IN, ANA))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_NOT_EMPLOYED);
    }

    @Test
    @DisplayName("uma obra arquivada ou eliminada não aceita picagens")
    void obraFechada() {
        obra.setStatus(EnterPriseStatus.archived);

        assertThatThrownBy(() -> service().preview(CODE, ANA))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_ENTERPRISE_CLOSED);
    }

    @Test
    @DisplayName("o URL do QR leva o código, não o id da obra")
    void urlLevaOCodigo() {
        EnterpriseClockCode code = new EnterpriseClockCode();
        code.setEnterprise(obra);
        code.regenerate(CODE, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
        when(codeRepository.findByEnterpriseId(obra.getId())).thenReturn(Optional.of(code));

        assertThat(service().codeFor(obra.getId()).orElseThrow().url())
                .isEqualTo("https://worksite.example/picar?c=" + CODE);
        verify(codeRepository, never()).save(any());
        // E o PNG gera-se sem rebentar.
        assertThat(service().qrPng(obra.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("ver o QR de uma obra sem QR não o cria — define-se de propósito")
    void lerNaoCria() {
        when(codeRepository.findByEnterpriseId(obra.getId())).thenReturn(Optional.empty());

        assertThat(service().codeFor(obra.getId())).isEmpty();
        assertThatThrownBy(() -> service().qrPng(obra.getId()))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CLOCK_CODE_NOT_CREATED);
        verify(codeRepository, never()).save(any());
    }

    @Test
    @DisplayName("gerar cria o QR na primeira vez e substitui o código nas seguintes")
    void gerarCriaESubstitui() {
        when(codeRepository.findByEnterpriseId(obra.getId())).thenReturn(Optional.empty());
        when(enterpriseRepository.findById(obra.getId())).thenReturn(Optional.of(obra));
        when(codeRepository.save(any(EnterpriseClockCode.class))).thenAnswer(call -> call.getArgument(0));

        String primeiro = service().generate(obra.getId()).url();

        EnterpriseClockCode existente = new EnterpriseClockCode();
        existente.setEnterprise(obra);
        existente.regenerate(CODE, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
        when(codeRepository.findByEnterpriseId(obra.getId())).thenReturn(Optional.of(existente));

        String segundo = service().generate(obra.getId()).url();

        assertThat(primeiro).startsWith("https://worksite.example/picar?c=");
        assertThat(segundo).isNotEqualTo("https://worksite.example/picar?c=" + CODE).isNotEqualTo(primeiro);
        assertThat(existente.getCode()).hasSize(32);
        // O código substituído fica guardado (e só o da segunda vez: na primeira não havia).
        ArgumentCaptor<RetiredClockCode> retired = ArgumentCaptor.forClass(RetiredClockCode.class);
        verify(retiredRepository).save(retired.capture());
        assertThat(retired.getValue().getCode()).isEqualTo(CODE);
        assertThat(retired.getValue().getEnterprise()).isEqualTo(obra);
        // A data muda com o código: é a do QR que está colado na obra.
        assertThat(existente.getGeneratedAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    }
}
