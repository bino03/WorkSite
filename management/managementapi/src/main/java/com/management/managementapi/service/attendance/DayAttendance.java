package com.management.managementapi.service.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.management.managementapi.model.enums.AbsenceType;

/**
 * o que se sabe de um dia de trabalho de um funcionário. Tudo derivado das
 * picagens, do horário em vigor nesse dia e dos feriados — nada disto é guardado.
 *
 * <p>Um total gravado mentiria na primeira correção, e as correções são uma
 * funcionalidade escolhida de propósito. Ver notes/roadmap/assiduidade.md.
 *
 * @param workedMinutes   minutos efetivamente trabalhados, já sem a pausa do horário
 * @param expectedMinutes minutos que o horário previa; 0 se não era dia de trabalho
 * @param overtimeMinutes o que passou do previsto; 0 se não passou
 * @param latenessMinutes atraso da primeira entrada face à hora prevista; 0 se não houve
 * @param status          como é que o dia se classifica
 * @param firstIn         primeira entrada local do dia, ou null se não houve nenhuma
 * @param lastOut         última saída local do dia, ou null
 * @param incomplete      número ímpar de picagens — falta uma saída (ou uma entrada)
 * @param holidayName     o nome do feriado, quando é feriado; null nos outros dias
 * @param absenceType     o tipo da ausência aprovada que cobre o dia, ou null
 * @param enterprises     os minutos trabalhados repartidos por obra; somam {@code workedMinutes}
 */
public record DayAttendance(
        LocalDate date,
        long workedMinutes,
        long expectedMinutes,
        long overtimeMinutes,
        long latenessMinutes,
        DayStatus status,
        LocalTime firstIn,
        LocalTime lastOut,
        boolean incomplete,
        String holidayName,
        AbsenceType absenceType,
        List<WorkedAtEnterprise> enterprises
) {

    public enum DayStatus {
        /** Trabalhou, e o dia está coerente. */
        WORKED,
        /** O horário previa trabalho e não há picagem nenhuma — falta por justificar. */
        MISSING,
        /** O horário não previa trabalho neste dia (fim de semana, horário parcial). */
        NOT_SCHEDULED,
        /** Não há horário atribuído que cubra este dia: nada a esperar, nada a cobrar. */
        NO_SCHEDULE,
        /** Feriado. Não era dia de trabalho, e por isso não é falta. */
        HOLIDAY,
        /** Ausência aprovada que cobre o dia (férias, baixa, falta justificada). */
        ON_LEAVE
    }

    public Duration worked() {
        return Duration.ofMinutes(workedMinutes);
    }

    /** Um dia que o utilizador tem de olhar: ou falta, ou está mal preenchido. */
    public boolean needsAttention() {
        return status == DayStatus.MISSING || incomplete;
    }
}
