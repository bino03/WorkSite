package com.management.managementapi.service.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

/**
 * o cálculo de um dia de assiduidade. Puro de propósito: recebe as picagens e o
 * horário já carregados, não vai buscar nada. É o que o torna testável sem mocks
 * e sem base de dados — e esta é a parte do módulo onde um erro é silencioso, não
 * uma exceção.
 *
 * <p>Nada do que isto devolve é guardado. As correções a uma picagem são uma
 * funcionalidade escolhida, logo qualquer total gravado ficaria a mentir na
 * primeira correção.
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {
    }

    /**
     * @param day       o dia local a calcular
     * @param entries   as picagens desse dia local, por ordem de instante
     * @param scheduled o dia do horário em vigor, ou null se não havia horário ou
     *                  se o horário não previa trabalho neste dia da semana
     * @param hasSchedule se existia um horário atribuído a cobrir este dia — serve
     *                  para distinguir "fim de semana" de "sem horário nenhum", que
     *                  são coisas diferentes e só uma delas é um problema
     * @param holiday   o feriado deste dia, ou null
     * @param absence   a ausência <b>aprovada</b> que cobre este dia, ou null. Uma
     *                  ausência pendente não entra: enquanto não for decidida, o dia
     *                  continua a ser o que as picagens dizem
     */
    public static DayAttendance forDay(LocalDate day,
                                       List<TimeEntry> entries,
                                       WorkScheduleDay scheduled,
                                       boolean hasSchedule,
                                       Holiday holiday,
                                       Absence absence,
                                       ZoneId zone) {

        long expectedMinutes = expected(scheduled, holiday, absence);
        LocalTime firstIn = firstLocalTime(entries, TimeDirection.IN, zone);
        LocalTime lastOut = lastLocalTime(entries, TimeDirection.OUT, zone);
        boolean incomplete = entries.size() % 2 != 0;

        Map<UUID, WorkedAtEnterprise> grossByEnterprise = pairedMinutesByEnterprise(entries);
        long grossMinutes = grossByEnterprise.values().stream().mapToLong(WorkedAtEnterprise::workedMinutes).sum();
        // A pausa desconta-se do bruto, e só se houve trabalho: descontar uma hora
        // de almoço a quem não apareceu daria um total negativo.
        long breakMinutes = (scheduled == null || grossMinutes == 0) ? 0 : scheduled.getBreakMinutes();
        long workedMinutes = Math.max(0, grossMinutes - breakMinutes);

        return new DayAttendance(
                day,
                workedMinutes,
                expectedMinutes,
                Math.max(0, workedMinutes - expectedMinutes),
                lateness(firstIn, scheduled, holiday, absence),
                status(entries, scheduled, hasSchedule, holiday, absence),
                firstIn,
                lastOut,
                incomplete,
                holiday == null ? null : holiday.getName(),
                absence == null ? null : absence.getType(),
                shareOut(grossByEnterprise, grossMinutes, workedMinutes));
    }

    /**
     * Num feriado ou num dia de ausência aprovada não se espera trabalho — logo não
     * há horas previstas, e por consequência tudo o que for trabalhado nesse dia
     * conta como extra. Meia ausência deixa meio dia de expectativa de pé.
     */
    private static long expected(WorkScheduleDay scheduled, Holiday holiday, Absence absence) {
        if (scheduled == null || holiday != null) {
            return 0;
        }
        long full = scheduled.expectedWork().toMinutes();
        if (absence == null) {
            return full;
        }
        return absence.getHalfDay() == AbsenceHalfDay.NONE ? 0 : full / 2;
    }

    /**
     * Soma os pares entrada→saída. A sequência é garantida à entrada pelo
     * {@code TimeEntryService}, mas um dia pode estar <b>incompleto</b> (alguém
     * esqueceu-se de picar a saída): a picagem sem par é ignorada em vez de o dia
     * inteiro ser descartado — o dia fica marcado como incompleto e o utilizador
     * corrige.
     *
     * <p>Soma-se por obra, e a obra de um par é a da <b>entrada</b> — é onde a pessoa
     * picou ao chegar. A chave null é "sem obra".
     */
    private static Map<UUID, WorkedAtEnterprise> pairedMinutesByEnterprise(List<TimeEntry> entries) {
        Map<UUID, WorkedAtEnterprise> byEnterprise = new LinkedHashMap<>();
        TimeEntry openIn = null;

        for (TimeEntry entry : entries) {
            if (entry.getDirection() == TimeDirection.IN) {
                openIn = entry;
            } else if (openIn != null) {
                long minutes = Duration.between(openIn.getHappenedAt(), entry.getHappenedAt()).toMinutes();
                Enterprise enterprise = openIn.getEnterprise();
                UUID enterpriseId = enterprise == null ? null : enterprise.getId();
                WorkedAtEnterprise soFar = byEnterprise.get(enterpriseId);
                byEnterprise.put(enterpriseId, new WorkedAtEnterprise(
                        enterpriseId,
                        enterprise == null ? null : enterprise.getName(),
                        minutes + (soFar == null ? 0 : soFar.workedMinutes())));
                openIn = null;
            }
        }
        return byEnterprise;
    }

    /**
     * A pausa desconta-se ao dia e não a um par, por isso reparte-se pelas obras na
     * proporção do tempo passado em cada uma (decisão de 2026-10-07): assim a soma das
     * obras bate sempre com o total do dia. O resto da divisão inteira vai para a obra
     * com mais tempo, para não se perderem minutos.
     */
    private static List<WorkedAtEnterprise> shareOut(Map<UUID, WorkedAtEnterprise> grossByEnterprise,
                                                     long grossMinutes, long workedMinutes) {
        if (grossMinutes == 0) {
            return List.of();
        }
        List<WorkedAtEnterprise> gross = new ArrayList<>(grossByEnterprise.values());
        WorkedAtEnterprise largest = gross.stream()
                .max(Comparator.comparingLong(WorkedAtEnterprise::workedMinutes))
                .orElseThrow();

        long allocated = 0;
        List<WorkedAtEnterprise> shares = new ArrayList<>();
        for (WorkedAtEnterprise enterprise : gross) {
            long share = enterprise.workedMinutes() * workedMinutes / grossMinutes;
            allocated += share;
            shares.add(new WorkedAtEnterprise(enterprise.enterpriseId(), enterprise.enterpriseName(), share));
        }

        long remainder = workedMinutes - allocated;
        int largestIndex = gross.indexOf(largest);
        WorkedAtEnterprise toTopUp = shares.get(largestIndex);
        shares.set(largestIndex, new WorkedAtEnterprise(
                toTopUp.enterpriseId(), toTopUp.enterpriseName(), toTopUp.workedMinutes() + remainder));
        return shares;
    }

    /**
     * Atraso = primeira entrada depois da hora prevista. Sem entrada não há atraso
     * (é falta, que é outra coisa), e sem horário não há hora a que chegar.
     */
    private static long lateness(LocalTime firstIn, WorkScheduleDay scheduled,
                                 Holiday holiday, Absence absence) {
        // Num feriado ou num dia de ausência não há hora a que chegar, e quem foi
        // trabalhar não deve levar um atraso por isso.
        if (holiday != null || absence != null) {
            return 0;
        }
        if (firstIn == null || scheduled == null || !firstIn.isAfter(scheduled.getStartTime())) {
            return 0;
        }
        return Duration.between(scheduled.getStartTime(), firstIn).toMinutes();
    }

    /**
     * A ordem importa. Um feriado e uma ausência aprovada ganham a "falta por
     * justificar" — era precisamente isso que faltava antes da fase 3: um dia de
     * férias aparecia como falta. Mas <b>não</b> ganham a "trabalhou": quem foi
     * trabalhar num feriado trabalhou, e o dia tem de o dizer.
     */
    private static DayStatus status(List<TimeEntry> entries, WorkScheduleDay scheduled,
                                   boolean hasSchedule, Holiday holiday, Absence absence) {
        if (!entries.isEmpty()) {
            return DayStatus.WORKED;
        }
        if (holiday != null) {
            return DayStatus.HOLIDAY;
        }
        if (absence != null) {
            return DayStatus.ON_LEAVE;
        }
        if (!hasSchedule) {
            return DayStatus.NO_SCHEDULE;
        }
        return scheduled == null ? DayStatus.NOT_SCHEDULED : DayStatus.MISSING;
    }

    private static LocalTime firstLocalTime(List<TimeEntry> entries, TimeDirection direction, ZoneId zone) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .findFirst()
                .map(entry -> entry.getHappenedAt().atZoneSameInstant(zone).toLocalTime())
                .orElse(null);
    }

    private static LocalTime lastLocalTime(List<TimeEntry> entries, TimeDirection direction, ZoneId zone) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .reduce((first, second) -> second)
                .map(entry -> entry.getHappenedAt().atZoneSameInstant(zone).toLocalTime())
                .orElse(null);
    }
}
