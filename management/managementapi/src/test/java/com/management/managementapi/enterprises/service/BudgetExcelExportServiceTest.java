package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetExportSummaryDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportRowDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A exportação para o {@code Despesas - <Obra>.xlsx} do vault.
 *
 * O que se prova aqui é o contrato de {@code docs/excel-parity.md}: a folha
 * "Orçamento inicial" volta a entrar pelo {@link BudgetExcelImportService} sem
 * perder a árvore (round-trip), a "Despesas" tem a {@code TabelaDespesas} com os
 * cabeçalhos, a linha de totais e as regras de linha (N despesas → N linhas,
 * nota de crédito negativa, parcial por liquidar, agregado "junto com"), e o
 * painel só sai com as tabelas de que as fórmulas dependem.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetExcelExportServiceTest {

    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ConstructionBudgetItemService budgetService;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private InvoicePaymentRepository invoicePaymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private InvoiceDocumentsExportService documentsExportService;

    @InjectMocks private BudgetExcelExportService service;

    // o importador real, para o round-trip
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private AuthContext authContext;
    @InjectMocks private BudgetExcelImportService importService;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private Enterprise enterprise;
    private final List<ConstructionInvoice> invoices = new ArrayList<>();
    private final List<ConstructionExpense> expenses = new ArrayList<>();
    private final Map<UUID, List<InvoicePaymentSummaryDTO>> payments = new HashMap<>();
    private final Map<UUID, BigDecimal> paid = new HashMap<>();

    // ── fixture ────────────────────────────────────────────────

    private BudgetItemNodeDTO item11, item12;

    @BeforeEach
    void setUp() {
        when(documentsExportService.plan(any())).thenReturn(new InvoiceDocumentsExportService.Plan(List.of(), 0, List.of()));
        enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setName("Vila Teste Claude");
        enterprise.setSlug("Vila Teste");
        enterprise.setIsTest(false);
        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));

        when(budgetService.getTree(ENTERPRISE_ID)).thenReturn(tree());
        when(invoiceRepository.findAllByEnterpriseIdForExport(ENTERPRISE_ID)).thenReturn(invoices);
        when(expenseRepository.findAllByEnterpriseId(ENTERPRISE_ID)).thenReturn(expenses);
        when(paymentService.paymentsForInvoices(any(), anyBoolean())).thenReturn(payments);
        when(invoicePaymentRepository.sumPaidByInvoices(any())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            return ids.stream().filter(paid::containsKey)
                    .map(id -> (InvoicePaymentRepository.InvoicePaidSum) new PaidSum(id, paid.get(id)))
                    .toList();
        });
    }

    private record PaidSum(UUID invoiceId, BigDecimal amount) implements InvoicePaymentRepository.InvoicePaidSum {
        @Override public UUID getInvoiceId() { return invoiceId; }
        @Override public BigDecimal getPaid() { return amount; }
    }

    /**
     * Uma fatia realista do orçamento: um capítulo simples, um capítulo com a
     * "Alternativa …" sem índice a trazer o preço do "8.4", e um capítulo com
     * sub-título e nota — os três casos que o importador reclassifica por
     * heurística e que o round-trip tem de aguentar.
     */
    private BudgetTreeDTO tree() {
        item11 = node("1.1", "Montagem do estaleiro", BudgetRowKind.ITEM, "un", "1", "600", "600", 1);
        item12 = node("1.2", "Desmontagem", BudgetRowKind.ITEM, "un", "1", "400", "400", 1);
        BudgetItemNodeDTO chapter1 = node("1", "ESTALEIRO", BudgetRowKind.ITEM, null, null, null, "1000", 0, item11, item12);

        BudgetItemNodeDTO sub841 = node("8.4.1", "Rebordo de vãos", BudgetRowKind.ITEM, "m", "513.34", "34.4", null, 2);
        BudgetItemNodeDTO alternative = node(null, "Alternativa em reforço de Etics", BudgetRowKind.ITEM, "m", "634.34", "19.66", "12471.12", 2);
        BudgetItemNodeDTO sub84 = node("8.4", "Capeamentos", BudgetRowKind.ITEM, null, null, null, null, 1, sub841, alternative);
        BudgetItemNodeDTO chapter8 = node("8", "REVESTIMENTOS EXTERIORES", BudgetRowKind.ITEM, null, null, null, "135519.25", 0, sub84);

        BudgetItemNodeDTO note = node(null, "(inclui pintura)", BudgetRowKind.NOTE, null, null, null, null, 3);
        BudgetItemNodeDTO item91 = node("9.1", "Reboco", BudgetRowKind.ITEM, "m2", "10", "10", "100", 2, note);
        BudgetItemNodeDTO heading = node(null, "Paredes", BudgetRowKind.HEADING, null, null, null, null, 1, item91);
        BudgetItemNodeDTO chapter9 = node("9", "ACABAMENTOS", BudgetRowKind.ITEM, null, null, null, "100", 0, heading);

        List<BudgetItemNodeDTO> roots = List.of(chapter1, chapter8, chapter9);
        BigDecimal total = roots.stream().map(BudgetItemNodeDTO::rolledUpBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetTreeDTO(ENTERPRISE_ID, enterprise.getName(), total, BigDecimal.ZERO, total,
                BigDecimal.ZERO, 11, 0, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, roots);
    }

    private static BudgetItemNodeDTO node(String code, String name, BudgetRowKind kind, String unit,
                                          String quantity, String unitPrice, String totalPrice, int depth,
                                          BudgetItemNodeDTO... children) {
        List<BudgetItemNodeDTO> kids = List.of(children);
        BigDecimal own = totalPrice == null ? null : new BigDecimal(totalPrice);
        BigDecimal childSum = kids.stream().map(BudgetItemNodeDTO::rolledUpBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean childHasPrice = kids.stream().anyMatch(k -> k.rolledUpBudget().signum() != 0 || k.totalPrice() != null);
        BigDecimal rolledUp = childHasPrice ? childSum : own == null ? BigDecimal.ZERO : own;
        return new BudgetItemNodeDTO(UUID.randomUUID(), null, kind, kind == BudgetRowKind.ITEM, code, 0, depth,
                name, unit, dec(quantity), dec(unitPrice), own, null, null, null,
                rolledUp, false, null, BigDecimal.ZERO, rolledUp, null, false, 0, 0, 0, 0, BigDecimal.ZERO,
                OffsetDateTime.now(), OffsetDateTime.now(), kids);
    }

    private static BigDecimal dec(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private ConstructionInvoice invoice(String number, String date, String total, String supplier) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setEnterprise(enterprise);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(date == null ? null : LocalDate.parse(date));
        invoice.setTotalAmount(dec(total));
        invoice.setSupplierName(supplier);
        invoice.setSupplierNif(supplier == null ? null : "500000000");
        invoice.setDescription(supplier == null ? null : supplier + " - material");
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);
        invoices.add(invoice);
        return invoice;
    }

    private ConstructionExpense expense(ConstructionInvoice invoice, BudgetItemNodeDTO item, String amount) {
        ConstructionBudgetItem budgetItem = new ConstructionBudgetItem();
        budgetItem.setId(item.id());
        ConstructionExpense expense = new ConstructionExpense();
        expense.setId(UUID.randomUUID());
        expense.setBudgetItem(budgetItem);
        expense.setInvoice(invoice);
        expense.setName("Despesa " + amount);
        expense.setExpenseDate(LocalDate.of(2026, 8, 1));
        expense.setTotalPrice(new BigDecimal(amount));
        expense.setCreatedAt(OffsetDateTime.now().plusSeconds(expenses.size()));
        expenses.add(expense);
        return expense;
    }

    private void payment(ConstructionInvoice invoice, String method, String date, String onThisInvoice,
                         String paymentAmount, String reference, List<String> alsoCovers) {
        payments.computeIfAbsent(invoice.getId(), k -> new ArrayList<>()).add(new InvoicePaymentSummaryDTO(
                UUID.randomUUID(), LocalDate.parse(date), method, new BigDecimal(onThisInvoice),
                new BigDecimal(paymentAmount), reference, null, null, null, null, null, null, alsoCovers));
        paid.merge(invoice.getId(), new BigDecimal(onThisInvoice), BigDecimal::add);
    }

    private XSSFWorkbook exportWorkbook(Set<BudgetExportSheet> sheets) throws Exception {
        BudgetExcelExportService.ExportFile file = service.export(ENTERPRISE_ID, sheets);
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    // ── "Orçamento inicial": round-trip ─────────────────────────

    @Test
    @DisplayName("A folha \"Orçamento inicial\" volta a entrar pelo importador com a mesma árvore e o mesmo total")
    void budgetSheetRoundTripsThroughTheImporter() throws Exception {
        BudgetExcelExportService.ExportFile file = service.export(ENTERPRISE_ID, EnumSet.of(BudgetExportSheet.BUDGET));

        ConstructionBudget lot = new ConstructionBudget();
        lot.setEnterprise(enterprise);
        when(budgetRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(lot));
        BudgetImportResultDTO imported = importService.importBudget(ENTERPRISE_ID,
                new MockMultipartFile("file", file.fileName(), BudgetExcelExportService.CONTENT_TYPE, file.content()),
                true, false);

        assertThat(imported.warnings()).isEmpty();
        assertThat(imported.itemCount()).isEqualTo(9);
        assertThat(imported.headingCount()).isEqualTo(1);
        assertThat(imported.noteCount()).isEqualTo(1);
        assertThat(imported.parsedTotal()).isEqualByComparingTo("13571.12");
        assertThat(imported.excelTotal()).isEqualByComparingTo("13571.12");

        assertThat(row(imported, "1.1").parentCode()).isEqualTo("1");
        assertThat(row(imported, "8.4.1").parentCode()).isEqualTo("8.4");
        BudgetImportRowDTO alt = imported.rows().stream()
                .filter(r -> r.name().startsWith("Alternativa")).findFirst().orElseThrow();
        assertThat(alt.kind()).isEqualTo(BudgetRowKind.ITEM);
        assertThat(alt.parentCode()).isEqualTo("8.4");
        assertThat(alt.totalPrice()).isEqualByComparingTo("12471.12");
        BudgetImportRowDTO paredes = imported.rows().stream()
                .filter(r -> "Paredes".equals(r.name())).findFirst().orElseThrow();
        assertThat(paredes.kind()).isEqualTo(BudgetRowKind.HEADING);
        assertThat(row(imported, "9.1").parentCode()).isEqualTo("Paredes");
        BudgetImportRowDTO nota = imported.rows().stream()
                .filter(r -> r.name().startsWith("(")).findFirst().orElseThrow();
        assertThat(nota.kind()).isEqualTo(BudgetRowKind.NOTE);
        assertThat(nota.parentCode()).isEqualTo("9.1");
    }

    private static BudgetImportRowDTO row(BudgetImportResultDTO result, String code) {
        return result.rows().stream().filter(r -> code.equals(r.code())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("O orçamento é o documento do vault: bloco da empresa com logo, depois Rubrica | Descrição | Preço total")
    void budgetSheetUsesTheVaultHeader() throws Exception {
        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.BUDGET))) {
            XSSFSheet sheet = wb.getSheet("Orçamento inicial");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("ORÇAMENTO");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Empresa");
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).isEqualTo("Obra");
            assertThat(sheet.getRow(8).getCell(1).getStringCellValue()).isEqualTo(enterprise.getName());
            assertThat(sheet.getDrawingPatriarch().getShapes()).hasSize(1); // o logo

            Row header = sheet.getRow(BudgetExcelExportService.BUDGET_HEADER_ROW);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Rubrica");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Descrição");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Preço total");
            // depois do cabeçalho, uma linha em branco e o primeiro capítulo, com o ponto do vault
            assertThat(sheet.getRow(BudgetExcelExportService.BUDGET_HEADER_ROW + 2).getCell(0).getStringCellValue())
                    .isEqualTo("1.");

            // a última linha só fecha a moldura; o TOTAL está logo acima
            Row total = sheet.getRow(sheet.getLastRowNum() - 1);
            assertThat(total.getCell(1).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
        }
    }

    // ── "Despesas" ──────────────────────────────────────────────

    @Test
    @DisplayName("A \"Despesas\" é a TabelaDespesas do vault: cabeçalhos, totais, uma linha por despesa, a mais recente primeiro")
    void expensesSheetMatchesTheVaultTable() throws Exception {
        ConstructionInvoice split = invoice("FT A/1", "2026-09-01", "100", "Casa Dolores");
        split.setSentToAccountant(true);
        expense(split, item11, "60");
        expense(split, item12, "40");
        payment(split, "TRANSFERENCIA", "2026-09-05", "100", "100", "extrato ABANCA", List.of());

        invoice("FT A/2", "2026-09-02", "200", "Leroy");

        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.EXPENSES))) {
            XSSFSheet sheet = wb.getSheet("Despesas");
            assertThat(headers(sheet)).containsExactly("Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada",
                    "Metodo Pagamento", "Bizdocs", "Observações", "Rubrica", "Fornecedor", "NIF");

            XSSFTable table = sheet.getTables().get(0);
            assertThat(table.getName()).isEqualTo("TabelaDespesas");
            assertThat(table.getCTTable().getTotalsRowCount()).isEqualTo(1);
            assertThat(table.getArea().formatAsString()).isEqualTo("A1:K5");

            // FT A/2 é de 02-09, FT A/1 de 01-09: a mais recente vem primeiro, como no vault
            Row newest = sheet.getRow(1);
            assertThat(newest.getCell(0).getStringCellValue()).isEqualTo("FT A/2");
            assertThat(newest.getCell(3).getNumericCellValue()).isEqualTo(200d);
            assertThat(newest.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(newest.getCell(8).getStringCellValue()).isEmpty();

            Row first = sheet.getRow(2);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("FT A/1");
            assertThat(first.getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(first.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("dd/mm/yyyy");
            assertThat(first.getCell(3).getNumericCellValue()).isEqualTo(60d);
            assertThat(first.getCell(3).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00\\ \"€\"");
            assertThat(first.getCell(4).getStringCellValue()).isEqualTo("x");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("Transferência");
            assertThat(first.getCell(6).getStringCellValue()).isEqualTo("X");
            assertThat(first.getCell(7).getStringCellValue())
                    .isEqualTo("Pago por transferência em 05-09-2026 (extrato ABANCA)");
            assertThat(first.getCell(8).getStringCellValue()).isEqualTo("1.1 — Montagem do estaleiro");
            assertThat(first.getCell(9).getStringCellValue()).isEqualTo("Casa Dolores");
            assertThat(first.getCell(10).getStringCellValue()).isEqualTo("500000000");

            Row second = sheet.getRow(3);
            assertThat(second.getCell(0).getStringCellValue()).isEqualTo("FT A/1");
            assertThat(second.getCell(3).getNumericCellValue()).isEqualTo(40d);
            assertThat(second.getCell(8).getStringCellValue()).isEqualTo("1.2 — Desmontagem");

            Row totals = sheet.getRow(4);
            assertThat(totals.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(totals.getCell(3).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(totals.getCell(3).getCellFormula()).isEqualTo("SUBTOTAL(109,TabelaDespesas[Valor])");
        }
    }

    @Test
    @DisplayName("Nota de crédito sai negativa; parcial fica por liquidar; agregado diz \"junto com\"; à mão sai sem nº")
    void expensesSheetHonoursTheRowRules() throws Exception {
        ConstructionInvoice origin = invoice("FT A/1", "2026-09-01", "100", "Casa Dolores");
        ConstructionInvoice creditNote = invoice("NC 1", "2026-09-03", "20", "Casa Dolores");
        creditNote.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        creditNote.setRelatedInvoiceId(origin.getId());

        ConstructionInvoice partial = invoice("FT A/2", "2026-09-02", "200", "Leroy");
        payment(partial, "MULTIBANCO", "2026-09-10", "50", "50", null, List.of());

        ConstructionInvoice aggregatedA = invoice("FT A/4", "2026-09-04", "10", "Leroy");
        ConstructionInvoice aggregatedB = invoice("FT A/5", "2026-09-04", "10", "Leroy");
        payment(aggregatedA, "NUMERARIO", "2026-09-12", "10", "20", null, List.of("FT A/5"));
        payment(aggregatedB, "NUMERARIO", "2026-09-12", "10", "20", null, List.of("FT A/4"));

        ConstructionInvoice toPrint = invoice(null, null, null, null);
        toPrint.setDocumentStatus(ConstructionInvoice.DocumentStatus.TO_PRINT);

        expense(null, item11, "15");

        BudgetExportSummaryDTO summary = service.summary(ENTERPRISE_ID);
        assertThat(summary.creditNoteCount()).isEqualTo(1);
        assertThat(summary.partialPaymentCount()).isEqualTo(1);
        assertThat(summary.manualExpenseCount()).isEqualTo(1);
        assertThat(summary.missingNumberCount()).isEqualTo(1);
        assertThat(summary.needsReviewCount()).isEqualTo(1);
        assertThat(summary.expenseRowCount()).isEqualTo(7);
        assertThat(summary.expensesTotal()).isEqualByComparingTo("315"); // 100 − 20 + 200 + 10 + 10 + 15
        assertThat(summary.fileName()).isEqualTo("Despesas - Vila Teste.xlsx");

        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.EXPENSES))) {
            Sheet sheet = wb.getSheet("Despesas");

            Row nc = rowWithNumber(sheet, "NC 1");
            assertThat(nc.getCell(3).getNumericCellValue()).isEqualTo(-20d);
            assertThat(nc.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(nc.getCell(7).getStringCellValue()).isEqualTo("Nota de crédito da fatura FT A/1");

            Row part = rowWithNumber(sheet, "FT A/2");
            assertThat(part.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(part.getCell(5).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(part.getCell(7).getStringCellValue())
                    .isEqualTo("Pago parcialmente 50,00 € por pagamento mb em 10-09-2026");

            Row agg = rowWithNumber(sheet, "FT A/4");
            assertThat(agg.getCell(4).getStringCellValue()).isEqualTo("x");
            assertThat(agg.getCell(5).getStringCellValue()).isEqualTo("Numerário");
            assertThat(agg.getCell(7).getStringCellValue())
                    .isEqualTo("Pago por numerário em 12-09-2026, 20,00 € junto com FT A/5");

            Row print = rowWithNumber(sheet, "Imprimir fatura");
            assertThat(print.getCell(1).getCellType()).isEqualTo(CellType.BLANK);

            // por data, da mais recente: 04-09 (×2), 03-09, 02-09, 01-09, a despesa à mão de 01-08
            // e, sem data, a "Imprimir fatura" no fim
            Row manual = sheet.getRow(6);
            assertThat(manual.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(manual.getCell(3).getNumericCellValue()).isEqualTo(15d);
            assertThat(manual.getCell(8).getStringCellValue()).isEqualTo("1.1 — Montagem do estaleiro");
        }
    }

    @Test
    @DisplayName("Obra sem faturas: a tabela fica com uma linha vazia e totais a zero, não parte")
    void expensesSheetWithoutInvoicesStillHasAValidTable() throws Exception {
        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.EXPENSES))) {
            XSSFSheet sheet = wb.getSheet("Despesas");
            assertThat(sheet.getTables().get(0).getArea().formatAsString()).isEqualTo("A1:K3");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
        }
    }

    // ── "Orçamento vs Gasto" + "Rubricas" ────────────────────────

    @Test
    @DisplayName("Pedir o painel arrasta a \"Despesas\" e gera a \"Rubricas\", com as fórmulas do vault")
    void comparisonPullsInExpensesAndRubrics() throws Exception {
        ConstructionInvoice inv = invoice("FT A/1", "2026-09-01", "60", "Casa Dolores");
        expense(inv, item11, "60");

        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.COMPARISON))) {
            assertThat(sheetNames(wb)).containsExactly("Despesas", "Orçamento vs Gasto", "Rubricas");

            XSSFSheet rubrics = wb.getSheet("Rubricas");
            assertThat(headers(rubrics).subList(0, 11)).containsExactly("Art", "Descrição", "Cap", "Nível", "Tipo", "Orçamentado",
                    "Gasto", "Saldo", "% consumido", "Nº faturas", "Etiqueta");
            assertThat(rubrics.getTables().get(0).getName()).isEqualTo("TabelaRubricas");
            // só as rubricas com índice; a "Alternativa …" fica fora, como no vault
            assertThat(rubrics.getTables().get(0).getArea().formatAsString()).isEqualTo("A1:K9");

            Row chapter = rubrics.getRow(1);
            assertThat(chapter.getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(chapter.getCell(4).getStringCellValue()).isEqualTo("CAPÍTULO");
            assertThat(chapter.getCell(5).getNumericCellValue()).isEqualTo(1000d);
            assertThat(chapter.getCell(6).getCellFormula()).isEqualTo(
                    "SUMIF(TabelaDespesas[Rubrica],$K2,TabelaDespesas[Valor])"
                    + "+SUMIF(TabelaDespesas[Rubrica],$A2,TabelaDespesas[Valor])"
                    + "+SUMIF(TabelaDespesas[Rubrica],$A2&\".\",TabelaDespesas[Valor])");
            assertThat(chapter.getCell(10).getStringCellValue()).isEqualTo("1 — ESTALEIRO");

            Row sub84 = rubrics.getRow(5);
            assertThat(sub84.getCell(0).getStringCellValue()).isEqualTo("8.4");
            assertThat(sub84.getCell(4).getStringCellValue()).isEqualTo("ITEM");
            assertThat(sub84.getCell(5).getNumericCellValue()).isEqualTo(12471.12);
            Row sub841 = rubrics.getRow(6);
            assertThat(sub841.getCell(4).getStringCellValue()).isEqualTo("TÍTULO");

            XSSFSheet panel = wb.getSheet("Orçamento vs Gasto");
            assertThat(panel.getRow(4).getCell(5).getCellFormula()).isEqualTo("SUM(TabelaDespesas[Valor])");
            assertThat(panel.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Cap");
            assertThat(panel.getRow(8).getCell(1).getStringCellValue()).isEqualTo("ESTALEIRO");
            assertThat(panel.getRow(8).getCell(3).getCellFormula())
                    .isEqualTo("SUMIF(TabelaRubricas[Cap],$A9,TabelaRubricas[Gasto])");
            assertThat(panel.getRow(11).getCell(1).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(panel.getRow(12).getCell(3).getCellFormula())
                    .isEqualTo("SUMPRODUCT((TabelaDespesas[Rubrica]=\"\")*TabelaDespesas[Valor])");

            // a dropdown da coluna Rubrica aponta para a coluna auxiliar da "Rubricas"
            XSSFSheet expensesSheet = wb.getSheet("Despesas");
            assertThat(expensesSheet.getDataValidations()).hasSize(1);
            assertThat(expensesSheet.getDataValidations().get(0).getValidationConstraint().getFormula1())
                    .isEqualTo("'Rubricas'!$M$1:$M$7");
            assertThat(wb.getForceFormulaRecalculation()).isTrue();
        }
    }

    // ── guardas e nome do ficheiro ──────────────────────────────

    @Test
    @DisplayName("Sem folhas escolhidas é erro")
    void noSheetsIsAnError() {
        assertThatThrownBy(() -> service.export(ENTERPRISE_ID, EnumSet.noneOf(BudgetExportSheet.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUDGET_EXPORT_NO_SHEETS);
    }

    @Test
    @DisplayName("Obra sem orçamento: só a \"Despesas\" sai; orçamento ou painel são erro")
    void noBudgetOnlyAllowsExpenses() throws Exception {
        BudgetTreeDTO empty = new BudgetTreeDTO(ENTERPRISE_ID, enterprise.getName(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, List.of());
        when(budgetService.getTree(ENTERPRISE_ID)).thenReturn(empty);

        assertThat(service.summary(ENTERPRISE_ID).hasBudget()).isFalse();
        assertThatThrownBy(() -> service.export(ENTERPRISE_ID, EnumSet.of(BudgetExportSheet.BUDGET)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUDGET_EXPORT_NO_BUDGET);
        assertThatThrownBy(() -> service.export(ENTERPRISE_ID, EnumSet.of(BudgetExportSheet.COMPARISON)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUDGET_EXPORT_NO_BUDGET);
        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(BudgetExportSheet.EXPENSES))) {
            assertThat(sheetNames(wb)).containsExactly("Despesas");
        }
    }

    @Test
    @DisplayName("O nome do ficheiro é o do vault; obra de teste leva prefixo")
    void fileNameFollowsTheVault() {
        assertThat(BudgetExcelExportService.fileName(enterprise)).isEqualTo("Despesas - Vila Teste.xlsx");

        enterprise.setSlug("Obra: A/B?");
        enterprise.setIsTest(true);
        assertThat(BudgetExcelExportService.fileName(enterprise)).isEqualTo("TESTE - Despesas - Obra- A-B-.xlsx");
    }

    @Test
    @DisplayName("Uma obra sem slug fica com um ao exportar: o nome limpo, com sufixo se já existir")
    void missingSlugIsCreatedFromTheName() {
        enterprise.setSlug(null);
        enterprise.setName("Obra: Nova/Zona?  Sul");
        when(enterpriseRepository.existsBySlugAndIdNot("Obra- Nova-Zona- Sul", ENTERPRISE_ID)).thenReturn(true);
        when(enterpriseRepository.existsBySlugAndIdNot("Obra- Nova-Zona- Sul 2", ENTERPRISE_ID)).thenReturn(false);

        BudgetExportSummaryDTO summary = service.summary(ENTERPRISE_ID);

        assertThat(enterprise.getSlug()).isEqualTo("Obra- Nova-Zona- Sul 2");
        assertThat(summary.fileName()).isEqualTo("Despesas - Obra- Nova-Zona- Sul 2.xlsx");
        assertThat(summary.warnings()).anyMatch(w -> w.contains("não tinha slug") && w.contains("Obra- Nova-Zona- Sul 2"));
        verify(enterpriseRepository).save(enterprise);
    }

    @Test
    @DisplayName("Uma fatura repartida que não soma o total fica no relatório")
    void splitMismatchIsReported() {
        ConstructionInvoice split = invoice("FT A/1", "2026-09-01", "100", "Casa Dolores");
        expense(split, item11, "60");
        expense(split, item12, "30");

        assertThat(service.summary(ENTERPRISE_ID).warnings())
                .anyMatch(w -> w.contains("FT A/1") && w.contains("90,00 €") && w.contains("100,00 €"));
    }

    @Test
    @DisplayName("Obra com dois lotes: uma folha de orçamento por lote, e o lote à frente do índice na Rubrica")
    void twoLotsExportOneBudgetSheetEachAndPrefixTheRubric() throws Exception {
        BudgetTreeDTO lotATree = tree();
        BudgetItemNodeDTO lotAItem = item11;
        BudgetTreeDTO lotBTree = tree(); // mesma numeração — o 1.1 existe nos dois
        BudgetItemNodeDTO lotBItem = item11;
        UUID lotA = UUID.randomUUID(), lotB = UUID.randomUUID();
        when(budgetService.listLots(ENTERPRISE_ID)).thenReturn(List.of(
                new BudgetLotDTO(lotA, "Lote A", 0, 11, lotATree.budgetTotal(), BigDecimal.ZERO),
                new BudgetLotDTO(lotB, "Lote B", 1, 11, lotBTree.budgetTotal(), BigDecimal.ZERO)));
        when(budgetService.getBudgetTree(lotA)).thenReturn(lotATree);
        when(budgetService.getBudgetTree(lotB)).thenReturn(lotBTree);

        expense(invoice("FT L/1", "2026-09-01", "50", "Casa Dolores"), lotBItem, "50");
        expense(invoice("FT L/2", "2026-09-02", "70", "Leroy"), lotAItem, "70");

        try (XSSFWorkbook wb = exportWorkbook(EnumSet.of(
                BudgetExportSheet.BUDGET, BudgetExportSheet.EXPENSES, BudgetExportSheet.COMPARISON))) {
            assertThat(sheetNames(wb)).contains("Orçamento - Lote A", "Orçamento - Lote B")
                    .doesNotContain("Orçamento inicial");

            Sheet expenses = wb.getSheet("Despesas");
            assertThat(rowWithNumber(expenses, "FT L/1").getCell(8).getStringCellValue())
                    .isEqualTo("Lote B · 1.1 — Montagem do estaleiro");
            assertThat(rowWithNumber(expenses, "FT L/2").getCell(8).getStringCellValue())
                    .isEqualTo("Lote A · 1.1 — Montagem do estaleiro");

            Sheet rubrics = wb.getSheet("Rubricas");
            // a coluna M (escondida) é a lista da dropdown, por isso compara-se até à L
            assertThat(headers(rubrics).subList(10, 12)).containsExactly("Etiqueta", "Lote");

            // o painel separa o capítulo 1 de cada lote
            Sheet panel = wb.getSheet("Orçamento vs Gasto");
            List<String> chapterNames = new ArrayList<>();
            for (int r = 8; r < 14; r++) chapterNames.add(panel.getRow(r).getCell(1).getStringCellValue());
            assertThat(chapterNames).containsExactly("Lote A · ESTALEIRO", "Lote A · REVESTIMENTOS EXTERIORES",
                    "Lote A · ACABAMENTOS", "Lote B · ESTALEIRO", "Lote B · REVESTIMENTOS EXTERIORES",
                    "Lote B · ACABAMENTOS");
            assertThat(panel.getRow(8).getCell(3).getCellFormula()).contains("SUMIFS(", "[Lote],\"Lote A\"");

            // a importação do orçamento do Lote B escolhe a folha dele
            assertThat(BudgetExcelImportService.budgetSheet(wb, "Lote B").getSheetName())
                    .isEqualTo("Orçamento - Lote B");
        }
    }

    // ── helpers ─────────────────────────────────────────────────

    private static List<String> headers(Sheet sheet) {
        List<String> out = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) out.add(cell.getStringCellValue());
        return out;
    }

    private static List<String> sheetNames(XSSFWorkbook wb) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) out.add(wb.getSheetName(i));
        return out;
    }

    private static Row rowWithNumber(Sheet sheet, String number) {
        for (Row row : sheet) {
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.STRING && number.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Sem linha com nº " + number);
    }
}
