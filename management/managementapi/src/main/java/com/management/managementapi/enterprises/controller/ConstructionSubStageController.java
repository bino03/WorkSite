package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.substage.ConstructionSubStageResponseDTO;
import com.management.managementapi.enterprises.dto.substage.CreateConstructionSubStageDTO;
import com.management.managementapi.enterprises.mapper.ConstructionSubStageMapper;
import com.management.managementapi.enterprises.model.ConstructionSubStage;
import com.management.managementapi.enterprises.service.ConstructionSubStageService;
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
@RequestMapping("/construction-sub-stages")
@RequiredArgsConstructor
public class ConstructionSubStageController {

    private final ConstructionSubStageService service;
    private final ConstructionSubStageMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @GetMapping("/stage/{stageId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<ConstructionSubStageResponseDTO>> listByStage(@PathVariable UUID stageId) {
        return ResponseEntity.ok(service.listByStage(stageId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionSubStageResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionSubStageResponseDTO> create(
            @Valid @RequestBody CreateConstructionSubStageDTO dto,
            HttpServletRequest request) {
        ConstructionSubStage created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_SUB_STAGE, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionSubStageResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateConstructionSubStageDTO dto,
            HttpServletRequest request) {
        ConstructionSubStage updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_SUB_STAGE, id, updated.getName(), null, request));

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionSubStage subStage = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_SUB_STAGE, id, subStage.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
