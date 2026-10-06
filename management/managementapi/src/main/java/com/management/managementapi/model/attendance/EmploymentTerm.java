package com.management.managementapi.model.attendance;

import java.time.LocalDate;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * as condições de um {@link Employment} durante um período: que horário estava
 * atribuído e quantos dias de férias por ano. É o que permite que um relatório de
 * janeiro continue correto depois de mudar o horário em março.
 */
@Entity
@Table(name = "employment_term", schema = "attendance")
public class EmploymentTerm extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employment_id", nullable = false)
    private Employment employment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_schedule_id", nullable = false)
    private WorkSchedule workSchedule;

    @Column(name = "vacation_days_per_year", nullable = false)
    private int vacationDaysPerYear = 22;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Null = é o período em vigor. Só pode haver um por emprego. */
    @Column(name = "valid_to")
    private LocalDate validTo;

    public Employment getEmployment() { return employment; }
    public void setEmployment(Employment employment) { this.employment = employment; }

    public WorkSchedule getWorkSchedule() { return workSchedule; }
    public void setWorkSchedule(WorkSchedule workSchedule) { this.workSchedule = workSchedule; }

    public int getVacationDaysPerYear() { return vacationDaysPerYear; }
    public void setVacationDaysPerYear(int vacationDaysPerYear) { this.vacationDaysPerYear = vacationDaysPerYear; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

    /** Intervalo fechado nos dois extremos: o último dia de um período é dia desse período. */
    public boolean covers(LocalDate date) {
        if (date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }
}
