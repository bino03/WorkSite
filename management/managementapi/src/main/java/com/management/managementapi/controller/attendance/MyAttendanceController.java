package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.response.AbsenceResponseDTO;
import com.management.managementapi.dto.attendance.response.VacationBalanceDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ForbiddenException;
import com.management.managementapi.mapper.attendance.AbsenceMapper;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.attendance.AbsenceService;
import com.management.managementapi.service.attendance.HolidayService;
import com.management.managementapi.service.attendance.VacationBalanceService;

import lombok.RequiredArgsConstructor;

/**
 * "As minhas férias" (fase 5b da assiduidade, 2026-11-20): o funcionário vê as suas
 * ausências, o seu saldo de férias e os feriados, para o calendário. **Só leitura** —
 * marcar e aprovar continua a ser do admin no espaço Equipa.
 *
 * <p>Sempre sobre o próprio: o perfil vem da sessão, nenhuma rota recebe um id de perfil,
 * por isso não há como ver as férias de um colega.
 */
@RestController
@RequestMapping("/attendance/me")
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
@RequiredArgsConstructor
public class MyAttendanceController {

    /** Um calendário não precisa de mais do que um ano de cada vez. */
    private static final int MAX_RANGE_DAYS = 400;

    private final AbsenceService absenceService;
    private final VacationBalanceService balanceService;
    private final HolidayService holidayService;
    private final AbsenceMapper mapper;
    private final AuthContext authContext;

    @GetMapping("/absences")
    public List<AbsenceResponseDTO> myAbsences(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        requireRange(from, to);
        return absenceService.listForProfile(me(), from, to).stream()
                .map(absence -> mapper.toSummary(absence,
                        balanceService.workingDaysOf(absence, absence.getStartsOn().getYear())))
                .toList();
    }

    @GetMapping("/vacation-balance")
    public ResponseEntity<VacationBalanceDTO> myBalance(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(balanceService.forYear(me(), year == null ? Year.now().getValue() : year));
    }

    @GetMapping("/holidays")
    public List<Holiday> holidays(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        requireRange(from, to);
        return holidayService.listBetween(from, to);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BusinessException(ErrorCode.ABSENCE_INVALID_DATES);
        }
    }

    private UUID me() {
        return authContext.currentProfileId()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN, "Sem perfil associado a esta sessão"));
    }
}
