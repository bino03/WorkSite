package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.request.HolidayUpsertDTO;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.attendance.HolidayService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * feriados. Sem eles, um dia de férias que cai num feriado conta a mais e um
 * feriado aparece como falta.
 */
@RestController
@RequestMapping("/attendance/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Holiday> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return (from == null || to == null) ? service.list() : service.listBetween(from, to);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Holiday> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Holiday> create(
            @Valid @RequestBody HolidayUpsertDTO dto,
            HttpServletRequest request) {

        Holiday created = service.create(dto);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logCreate(userId, userName(), EntityType.HOLIDAY,
                        created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Holiday> update(
            @PathVariable UUID id,
            @Valid @RequestBody HolidayUpsertDTO dto,
            HttpServletRequest request) {

        Holiday updated = service.update(id, dto);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logEdit(userId, userName(), EntityType.HOLIDAY,
                        id, updated.getName(), null, request));

        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        Holiday holiday = service.getById(id);
        authContext.currentProfileId().ifPresent(userId ->
                activityLogger.logDelete(userId, userName(), EntityType.HOLIDAY,
                        id, holiday.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String userName() {
        return authContext.currentUserName().orElse("unknown");
    }
}
