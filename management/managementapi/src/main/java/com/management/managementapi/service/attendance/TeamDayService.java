package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.response.TeamDayPersonDTO;
import com.management.managementapi.mapper.attendance.TimeEntryMapper;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;

/**
 * o dia da equipa inteira, para o cartão "Quem está" do painel Hoje: quem tem ficha
 * nesse dia, a que horas o horário a esperava, e as picagens.
 *
 * <p>Existe porque o relatório do mês só guarda as obras de pares IN→OUT fechados —
 * quem picou entrada e ainda não saiu não tinha obra em lado nenhum. Duas consultas
 * para a equipa toda (fichas e picagens do dia), não uma por pessoa.
 */
@Service
@Transactional(readOnly = true)
public class TeamDayService {

    private final EmploymentRepository employmentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryMapper mapper;
    private final AttendanceZone zone;

    public TeamDayService(EmploymentRepository employmentRepository,
                          TimeEntryRepository timeEntryRepository,
                          TimeEntryMapper mapper,
                          AttendanceZone zone) {
        this.employmentRepository = employmentRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.mapper = mapper;
        this.zone = zone;
    }

    public List<TeamDayPersonDTO> forDay(LocalDate day) {
        Map<UUID, List<TimeEntry>> byProfile = timeEntryRepository
                .findBetween(zone.startOfDay(day), zone.startOfNextDay(day), null)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getProfile().getId()));

        return employmentRepository.findOverlapping(day, day).stream()
                .map(employment -> new TeamDayPersonDTO(
                        employment.getProfile().getId(),
                        employment.getProfile().getName(),
                        scheduledStart(employment, day),
                        mapper.toResponses(byProfile.getOrDefault(employment.getProfile().getId(), List.of()),
                                zone.zoneId())))
                .toList();
    }

    static LocalTime scheduledStart(Employment employment, LocalDate day) {
        EmploymentTerm term = employment.termOn(day);
        if (term == null) {
            return null;
        }
        short weekday = (short) day.getDayOfWeek().getValue();
        return term.getWorkSchedule().getDays().stream()
                .filter(scheduleDay -> scheduleDay.getWeekday() == weekday)
                .map(scheduleDay -> scheduleDay.getStartTime())
                .findFirst()
                .orElse(null);
    }
}
