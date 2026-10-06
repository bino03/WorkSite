package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.dto.attendance.request.AbsenceUpsertDTO;
import com.management.managementapi.dto.attendance.response.AbsenceDocumentResponseDTO;
import com.management.managementapi.dto.attendance.response.AbsenceResponseDTO;
import com.management.managementapi.dto.attendance.response.VacationBalanceDTO;
import com.management.managementapi.mapper.attendance.AbsenceMapper;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.AbsenceService;
import com.management.managementapi.service.attendance.VacationBalanceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * ausências e saldo de férias. Tudo `ADMIN` — o funcionário a pedir as suas próprias
 * férias é a fase 5 de notes/roadmap/assiduidade.md.
 */
@RestController
@RequestMapping("/attendance/absences")
@RequiredArgsConstructor
public class AbsenceController {

    private final AbsenceService service;
    private final VacationBalanceService balanceService;
    private final AbsenceMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── READ ──────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AbsenceResponseDTO> listForProfile(
            @RequestParam UUID profileId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return service.listForProfile(profileId, from, to).stream().map(this::summary).toList();
    }

    /** Os dados do mapa de equipa: quem está fora, quando. O mapa em si é frontend. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/team")
    public List<AbsenceResponseDTO> team(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) AbsenceStatus status) {
        return service.listTeam(from, to, status).stream().map(this::summary).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/pending")
    public List<AbsenceResponseDTO> pending() {
        return service.listPending().stream().map(this::summary).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<AbsenceResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(detail(service.getById(id)));
    }

    /** O saldo de férias de um ano civil, em dias úteis. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/balance")
    public ResponseEntity<VacationBalanceDTO> balance(
            @RequestParam UUID profileId,
            @RequestParam(required = false) Integer year) {
        int target = year == null ? Year.now().getValue() : year;
        return ResponseEntity.ok(balanceService.forYear(profileId, target));
    }

    // ── WRITE ─────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<AbsenceResponseDTO> create(
            @Valid @RequestBody AbsenceUpsertDTO dto,
            HttpServletRequest request) {

        Absence created = service.create(dto, actorId());
        log(created, request, true);

        return ResponseEntity.status(HttpStatus.CREATED).body(detail(created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<AbsenceResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody AbsenceUpsertDTO dto,
            HttpServletRequest request) {

        Absence updated = service.update(id, dto);
        log(updated, request, false);

        return ResponseEntity.ok(detail(updated));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<AbsenceResponseDTO> approve(@PathVariable UUID id, HttpServletRequest request) {
        Absence decided = service.decide(id, true, actorId());
        log(decided, request, false);
        return ResponseEntity.ok(detail(decided));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<AbsenceResponseDTO> reject(@PathVariable UUID id, HttpServletRequest request) {
        Absence decided = service.decide(id, false, actorId());
        log(decided, request, false);
        return ResponseEntity.ok(detail(decided));
    }

    /**
     * Justificar uma falta: cria uma ausência já aprovada que cobre o dia. Não há
     * estado "falta justificada" à parte — a ausência <b>é</b> a justificação.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/justify")
    public ResponseEntity<AbsenceResponseDTO> justify(
            @RequestParam UUID profileId,
            @RequestParam LocalDate day,
            @RequestParam AbsenceType type,
            @RequestParam(required = false) String note,
            HttpServletRequest request) {

        Absence created = service.justifyDay(profileId, day, type, note, actorId());
        log(created, request, true);

        return ResponseEntity.status(HttpStatus.CREATED).body(detail(created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        Absence absence = service.getById(id);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logDelete(userId, actorName(), EntityType.ABSENCE,
                        id, absence.getProfile().getName(), request));

        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<AbsenceResponseDTO> restore(@PathVariable UUID id, HttpServletRequest request) {
        Absence restored = service.restore(id);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logRestore(userId, actorName(), EntityType.ABSENCE,
                        id, restored.getProfile().getName(), request));

        return ResponseEntity.ok(detail(restored));
    }

    // ── Justificativos ────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AbsenceDocumentResponseDTO> addDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toDocument(service.attachDocument(id, file)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        service.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────

    private AbsenceResponseDTO summary(Absence absence) {
        return mapper.toSummary(absence, workingDays(absence));
    }

    private AbsenceResponseDTO detail(Absence absence) {
        return mapper.toDetail(absence, workingDays(absence));
    }

    /** Os dias úteis contam-se no ano em que a ausência começa. */
    private double workingDays(Absence absence) {
        return balanceService.workingDaysOf(absence, absence.getStartsOn().getYear());
    }

    private void log(Absence absence, HttpServletRequest request, boolean created) {
        authContext.currentProfileId().ifPresent(userId -> {
            String name = absence.getProfile().getName();
            if (created) {
                activityLogger.logCreate(userId, actorName(), EntityType.ABSENCE, absence.getId(), name, request);
            } else {
                activityLogger.logEdit(userId, actorName(), EntityType.ABSENCE, absence.getId(), name, null, request);
            }
        });
    }

    private UUID actorId() {
        return authContext.currentProfileId().orElse(null);
    }

    private String actorName() {
        return authContext.currentUserName().orElse("unknown");
    }
}
