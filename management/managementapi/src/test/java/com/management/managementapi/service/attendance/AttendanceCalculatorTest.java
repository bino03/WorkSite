package com.management.managementapi.service.attendance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.model.enums.HolidayScope;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Esta é a camada onde um erro é silencioso: não lança exceção, devolve o número
 * errado. Um atraso a mais ou uma hora extra a menos não falha teste nenhum a não
 * ser que o teste exista.
 *
 * <p>O exemplo do roadmap — horário 08–17 com 1 h de almoço, entrada às 08:20,
 * saída às 18:00 → 20 min de atraso, 8 h normais e 1 h extra — está aqui como
 * teste, porque foi assim que a fase 2 foi especificada.
 */
class AttendanceCalculatorTest {

    private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
    private static final LocalDate QUARTA_DE_JULHO = LocalDate.of(2026, 7, 15);

    /** Em julho Lisboa está em UTC+1, logo a hora local menos uma hora dá o instante UTC. */
    private static OffsetDateTime local(int hora, int minuto) {
        return OffsetDateTime.of(2026, 7, 15, hora - 1, minuto, 0, 0, ZoneOffset.UTC);
    }

    private static TimeEntry entry(TimeDirection direction, int hora, int minuto) {
        TimeEntry entry = new TimeEntry();
        entry.setDirection(direction);
        entry.setHappenedAt(local(hora, minuto));
        return entry;
    }

    private static WorkScheduleDay horario(String start, String end, int pausa) {
        WorkScheduleDay day = new WorkScheduleDay();
        day.setDayOfWeek(DayOfWeek.WEDNESDAY);
        day.setStartTime(LocalTime.parse(start));
        day.setEndTime(LocalTime.parse(end));
        day.setBreakMinutes(pausa);
        return day;
    }

    private static DayAttendance calcular(List<TimeEntry> entries, WorkScheduleDay scheduled) {
        return AttendanceCalculator.forDay(QUARTA_DE_JULHO, entries, scheduled, true, null, null, LISBOA);
    }

