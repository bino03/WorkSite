package com.management.managementapi.enterprises.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentCreateDTO;
import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentResponseDTO;
import com.management.managementapi.enterprises.service.InvoiceIncidentService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Inconsistências ("Por conciliar") — a página que lista o que ficou por acertar
 * entre faturas, tipicamente depois de uma transferência. Só ADMIN.
 */
@RestController
@RequestMapping("/invoice-incidents")
@RequiredArgsConstructor
public class InvoiceIncidentController {

    private final InvoiceIncidentService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /** Por resolver primeiro, depois os mais recentes. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<InvoiceIncidentResponseDTO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceIncidentResponseDTO get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceIncidentResponseDTO> create(
            @Valid @RequestBody InvoiceIncidentCreateDTO dto,
            HttpServletRequest request) {
        InvoiceIncidentResponseDTO created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.INVOICE_INCIDENT, created.id(), created.title(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceIncidentResponseDTO> resolve(
            @PathVariable UUID id,
            HttpServletRequest request) {
        InvoiceIncidentResponseDTO resolved = service.resolve(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.INVOICE_INCIDENT, id, resolved.title(), null, request));

        return ResponseEntity.ok(resolved);
    }
}
