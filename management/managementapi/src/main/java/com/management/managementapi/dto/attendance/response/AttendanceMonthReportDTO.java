package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.util.List;

/**
 * o fecho de um mês: o mesmo tempo visto por pessoa e por obra. As horas por obra
 * somam as horas por pessoa — a pausa de um dia em duas obras reparte-se em
 * proporção, não se desconta duas vezes nem se perde.
 *
 * @param employees um resumo por funcionário vinculado em algum dia do mês,
 *                  incluindo quem entrou ou saiu a meio
 * @param enterprises as horas por obra; a obra null ("sem obra") vem no fim
 */
public record AttendanceMonthReportDTO(
        LocalDate from,
        LocalDate to,
        List<AttendanceSummaryDTO> employees,
        List<EnterpriseMonthDTO> enterprises
) {}
