package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.request.TimeEntryUpsertDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryRevisionResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.attendance.TimeEntryMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.TimeEntryRevision;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;
import com.management.managementapi.model.enums.TimeEntrySource;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.repository.attendance.TimeEntryRevisionRepository;

/**
 * as picagens: registar, corrigir, anular e restaurar.
 *
 * <p>Toda a alteração grava uma linha em {@code time_entry_revision} na
 * <b>mesma transação</b>. O {@code ActivityLogger} do projeto é {@code @Async} e
 * pode perder linhas; para faturas nunca importou, mas é o registo corrigido que
 * uma auditoria de assiduidade põe em causa. O {@code activity_log} continua a ser
 * escrito pelo controller, para o histórico geral da app.
 *
 * <p>Picagens nunca se apagam fisicamente — só {@code deleted_at}.
 */
@Service
@Transactional
public class TimeEntryService {

    private final TimeEntryRepository repository;
    private final TimeEntryRevisionRepository revisionRepository;
    private final EmploymentRepository employmentRepository;
    private final ProfileRepository profileRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final TimeEntryMapper mapper;
    private final AttendanceZone zone;

    public TimeEntryService(TimeEntryRepository repository,
                            TimeEntryRevisionRepository revisionRepository,
                            EmploymentRepository employmentRepository,
                            ProfileRepository profileRepository,
                            EnterpriseRepository enterpriseRepository,
                            TimeEntryMapper mapper,
                            AttendanceZone zone) {
        this.repository = repository;
        this.revisionRepository = revisionRepository;
        this.employmentRepository = employmentRepository;
        this.profileRepository = profileRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.mapper = mapper;
        this.zone = zone;
    }

    // ── Leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TimeEntryResponseDTO> listForProfile(UUID profileId, Pageable pageable) {
        return repository.findByProfileIdAndDeletedAtIsNullOrderByHappenedAtDesc(profileId, pageable)
                .map(entry -> mapper.toResponse(entry, zone.zoneId()));
    }

    /** As picagens de um dia local — o dia vai de 00:00 em Lisboa ao 00:00 seguinte. */
    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> listForDay(UUID profileId, LocalDate day) {
        return mapper.toResponses(
                repository.findForProfileBetween(profileId, zone.startOfDay(day), zone.startOfNextDay(day)),
                zone.zoneId());
    }

    @Transactional(readOnly = true)
    public List<TimeEntryResponseDTO> listDeletedForProfile(UUID profileId) {
        return mapper.toResponses(
                repository.findByProfileIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(profileId),
                zone.zoneId());
    }

    @Transactional(readOnly = true)
    public List<TimeEntryRevisionResponseDTO> revisions(UUID timeEntryId) {
        return revisionRepository.findByTimeEntryIdOrderByCreatedAtDesc(timeEntryId)
                .stream().map(mapper::toRevisionResponse).toList();
    }

    @Transactional(readOnly = true)
    public TimeEntry getById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TIME_ENTRY_NOT_FOUND,
                        "Picagem " + id + " não encontrada"));
    }

    @Transactional(readOnly = true)
    public TimeEntryResponseDTO getResponseById(UUID id) {
        return mapper.toResponse(getById(id), zone.zoneId());
    }

    // ── Escrita ───────────────────────────────────────────────

    public TimeEntry register(TimeEntryUpsertDTO dto, UUID actorId, String actorName) {
        validateNotBeforeHire(dto.profileId(), dto.happenedAt());
        validateSequence(dto.profileId(), dto.happenedAt(), dto.direction(), null);

        TimeEntry entry = new TimeEntry();
        entry.setProfile(resolveProfile(dto.profileId()));
        entry.setEnterprise(resolveEnterprise(dto.enterpriseId()));
        entry.setHappenedAt(dto.happenedAt());
        entry.setDirection(dto.direction());
        entry.setSource(TimeEntrySource.MANUAL);
        entry.setRegisteredBy(resolveProfile(actorId));
        entry.setNote(dto.note());

        TimeEntry saved = repository.save(entry);
        revisionRepository.save(TimeEntryRevision.before(
                saved, TimeEntryChange.CREATE, actorId, actorName, dto.reason()));
        return saved;
    }

    public TimeEntry correct(UUID id, TimeEntryUpsertDTO dto, UUID actorId, String actorName) {
        TimeEntry entry = getById(id);

        validateNotBeforeHire(dto.profileId(), dto.happenedAt());
        validateSequence(dto.profileId(), dto.happenedAt(), dto.direction(), id);

        // A fotografia tem de ser tirada ANTES de mexer na entidade, senão guarda
        // o estado novo e a revisão não prova nada.
        TimeEntryRevision revision = TimeEntryRevision.before(
                entry, TimeEntryChange.UPDATE, actorId, actorName, dto.reason());

        entry.setProfile(resolveProfile(dto.profileId()));
        entry.setEnterprise(resolveEnterprise(dto.enterpriseId()));
        entry.setHappenedAt(dto.happenedAt());
        entry.setDirection(dto.direction());
        entry.setNote(dto.note());

        TimeEntry saved = repository.save(entry);
        revisionRepository.save(revision);
        return saved;
    }

    public void softDelete(UUID id, String reason, UUID actorId, String actorName) {
        TimeEntry entry = getById(id);

        TimeEntryRevision revision = TimeEntryRevision.before(
                entry, TimeEntryChange.DELETE, actorId, actorName, reason);

        entry.setDeletedAt(OffsetDateTime.now());
        repository.save(entry);
        revisionRepository.save(revision);
    }

    public TimeEntry restore(UUID id, String reason, UUID actorId, String actorName) {
        TimeEntry entry = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TIME_ENTRY_NOT_FOUND,
                        "Picagem " + id + " não encontrada"));

        if (entry.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.TIME_ENTRY_NOT_DELETED);
        }
        validateSequence(entry.getProfile().getId(), entry.getHappenedAt(), entry.getDirection(), id);

        TimeEntryRevision revision = TimeEntryRevision.before(
                entry, TimeEntryChange.RESTORE, actorId, actorName, reason);

        entry.setDeletedAt(null);
        TimeEntry saved = repository.save(entry);
        revisionRepository.save(revision);
        return saved;
    }

    // ── Regras ────────────────────────────────────────────────

    /**
     * Uma entrada é seguida de uma saída, e uma saída de uma entrada. Fica no
     * service e não num CHECK porque um CHECK não consegue exprimir sequência.
     *
     * <p>A sequência é avaliada dentro do <b>dia local</b>: um dia de trabalho não
     * atravessa a meia-noite (decisão de 2026-10-06), por isso cada dia começa
     * sempre a zero e um dia mal preenchido não contamina o seguinte.
     */
    private void validateSequence(UUID profileId, OffsetDateTime happenedAt,
                                  TimeDirection direction, UUID ignoreEntryId) {
        LocalDate day = zone.dateOf(happenedAt);

        List<TimeEntry> sameDay = repository.findForProfileBetween(
                profileId, zone.startOfDay(day), zone.startOfNextDay(day));

        TimeDirection previous = null;
        boolean placed = false;

        for (TimeEntry existing : sameDay) {
            if (existing.getId().equals(ignoreEntryId)) {
                continue;
            }
            if (existing.getHappenedAt().isEqual(happenedAt)) {
                throw new BusinessException(ErrorCode.TIME_ENTRY_DUPLICATE_INSTANT);
            }
            if (!placed && existing.getHappenedAt().isAfter(happenedAt)) {
                requireAlternating(previous, direction);
                previous = direction;
                placed = true;
            }
            requireAlternating(previous, existing.getDirection());
            previous = existing.getDirection();
        }

        if (!placed) {
            requireAlternating(previous, direction);
        }
    }

    private void requireAlternating(TimeDirection previous, TimeDirection next) {
        boolean validStart = previous == null && next == TimeDirection.IN;
        boolean validPair = previous != null && previous != next;

        if (!validStart && !validPair) {
            throw new BusinessException(ErrorCode.TIME_ENTRY_OUT_OF_SEQUENCE);
        }
    }

    /**
     * Sem dados de emprego não se valida a admissão — é possível registar picagens
     * de alguém antes de lhe ter criado a ficha, e isso não é um erro.
     */
    private void validateNotBeforeHire(UUID profileId, OffsetDateTime happenedAt) {
        Employment employment = employmentRepository.findByProfileId(profileId).orElse(null);
        if (employment == null) {
            return;
        }
        if (zone.dateOf(happenedAt).isBefore(employment.getHiredAt())) {
            throw new BusinessException(ErrorCode.TIME_ENTRY_BEFORE_HIRE);
        }
    }

    private Profile resolveProfile(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_PROFILE_NOT_FOUND,
                        "Perfil " + profileId + " não encontrado"));
    }

    private Enterprise resolveEnterprise(UUID enterpriseId) {
        if (enterpriseId == null) {
            return null;
        }
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));
    }
}
