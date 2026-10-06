package com.management.managementapi.model.enums;

/** meio dia de ausência. Só se aplica a uma ausência de um dia só. */
public enum AbsenceHalfDay {
    NONE, MORNING, AFTERNOON;

    /** Quantos dias uma ausência deste tipo consome: meio dia conta 0,5. */
    public double dayWeight() {
        return this == NONE ? 1.0 : 0.5;
    }
}
