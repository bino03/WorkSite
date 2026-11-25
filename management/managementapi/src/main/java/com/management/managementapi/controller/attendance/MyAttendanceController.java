package com.management.managementapi.controller.attendance;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.request.MyVacationRequestDTO;
import com.management.managementapi.dto.attendance.response.AbsenceResponseDTO;
import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.VacationBalanceDTO;
import com.management.managementapi.dto.attendance.response.VacationRequestPreviewDTO;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.service.attendance.AttendanceSummaryService;
import jakarta.validation.Valid;
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
 * "As minhas férias e horas" (fase 5b da assiduidade, 2026-11-20 e 2026-11-23): o
 * funcionário vê as suas ausências, o seu saldo de férias, os feriados e as suas horas
 * do mês. A única escrita é **pedir férias** (fica {@code PENDING}) e anular um pedido
 * seu ainda por decidir — marcar outras ausências e aprovar continua a ser do admin.
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
    private final AttendanceSummaryService summaryService;
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

    /** As minhas horas do mês, dia a dia — o mesmo cálculo que o admin vê na Equipa. */
    @GetMapping("/summary/month")
    public AttendanceSummaryDTO myMonth(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return summaryService.forMonth(me(), month);
    }

    // ── Pedir férias (a única escrita do funcionário nesta área) ──

    /** Quantos dias um pedido gastaria e o que há disponível — o aviso de saldo, antes de pedir. */
    @GetMapping("/vacation-requests/preview")
    public VacationRequestPreviewDTO previewRequest(@RequestParam LocalDate startsOn,
                                                    @RequestParam LocalDate endsOn,
                                                    @RequestParam(required = false) AbsenceHalfDay halfDay) {
        requireRange(startsOn, endsOn);
        return balanceService.previewRequest(me(), startsOn, endsOn, halfDay);
    }

    @PostMapping("/vacation-requests")
    public ResponseEntity<AbsenceResponseDTO> requestVacation(@Valid @RequestBody MyVacationRequestDTO dto) {
        Absence created = absenceService.requestVacation(me(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSummary(created,
                balanceService.workingDaysOf(created, created.getStartsOn().getYear())));
    }

    /** Anular um pedido meu ainda por decidir. Depois de decidido, só pelo escritório. */
    @DeleteMapping("/vacation-requests/{id}")
    public ResponseEntity<Void> cancelRequest(@PathVariable UUID id) {
        absenceService.cancelOwnRequest(id, me());
        return ResponseEntity.noContent().build();
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
