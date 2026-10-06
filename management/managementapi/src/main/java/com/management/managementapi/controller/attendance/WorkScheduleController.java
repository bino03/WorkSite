package com.management.managementapi.controller.attendance;

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
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.request.WorkScheduleUpsertDTO;
import com.management.managementapi.dto.attendance.response.WorkScheduleResponseDTO;
import com.management.managementapi.mapper.attendance.WorkScheduleMapper;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.WorkScheduleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * catálogo de horários de trabalho. Tudo só para ADMIN: na fase 1 da assiduidade
 * o funcionário não tem self-service (ver notes/roadmap/assiduidade.md, fase 5).
 */
@RestController
@RequestMapping("/attendance/work-schedules")
@RequiredArgsConstructor
public class WorkScheduleController {

    private final WorkScheduleService service;
    private final WorkScheduleMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── READ ──────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Page<WorkScheduleResponseDTO> list(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.list(pageable);
    }

    /** Zona de recuperação: os horários apagados, para poder restaurá-los. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/deleted")
    public List<WorkScheduleResponseDTO> listDeleted() {
        return service.listDeleted();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<WorkScheduleResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getResponseById(id));
    }

    // ── WRITE ─────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<WorkScheduleResponseDTO> create(
            @Valid @RequestBody WorkScheduleUpsertDTO dto,
            HttpServletRequest request) {

        WorkSchedule created = service.create(dto, authContext.currentProfileId().orElse(null));

        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logCreate(userId, currentUserName(),
                        EntityType.WORK_SCHEDULE, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<WorkScheduleResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkScheduleUpsertDTO dto,
            HttpServletRequest request) {

        WorkSchedule updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logEdit(userId, currentUserName(),
                        EntityType.WORK_SCHEDULE, id, updated.getName(), null, request));

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        WorkSchedule schedule = service.getById(id);

        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logDelete(userId, currentUserName(),
                        EntityType.WORK_SCHEDULE, id, schedule.getName(), request));

        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<WorkScheduleResponseDTO> restore(
            @PathVariable UUID id,
            HttpServletRequest request) {

        WorkSchedule restored = service.restore(id);

        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logRestore(userId, currentUserName(),
                        EntityType.WORK_SCHEDULE, id, restored.getName(), request));

        return ResponseEntity.ok(mapper.toResponse(restored));
    }

    private String currentUserName() {
        return authContext.currentUserName().orElse("unknown");
    }
}
