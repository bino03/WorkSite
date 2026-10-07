package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.management.managementapi.dto.attendance.response.AttendanceMonthReportDTO;
import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.DayAttendanceDTO;
import com.management.managementapi.dto.attendance.response.EnterpriseMonthDTO;
import com.management.managementapi.dto.attendance.response.EnterpriseMonthDTO.EmployeeAtEnterprise;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A vista por obra é a vista por pessoa reagrupada. Se as duas deixassem de bater
 * certo, nenhuma exceção o diria — só o relatório ficava a mentir numa das folhas.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock private EmploymentRepository employmentRepository;
    @Mock private AttendanceSummaryService summaryService;

    private static final YearMonth JULHO = YearMonth.of(2026, 7);
    private static final UUID PETRUS = UUID.randomUUID();
    private static final UUID ALEU = UUID.randomUUID();

    private static Employment emprego(UUID profileId, String nome) {
        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setName(nome);
        Employment employment = new Employment();
        employment.setProfile(profile);
        return employment;
    }

    private static DayAttendanceDTO dia(int diaDoMes, WorkedAtEnterprise... obras) {
        long total = 0;
        for (WorkedAtEnterprise obra : obras) {
            total += obra.workedMinutes();
        }
        return new DayAttendanceDTO(LocalDate.of(2026, 7, diaDoMes), total, 480, 0, 0,
                DayStatus.WORKED, null, null, false, false, null, null, List.of(obras));
    }

    private static AttendanceSummaryDTO resumo(UUID profileId, String nome, DayAttendanceDTO... dias) {
        long total = 0;
        for (DayAttendanceDTO dia : dias) {
            total += dia.workedMinutes();
        }
        return new AttendanceSummaryDTO(profileId, nome, JULHO.atDay(1), JULHO.atEndOfMonth(),
                total, 0, 0, 0, dias.length, 0, 0, 0, 0, 0, List.of(dias));
    }

    @Test
    @DisplayName("as horas por obra somam as horas por pessoa, com os dias contados por obra")
    void porObraBateComPorPessoa() {
        UUID ana = UUID.randomUUID();
        UUID rui = UUID.randomUUID();
        when(employmentRepository.findOverlapping(JULHO.atDay(1), JULHO.atEndOfMonth()))
                .thenReturn(List.of(emprego(ana, "Ana"), emprego(rui, "Rui")));
        when(summaryService.forMonth(ana, JULHO)).thenReturn(resumo(ana, "Ana",
                dia(1, new WorkedAtEnterprise(PETRUS, "Vila Petrus", 320), new WorkedAtEnterprise(ALEU, "Vila Aleu", 160)),
                dia(2, new WorkedAtEnterprise(PETRUS, "Vila Petrus", 480))));
        when(summaryService.forMonth(rui, JULHO)).thenReturn(resumo(rui, "Rui",
                dia(1, new WorkedAtEnterprise(null, null, 60), new WorkedAtEnterprise(ALEU, "Vila Aleu", 420))));

        AttendanceMonthReportDTO report = new AttendanceReportService(employmentRepository, summaryService)
                .forMonth(JULHO);

        assertThat(report.employees()).extracting(AttendanceSummaryDTO::profileName).containsExactly("Ana", "Rui");
        // Por nome, e "sem obra" (nome null) no fim.
        assertThat(report.enterprises()).extracting(EnterpriseMonthDTO::enterpriseName)
                .containsExactly("Vila Aleu", "Vila Petrus", null);

        EnterpriseMonthDTO aleu = report.enterprises().get(0);
        assertThat(aleu.workedMinutes()).isEqualTo(580);
        assertThat(aleu.employees()).containsExactly(
                new EmployeeAtEnterprise(ana, "Ana", 160, 1),
                new EmployeeAtEnterprise(rui, "Rui", 420, 1));

        EnterpriseMonthDTO petrus = report.enterprises().get(1);
        assertThat(petrus.employees()).containsExactly(new EmployeeAtEnterprise(ana, "Ana", 800, 2));

        long porObra = report.enterprises().stream().mapToLong(EnterpriseMonthDTO::workedMinutes).sum();
        long porPessoa = report.employees().stream().mapToLong(AttendanceSummaryDTO::workedMinutes).sum();
        assertThat(porObra).isEqualTo(porPessoa).isEqualTo(1440);
    }

    @Test
    @DisplayName("um mês sem ninguém vinculado dá um relatório vazio, não um erro")
    void mesSemNinguem() {
        when(employmentRepository.findOverlapping(JULHO.atDay(1), JULHO.atEndOfMonth())).thenReturn(List.of());

        AttendanceMonthReportDTO report = new AttendanceReportService(employmentRepository, summaryService)
                .forMonth(JULHO);

        assertThat(report.employees()).isEmpty();
        assertThat(report.enterprises()).isEmpty();
    }
}
