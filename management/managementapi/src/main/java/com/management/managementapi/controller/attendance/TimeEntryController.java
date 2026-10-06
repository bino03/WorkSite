package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import com.management.managementapi.dto.attendance.request.TimeEntryUpsertDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryRevisionResponseDTO;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.TimeEntryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * picagens. Tudo `ADMIN` — na fase 1 é o admin que regista, e a picagem feita pelo
 * próprio funcionário (QR, self-service) é a fase 5 de notes/roadmap/assiduidade.md.
 *
 * <p>Não há endpoint de apagar a sério: picagens só têm soft-delete.
 */
@RestController
@RequestMapping("/attendance/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── READ ──────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Page<TimeEntryResponseDTO> listForProfile(
            @RequestParam UUID profileId,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.listForProfile(profileId, pageable);
    }

    /** As picagens de um dia local — 00:00 a 00:00 em `Europe/Lisbon`, não em UTC. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/day")
    public List<TimeEntryResponseDTO> listForDay(
            @RequestParam UUID profileId,
            @RequestParam LocalDate day) {
        return service.listForDay(profileId, day);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/deleted")
    public List<TimeEntryResponseDTO> listDeleted(@RequestParam UUID profileId) {
        return service.listDeletedForProfile(profileId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<TimeEntryResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getResponseById(id));
    }

    /** O rasto de auditoria: o que esta picagem dizia antes de cada alteração. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/revisions")
    public List<TimeEntryRevisionResponseDTO> revisions(@PathVariable UUID id) {
        return service.revisions(id);
    }

    // ── WRITE ─────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TimeEntryResponseDTO> register(
            @Valid @RequestBody TimeEntryUpsertDTO dto,
            HttpServletRequest request) {

        TimeEntry created = service.register(dto, actorId(), actorName());
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logCreate(userId, actorName(), EntityType.TIME_ENTRY,
                        created.getId(), created.getProfile().getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(service.getResponseById(created.getId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TimeEntryResponseDTO> correct(
            @PathVariable UUID id,
            @Valid @RequestBody TimeEntryUpsertDTO dto,
            HttpServletRequest request) {

        TimeEntry updated = service.correct(id, dto, actorId(), actorName());
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logEdit(userId, actorName(), EntityType.TIME_ENTRY,
                        id, updated.getProfile().getName(), null, request));

        return ResponseEntity.ok(service.getResponseById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        TimeEntry entry = service.getById(id);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logDelete(userId, actorName(), EntityType.TIME_ENTRY,
                        id, entry.getProfile().getName(), request));

        service.softDelete(id, reason, actorId(), actorName());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<TimeEntryResponseDTO> restore(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        TimeEntry restored = service.restore(id, reason, actorId(), actorName());
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logRestore(userId, actorName(), EntityType.TIME_ENTRY,
                        id, restored.getProfile().getName(), request));

        return ResponseEntity.ok(service.getResponseById(id));
    }

    private UUID actorId() {
        return authContext.currentProfileId().orElse(null);
    }

    private String actorName() {
        return authContext.currentUserName().orElse("unknown");
    }
}
