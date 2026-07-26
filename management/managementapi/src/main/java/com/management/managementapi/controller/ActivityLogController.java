package com.management.managementapi.controller;

import com.management.managementapi.dto.activity.ActivityLogFilterDTO;
import com.management.managementapi.dto.activity.ActivityLogListDTO;
import com.management.managementapi.dto.activity.ActivityLogResponseDTO;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    /**
     * GET /api/activities/recent
     * Atividades mais recentes — para o painel de dashboard.
     * Apenas ADMIN e EMPLOYEE têm acesso.
     */
    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Page<ActivityLogListDTO>> getRecentActivities(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getRecentActivities(pageable));
    }

    /**
     * GET /api/activities/user/{userId}
     * Atividades de um utilizador específico.
     * ADMIN vê qualquer utilizador; o próprio utilizador vê as suas.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Page<ActivityLogListDTO>> getUserActivities(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getUserActivities(userId, pageable));
    }

    /**
     * GET /api/activities/entity/{entityType}/{entityId}
     * Histórico completo de uma entidade (ex: imóvel, contacto).
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<ActivityLogResponseDTO>> getEntityActivities(
            @PathVariable String entityType,
            @PathVariable UUID entityId) {
        EntityType type = EntityType.fromString(entityType);
        return ResponseEntity.ok(activityLogService.getEntityActivities(type, entityId));
    }

    /**
     * POST /api/activities/filter
     * Filtrar atividades por utilizador, tipo, entidade e/ou range de datas.
     */
    @PostMapping("/filter")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Page<ActivityLogListDTO>> filterActivities(
            @RequestBody ActivityLogFilterDTO filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(activityLogService.filterActivities(filter, pageable));
    }
}
