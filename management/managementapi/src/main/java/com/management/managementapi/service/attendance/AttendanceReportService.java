package com.management.managementapi.service.attendance;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.response.AttendanceMonthReportDTO;
import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.DayAttendanceDTO;
import com.management.managementapi.dto.attendance.response.EnterpriseMonthDTO;
import com.management.managementapi.dto.attendance.response.EnterpriseMonthDTO.EmployeeAtEnterprise;
import com.management.managementapi.repository.attendance.EmploymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * o relatório mensal: por funcionário e por obra. Não há cálculo novo aqui — os
 * números por pessoa são os do {@link AttendanceSummaryService}, e os por obra são
 * os mesmos dias reagrupados. É isso que garante que as duas vistas batem certo.
 */
@Service
@RequiredArgsConstructor
public class AttendanceReportService {

    private final EmploymentRepository employmentRepository;
    private final AttendanceSummaryService summaryService;

    @Transactional(readOnly = true)
    public AttendanceMonthReportDTO forMonth(YearMonth month) {
        List<AttendanceSummaryDTO> employees = employmentRepository
                .findOverlapping(month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(employment -> summaryService.forMonth(employment.getProfile().getId(), month))
                .toList();

        return new AttendanceMonthReportDTO(
                month.atDay(1),
                month.atEndOfMonth(),
                employees,
                byEnterprise(employees));
    }

    private static List<EnterpriseMonthDTO> byEnterprise(List<AttendanceSummaryDTO> employees) {
        // A chave null é "sem obra"; um HashMap aceita-a, um groupingBy não.
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Map<UUID, EmployeeAtEnterprise>> perEnterprise = new LinkedHashMap<>();

        for (AttendanceSummaryDTO employee : employees) {
            for (DayAttendanceDTO day : employee.days()) {
                for (WorkedAtEnterprise worked : day.enterprises()) {
                    names.put(worked.enterpriseId(), worked.enterpriseName());
                    perEnterprise
                            .computeIfAbsent(worked.enterpriseId(), id -> new LinkedHashMap<>())
                            .merge(employee.profileId(),
                                    new EmployeeAtEnterprise(employee.profileId(), employee.profileName(),
                                            worked.workedMinutes(), 1),
                                    AttendanceReportService::add);
                }
            }
        }

        List<EnterpriseMonthDTO> result = new ArrayList<>();
        perEnterprise.forEach((enterpriseId, people) -> {
            List<EmployeeAtEnterprise> sorted = people.values().stream()
                    .sorted(Comparator.comparing(EmployeeAtEnterprise::profileName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            result.add(new EnterpriseMonthDTO(
                    enterpriseId,
                    names.get(enterpriseId),
                    sorted.stream().mapToLong(EmployeeAtEnterprise::workedMinutes).sum(),
                    sorted));
        });

        // "Sem obra" no fim: o nome é null, e é o que menos interessa ver primeiro.
        result.sort(Comparator.comparing(EnterpriseMonthDTO::enterpriseName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    private static EmployeeAtEnterprise add(EmployeeAtEnterprise a, EmployeeAtEnterprise b) {
        return new EmployeeAtEnterprise(a.profileId(), a.profileName(),
                a.workedMinutes() + b.workedMinutes(), a.daysWorked() + b.daysWorked());
    }
}
