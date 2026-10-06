package com.management.managementapi.model.attendance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * um dia da semana de um {@link WorkSchedule}: a que horas começa, a que horas acaba,
 * e quantos minutos de pausa se descontam. A pausa vive aqui porque ninguém a pica.
 *
 * <p>As horas são locais ({@code time} na base de dados, sem fuso): a entrada é às 08:00
 * em janeiro e em julho, mesmo que o instante UTC não seja o mesmo. O fuso aplica-se no
 * cálculo, não no armazenamento.
 */
@Entity
@Table(name = "work_schedule_day", schema = "attendance")
public class WorkScheduleDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private WorkSchedule schedule;

    /** 1 = segunda … 7 = domingo (ISO-8601, igual ao {@link DayOfWeek} do Java). */
    @Column(name = "weekday", nullable = false)
    private short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    public WorkSchedule getSchedule() { return schedule; }
    public void setSchedule(WorkSchedule schedule) { this.schedule = schedule; }

    public short getWeekday() { return weekday; }
    public void setWeekday(short weekday) { this.weekday = weekday; }

    public DayOfWeek getDayOfWeek() { return DayOfWeek.of(weekday); }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.weekday = (short) dayOfWeek.getValue(); }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }

    /** Horas de trabalho previstas neste dia, já sem a pausa. */
    public Duration expectedWork() {
        return Duration.between(startTime, endTime).minusMinutes(breakMinutes);
    }
}
