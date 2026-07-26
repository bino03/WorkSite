package com.management.managementapi.model.enums;

public enum EntityType {
    ENTERPRISE("enterprise"),
    USER("user"),
    CONSTRUCTION_STAGE("construction_stage"),
    CONSTRUCTION_SUB_STAGE("construction_sub_stage"),
    CONSTRUCTION_EXPENSE("construction_expense");

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
