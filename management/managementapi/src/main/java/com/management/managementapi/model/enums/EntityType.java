package com.management.managementapi.model.enums;

public enum EntityType {
    ENTERPRISE("enterprise"),
    USER("user"),
    BUDGET_ITEM("budget_item"),
    CONSTRUCTION_EXPENSE("construction_expense"),
    CONSTRUCTION_INVOICE("construction_invoice"),
    TASK("task"),

    // Mantidos só para leitura do histórico: o activity_log ainda tem linhas
    // destes tipos, das etapas/sub-etapas que a V15 substituiu pela árvore.
    CONSTRUCTION_STAGE("construction_stage"),
    CONSTRUCTION_SUB_STAGE("construction_sub_stage");

    private final String dbValue;

    EntityType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    @Override
    public String toString() {
        return dbValue;
    }

    public static EntityType fromString(String value) {
        if (value == null) return null;
        for (EntityType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown entity type: " + value);
    }
}
