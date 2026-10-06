package com.management.managementapi.model.enums;

/**
 * estado de uma ausência. `PENDING` já desconta do saldo disponível — senão
 * marcava-se o dobro dos dias que se tem — mas só `APPROVED` altera o cálculo do dia.
 */
public enum AbsenceStatus {
    PENDING, APPROVED, REJECTED
}
