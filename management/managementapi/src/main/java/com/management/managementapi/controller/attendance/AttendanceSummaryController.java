package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.DayAttendanceDTO;
import com.management.managementapi.service.attendance.AttendanceSummaryService;

import lombok.RequiredArgsConstructor;

/**
 * horas, extras, atrasos e faltas — tudo derivado das picagens e do horário que
 * estava em vigor em cada dia. Nada disto está guardado em tabela nenhuma, por isso
 * só há leitura.
 */
@RestController
@RequestMapping("/attendance/summary")
@RequiredArgsConstructor
public class AttendanceSummaryController {

    private final AttendanceSummaryService service;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/day")
    public ResponseEntity<DayAttendanceDTO> day(
            @RequestParam UUID profileId,
            @RequestParam LocalDate day) {
        return ResponseEntity.ok(service.forDay(profileId, day));
    }

    /** A semana ISO (segunda a domingo) a que o dia indicado pertence. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/week")
    public ResponseEntity<AttendanceSummaryDTO> week(
            @RequestParam UUID profileId,
            @RequestParam LocalDate day) {
        return ResponseEntity.ok(service.forWeek(profileId, day));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/month")
    public ResponseEntity<AttendanceSummaryDTO> month(
            @RequestParam UUID profileId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(service.forMonth(profileId, month));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/range")
    public ResponseEntity<AttendanceSummaryDTO> range(
            @RequestParam UUID profileId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(service.forRange(profileId, from, to));
    }
}
