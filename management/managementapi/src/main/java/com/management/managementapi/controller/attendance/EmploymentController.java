package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.request.EmploymentTermDTO;
import com.management.managementapi.dto.attendance.request.EmploymentUpsertDTO;
import com.management.managementapi.dto.attendance.response.EmploymentResponseDTO;
import com.management.managementapi.mapper.attendance.EmploymentMapper;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.EmploymentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * dados de emprego por funcionário. Tudo `ADMIN`: o self-service é a fase 5 de
 * notes/roadmap/assiduidade.md.
 */
@RestController
@RequestMapping("/attendance/employments")
@RequiredArgsConstructor
public class EmploymentController {

    private final EmploymentService service;
    private final EmploymentMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<EmploymentResponseDTO> list() {
        return service.list();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{profileId}")
    public ResponseEntity<EmploymentResponseDTO> getByProfile(@PathVariable UUID profileId) {
        return ResponseEntity.ok(service.getResponseByProfileId(profileId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EmploymentResponseDTO> create(
            @Valid @RequestBody EmploymentUpsertDTO dto,
            HttpServletRequest request) {

        Employment created = service.create(dto);
        log(EntityType.EMPLOYMENT, created, request, true);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    /** As datas do vínculo. As condições mudam-se por {@code POST /{profileId}/terms}. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{profileId}/dates")
    public ResponseEntity<EmploymentResponseDTO> updateDates(
            @PathVariable UUID profileId,
            @RequestParam LocalDate hiredAt,
            @RequestParam(required = false) LocalDate endedAt,
            HttpServletRequest request) {

        Employment updated = service.updateDates(profileId, hiredAt, endedAt);
        log(EntityType.EMPLOYMENT, updated, request, false);

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    /**
     * Muda o horário e/ou os dias de férias a partir de uma data. Não altera o
     * período em vigor — fecha-o e abre um novo, para o passado não mudar.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{profileId}/terms")
    public ResponseEntity<EmploymentResponseDTO> addTerm(
            @PathVariable UUID profileId,
            @Valid @RequestBody EmploymentTermDTO dto,
            HttpServletRequest request) {

        Employment updated = service.addTerm(profileId, dto);
        log(EntityType.EMPLOYMENT, updated, request, false);

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    private void log(EntityType type, Employment employment, HttpServletRequest request, boolean created) {
        authContext.currentProfileId().ifPresent(userId -> {
            String userName = authContext.currentUserName().orElse("unknown");
            String name = employment.getProfile().getName();
            if (created) {
                activityLogger.logCreate(userId, userName, type, employment.getId(), name, request);
            } else {
                activityLogger.logEdit(userId, userName, type, employment.getId(), name, null, request);
            }
        });
    }
}
