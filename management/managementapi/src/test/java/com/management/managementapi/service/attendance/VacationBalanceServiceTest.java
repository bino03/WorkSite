package com.management.managementapi.service.attendance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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

import com.management.managementapi.dto.attendance.response.VacationBalanceDTO;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.model.enums.HolidayScope;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.HolidayRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * O saldo conta <b>dias úteis</b> — 22 é o mínimo legal em dias úteis, não em dias
 * de calendário. Contar calendário é o erro óbvio e silencioso: daria 9 dias a uma
 * semana de férias de segunda a sexta da semana seguinte.
 *
 * <p>A verificação que o roadmap pedia está aqui: marcar 5 dias que atravessam um
 * feriado faz o saldo descer 4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VacationBalanceServiceTest {

    @Mock private EmploymentRepository employmentRepository;
    @Mock private AbsenceRepository absenceRepository;
    @Mock private HolidayRepository holidayRepository;

    private static final UUID PERFIL = UUID.randomUUID();
    private static final int ANO = 2026;

    private VacationBalanceService service() {
        return new VacationBalanceService(employmentRepository, absenceRepository, holidayRepository);
    }

    /** Horário de segunda a sexta — os fins de semana não são dias de trabalho. */
    private static WorkSchedule horarioSegundaASexta() {
        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setName("08–17");
        List<WorkScheduleDay> days = new ArrayList<>();
        for (DayOfWeek weekday : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            WorkScheduleDay day = new WorkScheduleDay();
            day.setDayOfWeek(weekday);
            day.setStartTime(LocalTime.of(8, 0));
            day.setEndTime(LocalTime.of(17, 0));
            day.setBreakMinutes(60);
            days.add(day);
        }
        schedule.setDays(days);
        return schedule;
    }

    private void emprego(int diasDeFerias) {
        Profile profile = new Profile();
        profile.setId(PERFIL);
        profile.setName("Funcionário");

        Employment employment = new Employment();
        employment.setProfile(profile);
        employment.setHiredAt(LocalDate.of(2020, 1, 1));

        EmploymentTerm term = new EmploymentTerm();
        term.setEmployment(employment);
        term.setWorkSchedule(horarioSegundaASexta());
        term.setVacationDaysPerYear(diasDeFerias);
        term.setValidFrom(LocalDate.of(2020, 1, 1));
        employment.getTerms().add(term);

        when(employmentRepository.findByProfileId(PERFIL)).thenReturn(Optional.of(employment));
    }

    private static Absence ferias(LocalDate from, LocalDate to, AbsenceStatus status) {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        absence.setType(AbsenceType.VACATION);
        absence.setStartsOn(from);
        absence.setEndsOn(to);
        absence.setHalfDay(AbsenceHalfDay.NONE);
        absence.setStatus(status);
        Profile profile = new Profile();
        profile.setId(PERFIL);
        absence.setProfile(profile);
        return absence;
    }

    private void ausencias(Absence... absences) {
        when(absenceRepository.findForProfileOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(absences));
    }

    private void feriados(LocalDate... datas) {
        List<Holiday> holidays = new ArrayList<>();
        for (LocalDate data : datas) {
            Holiday holiday = new Holiday();
            holiday.setDate(data);
            holiday.setName("Feriado");
            holiday.setScope(HolidayScope.NATIONAL);
            holidays.add(holiday);
        }
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any())).thenReturn(holidays);
    }

    @Test
    @DisplayName("sem férias marcadas, o saldo disponível são os dias a que tem direito")
    void saldoCheio() {
        emprego(22);
        ausencias();
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.entitled()).isEqualTo(22);
        assertThat(saldo.taken()).isZero();
        assertThat(saldo.available()).isEqualTo(22);
    }

    @Test
    @DisplayName("5 dias de férias de segunda a sexta descem 5 ao saldo")
    void semanaCompleta() {
        emprego(22);
        // 2026-07-13 é segunda, 2026-07-17 é sexta.
        ausencias(ferias(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), AbsenceStatus.APPROVED));
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isEqualTo(5.0);
        assertThat(saldo.available()).isEqualTo(17.0);
    }

    @Test
    @DisplayName("a verificação do roadmap: 5 dias que atravessam um feriado descem 4")
    void cincoDiasComUmFeriado() {
        emprego(22);
        ausencias(ferias(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), AbsenceStatus.APPROVED));
        feriados(LocalDate.of(2026, 7, 15));

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isEqualTo(4.0);
        assertThat(saldo.available()).isEqualTo(18.0);
    }

    @Test
    @DisplayName("férias que atravessam o fim de semana não gastam o sábado e o domingo")
    void fimDeSemanaNaoGastaSaldo() {
        emprego(22);
        // Segunda 13 a segunda 20: 8 dias de calendário, 6 dias úteis.
        ausencias(ferias(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20), AbsenceStatus.APPROVED));
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("um pedido pendente desconta do disponível, mas não do gozado")
    void pendenteDescontaDoDisponivel() {
        emprego(22);
        ausencias(
                ferias(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), AbsenceStatus.APPROVED),
                ferias(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7), AbsenceStatus.PENDING));
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isEqualTo(5.0);
        assertThat(saldo.pending()).isEqualTo(5.0);
        // Sem contar as pendentes, marcava-se o dobro dos dias que se tem.
        assertThat(saldo.available()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("um pedido recusado não gasta saldo nenhum")
    void recusadoNaoGastaSaldo() {
        emprego(22);
        ausencias(ferias(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), AbsenceStatus.REJECTED));
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isZero();
        assertThat(saldo.pending()).isZero();
        assertThat(saldo.available()).isEqualTo(22.0);
    }

    @Test
    @DisplayName("meio dia de férias gasta 0,5")
    void meioDiaGastaMeio() {
        emprego(22);
        Absence meio = ferias(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15), AbsenceStatus.APPROVED);
        meio.setHalfDay(AbsenceHalfDay.MORNING);
        ausencias(meio);
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.taken()).isEqualTo(0.5);
        assertThat(saldo.available()).isEqualTo(21.5);
    }

    @Test
    @DisplayName("os dias a que tem direito são por funcionário — 22 é só o default")
    void direitoEditavelPorFuncionario() {
        emprego(25);
        ausencias();
        feriados();

        assertThat(service().forYear(PERFIL, ANO).entitled()).isEqualTo(25);
    }

    @Test
    @DisplayName("umas férias que atravessam o ano só gastam os dias que caem no ano pedido")
    void feriasQueAtravessamOAno() {
        emprego(22);
        // Quarta 30 de dezembro de 2026 a sexta 1 de janeiro de 2027: 2 dias úteis
        // em 2026 (30 e 31) e 1 em 2027.
        ausencias(ferias(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 1), AbsenceStatus.APPROVED));
        feriados();

        assertThat(service().forYear(PERFIL, ANO).taken()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("sem dados de emprego não há direito a dias nem saldo")
    void semEmprego() {
        when(employmentRepository.findByProfileId(PERFIL)).thenReturn(Optional.empty());
        ausencias();
        feriados();

        VacationBalanceDTO saldo = service().forYear(PERFIL, ANO);

        assertThat(saldo.entitled()).isZero();
        assertThat(saldo.available()).isZero();
    }
}