    @Test
    @DisplayName("08–17 c/ 1h, entrada 08:20, saída 18:00 → 20min atraso, 8h40 trabalhadas, 40min extra")
    void exemploDoRoadmap() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 20), entry(TimeDirection.OUT, 18, 0)),
                horario("08:00", "17:00", 60));

        assertThat(day.latenessMinutes()).isEqualTo(20);
        // 08:20→18:00 são 9h40; menos a hora de almoço do horário, 8h40.
        assertThat(day.workedMinutes()).isEqualTo(520);
        assertThat(day.expectedMinutes()).isEqualTo(480);
        // Chegou 20 min tarde e saiu 1h tarde, logo trabalhou 40 min a mais — não 1h.
        // O roadmap dizia "1h extra": era erro de aritmética da especificação, e este
        // teste é que o apanhou. Corrigido lá a 2026-10-06.
        assertThat(day.overtimeMinutes()).isEqualTo(40);
        assertThat(day.status()).isEqualTo(DayStatus.WORKED);
        assertThat(day.incomplete()).isFalse();
    }

    @Test
    @DisplayName("a pausa do horário desconta-se mesmo que ninguém a pique")
    void pausaDescontaSemSerPicada() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 0), entry(TimeDirection.OUT, 17, 0)),
                horario("08:00", "17:00", 60));

        // 9 horas entre picagens, menos a hora de almoço que o horário declara.
        assertThat(day.workedMinutes()).isEqualTo(480);
        assertThat(day.overtimeMinutes()).isZero();
        assertThat(day.latenessMinutes()).isZero();
    }

    @Test
    @DisplayName("quem pica a saída ao almoço e volta não leva a pausa descontada duas vezes")
    void pausaNaoDescontaDuasVezes() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 0), entry(TimeDirection.OUT, 12, 0),
                        entry(TimeDirection.IN, 13, 0), entry(TimeDirection.OUT, 17, 0)),
                horario("08:00", "17:00", 60));

        // 4h + 4h = 8h de pares, menos a pausa declarada = 7h.
        // É intencional: o horário diz que há uma hora de pausa, e quem a picou
        // também a tem a descontar — senão duas pessoas com o mesmo horário e as
        // mesmas horas no local teriam totais diferentes só por uma ter picado.
        assertThat(day.workedMinutes()).isEqualTo(420);
    }

    @Test
    @DisplayName("um dia sem picagens, mas que o horário previa, é falta por justificar")
    void diaSemPicagensEhFalta() {
        DayAttendance day = calcular(List.of(), horario("08:00", "17:00", 60));

        assertThat(day.status()).isEqualTo(DayStatus.MISSING);
        assertThat(day.workedMinutes()).isZero();
        assertThat(day.expectedMinutes()).isEqualTo(480);
        assertThat(day.latenessMinutes()).isZero();
        assertThat(day.needsAttention()).isTrue();
    }

    @Test
    @DisplayName("um dia que o horário não previa não é falta — é fim de semana")
    void diaNaoPrevistoNaoEhFalta() {
        DayAttendance day = calcular(List.of(), null);

        assertThat(day.status()).isEqualTo(DayStatus.NOT_SCHEDULED);
        assertThat(day.expectedMinutes()).isZero();
        assertThat(day.needsAttention()).isFalse();
    }

    @Test
    @DisplayName("sem horário atribuído nenhum, o dia não é falta nem fim de semana")
    void semHorarioAtribuido() {
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO, List.of(), null, false, null, null, LISBOA);

        assertThat(day.status()).isEqualTo(DayStatus.NO_SCHEDULE);
        assertThat(day.needsAttention()).isFalse();
    }

    @Test
    @DisplayName("trabalhar num dia que o horário não previa conta tudo como extra")
    void trabalhoEmDiaNaoPrevistoEhTodoExtra() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 9, 0), entry(TimeDirection.OUT, 13, 0)),
                null);

        assertThat(day.workedMinutes()).isEqualTo(240);
        assertThat(day.expectedMinutes()).isZero();
        assertThat(day.overtimeMinutes()).isEqualTo(240);
        assertThat(day.latenessMinutes()).isZero();
        assertThat(day.status()).isEqualTo(DayStatus.WORKED);
    }

    @Test
    @DisplayName("esquecer-se de picar a saída marca o dia como incompleto sem perder o resto")
    void diaIncompleto() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 0), entry(TimeDirection.OUT, 12, 0),
                        entry(TimeDirection.IN, 13, 0)),
                horario("08:00", "17:00", 60));

        assertThat(day.incomplete()).isTrue();
        assertThat(day.needsAttention()).isTrue();
        // O par fechado (08–12) continua a contar; a entrada sem saída é ignorada.
        assertThat(day.workedMinutes()).isEqualTo(180);
        assertThat(day.lastOut()).isEqualTo(LocalTime.parse("12:00"));
    }

    @Test
    @DisplayName("chegar adiantado não dá atraso negativo")
    void chegarAdiantadoNaoDaAtrasoNegativo() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 7, 30), entry(TimeDirection.OUT, 17, 0)),
                horario("08:00", "17:00", 60));

        assertThat(day.latenessMinutes()).isZero();
        assertThat(day.overtimeMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("sair mais cedo não dá horas extra negativas")
    void sairMaisCedoNaoDaExtraNegativo() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 0), entry(TimeDirection.OUT, 14, 0)),
                horario("08:00", "17:00", 60));

        assertThat(day.workedMinutes()).isEqualTo(300);
        assertThat(day.expectedMinutes()).isEqualTo(480);
        assertThat(day.overtimeMinutes()).isZero();
    }

    @Test
    @DisplayName("a pausa não se desconta a quem não trabalhou — o total nunca é negativo")
    void semTrabalhoNaoDescontaPausa() {
        DayAttendance day = calcular(List.of(), horario("08:00", "17:00", 60));

        assertThat(day.workedMinutes()).isZero();
    }

    @Test
    @DisplayName("as horas de entrada e saída são locais, não UTC")
    void horasSaoLocais() {
        DayAttendance day = calcular(
                List.of(entry(TimeDirection.IN, 8, 20), entry(TimeDirection.OUT, 18, 0)),
                horario("08:00", "17:00", 60));

        assertThat(day.firstIn()).isEqualTo(LocalTime.parse("08:20"));
        assertThat(day.lastOut()).isEqualTo(LocalTime.parse("18:00"));
    }

    private static Holiday feriado(String nome) {
        Holiday holiday = new Holiday();
        holiday.setDate(QUARTA_DE_JULHO);
        holiday.setName(nome);
        holiday.setScope(HolidayScope.NATIONAL);
        return holiday;
    }

    private static Absence ausencia(AbsenceType type, AbsenceHalfDay halfDay) {
        Absence absence = new Absence();
        absence.setType(type);
        absence.setStartsOn(QUARTA_DE_JULHO);
        absence.setEndsOn(QUARTA_DE_JULHO);
        absence.setHalfDay(halfDay);
        absence.setStatus(AbsenceStatus.APPROVED);
        return absence;
    }

    @Test
    @DisplayName("um feriado não é falta — era o buraco que a fase 3 fechou")
    void feriadoNaoEhFalta() {
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO, List.of(), horario("08:00", "17:00", 60), true,
                feriado("Dia de Portugal"), null, LISBOA);

        assertThat(day.status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(day.holidayName()).isEqualTo("Dia de Portugal");
        assertThat(day.expectedMinutes()).isZero();
        assertThat(day.needsAttention()).isFalse();
    }

    @Test
    @DisplayName("um dia de férias aprovado não é falta")
    void feriasNaoSaoFalta() {
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO, List.of(), horario("08:00", "17:00", 60), true,
                null, ausencia(AbsenceType.VACATION, AbsenceHalfDay.NONE), LISBOA);

        assertThat(day.status()).isEqualTo(DayStatus.ON_LEAVE);
        assertThat(day.absenceType()).isEqualTo(AbsenceType.VACATION);
        assertThat(day.expectedMinutes()).isZero();
        assertThat(day.needsAttention()).isFalse();
    }

    @Test
    @DisplayName("meia ausência deixa meio dia de horas previstas de pé")
    void meiaAusenciaDeixaMeioDiaPrevisto() {
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO, List.of(), horario("08:00", "17:00", 60), true,
                null, ausencia(AbsenceType.VACATION, AbsenceHalfDay.MORNING), LISBOA);

        assertThat(day.expectedMinutes()).isEqualTo(240);
    }

    @Test
    @DisplayName("quem foi trabalhar num feriado trabalhou — e conta tudo como extra, sem atraso")
    void trabalharNumFeriado() {
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO,
                List.of(entry(TimeDirection.IN, 9, 30), entry(TimeDirection.OUT, 13, 30)),
                horario("08:00", "17:00", 60), true,
                feriado("Dia de Portugal"), null, LISBOA);

        assertThat(day.status()).isEqualTo(DayStatus.WORKED);
        assertThat(day.expectedMinutes()).isZero();
        assertThat(day.overtimeMinutes()).isEqualTo(180);
        // Entrou 1h30 depois da hora do horário, mas num feriado não há hora a cumprir.
        assertThat(day.latenessMinutes()).isZero();
    }

    @Test
    @DisplayName("uma falta injustificada registada continua a ser falta — registar não é esconder")
    void faltaInjustificadaContinuaFalta() {
        // O filtro de `justifiesTheDay()` é feito no AttendanceSummaryService, por
        // isso aqui a ausência chega como null — este teste documenta o contrato.
        DayAttendance day = AttendanceCalculator.forDay(
                QUARTA_DE_JULHO, List.of(), horario("08:00", "17:00", 60), true,
                null, null, LISBOA);

        assertThat(day.status()).isEqualTo(DayStatus.MISSING);
        assertThat(day.needsAttention()).isTrue();
    }

    @Test
    @DisplayName("no dia em que os relógios adiantam, as horas trabalhadas são as reais, não as do relógio")
    void diaDaMudancaDeHora() {
        // 2026-03-29: Lisboa passa de UTC+0 para UTC+1 às 01:00 locais. Quem entrou
        // às 00:30 (00:30Z) e saiu às 08:00 locais (07:00Z) esteve 6h30 de relógio
        // de parede "perdido", mas trabalhou 6h30 reais.
        OffsetDateTime entrada = OffsetDateTime.of(2026, 3, 29, 0, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime saida = OffsetDateTime.of(2026, 3, 29, 7, 0, 0, 0, ZoneOffset.UTC);

        TimeEntry in = new TimeEntry();
        in.setDirection(TimeDirection.IN);
        in.setHappenedAt(entrada);
        TimeEntry out = new TimeEntry();
        out.setDirection(TimeDirection.OUT);
        out.setHappenedAt(saida);

        DayAttendance day = AttendanceCalculator.forDay(
                LocalDate.of(2026, 3, 29), List.of(in, out), null, true, null, null, LISBOA);

        // O cálculo é sobre instantes, por isso dá o tempo real decorrido (6h30),
        // não a diferença das horas de parede (que seria 7h30).
        assertThat(day.workedMinutes()).isEqualTo(390);
        assertThat(day.firstIn()).isEqualTo(LocalTime.parse("00:30"));
        assertThat(day.lastOut()).isEqualTo(LocalTime.parse("08:00"));
    }
}
