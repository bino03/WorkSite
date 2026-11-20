package com.management.managementapi.controller.attendance;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.request.ClockRequestDTO;
import com.management.managementapi.dto.attendance.response.ClockCodeResponseDTO;
import com.management.managementapi.dto.attendance.response.ClockPreviewDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.ForbiddenException;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.ClockService;
import com.management.managementapi.service.attendance.TimeEntryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * picagem por QR (fase 5 de notes/roadmap/assiduidade.md). Duas metades:
 *
 * <ul>
 *   <li>{@code /codes/**} — {@code ADMIN}: o QR de cada obra (drawer da obra), criar/substituir e o PNG para imprimir.</li>
 *   <li>o resto — {@code ADMIN} ou {@code EMPLOYEE}, sempre sobre <b>o próprio</b>: o perfil
 *       vem do token, nunca do pedido, por isso não há id de outra pessoa para trocar.</li>
 * </ul>
 */
@RestController
@RequestMapping("/attendance/clock")
@RequiredArgsConstructor
public class ClockController {

    private final ClockService service;
    private final TimeEntryService timeEntryService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── Funcionário ───────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping("/preview")
    public ResponseEntity<ClockPreviewDTO> preview(@RequestParam("code") String code) {
        return ResponseEntity.ok(service.preview(code, me()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping
    public ResponseEntity<TimeEntryResponseDTO> clock(@Valid @RequestBody ClockRequestDTO dto,
                                                      HttpServletRequest request) {
        UUID me = me();
        TimeEntry created = service.clock(dto.code(), dto.direction(), me);
        activityLogger.logCreate(me, authContext.currentUserName().orElse("unknown"), EntityType.TIME_ENTRY,
                created.getId(), created.getProfile().getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(timeEntryService.getResponseById(created.getId()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping("/me/today")
    public ResponseEntity<List<TimeEntryResponseDTO>> myToday() {
        return ResponseEntity.ok(service.today(me()));
    }

    // ── Admin: QR das obras ───────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/{enterpriseId}")
    public ResponseEntity<ClockCodeResponseDTO> code(@PathVariable UUID enterpriseId) {
        // 204 = a obra ainda não tem QR: não é erro, é o estado inicial de todas as obras.
        return service.codeFor(enterpriseId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/codes/{enterpriseId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable UUID enterpriseId) {
        // no-store: depois de "gerar novo", o browser não pode mostrar o QR antigo da cache.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(service.qrPng(enterpriseId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    /** Cria o QR da obra, ou substitui o atual — o antigo deixa de picar logo. */
    @PostMapping("/codes/{enterpriseId}")
    public ResponseEntity<ClockCodeResponseDTO> generate(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.generate(enterpriseId));
    }

    private UUID me() {
        return authContext.currentProfileId()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN, "Sem perfil associado a esta sessão"));
    }
}
