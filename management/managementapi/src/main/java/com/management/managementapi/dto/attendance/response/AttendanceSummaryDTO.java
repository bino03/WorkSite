package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * o resumo de um período para um funcionário. Todos os números são derivados das
 * picagens e do horário em vigor em cada dia — nenhum está guardado.
 *
 * @param scheduleChanges quantos horários diferentes estiveram em vigor no período.
 *                        Mais do que um quer dizer que os totais atravessam uma
 *                        mudança de condições, e é informação que um relatório tem
 *                        de dar em vez de esconder.
 */
public record AttendanceSummaryDTO(
        UUID profileId,
        String profileName,
        LocalDate from,
        LocalDate to,
        long workedMinutes,
        long expectedMinutes,
        long overtimeMinutes,
        long latenessMinutes,
        int daysWorked,
        int daysMissing,
        int daysIncomplete,
        int daysOnLeave,
        int daysHoliday,
        int scheduleChanges,
        List<DayAttendanceDTO> days
) {}
