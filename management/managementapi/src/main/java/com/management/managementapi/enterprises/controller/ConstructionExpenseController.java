package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.expense.ConstructionExpenseResponseDTO;
import com.management.managementapi.enterprises.dto.expense.CreateConstructionExpenseDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.service.ConstructionExpenseService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/construction-expenses")
@RequiredArgsConstructor
public class ConstructionExpenseController {

    private final ConstructionExpenseService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @GetMapping("/sub-stage/{subStageId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<ConstructionExpenseResponseDTO>> listBySubStage(@PathVariable UUID subStageId) {
        return ResponseEntity.ok(service.listBySubStage(subStageId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionExpenseResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.toResponseDTO(service.getById(id)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionExpenseResponseDTO> create(
            @Valid @RequestPart("expenseData") CreateConstructionExpenseDTO dto,
            @RequestPart(value = "invoiceFile", required = false) MultipartFile invoiceFile,
            HttpServletRequest request) {
        ConstructionExpense created = service.create(dto, invoiceFile);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(service.toResponseDTO(created));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionExpenseResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestPart("expenseData") CreateConstructionExpenseDTO dto,
            @RequestPart(value = "invoiceFile", required = false) MultipartFile invoiceFile,
            HttpServletRequest request) {
        ConstructionExpense updated = service.update(id, dto, invoiceFile);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, id, updated.getName(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionExpense expense = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, id, expense.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
