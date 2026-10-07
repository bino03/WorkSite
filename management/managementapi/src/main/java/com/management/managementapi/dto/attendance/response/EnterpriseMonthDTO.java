package com.management.managementapi.dto.attendance.response;

import java.util.List;
import java.util.UUID;

/**
 * as horas de um mês numa obra, e de quem são. {@code enterpriseId} null é o
 * tempo picado sem obra — aparece para se ver que existe, não se esconde.
 */
public record EnterpriseMonthDTO(
        UUID enterpriseId,
        String enterpriseName,
        long workedMinutes,
        List<EmployeeAtEnterprise> employees
) {

    /** @param daysWorked em quantos dias do mês esta pessoa trabalhou nesta obra */
    public record EmployeeAtEnterprise(
            UUID profileId,
            String profileName,
            long workedMinutes,
            int daysWorked
    ) {}
}
