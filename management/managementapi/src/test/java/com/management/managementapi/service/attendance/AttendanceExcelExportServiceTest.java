package com.management.managementapi.service.attendance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.TimeEntryRevision;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.repository.attendance.TimeEntryRevisionRepository;
import com.management.managementapi.service.attendance.AttendanceExcelExportService.ExportFile;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * O ficheiro é para auditoria: o que interessa provar é que as picagens anuladas e
 * as correções lá estão, e que as horas saem na hora de Lisboa, não em UTC.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceExcelExportServiceTest {

    @Mock private AttendanceReportService reportService;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private TimeEntryRevisionRepository revisionRepository;
    @Mock private EnterpriseRepository enterpriseRepository;

    private static final YearMonth JULHO = YearMonth.of(2026, 7);
    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");

    private AttendanceExcelExportService service() {
        return new AttendanceExcelExportService(reportService, timeEntryRepository, revisionRepository,
                enterpriseRepository, ZONE);
    }

    private static Workbook read(ExportFile file) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    private static Enterprise obra(UUID id, String nome) {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(id);
        enterprise.setName(nome);
        return enterprise;
    }

    @Test
    @DisplayName("o livro tem as cinco folhas, as anuladas e as correções, e a hora local")
    void livroCompleto() throws IOException {
        UUID ana = UUID.randomUUID();
        Enterprise petrus = obra(UUID.randomUUID(), "Vila Petrus");
        Enterprise aleu = obra(UUID.randomUUID(), "Vila Aleu");

        DayAttendanceDTO dia = new DayAttendanceDTO(LocalDate.of(2026, 7, 15), 480, 480, 0, 0,
                DayStatus.WORKED, null, null, false, false, null, null,
                List.of(new WorkedAtEnterprise(petrus.getId(), "Vila Petrus", 480)));
        AttendanceSummaryDTO resumo = new AttendanceSummaryDTO(ana, "Ana", JULHO.atDay(1), JULHO.atEndOfMonth(),
                480, 480, 0, 0, 1, 0, 0, 0, 0, 0, List.of(dia));
        when(reportService.forMonth(JULHO)).thenReturn(new AttendanceMonthReportDTO(
                JULHO.atDay(1), JULHO.atEndOfMonth(), List.of(resumo),
                List.of(new EnterpriseMonthDTO(petrus.getId(), "Vila Petrus", 480,
                        List.of(new EmployeeAtEnterprise(ana, "Ana", 480, 1))))));

        Profile profile = new Profile();
        profile.setId(ana);
        profile.setName("Ana");

        TimeEntry viva = new TimeEntry();
        viva.setId(UUID.randomUUID());
        viva.setProfile(profile);
        viva.setEnterprise(petrus);
        viva.setDirection(TimeDirection.IN);
        // 07:00Z em julho = 08:00 em Lisboa.
        viva.setHappenedAt(OffsetDateTime.of(2026, 7, 15, 7, 0, 0, 0, ZoneOffset.UTC));

        TimeEntry anulada = new TimeEntry();
        anulada.setId(UUID.randomUUID());
        anulada.setProfile(profile);
        anulada.setDirection(TimeDirection.OUT);
        anulada.setHappenedAt(OffsetDateTime.of(2026, 7, 15, 20, 0, 0, 0, ZoneOffset.UTC));
        anulada.setDeletedAt(OffsetDateTime.of(2026, 7, 16, 9, 0, 0, 0, ZoneOffset.UTC));
        when(timeEntryRepository.findAllForAudit(any(), any())).thenReturn(List.of(viva, anulada));

        // A picagem viva foi corrigida: antes estava na Vila Aleu.
        TimeEntry antes = new TimeEntry();
        antes.setId(viva.getId());
        antes.setDirection(TimeDirection.IN);
        antes.setHappenedAt(viva.getHappenedAt());
        antes.setEnterprise(aleu);
        TimeEntryRevision correcao = TimeEntryRevision.before(antes, TimeEntryChange.UPDATE,
                UUID.randomUUID(), "Admin", "obra errada");
        when(revisionRepository.findByTimeEntryIdInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(List.of(correcao));
        when(enterpriseRepository.findAllById(any())).thenReturn(List.of(aleu));

        ExportFile file = service().exportMonth(JULHO);

        assertThat(file.fileName()).isEqualTo("Assiduidade - 2026-07.xlsx");
        try (Workbook workbook = read(file)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheetName(0)).isEqualTo(AttendanceExcelExportService.SHEET_EMPLOYEES);

            // 480 min = 1/3 de dia, que o formato [h]:mm mostra como 8:00.
            Row linhaDaAna = workbook.getSheet(AttendanceExcelExportService.SHEET_EMPLOYEES).getRow(1);
            assertThat(linhaDaAna.getCell(0).getStringCellValue()).isEqualTo("Ana");
            assertThat(linhaDaAna.getCell(1).getNumericCellValue()).isEqualTo(480 / 1440.0);
            assertThat(linhaDaAna.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("[h]:mm");

            Sheet entries = workbook.getSheet(AttendanceExcelExportService.SHEET_ENTRIES);
            assertThat(entries.getLastRowNum()).isEqualTo(2);
            assertThat(entries.getRow(1).getCell(1).getLocalDateTimeCellValue().getHour()).isEqualTo(8);
            assertThat(entries.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Vila Petrus");
            // A anulada está lá, sem obra e com a data de anulação em hora local.
            assertThat(entries.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Sem obra");
            assertThat(entries.getRow(2).getCell(7).getLocalDateTimeCellValue().getHour()).isEqualTo(10);

            Row revision = workbook.getSheet(AttendanceExcelExportService.SHEET_REVISIONS).getRow(1);
            assertThat(revision.getCell(2).getStringCellValue()).isEqualTo("Correção");
            assertThat(revision.getCell(3).getStringCellValue()).isEqualTo("obra errada");
            assertThat(revision.getCell(8).getStringCellValue()).isEqualTo("Vila Aleu");
        }
    }

    @Test
    @DisplayName("um mês sem picagens não consulta correções e gera o livro na mesma")
    void mesVazio() throws IOException {
        when(reportService.forMonth(JULHO)).thenReturn(new AttendanceMonthReportDTO(
                JULHO.atDay(1), JULHO.atEndOfMonth(), List.of(), List.of()));
        when(timeEntryRepository.findAllForAudit(any(), any())).thenReturn(List.of());

        try (Workbook workbook = read(service().exportMonth(JULHO))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(workbook.getSheet(AttendanceExcelExportService.SHEET_ENTRIES).getLastRowNum()).isZero();
        }
    }
}
