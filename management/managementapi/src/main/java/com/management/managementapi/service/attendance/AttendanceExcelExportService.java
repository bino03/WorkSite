package com.management.managementapi.service.attendance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.attendance.response.AttendanceMonthReportDTO;
import com.management.managementapi.dto.attendance.response.AttendanceSummaryDTO;
import com.management.managementapi.dto.attendance.response.DayAttendanceDTO;
import com.management.managementapi.dto.attendance.response.EnterpriseMonthDTO;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.TimeEntryRevision;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;
import com.management.managementapi.model.enums.TimeEntrySource;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.repository.attendance.TimeEntryRevisionRepository;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

import lombok.RequiredArgsConstructor;

/**
 * o mês de assiduidade num {@code .xlsx}: o que o relatório mostra e os registos de
 * onde saiu, para se poder arquivar e mostrar numa auditoria.
 *
 * <p>Gerado a pedido (decisão de 2026-10-06): a prova é a base de dados com as
 * revisões, e o ficheiro é uma vista dela. Por isso as folhas "Picagens" e
 * "Correções" vão sempre — incluindo as picagens anuladas, que são exatamente as
 * que uma auditoria quer ver.
 *
 * <p>Horas escrevem-se como fração de dia com formato {@code [h]:mm}: o Excel soma-as
 * e mostra 172:30 em vez de dar a volta às 24 h.
 */
@Service
@RequiredArgsConstructor
public class AttendanceExcelExportService {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String SHEET_EMPLOYEES = "Por funcionário";
    static final String SHEET_ENTERPRISES = "Por obra";
    static final String SHEET_DAYS = "Dias";
    static final String SHEET_ENTRIES = "Picagens";
    static final String SHEET_REVISIONS = "Correções";

    static final String NO_ENTERPRISE = "Sem obra";

    private static final String FORMAT_DURATION = "[h]:mm";
    private static final String FORMAT_DATE = "dd/mm/yyyy";
    private static final String FORMAT_DATE_TIME = "dd/mm/yyyy hh:mm";
    private static final double MINUTES_PER_DAY = 24 * 60;
    private static final int COLUMN_WIDTH = 18 * 256;

    private final AttendanceReportService reportService;
    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryRevisionRepository revisionRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AttendanceZone zone;

    public record ExportFile(String fileName, byte[] content) {}

    @Transactional(readOnly = true)
    public ExportFile exportMonth(YearMonth month) {
        AttendanceMonthReportDTO report = reportService.forMonth(month);
        List<TimeEntry> entries = timeEntryRepository.findAllForAudit(
                zone.startOfDay(month.atDay(1)), zone.startOfNextDay(month.atEndOfMonth()));
        List<TimeEntryRevision> revisions = entries.isEmpty()
                ? List.of()
                : revisionRepository.findByTimeEntryIdInOrderByCreatedAtAsc(
                        entries.stream().map(TimeEntry::getId).toList());

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeEmployees(workbook.createSheet(SHEET_EMPLOYEES), styles, report.employees());
            writeEnterprises(workbook.createSheet(SHEET_ENTERPRISES), styles, report.enterprises());
            writeDays(workbook.createSheet(SHEET_DAYS), styles, report.employees());
            writeEntries(workbook.createSheet(SHEET_ENTRIES), styles, entries);
            writeRevisions(workbook.createSheet(SHEET_REVISIONS), styles, revisions, entries);
            workbook.write(out);
            return new ExportFile("Assiduidade - " + month + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha a escrever o export de assiduidade", e);
        }
    }

