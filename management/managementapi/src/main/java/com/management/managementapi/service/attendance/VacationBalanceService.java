package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.response.VacationBalanceDTO;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.HolidayRepository;

/**
 * o saldo de férias de um ano civil.
 *
 * <p>Conta <b>dias úteis</b>, não dias de calendário: 22 dias é o mínimo legal
 * português em dias úteis, e contar calendário daria saldos errados a quem
 * atravessa um fim de semana. Por isso precisa de três coisas — o horário em vigor
 * (que dias da semana a pessoa trabalha), os feriados, e as ausências.
 *
 * <p>É também por isso que isto não é um método na entidade {@code Absence}: ela
 * não conhece nenhuma das três.
 *
 * <p>{@code PENDING} desconta do disponível, {@code APPROVED} desconta do gozado.
 * Se as pendentes não descontassem, marcava-se o dobro dos dias que se tem e só se
 * descobria ao aprovar.
 */
@Service
public class VacationBalanceService {

    private final EmploymentRepository employmentRepository;
    private final AbsenceRepository absenceRepository;
    private final HolidayRepository holidayRepository;

    public VacationBalanceService(EmploymentRepository employmentRepository,
                                  AbsenceRepository absenceRepository,
                                  HolidayRepository holidayRepository) {
        this.employmentRepository = employmentRepository;
        this.absenceRepository = absenceRepository;
        this.holidayRepository = holidayRepository;
    }

    @Transactional(readOnly = true)
    public VacationBalanceDTO forYear(UUID profileId, int year) {
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate lastDay = LocalDate.of(year, 12, 31);

        Employment employment = employmentRepository.findByProfileId(profileId).orElse(null);
        int entitled = entitlement(employment, year);

        Set<LocalDate> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(firstDay, lastDay)
                .stream().map(holiday -> holiday.getDate()).collect(HashSet::new, Set::add, Set::addAll);

        List<Absence> vacations = absenceRepository
                .findForProfileOverlapping(profileId, firstDay, lastDay, null)
                .stream()
                .filter(absence -> absence.getType() == AbsenceType.VACATION)
                .filter(absence -> absence.getStatus() != AbsenceStatus.REJECTED)
                .toList();

        double taken = 0;
        double pending = 0;
        for (Absence absence : vacations) {
            double days = workingDays(absence, employment, holidays, year);
            if (absence.getStatus() == AbsenceStatus.APPROVED) {
                taken += days;
            } else {
                pending += days;
            }
        }

        return new VacationBalanceDTO(
                profileId,
                employment == null ? null : employment.getProfile().getName(),
                year,
                entitled,
                taken,
                pending,
                entitled - taken - pending);
    }

    /** Quantos dias úteis de uma ausência caem dentro do ano e contam como férias. */
    @Transactional(readOnly = true)
    public double workingDaysOf(Absence absence, int year) {
        Employment employment = employmentRepository.findByProfileId(absence.getProfile().getId())
                .orElse(null);
        Set<LocalDate> holidays = holidayRepository
                .findByDateBetweenOrderByDateAsc(absence.getStartsOn(), absence.getEndsOn())
                .stream().map(holiday -> holiday.getDate()).collect(HashSet::new, Set::add, Set::addAll);

        return workingDays(absence, employment, holidays, year);
    }

    /**
     * Os dias de uma ausência que são dias de trabalho: fora dos fins de semana (ou
     * do que o horário diga que não é dia de trabalho) e fora dos feriados. É isto
     * que faz 5 dias de férias que atravessam um feriado descerem 4 ao saldo.
     *
     * <p>Sem horário atribuído não há como saber que dias eram de trabalho, e por
     * isso conta-se cada dia de calendário — é a hipótese conservadora, e não
     * inventa um horário que ninguém atribuiu.
     */
    private double workingDays(Absence absence, Employment employment,
                               Set<LocalDate> holidays, int year) {
        double total = 0;

        for (LocalDate day = absence.getStartsOn();
             !day.isAfter(absence.getEndsOn());
             day = day.plusDays(1)) {

            if (day.getYear() != year || holidays.contains(day)) {
                continue;
            }
            if (!isWorkingDay(employment, day)) {
                continue;
            }
            total += absence.getHalfDay() == AbsenceHalfDay.NONE ? 1.0 : 0.5;
        }
        return total;
    }

    private boolean isWorkingDay(Employment employment, LocalDate day) {
        if (employment == null) {
            return true;
        }
        EmploymentTerm term = employment.termOn(day);
        if (term == null) {
            return false;
        }
        WorkSchedule schedule = term.getWorkSchedule();
        short weekday = (short) day.getDayOfWeek().getValue();
        return schedule.getDays().stream().anyMatch(scheduleDay -> scheduleDay.getWeekday() == weekday);
    }

    /**
     * Os dias a que tem direito nesse ano. Vem do período em vigor no fim do ano —
     * ou do último que o cobriu, se já saiu. 22 é só o default da coluna; o valor
     * real é por funcionário, como pedido.
     */
    private int entitlement(Employment employment, int year) {
        if (employment == null) {
            return 0;
        }
        EmploymentTerm atYearEnd = employment.termOn(LocalDate.of(year, 12, 31));
        if (atYearEnd != null) {
            return atYearEnd.getVacationDaysPerYear();
        }
        return employment.getTerms().stream()
                .filter(term -> term.getValidFrom().getYear() <= year)
                .reduce((first, second) -> second)
                .map(EmploymentTerm::getVacationDaysPerYear)
                .orElse(0);
    }
}
