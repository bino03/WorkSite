package com.management.managementapi.model.enums;

/** tipo de ausência. Só `VACATION` desconta do saldo de férias. */
public enum AbsenceType {
    VACATION,
    SICK_LEAVE,
    JUSTIFIED,
    UNJUSTIFIED,
    OTHER;

    public boolean countsAgainstVacationBalance() {
        return this == VACATION;
    }

    /**
     * Se a ausência justifica o dia — ou seja, se deixa de aparecer como falta por
     * justificar. Uma falta injustificada é registada como ausência para ficar
     * documentada, mas continua a ser uma falta: é o contrário de a esconder.
     */
    public boolean justifiesTheDay() {
        return this != UNJUSTIFIED;
    }
}
