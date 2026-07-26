package com.management.managementapi.model.enums;

public enum ActivityType {
    VIEW("view"),
    CREATE("create"),
    EDIT("edit"),
    DELETE("delete"),
    LOGIN("login"),
    LOGOUT("logout"),
    RESTORE("restore");

    private final String dbValue;

    ActivityType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    @Override
    public String toString() {
        return dbValue;
    }

    public static ActivityType fromString(String value) {
        if (value == null) return null;
        for (ActivityType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown activity type: " + value);
    }
}
