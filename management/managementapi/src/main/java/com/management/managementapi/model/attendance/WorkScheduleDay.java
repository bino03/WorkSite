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
 * e quando é o almoço. Desde a V48 o almoço pica-se — o horário diz quando devia ser,
 * para medir o regresso e reconhecer um almoço esquecido.
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

    /**
     * Derivado de {@link #lunchStart}/{@link #lunchEnd} desde a V48 — só existe para o
     * código anterior (a app online até ao deploy) continuar a ler a pausa. Não tem setter.
     */
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    /** Início do almoço, ou null se o dia é de um só período. Desde a V48 o almoço pica-se. */
    @Column(name = "lunch_start")
    private LocalTime lunchStart;

    @Column(name = "lunch_end")
    private LocalTime lunchEnd;

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

    public LocalTime getLunchStart() { return lunchStart; }
    public LocalTime getLunchEnd() { return lunchEnd; }

    /** As duas ou nenhuma; a pausa acompanha-as. */
    public void setLunch(LocalTime lunchStart, LocalTime lunchEnd) {
        this.lunchStart = lunchStart;
        this.lunchEnd = lunchEnd;
        this.breakMinutes = hasLunch() ? (int) Duration.between(lunchStart, lunchEnd).toMinutes() : 0;
    }

    public boolean hasLunch() {
        return lunchStart != null && lunchEnd != null;
    }

    /** Horas de trabalho previstas neste dia, já sem a pausa. */
    public Duration expectedWork() {
        return Duration.between(startTime, endTime).minusMinutes(breakMinutes);
    }
}