    private void writeEmployees(Sheet sheet, Styles styles, List<AttendanceSummaryDTO> employees) {
        header(sheet, styles, "Funcionário", "Horas trabalhadas", "Horas previstas", "Extras", "Atrasos",
                "Dias trabalhados", "Faltas", "Dias incompletos", "Dias de ausência", "Feriados",
                "Mudanças de horário");
        int rowIndex = 1;
        for (AttendanceSummaryDTO employee : employees) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, employee.profileName());
            duration(row, 1, styles, employee.workedMinutes());
            duration(row, 2, styles, employee.expectedMinutes());
            duration(row, 3, styles, employee.overtimeMinutes());
            duration(row, 4, styles, employee.latenessMinutes());
            number(row, 5, employee.daysWorked());
            number(row, 6, employee.daysMissing());
            number(row, 7, employee.daysIncomplete());
            number(row, 8, employee.daysOnLeave());
            number(row, 9, employee.daysHoliday());
            number(row, 10, employee.scheduleChanges());
        }
    }

    /** Uma linha de total por obra, a negrito, e por baixo as pessoas que lá trabalharam. */
    private void writeEnterprises(Sheet sheet, Styles styles, List<EnterpriseMonthDTO> enterprises) {
        header(sheet, styles, "Obra", "Funcionário", "Horas", "Dias");
        int rowIndex = 1;
        for (EnterpriseMonthDTO enterprise : enterprises) {
            String name = enterpriseLabel(enterprise.enterpriseName());
            Row total = sheet.createRow(rowIndex++);
            text(total, 0, name).setCellStyle(styles.bold);
            text(total, 1, "Total").setCellStyle(styles.bold);
            duration(total, 2, styles, enterprise.workedMinutes()).setCellStyle(styles.boldDuration);

            for (EnterpriseMonthDTO.EmployeeAtEnterprise employee : enterprise.employees()) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, name);
                text(row, 1, employee.profileName());
                duration(row, 2, styles, employee.workedMinutes());
                number(row, 3, employee.daysWorked());
            }
        }
    }

    private void writeDays(Sheet sheet, Styles styles, List<AttendanceSummaryDTO> employees) {
        header(sheet, styles, "Funcionário", "Data", "Estado", "Entrada", "Saída", "Trabalhado", "Previsto",
                "Extra", "Atraso", "Incompleto", "Feriado", "Ausência", "Obras");
        int rowIndex = 1;
        for (AttendanceSummaryDTO employee : employees) {
            for (DayAttendanceDTO day : employee.days()) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, employee.profileName());
                date(row, 1, styles, day.date());
                text(row, 2, statusLabel(day.status()));
                text(row, 3, day.firstIn() == null ? null : day.firstIn().toString());
                text(row, 4, day.lastOut() == null ? null : day.lastOut().toString());
                duration(row, 5, styles, day.workedMinutes());
                duration(row, 6, styles, day.expectedMinutes());
                duration(row, 7, styles, day.overtimeMinutes());
                duration(row, 8, styles, day.latenessMinutes());
                text(row, 9, day.incomplete() ? "Sim" : null);
                text(row, 10, day.holidayName());
                text(row, 11, absenceLabel(day.absenceType()));
                text(row, 12, enterprisesOfDay(day));
            }
        }
    }

    private void writeEntries(Sheet sheet, Styles styles, List<TimeEntry> entries) {
        header(sheet, styles, "Funcionário", "Data e hora", "Sentido", "Obra", "Origem", "Registada por",
                "Nota", "Anulada em", "Registada em");
        int rowIndex = 1;
        for (TimeEntry entry : entries) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, entry.getProfile().getName());
            dateTime(row, 1, styles, entry.getHappenedAt());
            text(row, 2, directionLabel(entry.getDirection()));
            text(row, 3, enterpriseLabel(entry.getEnterprise() == null ? null : entry.getEnterprise().getName()));
            text(row, 4, sourceLabel(entry.getSource()));
            text(row, 5, entry.getRegisteredBy() == null ? null : entry.getRegisteredBy().getName());
            text(row, 6, entry.getNote());
            dateTime(row, 7, styles, entry.getDeletedAt());
            dateTime(row, 8, styles, entry.getCreatedAt());
        }
    }

    /**
     * Cada revisão guarda o estado <em>anterior</em> à alteração; o estado atual está na
     * folha "Picagens". A obra anterior vem só como id, por isso o nome resolve-se aqui.
     */
    private void writeRevisions(Sheet sheet, Styles styles, List<TimeEntryRevision> revisions,
                                List<TimeEntry> entries) {
        header(sheet, styles, "Alterada em", "Por", "Alteração", "Motivo", "Funcionário", "Picagem (atual)",
                "Antes: data e hora", "Antes: sentido", "Antes: obra", "Antes: nota", "Antes: anulada em");
        Map<UUID, TimeEntry> entriesById = entries.stream()
                .collect(Collectors.toMap(TimeEntry::getId, entry -> entry));
        Map<UUID, String> previousEnterpriseNames = enterpriseNames(revisions);

        int rowIndex = 1;
        for (TimeEntryRevision revision : revisions) {
            TimeEntry entry = entriesById.get(revision.getTimeEntryId());
            Row row = sheet.createRow(rowIndex++);
            dateTime(row, 0, styles, revision.getCreatedAt());
            text(row, 1, revision.getChangedByName());
            text(row, 2, changeLabel(revision.getChange()));
            text(row, 3, revision.getReason());
            text(row, 4, entry.getProfile().getName());
            dateTime(row, 5, styles, entry.getHappenedAt());
            dateTime(row, 6, styles, revision.getPreviousHappenedAt());
            text(row, 7, directionLabel(revision.getPreviousDirection()));
            text(row, 8, revision.getPreviousEnterpriseId() == null
                    ? null
                    : previousEnterpriseNames.get(revision.getPreviousEnterpriseId()));
            text(row, 9, revision.getPreviousNote());
            dateTime(row, 10, styles, revision.getPreviousDeletedAt());
        }
    }

    private Map<UUID, String> enterpriseNames(List<TimeEntryRevision> revisions) {
        Set<UUID> ids = new HashSet<>();
        revisions.forEach(revision -> {
            if (revision.getPreviousEnterpriseId() != null) {
                ids.add(revision.getPreviousEnterpriseId());
            }
        });
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            enterpriseRepository.findAllById(ids).forEach(enterprise -> names.put(enterprise.getId(), enterprise.getName()));
        }
        return names;
    }

    // ── células ─────────────────────────────────────────────

    private static void header(Sheet sheet, Styles styles, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.bold);
            sheet.setColumnWidth(i, COLUMN_WIDTH);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, titles.length - 1));
    }

    private static Cell text(Row row, int column, String value) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        return cell;
    }

    private static void number(Row row, int column, long value) {
        row.createCell(column).setCellValue(value);
    }

    private static Cell duration(Row row, int column, Styles styles, long minutes) {
        Cell cell = row.createCell(column);
        cell.setCellValue(minutes / MINUTES_PER_DAY);
        cell.setCellStyle(styles.duration);
        return cell;
    }

    private static void date(Row row, int column, Styles styles, LocalDate value) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(styles.date);
    }

    /** Um instante escreve-se na hora local da obra, nunca em UTC — é a hora que o trabalhador viu. */
    private void dateTime(Row row, int column, Styles styles, OffsetDateTime value) {
        Cell cell = row.createCell(column);
        if (value == null) {
            return;
        }
        LocalDateTime local = value.atZoneSameInstant(zone.zoneId()).toLocalDateTime();
        cell.setCellValue(local);
        cell.setCellStyle(styles.dateTime);
    }

    // ── rótulos ─────────────────────────────────────────────

    private static String enterprisesOfDay(DayAttendanceDTO day) {
        if (day.enterprises().size() < 2) {
            return day.enterprises().isEmpty() ? null : enterpriseLabel(day.enterprises().getFirst().enterpriseName());
        }
        return day.enterprises().stream()
                .map(worked -> enterpriseLabel(worked.enterpriseName()) + " " + hoursAndMinutes(worked.workedMinutes()))
                .collect(Collectors.joining(" · "));
    }

    private static String hoursAndMinutes(long minutes) {
        return "%d:%02d".formatted(minutes / 60, minutes % 60);
    }

    static String enterpriseLabel(String name) {
        return name == null ? NO_ENTERPRISE : name;
    }

    static String statusLabel(DayStatus status) {
        return switch (status) {
            case WORKED -> "Trabalhou";
            case MISSING -> "Falta";
            case NOT_SCHEDULED -> "Folga";
            case NO_SCHEDULE -> "Sem horário";
            case HOLIDAY -> "Feriado";
            case ON_LEAVE -> "Ausência";
        };
    }

    private static String absenceLabel(AbsenceType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case VACATION -> "Férias";
            case SICK_LEAVE -> "Baixa";
            case JUSTIFIED -> "Falta justificada";
            case UNJUSTIFIED -> "Falta injustificada";
            case OTHER -> "Outra";
        };
    }

    private static String directionLabel(TimeDirection direction) {
        if (direction == null) {
            return null;
        }
        return direction == TimeDirection.IN ? "Entrada" : "Saída";
    }

    private static String sourceLabel(TimeEntrySource source) {
        return switch (source) {
            case MANUAL -> "Manual";
        };
    }

    private static String changeLabel(TimeEntryChange change) {
        return switch (change) {
            case CREATE -> "Registo";
            case UPDATE -> "Correção";
            case DELETE -> "Anulação";
            case RESTORE -> "Reposição";
        };
    }

    /** Os estilos criam-se uma vez por livro: o Excel tem um limite de ~64 000. */
    private static final class Styles {
        final CellStyle bold;
        final CellStyle boldDuration;
        final CellStyle duration;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(XSSFWorkbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            short durationFormat = workbook.createDataFormat().getFormat(FORMAT_DURATION);

            bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            boldDuration = workbook.createCellStyle();
            boldDuration.setFont(boldFont);
            boldDuration.setDataFormat(durationFormat);

            duration = workbook.createCellStyle();
            duration.setDataFormat(durationFormat);

            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat(FORMAT_DATE));

            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat(FORMAT_DATE_TIME));
        }
    }
}
