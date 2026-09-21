package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.ExpensesImportAnswersDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceRegisterDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceTransferDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportInvoiceDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportQuestionDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportResultDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A leitura da folha "Despesas" do vault (docs/excel-parity.md §3, §4, §9) em
 * {@code dryRun}: cabeçalhos pelo nome (com a quebra de linha real do
 * "Metodo Pagamento"), os valores como a Vilatro os escreve ("x"/"X"/"Sim",
 * "IMPRIMIR", "Transferencia" sem acento, "11 643,33 €"), o agrupamento por
 * nº, as rubricas pelo índice, as perguntas que não se decidem sozinhas e a
 * verificação de totais que bloqueia. A quarentena (folha "Por identificar",
 * §6) está aqui também, leitura e gravação. A gravação da folha "Despesas" está no
 * {@link DespesasExcelImportRoundTripTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DespesasExcelImportServiceTest {

    static final String[] VAULT_HEADERS = {
            "Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada", "Metodo \nPagamento",
            "Bizdocs", "Observações", "Rubrica"};

    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionInvoiceService invoiceService;
    @Mock private PaymentService paymentService;
    @Mock private AuthContext authContext;

    @InjectMocks private DespesasExcelImportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();
    private final Map<String, ConstructionBudgetItem> rubrics = new HashMap<>();

    @BeforeEach
    void setUp() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setName("Vila Petrus");
        enterprise.setSlug("Vila Petrus");
        enterprise.setIsTest(false);
        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));
        when(invoiceRepository.findAllInvoiceNumbers()).thenReturn(List.of());
        when(invoiceRepository.findAllByEnterpriseIdForExport(ENTERPRISE_ID)).thenReturn(List.of());
        when(invoiceRepository.findAllByScopeWithoutEnterprise(any())).thenReturn(List.of());
        when(budgetItemRepository.findByEnterpriseIdAndCode(eq(ENTERPRISE_ID), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(rubrics.get(inv.getArgument(1, String.class))));

        rubric("8.2", "Betão armado", BudgetRowKind.ITEM);
        rubric("13.2.1", "Portas interiores", BudgetRowKind.ITEM);
        rubric("8", "Estrutura", BudgetRowKind.ITEM);
        rubric("9.1", "Paredes", BudgetRowKind.HEADING);
    }

    private void rubric(String code, String name, BudgetRowKind kind) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(UUID.randomUUID());
        item.setCode(code);
        item.setName(name);
        item.setRowKind(kind);
        rubrics.put(code, item);
    }

    // ── o ficheiro real da Vilatro ──────────────────────────────

    @Test
    @DisplayName("O Excel real do Vila Petrus entra sem erros e a soma das linhas bate com a linha de totais")
    void realVilaPetrusWorkbookParses() throws Exception {
        // qualquer índice existe: o que se prova aqui é a leitura, não o orçamento
        when(budgetItemRepository.findByEnterpriseIdAndCode(eq(ENTERPRISE_ID), anyString()))
                .thenAnswer(inv -> {
                    ConstructionBudgetItem item = new ConstructionBudgetItem();
                    item.setId(UUID.randomUUID());
                    item.setCode(inv.getArgument(1, String.class));
                    item.setName("rubrica");
                    return Optional.of(item);
                });

        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/excel-parity/Despesas - Vila Petrus.xlsx")) {
            content = in.readAllBytes();
        }
        ExpensesImportResultDTO result = service.importExpenses("PROJECT", ENTERPRISE_ID,
                new MockMultipartFile("file", "Despesas - Vila Petrus.xlsx", BudgetExcelExportService.CONTENT_TYPE, content),
                true, null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.sheetName()).isEqualTo("Despesas");
        assertThat(result.rowCount()).isEqualTo(52); // 53 linhas menos a nota "preencher após o reembolso"
        assertThat(result.warnings()).anyMatch(w -> w.startsWith("Linha 54: ignorada"));
        // linhas 20-21: Manitou da Civica, cobrado e devolvido, ambas com "-" no nº — a devolução é uma NC a perguntar
        assertThat(result.creditNoteCount()).isEqualTo(1);
        assertThat(result.questions()).singleElement().satisfies(q -> assertThat(q.excelRows()).containsExactly(21));
        assertThat(result.sheetTotal()).isNotNull();
        assertThat(result.totalDifference().abs()).isLessThanOrEqualTo(new java.math.BigDecimal("0.01"));
        assertThat(result.invoiceCount()).isGreaterThan(0);
        assertThat(result.invoiceCount() + result.creditNoteCount() + result.manualExpenseCount())
                .isEqualTo(result.invoices().size());
        assertThat(result.paidCount() + result.unpaidCount()).isEqualTo(result.invoiceCount());

        // decisão 18: um nº por fatura — dois grupos nunca partilham o número
        List<String> numbers = result.invoices().stream()
                .map(ExpensesImportInvoiceDTO::invoiceNumber).filter(n -> n != null).toList();
        assertThat(numbers).doesNotHaveDuplicates();
    }

    // ── cabeçalhos e valores como a Vilatro os escreve ──────────

    @Test
    @DisplayName("Lê pelo nome do cabeçalho (com a quebra de linha real do \"Metodo Pagamento\") e os valores em todas as grafias do vault")
    void readsVaultSpellings() throws Exception {
        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "Cimento", "11 643,33 €", "x", "Transferencia", "X", null, "8.2 — Betão armado"),
                row("FT 2", "06/01/2026", "Portas", "1.250,00 €", "Sim", "pagamento MB", null, null, "13.2.1 — Portas"),
                row("IMPRIMIR", "07/01/2026", "Areia", "300,00", "X", "Numerário", null, null, "8 — Estrutura"),
                row("Pedir fatura", "08/01/2026", "Brita", "50", null, null, null, null, null)
        ), true));

        assertThat(result.errors()).isEmpty();
        assertThat(result.invoiceCount()).isEqualTo(4);
        assertThat(result.paidCount()).isEqualTo(3);
        assertThat(result.unpaidCount()).isEqualTo(1);
        assertThat(result.parsedTotal()).isEqualByComparingTo("13243.33");
        assertThat(result.sheetTotal()).isEqualByComparingTo("13243.33");

        ExpensesImportInvoiceDTO ft1 = byNumber(result, "FT 1");
        assertThat(ft1.totalAmount()).isEqualByComparingTo("11643.33");
        assertThat(ft1.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(ft1.paymentMethod()).isEqualTo("TRANSFERENCIA");
        assertThat(ft1.paidOn()).isEqualTo(LocalDate.of(2026, 1, 5)); // sem "Pago … em", fica a data da fatura
        assertThat(ft1.sentToAccountant()).isTrue();
        assertThat(ft1.lines()).singleElement().satisfies(line -> {
            assertThat(line.rubricCode()).isEqualTo("8.2");
            assertThat(line.rubricLabel()).isEqualTo("8.2 — Betão armado");
        });
        assertThat(byNumber(result, "FT 2").paymentMethod()).isEqualTo("MULTIBANCO");

        ExpensesImportInvoiceDTO toPrint = result.invoices().get(2);
        assertThat(toPrint.invoiceNumber()).isNull();
        assertThat(toPrint.documentStatus()).isEqualTo("TO_PRINT");
        assertThat(toPrint.paymentMethod()).isEqualTo("NUMERARIO");
        ExpensesImportInvoiceDTO toRequest = result.invoices().get(3);
        assertThat(toRequest.documentStatus()).isEqualTo("TO_REQUEST");
        assertThat(toRequest.paymentStatus()).isEqualTo("UNPAID");

        assertThat(result.warnings()).anyMatch(w -> w.contains("fica a data da fatura"));
    }

    @Test
    @DisplayName("Linhas com o mesmo nº são uma fatura repartida; a mesma rubrica duas vezes é somada na gravação")
    void groupsRowsByInvoiceNumber() throws Exception {
        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 10", "05/01/2026", "Armazém", "700,00", null, null, null, null, "8.2 — Betão"),
                row("FT 10", "05/01/2026", "Armazém", "300,00", null, null, null, null, "13.2.1 — Portas"),
                row("FT 11", "06/01/2026", "Outra", "10,00", null, null, null, null, null)
        ), true));

        assertThat(result.errors()).isEmpty();
        assertThat(result.invoiceCount()).isEqualTo(2);
        ExpensesImportInvoiceDTO ft10 = byNumber(result, "FT 10");
        assertThat(ft10.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(ft10.excelRows()).containsExactly(2, 3);
        assertThat(ft10.lines()).hasSize(2);
    }

    @Test
    @DisplayName("Uma linha negativa é uma nota de crédito e, sem origem nas observações, pergunta-se a que fatura pertence")
    void creditNoteWithoutOriginAsksTheQuestion() throws Exception {
        byte[] file = workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 20", "05/01/2026", "Cimento", "1000,00", null, null, null, null, "8.2 — Betão"),
                row("NC 1", "10/01/2026", "Cimento", "-100,00", null, null, null, null, "8.2 — Betão")
        ), true);

        ExpensesImportResultDTO result = dryRun(file);
        assertThat(result.errors()).isEmpty();
        assertThat(result.creditNoteCount()).isEqualTo(1);
        assertThat(result.invoiceCount()).isEqualTo(1);
        assertThat(result.parsedTotal()).isEqualByComparingTo("900.00");
        assertThat(result.questions()).singleElement().satisfies(q -> {
            assertThat(q.kind()).isEqualTo("CREDIT_NOTE_ORIGIN");
            assertThat(q.excelRows()).containsExactly(3);
            assertThat(q.options()).extracting(ExpensesImportQuestionDTO.Option::value)
                    .contains("file:r2", "SKIP");
        });

        // respondida, a pergunta desaparece e a NC fica ligada
        String questionId = result.questions().get(0).id();
        ExpensesImportResultDTO answered = service.importExpenses("PROJECT", ENTERPRISE_ID, multipart(file), true,
                new ExpensesImportAnswersDTO(List.of(new ExpensesImportAnswersDTO.Answer(questionId, "file:r2"))));
        assertThat(answered.questions()).isEmpty();
        assertThat(byNumber(answered, "NC 1").creditNoteOrigin()).isEqualTo("r2");
        assertThat(byNumber(answered, "NC 1").totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("\"Nota de crédito da fatura X\" nas observações liga a NC sem perguntar")
    void creditNoteWithOriginInObservationsNeedsNoQuestion() throws Exception {
        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 20", "05/01/2026", "Cimento", "1000,00", null, null, null, null, null),
                row("NC 1", "10/01/2026", "Cimento", "-100,00", null, null, null, "Nota de crédito da fatura FT 20", null)
        ), true));

        assertThat(result.questions()).isEmpty();
        assertThat(byNumber(result, "NC 1").creditNoteOrigin()).isEqualTo("r2");
    }

    @Test
    @DisplayName("Faturas pagas com a mesma observação: pergunta-se se foi um só movimento")
    void identicalObservationsAskAboutAggregatePayment() throws Exception {
        byte[] file = workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 30", "05/01/2026", "A", "100,00", "x", "Transferência", null, "Pago em 20-01-2026 (extrato 12)", null),
                row("FT 31", "06/01/2026", "B", "200,00", "x", "Transferência", null, "Pago em 20-01-2026 (extrato 12)", null),
                row("FT 32", "07/01/2026", "C", "300,00", "x", "Transferência", null, "Pago em 21-01-2026", null)
        ), true);

        ExpensesImportResultDTO result = dryRun(file);
        assertThat(result.errors()).isEmpty();
        assertThat(result.questions()).singleElement().satisfies(q -> {
            assertThat(q.kind()).isEqualTo("AGGREGATE_PAYMENT");
            assertThat(q.excelRows()).containsExactly(2, 3);
        });
        ExpensesImportInvoiceDTO ft30 = byNumber(result, "FT 30");
        assertThat(ft30.paidOn()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(ft30.paymentReference()).isEqualTo("extrato 12");
        assertThat(ft30.notes()).isEqualTo("Pago em 20-01-2026 (extrato 12)");
        assertThat(byNumber(result, "FT 32").paidOn()).isEqualTo(LocalDate.of(2026, 1, 21));

        String questionId = result.questions().get(0).id();
        ExpensesImportResultDTO answered = service.importExpenses("PROJECT", ENTERPRISE_ID, multipart(file), true,
                new ExpensesImportAnswersDTO(List.of(new ExpensesImportAnswersDTO.Answer(questionId, "SEPARATE"))));
        assertThat(answered.questions()).isEmpty();
    }

    @Test
    @DisplayName("Uma fatura anulada por inteiro por NC entra sem pagamento, mesmo \"Liquidada\" — a app recusaria pagá-la")
    void fullyCreditedInvoiceLosesItsPayment() throws Exception {
        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 50", "05/01/2026", "Mosaico", "1310,42", "x", "Transferência", null, "Pago no lote de setembro", null),
                row("NC 1", "10/01/2026", "Anula a FT 50", "-1310,42", null, null, null, "Nota de crédito da fatura FT 50", null),
                row("FT 51", "06/01/2026", "Cimento", "100,00", "x", "Transferência", null, "Pago no lote de setembro", null)
        ), true));

        assertThat(result.errors()).isEmpty();
        assertThat(result.questions()).isEmpty(); // "Pago no lote…" sem data não é marca de agregado
        assertThat(byNumber(result, "FT 50").paymentStatus()).isEqualTo("UNPAID");
        assertThat(byNumber(result, "FT 51").paymentStatus()).isEqualTo("PAID");
        assertThat(result.paidCount()).isEqualTo(1);
        assertThat(result.unpaidCount()).isEqualTo(1);
        assertThat(result.warnings()).anyMatch(w -> w.contains("FT 50") && w.contains("anulada por inteiro"));
    }

    // ── erros que bloqueiam ─────────────────────────────────────

    @Test
    @DisplayName("Rubrica inexistente ou que é um título é erro na linha — nunca se cria uma rubrica")
    void unknownOrHeadingRubricIsAnError() throws Exception {
        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "A", "10,00", null, null, null, null, "99.9 — Nada"),
                row("FT 2", "05/01/2026", "B", "10,00", null, null, null, null, "9.1 — Paredes"),
                row("FT 3", "05/01/2026", "C", "10,00", null, null, null, null, "Betão")
        ), true));

        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors().get(0).excelRow()).isEqualTo(2);
        assertThat(result.errors().get(0).message()).contains("99.9").contains("não existe");
        assertThat(result.errors().get(1).message()).contains("título");
        assertThat(result.errors().get(2).message()).contains("sem índice");
    }

    @Test
    @DisplayName("Um nº que já existe na app é erro (decisão 18 — o nº é único no vault todo)")
    void duplicateNumberInAppIsAnError() throws Exception {
        when(invoiceRepository.findAllInvoiceNumbers()).thenReturn(List.of("FT/2026-0001"));

        ExpensesImportResultDTO result = dryRun(workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 2026 0001", "05/01/2026", "A", "10,00", null, null, null, null, null),
                row("FT 2", "05/01/2026", "B", "10,00", null, null, null, null, null)
        ), true));

        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.excelRow()).isEqualTo(2);
            assertThat(e.message()).contains("Já existe na app");
        });
        assertThat(byNumber(result, "FT 2026 0001").duplicate()).isTrue();
    }

    @Test
    @DisplayName("A soma das linhas tem de bater com a linha de totais — a diferença é erro, não aviso")
    void totalsMismatchIsAnError() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Despesas");
            header(sheet, VAULT_HEADERS);
            write(sheet.createRow(1), row("FT 1", "05/01/2026", "A", "10,00", null, null, null, null, null));
            Row totals = sheet.createRow(2);
            totals.createCell(0).setCellValue("TOTAL");
            totals.createCell(3).setCellValue(99.0);

            ExpensesImportResultDTO result = dryRun(bytes(wb));
            assertThat(result.sheetTotal()).isEqualByComparingTo("99.00");
            assertThat(result.errors()).singleElement().satisfies(e ->
                    assertThat(e.message()).contains("não bate certo"));
        }
    }

    @Test
    @DisplayName("Gravar com erros ou perguntas por responder é recusado")
    void writeIsRefusedWithErrorsOrQuestions() throws Exception {
        byte[] withError = workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "A", "10,00", null, null, null, null, "99.9 — Nada")), true);
        assertThatThrownBy(() -> service.importExpenses("PROJECT", ENTERPRISE_ID, multipart(withError), false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IMPORT_HAS_ERRORS);

        byte[] withQuestion = workbook(VAULT_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "A", "10,00", null, null, null, null, null),
                row("NC 1", "05/01/2026", "A", "-5,00", null, null, null, null, null)), true);
        assertThatThrownBy(() -> service.importExpenses("PROJECT", ENTERPRISE_ID, multipart(withQuestion), false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IMPORT_UNANSWERED);
    }

    @Test
    @DisplayName("Sem a folha \"Despesas\", ou sem uma coluna obrigatória, o ficheiro é recusado")
    void missingSheetOrColumnsAreRefused() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("Orçamento inicial");
            byte[] noSheet = bytes(wb);
            assertThatThrownBy(() -> dryRun(noSheet))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVOICE_IMPORT_NO_SHEET);
        }
        byte[] noColumn = workbook(new String[] {"Nº Fatura", "Data", "Valor"}, List.<Object[]>of(row("FT 1", "05/01/2026", "1")), false);
        assertThatThrownBy(() -> dryRun(noColumn))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IMPORT_MISSING_COLUMNS);
    }

    @Test
    @DisplayName("Despesas da empresa: sem coluna Rubrica é normal; com rubrica é erro")
    void companySheetHasNoRubrics() throws Exception {
        String[] companyHeaders = {"Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada", "Metodo Pagamento", "Bizdocs", "Observações"};
        ExpensesImportResultDTO ok = service.importExpenses("COMPANY", null,
                multipart(workbook(companyHeaders, List.<Object[]>of(row("FT 1", "05/01/2026", "Gasóleo", "60,00", "x", "MB", null, null)), true)),
                true, null);
        assertThat(ok.errors()).isEmpty();
        assertThat(ok.scope()).isEqualTo("COMPANY");
        assertThat(ok.invoiceCount()).isEqualTo(1);

        ExpensesImportResultDTO bad = service.importExpenses("COMPANY", null,
                multipart(workbook(VAULT_HEADERS, List.<Object[]>of(row("FT 1", "05/01/2026", "Gasóleo", "60,00", null, null, null, null, "8.2 — Betão")), true)),
                true, null);
        assertThat(bad.errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("não têm orçamento"));
    }

    // ── a quarentena (§6): folha "Por identificar" ──────────────

    static final String[] QUARANTINE_HEADERS = {
            "Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada", "Metodo Pagamento", "Bizdocs", "Observações",
            "Empreendimento", "Fornecedor", "Obras possíveis", "Perguntar a", "Aqui desde"};

    @Test
    @DisplayName("O Excel real da quarentena entra pela folha \"Por identificar\" e pela TabelaPorIdentificar; a folha \"Despesas\" não serve")
    void realQuarantineWorkbookParses() throws Exception {
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/excel-parity/Faturas por identificar.xlsx")) {
            content = in.readAllBytes();
        }
        ExpensesImportResultDTO result = service.importExpenses("UNIDENTIFIED", null,
                new MockMultipartFile("file", "Faturas por identificar.xlsx", BudgetExcelExportService.CONTENT_TYPE, content),
                true, null);

        assertThat(result.errors()).isEmpty();
        assertThat(result.scope()).isEqualTo("UNIDENTIFIED");
        assertThat(result.sheetName()).isEqualTo("Por identificar");
        assertThat(result.rowCount()).isEqualTo(5);
        assertThat(result.invoiceCount()).isEqualTo(5);
        assertThat(result.transferredCount()).isZero(); // a coluna "Empreendimento" está vazia em todas
        assertThat(result.sheetTotal()).isEqualByComparingTo("1046.27");
        assertThat(result.totalDifference().abs()).isLessThanOrEqualTo(new java.math.BigDecimal("0.01"));
        assertThat(result.paidCount()).isEqualTo(1); // só a Calinorte, pelo recibo REC2026/8
        assertThat(result.invoices()).allSatisfy(invoice -> {
            assertThat(invoice.supplierName()).isNotBlank();
            assertThat(invoice.transferTo()).isNull();
            assertThat(invoice.notes()).contains("Em quarentena desde ");
        });

        // a folha de uma obra não é a da quarentena, e vice-versa
        assertThatThrownBy(() -> service.importExpenses("UNIDENTIFIED", null, multipart(workbook(VAULT_HEADERS, List.<Object[]>of(), true)), true, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IMPORT_NO_SHEET);
        MockMultipartFile quarantineFile = new MockMultipartFile("file", "Faturas por identificar.xlsx", BudgetExcelExportService.CONTENT_TYPE, content);
        assertThatThrownBy(() -> service.importExpenses("COMPANY", null, quarantineFile, true, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IMPORT_NO_SHEET);
    }

    @Test
    @DisplayName("\"Empreendimento\" resolve pelo slug da obra ou por \"Despesas da empresa\"; sem obra, ou obra de teste, bloqueia")
    void quarantineTargetsResolveBySlug() throws Exception {
        Enterprise test = new Enterprise();
        test.setId(UUID.randomUUID());
        test.setName("Obra de teste");
        test.setSlug("Vila Teste");
        test.setIsTest(true);
        Enterprise petrus = enterpriseRepository.findById(ENTERPRISE_ID).orElseThrow();
        when(enterpriseRepository.findBySlug("Vila Petrus")).thenReturn(Optional.of(petrus));
        when(enterpriseRepository.findBySlug("Vila Teste")).thenReturn(Optional.of(test));
        when(enterpriseRepository.findBySlug("Vila Nenhures")).thenReturn(Optional.empty());

        ExpensesImportResultDTO result = service.importExpenses("UNIDENTIFIED", null, multipart(workbook("Por identificar", QUARANTINE_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "Cimento", "10,00", null, null, null, null, "Vila Petrus", "Cimpor", null, null, LocalDate.of(2026, 9, 1)),
                row("FT 2", "05/01/2026", "Gasóleo", "20,00", null, null, null, null, "despesas da Empresa", "Galp", null, null, null),
                row("FT 3", "05/01/2026", "Areia", "30,00", null, null, null, "obra ao lado", null, "Areias", "Petrus ou Aleu", "Sr. Manuel", LocalDate.of(2026, 9, 2)),
                row("FT 4", "05/01/2026", "Brita", "40,00", null, null, null, null, "Vila Nenhures", null, null, null, null),
                row("FT 5", "05/01/2026", "Tijolo", "50,00", null, null, null, null, "Vila Teste", null, null, null, null)
        ), true)), true, null);

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).excelRow()).isEqualTo(5);
        assertThat(result.errors().get(0).message()).contains("Vila Nenhures").contains("crie-a primeiro");
        assertThat(result.errors().get(1).excelRow()).isEqualTo(6);
        assertThat(result.errors().get(1).message()).contains("de teste");
        assertThat(result.transferredCount()).isEqualTo(2);

        assertThat(byNumber(result, "FT 1").transferTo()).isEqualTo("Vila Petrus");
        assertThat(byNumber(result, "FT 1").notes()).isEqualTo("Em quarentena desde 01-09-2026.");
        assertThat(byNumber(result, "FT 2").transferTo()).isEqualTo("Despesas da empresa");
        assertThat(byNumber(result, "FT 2").notes()).isNull();
        ExpensesImportInvoiceDTO stays = byNumber(result, "FT 3");
        assertThat(stays.transferTo()).isNull();
        assertThat(stays.supplierName()).isEqualTo("Areias");
        assertThat(stays.possibleEnterprises()).isEqualTo("Petrus ou Aleu");
        assertThat(stays.askWhom()).isEqualTo("Sr. Manuel");
        assertThat(stays.notes()).isEqualTo("obra ao lado · Em quarentena desde 02-09-2026.");
    }

    @Test
    @DisplayName("Gravar a quarentena regista em UNIDENTIFIED e transfere na mesma transação as linhas com \"Empreendimento\"")
    void quarantineWriteRegistersThenTransfers() throws Exception {
        Enterprise petrus = enterpriseRepository.findById(ENTERPRISE_ID).orElseThrow();
        when(enterpriseRepository.findBySlug("Vila Petrus")).thenReturn(Optional.of(petrus));
        Map<String, UUID> idByNumber = new HashMap<>();
        Map<UUID, ConstructionInvoice> saved = new HashMap<>();
        when(invoiceService.register(any())).thenAnswer(inv -> {
            InvoiceRegisterDTO dto = inv.getArgument(0);
            ConstructionInvoice entity = new ConstructionInvoice();
            entity.setId(UUID.randomUUID());
            entity.setInvoiceNumber(dto.invoiceNumber());
            entity.setTotalAmount(dto.totalAmount());
            saved.put(entity.getId(), entity);
            idByNumber.put(dto.invoiceNumber(), entity.getId());
            ConstructionInvoiceResponseDTO response = mock(ConstructionInvoiceResponseDTO.class);
            when(response.id()).thenReturn(entity.getId());
            return response;
        });
        when(invoiceRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0, UUID.class))));
        when(invoiceRepository.sumCreditNotesFor(any())).thenReturn(BigDecimal.ZERO);
        when(paymentService.paidSums(any())).thenReturn(Map.of());

        ExpensesImportResultDTO result = service.importExpenses("UNIDENTIFIED", null, multipart(workbook("Por identificar", QUARANTINE_HEADERS, List.<Object[]>of(
                row("FT 1", "05/01/2026", "Cimento", "10,00", null, null, null, null, "Vila Petrus", "Cimpor", null, null, null),
                row("FT 2", "05/01/2026", "Gasóleo", "20,00", null, null, "X", null, "Despesas da empresa", "Galp", null, null, null),
                row("FT 3", "05/01/2026", "Areia", "30,00", null, null, null, null, null, "Areias", "Petrus ou Aleu", "Sr. Manuel", null)
        ), true)), false, null);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.transferredCount()).isEqualTo(2);

        ArgumentCaptor<InvoiceRegisterDTO> registered = ArgumentCaptor.captor();
        verify(invoiceService, times(3)).register(registered.capture());
        assertThat(registered.getAllValues()).allSatisfy(dto -> {
            assertThat(dto.scope()).isEqualTo("UNIDENTIFIED");
            assertThat(dto.enterpriseId()).isNull();
        });
        InvoiceRegisterDTO stays = registered.getAllValues().get(2);
        assertThat(stays.supplierName()).isEqualTo("Areias");
        assertThat(stays.possibleEnterprises()).isEqualTo("Petrus ou Aleu");
        assertThat(stays.askWhom()).isEqualTo("Sr. Manuel");

        ArgumentCaptor<InvoiceTransferDTO> transfer = ArgumentCaptor.captor();
        verify(invoiceService).transfer(eq(idByNumber.get("FT 1")), transfer.capture());
        assertThat(transfer.getValue().targetScope()).isEqualTo("PROJECT");
        assertThat(transfer.getValue().targetEnterpriseId()).isEqualTo(ENTERPRISE_ID);
        assertThat(transfer.getValue().reason()).contains("Por identificar").contains("Vila Petrus");
        verify(invoiceService).transfer(eq(idByNumber.get("FT 2")), transfer.capture());
        assertThat(transfer.getValue().targetScope()).isEqualTo("COMPANY");
        assertThat(transfer.getValue().targetEnterpriseId()).isNull();
        verify(invoiceService, never()).transfer(eq(idByNumber.get("FT 3")), any());

        // o Bizdocs marca-se depois da transferência, na fatura já na obra/empresa
        assertThat(saved.get(idByNumber.get("FT 2")).isSentToAccountant()).isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────

    private ExpensesImportResultDTO dryRun(byte[] content) {
        return service.importExpenses("PROJECT", ENTERPRISE_ID, multipart(content), true, null);
    }

    static MockMultipartFile multipart(byte[] content) {
        return new MockMultipartFile("file", "Despesas - Vila Petrus.xlsx", BudgetExcelExportService.CONTENT_TYPE, content);
    }

    static ExpensesImportInvoiceDTO byNumber(ExpensesImportResultDTO result, String number) {
        return result.invoices().stream().filter(i -> number.equals(i.invoiceNumber())).findFirst().orElseThrow();
    }

    static Object[] row(Object... cells) {
        return cells;
    }

    /** Um livro com a folha "Despesas" e, opcionalmente, a linha TOTAL com a soma cacheada como o Excel a deixa. */
    static byte[] workbook(String[] headers, List<Object[]> rows, boolean totalsRow) throws Exception {
        return workbook("Despesas", headers, rows, totalsRow);
    }

    /** O mesmo, com o nome da folha à escolha ("Por identificar" na quarentena). */
    static byte[] workbook(String sheetName, String[] headers, List<Object[]> rows, boolean totalsRow) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(sheetName);
            header(sheet, headers);
            int r = 1;
            double sum = 0;
            for (Object[] cells : rows) {
                write(sheet.createRow(r++), cells);
                Object amount = cells.length > 3 ? cells[3] : null;
                if (amount instanceof String s) {
                    sum += DespesasExcelImportService.parseMoney(s).doubleValue();
                } else if (amount instanceof Number n) {
                    sum += n.doubleValue();
                }
            }
            if (totalsRow) {
                Row totals = sheet.createRow(r);
                totals.createCell(0).setCellValue("TOTAL");
                totals.createCell(3).setCellValue(sum);
            }
            return bytes(wb);
        }
    }

    static void header(XSSFSheet sheet, String[] headers) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            header.createCell(c).setCellValue(headers[c]);
        }
    }

    static void write(Row row, Object[] cells) {
        for (int c = 0; c < cells.length; c++) {
            if (cells[c] == null) continue;
            Cell cell = row.createCell(c);
            if (cells[c] instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else if (cells[c] instanceof LocalDate d) {
                CellStyle style = row.getSheet().getWorkbook().createCellStyle();
                style.setDataFormat(row.getSheet().getWorkbook().createDataFormat().getFormat("dd/mm/yyyy"));
                cell.setCellStyle(style);
                cell.setCellValue(d);
            } else {
                cell.setCellValue(cells[c].toString());
            }
        }
    }

    static byte[] bytes(XSSFWorkbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }
}
