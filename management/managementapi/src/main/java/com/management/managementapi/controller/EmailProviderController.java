package com.management.managementapi.controller;

import com.management.managementapi.dto.email.request.EmailProviderTestDTO;
import com.management.managementapi.dto.email.request.EmailProviderUpsertDTO;
import com.management.managementapi.dto.email.response.EmailProviderResponseDTO;
import com.management.managementapi.model.email.EmailProvider;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.email.EmailProviderService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Configuração SMTP — a tabela {@code settings.email_providers}.
 *
 * Tudo ADMIN: são credenciais de envio, e quem as controla controla os emails que
 * saem em nome da plataforma (convites e recuperação de password incluídos).
 */
@RestController
@RequestMapping("/settings/email-providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmailProviderController {

    private final EmailProviderService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<EmailProviderResponseDTO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.toResponse(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> create(
            @Valid @RequestBody EmailProviderUpsertDTO dto,
            HttpServletRequest request) {

        EmailProvider created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.EMAIL_PROVIDER, created.getId(), created.getProviderName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(service.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody EmailProviderUpsertDTO dto,
            HttpServletRequest request) {

        EmailProvider updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.EMAIL_PROVIDER, id, updated.getProviderName(), null, request));

        return ResponseEntity.ok(service.toResponse(updated));
    }

    /** Passa a ser o provedor usado nos emails do produto; desmarca o anterior. */
    @PatchMapping("/{id}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> setDefault(
            @PathVariable UUID id,
            HttpServletRequest request) {

        EmailProvider provider = service.setDefault(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.EMAIL_PROVIDER, id, provider.getProviderName(), null, request));

        return ResponseEntity.ok(service.toResponse(provider));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> activate(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.toResponse(setActiveAndLog(id, true, request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailProviderResponseDTO> deactivate(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.toResponse(setActiveAndLog(id, false, request)));
    }

    /**
     * Envia um email de teste com as credenciais <b>deste</b> provedor, esteja ele
     * predefinido ou não — é o que permite validar uma configuração antes de a pôr a
     * servir os convites.
     */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> test(
            @PathVariable UUID id,
            @Valid @RequestBody EmailProviderTestDTO dto) {

        service.sendTest(id, dto.to());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        EmailProvider provider = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.EMAIL_PROVIDER, id, provider.getProviderName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private EmailProvider setActiveAndLog(UUID id, boolean active, HttpServletRequest request) {
        EmailProvider provider = service.setActive(id, active);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.EMAIL_PROVIDER, id, provider.getProviderName(), null, request));

        return provider;
    }
}
