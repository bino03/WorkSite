package com.management.managementapi.service.attendance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.dto.attendance.request.AbsenceUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.AbsenceDocument;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.AbsenceDocumentRepository;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ausências: marcar, aprovar, recusar, anular, e anexar justificativos.
 *
 * <p>Uma ausência nasce sempre {@code PENDING}. Aprovar é um passo à parte para que
 * o ato de decidir fique registado com quem e quando — e é a aprovação que faz o
 * dia deixar de aparecer como falta.
 */
@Service
@Transactional
public class AbsenceService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceService.class);

    private static final String BUCKET = "documents";
    private static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic");
    /** Bate com o teto global do multipart (`spring.servlet.multipart.max-file-size`). */
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final AbsenceRepository repository;
    private final AbsenceDocumentRepository documentRepository;
    private final EmploymentRepository employmentRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storageService;

    public AbsenceService(AbsenceRepository repository,
                          AbsenceDocumentRepository documentRepository,
                          EmploymentRepository employmentRepository,
                          ProfileRepository profileRepository,
                          SupabaseStorageService storageService) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.employmentRepository = employmentRepository;
        this.profileRepository = profileRepository;
        this.storageService = storageService;
    }

    // ── Leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Absence> listForProfile(UUID profileId, LocalDate from, LocalDate to) {
        return repository.findForProfileOverlapping(profileId, from, to, null);
    }

    /** Os dados do mapa de equipa: quem está fora num intervalo. */
    @Transactional(readOnly = true)
    public List<Absence> listTeam(LocalDate from, LocalDate to, AbsenceStatus status) {
        return repository.findOverlapping(from, to, status);
    }

    @Transactional(readOnly = true)
    public List<Absence> listPending() {
        return repository.findByStatus(AbsenceStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Absence getById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ABSENCE_NOT_FOUND, "Ausência " + id + " não encontrada"));
    }

    // ── Escrita ───────────────────────────────────────────────

    public Absence create(AbsenceUpsertDTO dto, UUID requestedBy) {
        AbsenceHalfDay halfDay = dto.halfDay() == null ? AbsenceHalfDay.NONE : dto.halfDay();
        validateDates(dto.startsOn(), dto.endsOn(), halfDay);
        validateNotBeforeHire(dto.profileId(), dto.startsOn());
        validateNoOverlap(dto.profileId(), dto.startsOn(), dto.endsOn(), null);

        Absence absence = new Absence();
        absence.setProfile(resolveProfile(dto.profileId()));
        absence.setType(dto.type());
        absence.setStartsOn(dto.startsOn());
        absence.setEndsOn(dto.endsOn());
        absence.setHalfDay(halfDay);
        absence.setStatus(AbsenceStatus.PENDING);
        absence.setNote(dto.note());
        absence.setRequestedBy(resolveProfileOrNull(requestedBy));

        return repository.save(absence);
    }

    public Absence update(UUID id, AbsenceUpsertDTO dto) {
        Absence absence = getById(id);

        if (absence.getStatus() != AbsenceStatus.PENDING) {
            throw new BusinessException(ErrorCode.ABSENCE_ALREADY_DECIDED);
        }

        AbsenceHalfDay halfDay = dto.halfDay() == null ? AbsenceHalfDay.NONE : dto.halfDay();
        validateDates(dto.startsOn(), dto.endsOn(), halfDay);
        validateNotBeforeHire(dto.profileId(), dto.startsOn());
        validateNoOverlap(dto.profileId(), dto.startsOn(), dto.endsOn(), id);

        absence.setProfile(resolveProfile(dto.profileId()));
        absence.setType(dto.type());
        absence.setStartsOn(dto.startsOn());
        absence.setEndsOn(dto.endsOn());
        absence.setHalfDay(halfDay);
        absence.setNote(dto.note());

        return repository.save(absence);
    }

    public Absence decide(UUID id, boolean approve, UUID decidedBy) {
        Absence absence = getById(id);

        if (absence.getStatus() != AbsenceStatus.PENDING) {
            throw new BusinessException(ErrorCode.ABSENCE_ALREADY_DECIDED);
        }

        absence.setStatus(approve ? AbsenceStatus.APPROVED : AbsenceStatus.REJECTED);
        absence.setApprovedBy(resolveProfileOrNull(decidedBy));
        absence.setApprovedAt(OffsetDateTime.now());
        return repository.save(absence);
    }

    /**
     * Justificar uma falta é marcar uma ausência já aprovada que cobre o dia — não
     * há um estado "falta justificada" separado, porque a ausência <b>é</b> a
     * justificação. Evita duas fontes de verdade para o mesmo dia.
     */
    public Absence justifyDay(UUID profileId, LocalDate day, AbsenceType type,
                              String note, UUID decidedBy) {
        validateNoOverlap(profileId, day, day, null);

        Absence absence = new Absence();
        absence.setProfile(resolveProfile(profileId));
        absence.setType(type);
        absence.setStartsOn(day);
        absence.setEndsOn(day);
        absence.setHalfDay(AbsenceHalfDay.NONE);
        absence.setStatus(AbsenceStatus.APPROVED);
        absence.setNote(note);
        absence.setRequestedBy(resolveProfileOrNull(decidedBy));
        absence.setApprovedBy(resolveProfileOrNull(decidedBy));
        absence.setApprovedAt(OffsetDateTime.now());

        return repository.save(absence);
    }

    public void softDelete(UUID id) {
        Absence absence = getById(id);
        absence.setDeletedAt(OffsetDateTime.now());
        repository.save(absence);
    }

    public Absence restore(UUID id) {
        Absence absence = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ABSENCE_NOT_FOUND, "Ausência " + id + " não encontrada"));

        if (absence.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.ABSENCE_NOT_DELETED);
        }
        validateNoOverlap(absence.getProfile().getId(), absence.getStartsOn(), absence.getEndsOn(), id);

        absence.setDeletedAt(null);
        return repository.save(absence);
    }

    // ── Justificativos ────────────────────────────────────────

    /**
     * Valida o MIME e o tamanho <b>antes</b> de subir — subir primeiro e validar
     * depois deixa órfãos no bucket, que foi o que aconteceu com as faturas em
     * 2026-09-18.
     */
    public AbsenceDocument attachDocument(UUID absenceId, MultipartFile file) {
        Absence absence = getById(absenceId);

        if (file == null || file.isEmpty()) {
            throw FileUploadException.empty(file == null ? "ficheiro" : file.getOriginalFilename());
        }

        String originalFilename = file.getOriginalFilename() == null
                ? "justificativo" : file.getOriginalFilename();
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

        if (!ALLOWED_MIME.contains(mime)) {
            throw new BusinessException(ErrorCode.ABSENCE_DOCUMENT_TYPE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw FileUploadException.sizeExceeded(originalFilename, file.getSize(), MAX_BYTES);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw StorageException.uploadError(originalFilename, e);
        }

        String safeName = storageService.sanitizeFileName(originalFilename);
        String key = String.format("attendance/absence/%s/%s_%s",
                absence.getId(), UUID.randomUUID().toString().substring(0, 8), safeName);

        try (InputStream in = new ByteArrayInputStream(content)) {
            storageService.upload(BUCKET, key, mime, in);
        } catch (IOException e) {
            throw StorageException.uploadError(originalFilename, e);
        }

        AbsenceDocument document = new AbsenceDocument();
        document.setAbsence(absence);
        document.setBucket(BUCKET);
        document.setStorageKey(key);
        document.setOriginalFilename(originalFilename);
        document.setMimeType(mime);
        // O que foi para o Storage, não o tamanho do upload recebido.
        document.setSizeBytes(content.length);
        document.setUploadedAt(OffsetDateTime.now());

        return documentRepository.save(document);
    }

    public void deleteDocument(UUID absenceId, UUID documentId) {
        Absence absence = getById(absenceId);

        AbsenceDocument document = absence.getDocuments().stream()
                .filter(candidate -> candidate.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ABSENCE_DOCUMENT_NOT_FOUND,
                        "Documento " + documentId + " não encontrado nesta ausência"));

        deleteQuietly(document.getBucket(), document.getStorageKey());
        absence.getDocuments().remove(document);
        documentRepository.delete(document);
    }

    /**
     * O erro do Storage fica num {@code warn}: a linha desaparece de qualquer forma,
     * e um ficheiro órfão é melhor do que uma ausência que não se consegue apagar.
     */
    private void deleteQuietly(String bucket, String key) {
        if (bucket == null || key == null) {
            return;
        }
        try {
            storageService.delete(bucket, key);
        } catch (Exception e) {
            log.warn("Não consegui apagar {} / {} no Storage: {}", bucket, key, e.getMessage());
        }
    }

    // ── Regras ────────────────────────────────────────────────

    private void validateDates(LocalDate startsOn, LocalDate endsOn, AbsenceHalfDay halfDay) {
        if (endsOn.isBefore(startsOn)) {
            throw new BusinessException(ErrorCode.ABSENCE_INVALID_DATES);
        }
        if (halfDay != AbsenceHalfDay.NONE && !startsOn.isEqual(endsOn)) {
            throw new BusinessException(ErrorCode.ABSENCE_HALF_DAY_ON_RANGE);
        }
    }

    /**
     * Duas ausências sobrepostas para a mesma pessoa não têm significado: qual delas
     * explicaria o dia? Recusa-se na marcação em vez de deixar o cálculo escolher.
     */
    private void validateNoOverlap(UUID profileId, LocalDate startsOn, LocalDate endsOn, UUID ignoreId) {
        boolean overlaps = repository.findForProfileOverlapping(profileId, startsOn, endsOn, null)
                .stream()
                .filter(absence -> !absence.getId().equals(ignoreId))
                .anyMatch(absence -> absence.getStatus() != AbsenceStatus.REJECTED);

        if (overlaps) {
            throw new BusinessException(ErrorCode.ABSENCE_OVERLAPS);
        }
    }

    private void validateNotBeforeHire(UUID profileId, LocalDate startsOn) {
        Employment employment = employmentRepository.findByProfileId(profileId).orElse(null);
        if (employment != null && startsOn.isBefore(employment.getHiredAt())) {
            throw new BusinessException(ErrorCode.ABSENCE_BEFORE_HIRE);
        }
    }

    private Profile resolveProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_PROFILE_NOT_FOUND, "Perfil " + profileId + " não encontrado"));
    }

    private Profile resolveProfileOrNull(UUID profileId) {
        return profileId == null ? null : profileRepository.findById(profileId).orElse(null);
    }
}
