package com.management.managementapi.service.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.response.TeamDayPersonDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.mapper.attendance.TimeEntryMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.model.attendance.WorkScheduleDay;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;

/** O dia da equipa: a hora prevista vem do horário em vigor, e as picagens vão para a pessoa certa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamDayServiceTest {

    @Mock private EmploymentRepository employmentRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private TimeEntryMapper mapper;

    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");
    /** Uma segunda-feira. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);

    private static Employment employment(String name, DayOfWeek... weekdays) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);

        WorkSchedule schedule = new WorkSchedule();
        schedule.setId(UUID.randomUUID());
        List<WorkScheduleDay> days = new ArrayList<>();
        for (DayOfWeek weekday : weekdays) {
            WorkScheduleDay day = new WorkScheduleDay();
            day.setDayOfWeek(weekday);
            day.setStartTime(LocalTime.of(8, 0));
            day.setEndTime(LocalTime.of(17, 0));
            days.add(day);
        }
        schedule.setDays(days);

        Employment employment = new Employment();
        employment.setProfile(profile);
        employment.setHiredAt(LocalDate.of(2020, 1, 1));
        EmploymentTerm term = new EmploymentTerm();
        term.setEmployment(employment);
        term.setWorkSchedule(schedule);
        term.setValidFrom(LocalDate.of(2020, 1, 1));
        employment.getTerms().add(term);
        return employment;
    }

    @Test
    @DisplayName("cada pessoa com ficha vem com a hora de entrada do horário e só as suas picagens")
    void diaDaEquipa() {
        Employment ana = employment("Ana", DayOfWeek.MONDAY);
        Employment rui = employment("Rui", DayOfWeek.TUESDAY);
        when(employmentRepository.findOverlapping(SEGUNDA, SEGUNDA)).thenReturn(List.of(ana, rui));

        TimeEntry entrada = new TimeEntry();
        entrada.setProfile(ana.getProfile());
        entrada.setDirection(TimeDirection.IN);
        entrada.setHappenedAt(OffsetDateTime.of(2026, 11, 23, 8, 5, 0, 0, ZoneOffset.UTC));
        when(timeEntryRepository.findBetween(any(), any(), eq(null))).thenReturn(List.of(entrada));
        TimeEntryResponseDTO mapped = new TimeEntryResponseDTO(null, ana.getProfile().getId(), "Ana", null, null,
                entrada.getHappenedAt(), SEGUNDA, TimeDirection.IN, null, null, null, null);
        when(mapper.toResponses(anyList(), any())).thenAnswer(call ->
                ((List<?>) call.getArgument(0)).isEmpty() ? List.of() : List.of(mapped));

        List<TeamDayPersonDTO> people = new TeamDayService(employmentRepository, timeEntryRepository, mapper, ZONE)
                .forDay(SEGUNDA);

        assertThat(people).hasSize(2);
        assertThat(people.get(0).profileName()).isEqualTo("Ana");
        assertThat(people.get(0).scheduledStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(people.get(0).entries()).hasSize(1);
        // O Rui só trabalha à terça: na segunda é folga, sem hora prevista nem picagens.
        assertThat(people.get(1).scheduledStart()).isNull();
        assertThat(people.get(1).entries()).isEmpty();
    }
}
