package com.management.managementapi.service.attendance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.model.enums.HolidayScope;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.HolidayRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O teste que justifica o histórico de condições ter sido construído: um mês que
 * atravessa uma mudança de horário tem de ser calculado com o horário certo em cada
 * metade. Se o {@code termOn(dia)} deixasse de ser consultado por dia, nada falhava
 * — os números é que ficavam errados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceSummaryServiceTest {

    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private EmploymentRepository employmentRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private AbsenceRepository absenceRepository;

    private static final UUID PERFIL = UUID.randomUUID();
    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");

    private AttendanceSummaryService service() {
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(List.of());
        when(absenceRepository.findForProfileOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        return new AttendanceSummaryService(timeEntryRepository, employmentRepository,
                holidayRepository, absenceRepository, ZONE);
    }

    private AttendanceSummaryService serviceWith(List<Holiday> holidays, List<Absence> absences) {
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(holidays);
        when(absenceRepository.findForProfileOverlapping(any(), any(), any(), any())).thenReturn(absences);
        return new AttendanceSummaryService(timeEntryRepository, employmentRepository,
                holidayRepository, absenceRepository, ZONE);
    }

    private static Holiday feriado(LocalDate data, String nome) {
        Holiday holiday = new Holiday();
        holiday.setDate(data);
        holiday.setName(nome);
        holiday.setScope(HolidayScope.NATIONAL);
        return holiday;
    }

    private static Absence ausencia(AbsenceType type, LocalDate from, LocalDate to) {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        absence.setType(type);
        absence.setStartsOn(from);
        absence.setEndsOn(to);
        absence.setHalfDay(AbsenceHalfDay.NONE);
        absence.setStatus(AbsenceStatus.APPROVED);
        return absence;
    }

    /** Horário de segunda a sexta, com as horas indicadas e 1 h de almoço. */
    private static WorkSchedule horarioSemanal(String start, String end) {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setName(start + "–" + end);
        List<WorkScheduleDay> days = new ArrayList<>();
        for (DayOfWeek weekday : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            WorkScheduleDay day = new WorkScheduleDay();
            day.setSchedule(schedule);
            day.setDayOfWeek(weekday);
            day.setStartTime(LocalTime.parse(start));
            day.setEndTime(LocalTime.parse(end));
            day.setBreakMinutes(60);
            days.add(day);
        }
        schedule.setDays(days);
        return schedule;
    }

    private static EmploymentTerm periodo(WorkSchedule schedule, LocalDate from, LocalDate to) {
        EmploymentTerm term = new EmploymentTerm();
        term.setWorkSchedule(schedule);
        term.setValidFrom(from);
        term.setValidTo(to);
        term.setVacationDaysPerYear(22);
        return term;
    }

    private Employment emprego(LocalDate hiredAt, EmploymentTerm... terms) {
        Profile profile = new Profile();
        profile.setId(PERFIL);
        profile.setName("Funcionário");

        Employment employment = new Employment();
        employment.setProfile(profile);
        employment.setHiredAt(hiredAt);
        for (EmploymentTerm term : terms) {
            term.setEmployment(employment);
            employment.getTerms().add(term);
        }
        when(employmentRepository.findByProfileId(PERFIL)).thenReturn(Optional.of(employment));
        return employment;
    }

    /** Julho: Lisboa em UTC+1. */
    private static TimeEntry picagem(LocalDate dia, TimeDirection direction, int horaLocal) {
        TimeEntry entry = new TimeEntry();
        entry.setDirection(direction);
        entry.setHappenedAt(OffsetDateTime.of(dia, LocalTime.of(horaLocal - 1, 0), ZoneOffset.UTC));
        return entry;
    }

    private void picagens(TimeEntry... entries) {
        when(timeEntryRepository.findForProfileBetween(eq(PERFIL), any(), any()))
                .thenReturn(List.of(entries));
    }

    @Test
    @DisplayName("um mês que atravessa uma mudança de horário usa o horário certo em cada metade")
    void mesQueAtravessaMudancaDeHorario() {
        WorkSchedule oitoHoras = horarioSemanal("08:00", "17:00");   // 8h/dia
        WorkSchedule seteHoras = horarioSemanal("09:00", "17:00");   // 7h/dia
        emprego(LocalDate.of(2026, 1, 1),
                periodo(oitoHoras, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 15)),
                periodo(seteHoras, LocalDate.of(2026, 7, 16), null));
        picagens();

        // Quarta 15 (último dia do horário de 8h) e quinta 16 (primeiro do de 7h).
        AttendanceSummaryDTO resumo = service().forRange(
                PERFIL, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 16));

        assertThat(resumo.days()).hasSize(2);
        assertThat(resumo.days().get(0).expectedMinutes()).isEqualTo(480);
        assertThat(resumo.days().get(1).expectedMinutes()).isEqualTo(420);
        assertThat(resumo.expectedMinutes()).isEqualTo(900);
        assertThat(resumo.scheduleChanges()).isEqualTo(1);
    }

    @Test
    @DisplayName("um período sem mudanças reporta zero mudanças de horário")
    void periodoSemMudancas() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        picagens();

        AttendanceSummaryDTO resumo = service().forRange(
                PERFIL, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17));

        assertThat(resumo.scheduleChanges()).isZero();
    }

    @Test
    @DisplayName("o fim de semana não conta como falta, e a semana prevê 5 dias")
    void fimDeSemanaNaoContaComoFalta() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        picagens();

        // 2026-07-13 é uma segunda-feira.
        AttendanceSummaryDTO semana = service().forWeek(PERFIL, LocalDate.of(2026, 7, 15));

        assertThat(semana.days()).hasSize(7);
        assertThat(semana.from()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(semana.to()).isEqualTo(LocalDate.of(2026, 7, 19));
        assertThat(semana.expectedMinutes()).isEqualTo(5 * 480);
        assertThat(semana.daysMissing()).isEqualTo(5);

        assertThat(semana.days().get(5).status()).isEqualTo(DayStatus.NOT_SCHEDULED);
        assertThat(semana.days().get(6).status()).isEqualTo(DayStatus.NOT_SCHEDULED);
    }

    @Test
    @DisplayName("as horas picadas somam no dia certo e no total da semana")
    void somaAsHorasPicadas() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        LocalDate quarta = LocalDate.of(2026, 7, 15);
        picagens(picagem(quarta, TimeDirection.IN, 8), picagem(quarta, TimeDirection.OUT, 17));

        AttendanceSummaryDTO semana = service().forWeek(PERFIL, quarta);

        assertThat(semana.workedMinutes()).isEqualTo(480);
        assertThat(semana.daysWorked()).isEqualTo(1);
        assertThat(semana.daysMissing()).isEqualTo(4);
        assertThat(semana.days().get(2).workedMinutes()).isEqualTo(480);
    }

    @Test
    @DisplayName("o período todo é uma consulta só à base de dados, não uma por dia")
    void umaConsultaParaOPeriodoTodo() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        picagens();

        service().forMonth(PERFIL, YearMonth.of(2026, 7));

        verify(timeEntryRepository, times(1)).findForProfileBetween(eq(PERFIL), any(), any());
    }

    @Test
    @DisplayName("um funcionário sem dados de emprego não acumula faltas nem horas previstas")
    void semEmpregoNaoAcumulaFaltas() {
        when(employmentRepository.findByProfileId(PERFIL)).thenReturn(Optional.empty());
        picagens();

        AttendanceSummaryDTO resumo = service().forMonth(PERFIL, YearMonth.of(2026, 7));

        assertThat(resumo.days()).hasSize(31);
        assertThat(resumo.expectedMinutes()).isZero();
        assertThat(resumo.daysMissing()).isZero();
        assertThat(resumo.scheduleChanges()).isZero();
        assertThat(resumo.days().getFirst().status()).isEqualTo(DayStatus.NO_SCHEDULE);
    }

    @Test
    @DisplayName("antes da admissão não há horário em vigor, logo não há faltas")
    void antesDaAdmissaoNaoHaFaltas() {
        emprego(LocalDate.of(2026, 7, 16),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 7, 16), null));
        picagens();

        AttendanceSummaryDTO resumo = service().forRange(
                PERFIL, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17));

        // 13, 14, 15 são antes da admissão; 16 e 17 (quinta e sexta) são faltas.
        assertThat(resumo.daysMissing()).isEqualTo(2);
        assertThat(resumo.days().getFirst().status()).isEqualTo(DayStatus.NO_SCHEDULE);
    }

    @Test
    @DisplayName("um feriado no meio da semana não conta como falta e não é previsto")
    void feriadoNaSemana() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        when(timeEntryRepository.findForProfileBetween(eq(PERFIL), any(), any())).thenReturn(List.of());
        LocalDate quarta = LocalDate.of(2026, 7, 15);

        AttendanceSummaryDTO semana = serviceWith(
                List.of(feriado(quarta, "Feriado municipal")), List.of()).forWeek(PERFIL, quarta);

        assertThat(semana.daysHoliday()).isEqualTo(1);
        // Quatro faltas em vez de cinco, e um dia menos de horas previstas.
        assertThat(semana.daysMissing()).isEqualTo(4);
        assertThat(semana.expectedMinutes()).isEqualTo(4 * 480);
        assertThat(semana.days().get(2).holidayName()).isEqualTo("Feriado municipal");
    }

    @Test
    @DisplayName("uma semana de férias aprovadas não dá faltas nenhumas")
    void semanaDeFeriasNaoDaFaltas() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        when(timeEntryRepository.findForProfileBetween(eq(PERFIL), any(), any())).thenReturn(List.of());

        AttendanceSummaryDTO semana = serviceWith(List.of(),
                List.of(ausencia(AbsenceType.VACATION, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17))))
                .forWeek(PERFIL, LocalDate.of(2026, 7, 15));

        assertThat(semana.daysMissing()).isZero();
        assertThat(semana.daysOnLeave()).isEqualTo(5);
        assertThat(semana.expectedMinutes()).isZero();
    }

    @Test
    @DisplayName("uma falta injustificada registada como ausência continua a contar como falta")
    void faltaInjustificadaContinuaFalta() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        when(timeEntryRepository.findForProfileBetween(eq(PERFIL), any(), any())).thenReturn(List.of());
        LocalDate quarta = LocalDate.of(2026, 7, 15);

        AttendanceSummaryDTO semana = serviceWith(List.of(),
                List.of(ausencia(AbsenceType.UNJUSTIFIED, quarta, quarta)))
                .forWeek(PERFIL, quarta);

        // Registar uma falta injustificada documenta-a; não a transforma em justificada.
        assertThat(semana.daysOnLeave()).isZero();
        assertThat(semana.daysMissing()).isEqualTo(5);
    }

    @Test
    @DisplayName("os feriados e as ausências são uma consulta cada, não uma por dia")
    void umaConsultaPorColecao() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        when(timeEntryRepository.findForProfileBetween(eq(PERFIL), any(), any())).thenReturn(List.of());

        service().forMonth(PERFIL, YearMonth.of(2026, 7));

        verify(holidayRepository, times(1)).findByDateBetweenOrderByDateAsc(any(), any());
        verify(absenceRepository, times(1)).findForProfileOverlapping(any(), any(), any(), any());
    }

    @Test
    @DisplayName("o resumo de um dia só devolve esse dia")
    void resumoDeUmDia() {
        emprego(LocalDate.of(2026, 1, 1),
                periodo(horarioSemanal("08:00", "17:00"), LocalDate.of(2026, 1, 1), null));
        LocalDate quarta = LocalDate.of(2026, 7, 15);
        picagens(picagem(quarta, TimeDirection.IN, 8), picagem(quarta, TimeDirection.OUT, 17));

        assertThat(service().forDay(PERFIL, quarta).workedMinutes()).isEqualTo(480);
    }
}
