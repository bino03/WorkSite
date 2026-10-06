package com.management.managementapi.service.attendance;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.DayAttendanceDTO;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.HolidayRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

/**
 * os resumos de assiduidade: dia, semana, mês, intervalo arbitrário.
 *
 * <p>Junta as três peças que o cálculo precisa — as picagens do dia, o horário que
 * estava em vigor <em>nesse</em> dia (via {@code EmploymentTerm}) e o fuso — e
 * delega a aritmética no {@link AttendanceCalculator}, que é puro.
 *
 * <p>O horário é lido por dia e não uma vez para o período todo: é isso que faz um
 * mês que atravessa uma mudança de condições ser calculado com o horário certo em
 * cada metade.
 */
@Service
public class AttendanceSummaryService {

    private final TimeEntryRepository timeEntryRepository;
    private final EmploymentRepository employmentRepository;
    private final HolidayRepository holidayRepository;
    private final AbsenceRepository absenceRepository;
    private final AttendanceZone zone;

    public AttendanceSummaryService(TimeEntryRepository timeEntryRepository,
                                    EmploymentRepository employmentRepository,
                                    HolidayRepository holidayRepository,
                                    AbsenceRepository absenceRepository,
                                    AttendanceZone zone) {
        this.timeEntryRepository = timeEntryRepository;
        this.employmentRepository = employmentRepository;
        this.holidayRepository = holidayRepository;
        this.absenceRepository = absenceRepository;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryDTO forMonth(UUID profileId, YearMonth month) {
        return forRange(profileId, month.atDay(1), month.atEndOfMonth());
    }

    /** A semana ISO a que um dia pertence: segunda a domingo. */
    @Transactional(readOnly = true)
    public AttendanceSummaryDTO forWeek(UUID profileId, LocalDate anyDayOfWeek) {
        LocalDate monday = anyDayOfWeek.with(DayOfWeek.MONDAY);
        return forRange(profileId, monday, monday.plusDays(6));
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryDTO forRange(UUID profileId, LocalDate from, LocalDate to) {
        Employment employment = employmentRepository.findByProfileId(profileId).orElse(null);

        // Uma consulta para o período todo, agrupada por dia local — não uma por dia.
        Map<LocalDate, List<TimeEntry>> byDay = timeEntryRepository
                .findForProfileBetween(profileId, zone.startOfDay(from), zone.startOfNextDay(to))
                .stream()
                .collect(Collectors.groupingBy(entry -> zone.dateOf(entry.getHappenedAt())));

        // Uma consulta para os feriados e uma para as ausências do período todo,
        // pela mesma razão das picagens: não uma por dia.
        Map<LocalDate, Holiday> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(from, to)
                .stream()
                // Dois feriados na mesma data (um nacional e um municipal) são
                // possíveis; para o cálculo basta saber que o dia é feriado.
                .collect(Collectors.toMap(Holiday::getDate, holiday -> holiday, (first, second) -> first));

        List<Absence> approvedAbsences = absenceRepository.findForProfileOverlapping(
                profileId, from, to, AbsenceStatus.APPROVED);

        List<DayAttendance> days = new ArrayList<>();
        Set<UUID> schedulesSeen = new HashSet<>();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            EmploymentTerm term = employment == null ? null : employment.termOn(day);
            WorkScheduleDay scheduled = scheduledDay(term, day);

            if (term != null) {
                schedulesSeen.add(term.getWorkSchedule().getId());
            }

            days.add(AttendanceCalculator.forDay(
                    day,
                    byDay.getOrDefault(day, List.of()),
                    scheduled,
                    term != null,
                    holidays.get(day),
                    absenceCovering(approvedAbsences, day),
                    zone.zoneId()));
        }

        return summarise(profileId, employment, from, to, days, schedulesSeen.size());
    }

    @Transactional(readOnly = true)
    public DayAttendanceDTO forDay(UUID profileId, LocalDate day) {
        return forRange(profileId, day, day).days().getFirst();
    }

    /**
     * A ausência aprovada que cobre o dia. Só conta a que <b>justifica</b> o dia:
     * uma falta injustificada registada como ausência continua a ser uma falta —
     * registá-la serve para ficar documentada, não para a esconder.
     */
    private static Absence absenceCovering(List<Absence> absences, LocalDate day) {
        return absences.stream()
                .filter(absence -> absence.covers(day))
                .filter(absence -> absence.getType().justifiesTheDay())
                .findFirst()
                .orElse(null);
    }

    /**
     * O dia do horário que corresponde ao dia da semana. Null quer dizer que o
     * horário não prevê trabalho nesse dia — é assim que fins de semana e horários
     * parciais se exprimem, sem flag nenhuma.
     */
    private WorkScheduleDay scheduledDay(EmploymentTerm term, LocalDate day) {
        if (term == null) {
            return null;
        }
        short weekday = (short) day.getDayOfWeek().getValue();
        return term.getWorkSchedule().getDays().stream()
                .filter(scheduleDay -> scheduleDay.getWeekday() == weekday)
                .findFirst()
                .orElse(null);
    }

    private AttendanceSummaryDTO summarise(UUID profileId, Employment employment,
                                           LocalDate from, LocalDate to,
                                           List<DayAttendance> days, int schedulesSeen) {
        return new AttendanceSummaryDTO(
                profileId,
                employment == null ? null : employment.getProfile().getName(),
                from,
                to,
                days.stream().mapToLong(DayAttendance::workedMinutes).sum(),
                days.stream().mapToLong(DayAttendance::expectedMinutes).sum(),
                days.stream().mapToLong(DayAttendance::overtimeMinutes).sum(),
                days.stream().mapToLong(DayAttendance::latenessMinutes).sum(),
                (int) days.stream().filter(day -> day.status() == DayStatus.WORKED).count(),
                (int) days.stream().filter(day -> day.status() == DayStatus.MISSING).count(),
                (int) days.stream().filter(DayAttendance::incomplete).count(),
                (int) days.stream().filter(day -> day.status() == DayStatus.ON_LEAVE).count(),
                (int) days.stream().filter(day -> day.status() == DayStatus.HOLIDAY).count(),
                // Zero horários vistos (sem emprego) conta como zero mudanças, não como -1.
                Math.max(0, schedulesSeen - 1),
                days.stream().map(AttendanceSummaryService::toDto).toList());
    }

    private static DayAttendanceDTO toDto(DayAttendance day) {
        return new DayAttendanceDTO(
                day.date(),
                day.workedMinutes(),
                day.expectedMinutes(),
                day.overtimeMinutes(),
                day.latenessMinutes(),
                day.status(),
                day.firstIn(),
                day.lastOut(),
                day.incomplete(),
                day.needsAttention(),
                day.holidayName(),
                day.absenceType());
    }
}
