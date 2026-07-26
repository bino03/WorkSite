package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.stage.ConstructionStageResponseDTO;
import com.management.managementapi.enterprises.dto.stage.CreateConstructionStageDTO;
import com.management.managementapi.enterprises.mapper.ConstructionStageMapper;
import com.management.managementapi.enterprises.model.ConstructionStage;
import com.management.managementapi.enterprises.service.ConstructionStageService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/construction-stages")
@RequiredArgsConstructor
public class ConstructionStageController {

    private final ConstructionStageService service;
    private final ConstructionStageMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<ConstructionStageResponseDTO>> listByEnterprise(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.listByEnterprise(enterpriseId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionStageResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionStageResponseDTO> create(
            @Valid @RequestBody CreateConstructionStageDTO dto,
            HttpServletRequest request) {
        ConstructionStage created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_STAGE, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionStageResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateConstructionStageDTO dto,
            HttpServletRequest request) {
        ConstructionStage updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_STAGE, id, updated.getName(), null, request));

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionStage stage = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_STAGE, id, stage.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
