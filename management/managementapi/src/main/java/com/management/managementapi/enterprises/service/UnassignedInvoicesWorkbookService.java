package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoice.Scope;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.ExpenseRow;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.ExportFile;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.Look;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.Styles;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.service.HeavyWorkGate;
import com.management.managementapi.service.attendance.AttendanceZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STTotalsRowFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Os dois livros das faturas sem obra, que até aqui só se importavam:
 * {@code Despesas da empresa.xlsx} e {@code Faturas por identificar.xlsx}
 * (docs/excel-parity.md §9). As linhas são as do livro da obra, para o importador
 * ler as mesmas frases de pagamento de volta.
 *
 * Duas diferenças do §9, de propósito: a quarentena leva {@code NIF}, porque a app o
 * guarda e um backup não perde dados, e {@code Empreendimento} sai sempre vazio, porque
 * preenchido a fatura seria transferida ao voltar a entrar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnassignedInvoicesWorkbookService {

    static final String COMPANY_FILE_NAME = "Despesas da empresa.xlsx";
    static final String UNIDENTIFIED_FILE_NAME = "Faturas por identificar.xlsx";

    private static final String[] COMMON_HEADERS = {
            DespesasExcelImportService.HEADER_NUMBER, DespesasExcelImportService.HEADER_DATE,
            DespesasExcelImportService.HEADER_DESCRIPTION, DespesasExcelImportService.HEADER_AMOUNT,
            DespesasExcelImportService.HEADER_PAID, DespesasExcelImportService.HEADER_METHOD,
            DespesasExcelImportService.HEADER_BIZDOCS, DespesasExcelImportService.HEADER_OBSERVATIONS};
    private static final String[] COMPANY_EXTRA_HEADERS = {
            DespesasExcelImportService.HEADER_SUPPLIER, DespesasExcelImportService.HEADER_NIF};
    private static final String[] UNIDENTIFIED_EXTRA_HEADERS = {
            DespesasExcelImportService.HEADER_TARGET, DespesasExcelImportService.HEADER_SUPPLIER,
            DespesasExcelImportService.HEADER_NIF, DespesasExcelImportService.HEADER_POSSIBLE_ENTERPRISES,
            DespesasExcelImportService.HEADER_ASK_WHOM, DespesasExcelImportService.HEADER_QUARANTINED_SINCE};
    private static final double[] COMMON_WIDTHS = {20.66, 12.66, 55.66, 13.55, 14.66, 14.66, 10.33, 40.55};
    private static final double EXTRA_WIDTH = 24;
    private static final int POI_WIDTH_UNITS = 256;
    private static final int FIRST_EXTRA_COLUMN = COMMON_HEADERS.length;

    private final ConstructionInvoiceRepository invoiceRepository;
    private final BudgetExcelExportService excelExportService;
    private final HeavyWorkGate heavyWork;
    private final AttendanceZone zone;

    /** {@code COMPANY} ou {@code UNIDENTIFIED}. Sem faturas, quem chama nem pede: o importador recusa um livro vazio. */
    @Transactional(readOnly = true)
    public ExportFile export(Scope scope) {
        return heavyWork.run("exportação Excel das faturas sem obra", () -> doExport(scope));
    }

    private ExportFile doExport(Scope scope) {
        // a tabela do vault está ordenada pela Data, da mais recente para a mais antiga
        List<ConstructionInvoice> invoices = invoiceRepository.findAllByScopeWithoutEnterprise(scope).stream()
                .sorted(Comparator.comparing(ConstructionInvoice::getInvoiceDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<ExpenseRow> rows = excelExportService.rowsOfInvoicesWithoutRubrics(invoices);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BudgetExcelExportService.applyVaultTheme(workbook);
            write(workbook, scope, invoices, rows);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return new ExportFile(scope == Scope.UNIDENTIFIED ? UNIDENTIFIED_FILE_NAME : COMPANY_FILE_NAME, out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.error("Falha a gerar o Excel das faturas {}: {}", scope, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_WRITE_ERROR);
        }
    }

    private void write(XSSFWorkbook workbook, Scope scope, List<ConstructionInvoice> invoices, List<ExpenseRow> rows) {
        boolean quarantine = scope == Scope.UNIDENTIFIED;
        String[] extraHeaders = quarantine ? UNIDENTIFIED_EXTRA_HEADERS : COMPANY_EXTRA_HEADERS;
        int columnCount = FIRST_EXTRA_COLUMN + extraHeaders.length;

        Styles styles = new Styles(workbook);
        XSSFSheet sheet = workbook.createSheet(DespesasExcelImportService.sheetNameFor(scope));
        sheet.setZoom(115);

        Row header = sheet.createRow(0);
        header.setHeightInPoints(31.95f);
        CellStyle headerStyle = styles.get(Look.of(11).bold().color(BudgetExcelExportService.COLOR_WHITE)
                .fill(BudgetExcelExportService.COLOR_HEADER_DARK).center().wrap());
        for (int c = 0; c < columnCount; c++) {
            String title = c < FIRST_EXTRA_COLUMN ? COMMON_HEADERS[c] : extraHeaders[c - FIRST_EXTRA_COLUMN];
            BudgetExcelExportService.cell(header, c, title, headerStyle);
        }

        Look top = Look.of(11).v(VerticalAlignment.TOP);
        CellStyle plain = styles.get(top);
        CellStyle text = styles.get(top.format(BudgetExcelExportService.FORMAT_TEXT));
        CellStyle date = styles.get(top.format(BudgetExcelExportService.FORMAT_DATE).h(HorizontalAlignment.CENTER));
        CellStyle money = styles.get(top.format(BudgetExcelExportService.FORMAT_CURRENCY));
        CellStyle notes = styles.get(top.h(HorizontalAlignment.LEFT).wrap());

        for (int i = 0; i < rows.size(); i++) {
            ExpenseRow line = rows.get(i);
            Row row = sheet.createRow(i + 1);
            BudgetExcelExportService.cell(row, 0, line.number(), text);
            BudgetExcelExportService.cell(row, 1, line.date(), date);
            BudgetExcelExportService.cell(row, 2, line.description(), plain);
            BudgetExcelExportService.cell(row, 3, line.amount(), money);
            BudgetExcelExportService.cell(row, 4, line.paid() ? "x" : null, plain);
            BudgetExcelExportService.cell(row, 5, line.method(), plain);
            BudgetExcelExportService.cell(row, 6, line.bizdocs() ? "X" : null, plain);
            BudgetExcelExportService.cell(row, 7, line.observations(), notes);
            if (quarantine) {
                ConstructionInvoice invoice = invoices.get(i);
                BudgetExcelExportService.cell(row, 8, (String) null, plain);
                BudgetExcelExportService.cell(row, 9, line.supplierName(), plain);
                BudgetExcelExportService.cell(row, 10, line.supplierNif(), text);
                BudgetExcelExportService.cell(row, 11, invoice.getPossibleEnterprises(), plain);
                BudgetExcelExportService.cell(row, 12, invoice.getAskWhom(), plain);
                BudgetExcelExportService.cell(row, 13,
                        invoice.getCreatedAt() == null ? null : zone.dateOf(invoice.getCreatedAt()), date);
            } else {
                BudgetExcelExportService.cell(row, 8, line.supplierName(), plain);
                BudgetExcelExportService.cell(row, 9, line.supplierNif(), text);
            }
        }

        int totalsRow = rows.size() + 1;
        writeTotals(sheet, styles, scope, totalsRow, columnCount);
        BudgetExcelExportService.paintDoneMarks(sheet, totalsRow);

        for (int c = 0; c < columnCount; c++) {
            double width = c < COMMON_WIDTHS.length ? COMMON_WIDTHS[c] : EXTRA_WIDTH;
            sheet.setColumnWidth(c, (int) (width * POI_WIDTH_UNITS));
        }
    }

    private static void writeTotals(XSSFSheet sheet, Styles styles, Scope scope, int totalsRow, int columnCount) {
        String tableName = DespesasExcelImportService.tableNameFor(scope);
        Row totals = sheet.createRow(totalsRow);
        Look bold = Look.of(11).bold();
        BudgetExcelExportService.cell(totals, 0, "TOTAL", styles.get(bold));
        // a tabela tem de existir antes de a fórmula a nomear
        XSSFTable table = BudgetExcelExportService.createTable(sheet, tableName, 0, totalsRow, columnCount - 1, true);
        BudgetExcelExportService.formula(totals, 3, "SUBTOTAL(109," + tableName + "[Valor])",
                styles.get(bold.format(BudgetExcelExportService.FORMAT_CURRENCY)));
        for (CTTableColumn column : table.getCTTable().getTableColumns().getTableColumnList()) {
            if (DespesasExcelImportService.HEADER_AMOUNT.equals(column.getName())) {
                column.setTotalsRowFunction(STTotalsRowFunction.SUM);
            } else if (DespesasExcelImportService.HEADER_NUMBER.equals(column.getName())) {
                column.setTotalsRowLabel("TOTAL");
            }
        }
    }
}
