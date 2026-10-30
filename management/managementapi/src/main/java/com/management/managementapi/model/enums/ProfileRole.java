package com.management.managementapi.model.enums;

import java.util.List;

public enum ProfileRole {
    ADMIN, EMPLOYEE, ACCOUNTANT;

    /** Quem gere o espaço Obras e recebe os avisos dele (vencimentos, prazos de rubrica). */
    public static final List<ProfileRole> WORKS_MANAGERS = List.of(ADMIN, ACCOUNTANT);
}
