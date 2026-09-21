package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteCreateDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceRegisterDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSplitLineDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportInvoiceDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportResultDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentRequestDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentResultDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.dto.payment.MarkPaidRequestDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O critério 1 da fase 6 (roadmap): a folha "Despesas" que o
 * {@link BudgetExcelExportService} escreve volta a entrar pelo
 * {@link DespesasExcelImportService} sem erros, sem perguntas e com os mesmos
 * números — faturas, repartição, nota de crédito ligada, parcial, agregado,
 * "Imprimir fatura", despesa à mão. E a gravação chama os serviços reais pela
 * ordem certa: registar → repartir → NC → pagamentos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DespesasExcelImportRoundTripTest {

    // ── o exportador real ──
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ConstructionBudgetItemService budgetService;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private InvoicePaymentRepository invoicePaymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private InvoiceDocumentsExportService documentsExportService;
    @InjectMocks private BudgetExcelExportService exportService;

    // ── o importador real ──
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private ConstructionInvoiceService invoiceService;
    @Mock private AuthContext authContext;
    @InjectMocks private DespesasExcelImportService importService;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private Enterprise enterprise;
    private final List<ConstructionInvoice> invoices = new ArrayList<>();
    private final List<ConstructionExpense> expenses = new ArrayList<>();
    private final Map<UUID, List<InvoicePaymentSummaryDTO>> payments = new HashMap<>();
    private final Map<UUID, BigDecimal> paid = new HashMap<>();
    private final Map<String, ConstructionBudgetItem> rubrics = new HashMap<>();

    private BudgetItemNodeDTO item11, item12;

    @BeforeEach
    void setUp() {
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

        when(invoiceRepository.findAllInvoiceNumbers()).thenReturn(List.of());
        when(budgetItemRepository.findByEnterpriseIdAndCode(eq(ENTERPRISE_ID), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(rubrics.get(inv.getArgument(1, String.class))));
    }

    private record PaidSum(UUID invoiceId, BigDecimal amount) implements InvoicePaymentRepository.InvoicePaidSum {
        @Override public UUID getInvoiceId() { return invoiceId; }
        @Override public BigDecimal getPaid() { return amount; }
    }

    /**
     * Os casos das regras de linha do exportador, todos juntos: fatura repartida
     * por duas rubricas, fatura com nota de crédito, parcial, par agregado,
     * "Imprimir fatura" sem nada, e uma despesa à mão.
     */
    private void fixture() {
        ConstructionInvoice split = invoice("FT A/3", "2026-09-05", "100", "Armazém");
        expense(split, item11, "70");
        expense(split, item12, "30");

        ConstructionInvoice origin = invoice("FT A/1", "2026-09-01", "100", "Casa Dolores");
        expense(origin, item11, "100");
        ConstructionInvoice creditNote = invoice("NC 1", "2026-09-03", "20", "Casa Dolores");
        creditNote.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        creditNote.setRelatedInvoiceId(origin.getId());
        expense(creditNote, item11, "-20");

        ConstructionInvoice partial = invoice("FT A/2", "2026-09-02", "200", "Leroy");
        payment(partial, "MULTIBANCO", "2026-09-10", "50", "50", null, List.of());

        ConstructionInvoice aggregatedA = invoice("FT A/4", "2026-09-04", "10", "Leroy");
        ConstructionInvoice aggregatedB = invoice("FT A/5", "2026-09-04", "10", "Leroy");
        payment(aggregatedA, "NUMERARIO", "2026-09-12", "10", "20", "talão 7", List.of("FT A/5"));
        payment(aggregatedB, "NUMERARIO", "2026-09-12", "10", "20", "talão 7", List.of("FT A/4"));
        aggregatedA.setSentToAccountant(true);

        ConstructionInvoice toPrint = invoice(null, null, null, null);
        toPrint.setDocumentStatus(ConstructionInvoice.DocumentStatus.TO_PRINT);

        expense(null, item11, "15");
    }

    private MockMultipartFile exportExpenses() {
        BudgetExcelExportService.ExportFile file = exportService.export(ENTERPRISE_ID, EnumSet.of(BudgetExportSheet.EXPENSES));
        return new MockMultipartFile("file", file.fileName(), BudgetExcelExportService.CONTENT_TYPE, file.content());
    }

    // ── round-trip em dryRun ────────────────────────────────────

    @Test
    @DisplayName("A folha \"Despesas\" exportada volta a entrar sem erros, sem perguntas e com os mesmos números")
    void expensesSheetRoundTripsThroughTheImporter() {
        fixture();

        ExpensesImportResultDTO imported = importService.importExpenses("PROJECT", ENTERPRISE_ID, exportExpenses(), true, null);

        assertThat(imported.errors()).isEmpty();
        assertThat(imported.questions()).isEmpty();
        assertThat(imported.warnings()).noneMatch(w -> w.contains("totais"));

        assertThat(imported.rowCount()).isEqualTo(9);
        assertThat(imported.invoiceCount()).isEqualTo(6);
        assertThat(imported.creditNoteCount()).isEqualTo(1);
        assertThat(imported.manualExpenseCount()).isEqualTo(1);
        assertThat(imported.paidCount()).isEqualTo(2);
        assertThat(imported.partiallyPaidCount()).isEqualTo(1);
        assertThat(imported.unpaidCount()).isEqualTo(4);
        assertThat(imported.parsedTotal()).isEqualByComparingTo("415.00"); // 100 + 100 − 20 + 200 + 10 + 10 + 15
        assertThat(imported.sheetTotal()).isEqualByComparingTo("415.00");
        assertThat(imported.totalDifference()).isEqualByComparingTo("0");

        ExpensesImportInvoiceDTO split = byNumber(imported, "FT A/3");
        assertThat(split.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(split.lines()).extracting(ExpensesImportInvoiceDTO.Line::rubricCode).containsExactly("1.1", "1.2");
        assertThat(split.lines()).extracting(ExpensesImportInvoiceDTO.Line::amount)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("70"), new BigDecimal("30"));
        assertThat(split.supplierName()).isEqualTo("Armazém");
        assertThat(split.supplierNif()).isEqualTo("500000000");

        ExpensesImportInvoiceDTO nc = byNumber(imported, "NC 1");
        assertThat(nc.creditNote()).isTrue();
        assertThat(nc.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(nc.creditNoteOrigin()).isEqualTo(byNumber(imported, "FT A/1").key());
        assertThat(nc.lines()).singleElement().satisfies(line -> {
            assertThat(line.rubricCode()).isEqualTo("1.1");
            assertThat(line.amount()).isEqualByComparingTo("-20");
        });

        ExpensesImportInvoiceDTO partial = byNumber(imported, "FT A/2");
        assertThat(partial.paymentStatus()).isEqualTo("PARTIAL");
        assertThat(partial.paidAmount()).isEqualByComparingTo("50.00");
        assertThat(partial.paymentMethod()).isEqualTo("MULTIBANCO");
        assertThat(partial.paidOn()).isEqualTo(LocalDate.of(2026, 9, 10));

        ExpensesImportInvoiceDTO aggregated = byNumber(imported, "FT A/4");
        assertThat(aggregated.paymentStatus()).isEqualTo("PAID");
        assertThat(aggregated.paymentMethod()).isEqualTo("NUMERARIO");
        assertThat(aggregated.paidOn()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(aggregated.paymentReference()).isEqualTo("talão 7");
        assertThat(aggregated.sentToAccountant()).isTrue();
        assertThat(aggregated.notes()).isNull(); // a frase gerada foi consumida, não vira nota

        ExpensesImportInvoiceDTO toPrint = imported.invoices().stream()
                .filter(i -> "TO_PRINT".equals(i.documentStatus())).findFirst().orElseThrow();
        assertThat(toPrint.invoiceNumber()).isNull();
        assertThat(toPrint.totalAmount()).isNull();

        ExpensesImportInvoiceDTO manual = imported.invoices().stream()
                .filter(ExpensesImportInvoiceDTO::manualExpense).findFirst().orElseThrow();
        assertThat(manual.totalAmount()).isEqualByComparingTo("15.00");
        assertThat(manual.lines()).singleElement().satisfies(line -> assertThat(line.rubricCode()).isEqualTo("1.1"));
    }

    // ── gravação ────────────────────────────────────────────────

    @Test
    @DisplayName("Gravar chama registar → repartir → nota de crédito → pagamentos, e confere o que ficou")
    void writePersistsInTheRightOrderAndVerifies() {
        fixture();
        MockMultipartFile file = exportExpenses();

        Map<String, UUID> idByNumber = new HashMap<>();
        Map<UUID, ConstructionInvoice> saved = new HashMap<>();
        when(invoiceService.register(any())).thenAnswer(inv -> {
            InvoiceRegisterDTO dto = inv.getArgument(0);
            ConstructionInvoice entity = new ConstructionInvoice();
            entity.setId(UUID.randomUUID());
            entity.setInvoiceNumber(dto.invoiceNumber());
            entity.setTotalAmount(dto.totalAmount());
            saved.put(entity.getId(), entity);
            if (dto.invoiceNumber() != null) idByNumber.put(dto.invoiceNumber(), entity.getId());
            return responseWithId(entity.getId());
        });
        ConstructionInvoiceResponseDTO creditNoteResponse = responseWithId(UUID.randomUUID());
        when(invoiceService.createCreditNote(any(), any())).thenReturn(creditNoteResponse);
        when(invoiceRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.getArgument(0, UUID.class))));
        when(invoiceRepository.sumCreditNotesFor(any())).thenAnswer(inv ->
                idByNumber.get("FT A/1").equals(inv.getArgument(0)) ? new BigDecimal("20") : BigDecimal.ZERO);
        when(paymentService.registerAggregate(any(), any()))
                .thenReturn(new AggregatePaymentResultDTO(true, null, List.of(), new BigDecimal("20"), new BigDecimal("20")));
        when(paymentService.paidSums(any())).thenAnswer(inv -> Map.of(
                idByNumber.get("FT A/4"), new BigDecimal("10"),
                idByNumber.get("FT A/5"), new BigDecimal("10"),
                idByNumber.get("FT A/2"), new BigDecimal("50")));

        ExpensesImportResultDTO result = importService.importExpenses("PROJECT", ENTERPRISE_ID, file, false, null);

        assertThat(result.dryRun()).isFalse();
        verify(invoiceService, org.mockito.Mockito.times(6)).register(any());

        // repartição: FT A/3 em duas linhas, FT A/1 numa
        ArgumentCaptor<List<InvoiceSplitLineDTO>> lines = ArgumentCaptor.captor();
        verify(invoiceService).split(eq(idByNumber.get("FT A/3")), lines.capture());
        assertThat(lines.getValue()).extracting(InvoiceSplitLineDTO::amount)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("70"), new BigDecimal("30"));
        assertThat(lines.getValue()).extracting(InvoiceSplitLineDTO::budgetItemId)
                .containsExactly(item11.id(), item12.id());
        verify(invoiceService).split(eq(idByNumber.get("FT A/1")), any());
        verify(invoiceService, never()).split(eq(idByNumber.get("FT A/2")), any());

        // a NC aponta para a origem gravada e leva a despesa negativa
        ArgumentCaptor<CreditNoteCreateDTO> creditNote = ArgumentCaptor.captor();
        verify(invoiceService).createCreditNote(eq(idByNumber.get("FT A/1")), creditNote.capture());
        assertThat(creditNote.getValue().totalAmount()).isEqualByComparingTo("20");
        assertThat(creditNote.getValue().invoiceNumber()).isEqualTo("NC 1");
        assertThat(creditNote.getValue().expenses()).singleElement()
                .satisfies(line -> assertThat(line.amount()).isEqualByComparingTo("-20"));

        // um só movimento para A/4 + A/5; um parcial para A/2
        ArgumentCaptor<AggregatePaymentRequestDTO> aggregate = ArgumentCaptor.captor();
        verify(paymentService).registerAggregate(aggregate.capture(), any());
        assertThat(aggregate.getValue().invoiceIds())
                .containsExactlyInAnyOrder(idByNumber.get("FT A/4"), idByNumber.get("FT A/5"));
        assertThat(aggregate.getValue().amount()).isEqualByComparingTo("20");
        assertThat(aggregate.getValue().method()).isEqualTo("NUMERARIO");
        assertThat(aggregate.getValue().reference()).isEqualTo("talão 7");

        ArgumentCaptor<MarkPaidRequestDTO> partial = ArgumentCaptor.captor();
        verify(paymentService).markAsPaid(eq(idByNumber.get("FT A/2")), partial.capture(), any());
        assertThat(partial.getValue().amount()).isEqualByComparingTo("50");
        assertThat(partial.getValue().paidOn()).isEqualTo(LocalDate.of(2026, 9, 10));

        // Bizdocs → sent_to_accountant
        assertThat(saved.get(idByNumber.get("FT A/4")).isSentToAccountant()).isTrue();
        assertThat(saved.get(idByNumber.get("FT A/5")).isSentToAccountant()).isFalse();

        // a despesa à mão
        ArgumentCaptor<ConstructionExpense> manual = ArgumentCaptor.captor();
        verify(expenseRepository).save(manual.capture());
        assertThat(manual.getValue().getTotalPrice()).isEqualByComparingTo("15");
        assertThat(manual.getValue().getBudgetItem().getId()).isEqualTo(item11.id());

        // ordem: as NC antes de qualquer pagamento (o líquido depende delas)
        InOrder order = inOrder(invoiceService, paymentService);
        order.verify(invoiceService).createCreditNote(any(), any());
        order.verify(paymentService).registerAggregate(any(), any());
    }

    // ── helpers ─────────────────────────────────────────────────

    private static ConstructionInvoiceResponseDTO responseWithId(UUID id) {
        ConstructionInvoiceResponseDTO dto = mock(ConstructionInvoiceResponseDTO.class);
        when(dto.id()).thenReturn(id);
        return dto;
    }

    private static ExpensesImportInvoiceDTO byNumber(ExpensesImportResultDTO result, String number) {
        return result.invoices().stream().filter(i -> number.equals(i.invoiceNumber())).findFirst().orElseThrow();
    }

    private BudgetTreeDTO tree() {
        item11 = node("1.1", "Montagem do estaleiro", "600", 1);
        item12 = node("1.2", "Desmontagem", "400", 1);
        BudgetItemNodeDTO chapter1 = node("1", "ESTALEIRO", "1000", 0, item11, item12);
        rubric("1.1", item11);
        rubric("1.2", item12);
        rubric("1", chapter1);
        BigDecimal total = chapter1.rolledUpBudget();
        return new BudgetTreeDTO(ENTERPRISE_ID, enterprise.getName(), total, BigDecimal.ZERO, total,
                BigDecimal.ZERO, 3, 0, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, List.of(chapter1));
    }

    private void rubric(String code, BudgetItemNodeDTO node) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(node.id());
        item.setCode(code);
        item.setName(node.name());
        item.setRowKind(BudgetRowKind.ITEM);
        rubrics.put(code, item);
    }

    private static BudgetItemNodeDTO node(String code, String name, String totalPrice, int depth, BudgetItemNodeDTO... children) {
        List<BudgetItemNodeDTO> kids = List.of(children);
        BigDecimal own = new BigDecimal(totalPrice);
        BigDecimal childSum = kids.stream().map(BudgetItemNodeDTO::rolledUpBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rolledUp = kids.isEmpty() ? own : childSum;
        return new BudgetItemNodeDTO(UUID.randomUUID(), null, BudgetRowKind.ITEM, true, code, 0, depth,
                name, "un", BigDecimal.ONE, own, own, null, null, null,
                rolledUp, false, null, BigDecimal.ZERO, rolledUp, null, false, 0, 0, 0, 0, BigDecimal.ZERO,
                OffsetDateTime.now(), OffsetDateTime.now(), kids);
    }

    private ConstructionInvoice invoice(String number, String date, String total, String supplier) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setEnterprise(enterprise);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(date == null ? null : LocalDate.parse(date));
        invoice.setTotalAmount(total == null ? null : new BigDecimal(total));
        invoice.setSupplierName(supplier);
        invoice.setSupplierNif(supplier == null ? null : "500000000");
        invoice.setDescription(supplier == null ? null : supplier + " - material");
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);
        invoices.add(invoice);
        return invoice;
    }

    private void expense(ConstructionInvoice invoice, BudgetItemNodeDTO item, String amount) {
        ConstructionExpense expense = new ConstructionExpense();
        expense.setId(UUID.randomUUID());
        expense.setBudgetItem(rubrics.get(item.code()));
        expense.setInvoice(invoice);
        expense.setName("Despesa " + amount);
        expense.setExpenseDate(LocalDate.of(2026, 8, 1));
        expense.setTotalPrice(new BigDecimal(amount));
        expense.setCreatedAt(OffsetDateTime.now().plusSeconds(expenses.size()));
        expenses.add(expense);
    }

    private void payment(ConstructionInvoice invoice, String method, String date, String onThisInvoice,
                         String paymentAmount, String reference, List<String> alsoCovers) {
        payments.computeIfAbsent(invoice.getId(), k -> new ArrayList<>()).add(new InvoicePaymentSummaryDTO(
                UUID.randomUUID(), LocalDate.parse(date), method, new BigDecimal(onThisInvoice),
                new BigDecimal(paymentAmount), reference, null, null, null, null, null, null, alsoCovers));
        paid.merge(invoice.getId(), new BigDecimal(onThisInvoice), BigDecimal::add);
    }
}
