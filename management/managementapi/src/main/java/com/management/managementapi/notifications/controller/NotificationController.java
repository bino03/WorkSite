package com.management.managementapi.notifications.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.notifications.dto.NotificationResponseDTO;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.security.AuthContext;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/**
 * As notificações do próprio.
 *
 * Não há rota para ver as de outra pessoa, nem sequer para ADMIN: o
 * destinatário vem sempre do token, nunca do URL. É o que dispensa qualquer
 * verificação de ownership nos handlers.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final AuthContext authContext;

    private UUID me() {
        return authContext.currentProfileId()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED));
    }

    @Operation(summary = "As minhas notificações, das mais recentes para as mais antigas")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<NotificationResponseDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(me(), PageRequest.of(page, size));
    }

    @Operation(summary = "Quantas estão por ler — é o contador do sino")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(me()));
    }

    @Operation(summary = "Marcar uma como lida")
    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public NotificationResponseDTO markRead(@PathVariable UUID id) {
        return service.markRead(id, me());
    }

    @Operation(summary = "Marcar todas as minhas como lidas")
    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        return ResponseEntity.ok(Map.of("updated", service.markAllRead(me())));
    }
}
