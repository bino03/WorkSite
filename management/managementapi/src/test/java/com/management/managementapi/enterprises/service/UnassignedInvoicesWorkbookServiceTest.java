package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportInvoiceDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportResultDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoice.Scope;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.HeavyWorkGate;
import com.management.managementapi.service.attendance.AttendanceZone;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * O critério da fase 3 dos backups: os dois livros voltam a entrar pelo
 * {@link DespesasExcelImportService} sem erros, com o mesmo nº de linhas e os mesmos
 * totais. Com mocks, de propósito: contra a base de dados real, cada número já existente
 * daria erro de duplicado, mesmo em {@code dryRun}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnassignedInvoicesWorkbookServiceTest {

    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ConstructionBudgetItemService budgetService;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private InvoicePaymentRepository invoicePaymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private InvoiceDocumentsExportService documentsExportService;
    @Spy private HeavyWorkGate heavyWork = new HeavyWorkGate();
    @InjectMocks private BudgetExcelExportService excelExportService;

    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private ConstructionInvoiceService invoiceService;
    @Mock private AuthContext authContext;
    @InjectMocks private DespesasExcelImportService importService;

    private UnassignedInvoicesWorkbookService service;

    private final List<ConstructionInvoice> invoices = new ArrayList<>();
    private final Map<UUID, List<InvoicePaymentSummaryDTO>> payments = new HashMap<>();
    private final Map<UUID, BigDecimal> paid = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new UnassignedInvoicesWorkbookService(invoiceRepository, excelExportService, heavyWork,
                new AttendanceZone("Europe/Lisbon"));
        when(invoiceRepository.findAllByScopeWithoutEnterprise(any())).thenReturn(invoices);
        when(invoiceRepository.findAllInvoiceNumbers()).thenReturn(List.of());
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

    private ConstructionInvoice invoice(Scope scope, String number, String date, String total) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setScope(scope);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(LocalDate.parse(date));
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setSupplierName("Casa Dolores");
        invoice.setSupplierNif("500000000");
        invoice.setDescription("Material");
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);
        invoice.setCreatedAt(OffsetDateTime.parse("2026-07-14T23:30:00Z"));
        invoices.add(invoice);
        return invoice;
    }

    private void pay(ConstructionInvoice invoice, String amount) {
        payments.computeIfAbsent(invoice.getId(), k -> new ArrayList<>()).add(new InvoicePaymentSummaryDTO(
                UUID.randomUUID(), LocalDate.parse("2026-09-10"), "MULTIBANCO", new BigDecimal(amount),
                new BigDecimal(amount), null, null, null, null, null, null, null, List.of()));
        paid.merge(invoice.getId(), new BigDecimal(amount), BigDecimal::add);
    }

    private ExpensesImportResultDTO roundTrip(Scope scope) {
        BudgetExcelExportService.ExportFile file = service.export(scope);
        return importService.importExpenses(scope.name(), null,
                new MockMultipartFile("file", file.fileName(), BudgetExcelExportService.CONTENT_TYPE, file.content()),
                true, null);
    }

    private static ExpensesImportInvoiceDTO byNumber(ExpensesImportResultDTO result, String number) {
        return result.invoices().stream()
                .filter(invoice -> number.equals(invoice.invoiceNumber()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("As despesas da empresa voltam a entrar sem erros, com as mesmas linhas e os mesmos totais")
    void companyWorkbookRoundTrips() {
        ConstructionInvoice origin = invoice(Scope.COMPANY, "FT C/1", "2026-09-01", "100");
        pay(invoice(Scope.COMPANY, "FT C/2", "2026-09-02", "50"), "50");
        ConstructionInvoice creditNote = invoice(Scope.COMPANY, "NC C/1", "2026-09-03", "20");
        creditNote.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        creditNote.setRelatedInvoiceId(origin.getId());

        ExpensesImportResultDTO imported = roundTrip(Scope.COMPANY);

        assertThat(imported.errors()).isEmpty();
        assertThat(imported.questions()).isEmpty();
        assertThat(imported.rowCount()).isEqualTo(3);
        assertThat(imported.parsedTotal()).isEqualByComparingTo("130.00"); // 100 + 50 − 20
        assertThat(imported.sheetTotal()).isEqualByComparingTo("130.00");
        assertThat(imported.paidCount()).isEqualTo(1);
        assertThat(byNumber(imported, "FT C/2").supplierNif()).isEqualTo("500000000");
    }

    @Test
    @DisplayName("A quarentena volta a entrar sem erros, sem transferir nada e sem perder o NIF")
    void quarantineWorkbookRoundTrips() {
        ConstructionInvoice invoice = invoice(Scope.UNIDENTIFIED, "FT Q/1", "2026-09-01", "24.99");
        invoice.setPossibleEnterprises("Vila Aleu");
        invoice.setAskWhom("Encarregado");
        invoice(Scope.UNIDENTIFIED, "FT Q/2", "2026-09-02", "10");

        ExpensesImportResultDTO imported = roundTrip(Scope.UNIDENTIFIED);

        assertThat(imported.errors()).isEmpty();
        assertThat(imported.rowCount()).isEqualTo(2);
        assertThat(imported.parsedTotal()).isEqualByComparingTo("34.99");
        assertThat(imported.sheetTotal()).isEqualByComparingTo("34.99");

        ExpensesImportInvoiceDTO read = byNumber(imported, "FT Q/1");
        assertThat(read.transferTo()).isNull();
        assertThat(read.supplierNif()).isEqualTo("500000000");
        assertThat(read.possibleEnterprises()).isEqualTo("Vila Aleu");
        assertThat(read.askWhom()).isEqualTo("Encarregado");
    }

    @Test
    @DisplayName("Voltar a entrar não acumula a frase \"Em quarentena desde\": fica a primeira")
    void quarantineSinceNoteDoesNotPileUp() {
        invoice(Scope.UNIDENTIFIED, "FT Q/1", "2026-09-01", "10").setNotes("Em quarentena desde 02-01-2026.");
        invoice(Scope.UNIDENTIFIED, "FT Q/2", "2026-09-02", "10");

        ExpensesImportResultDTO imported = roundTrip(Scope.UNIDENTIFIED);

        assertThat(byNumber(imported, "FT Q/1").notes()).isEqualTo("Em quarentena desde 02-01-2026.");
        // sem a frase, "Aqui desde" é a data de Lisboa do carregamento: 23:30 UTC de julho já é dia 15
        assertThat(byNumber(imported, "FT Q/2").notes()).isEqualTo("Em quarentena desde 15-07-2026.");
    }

    @Test
    @DisplayName("Os dois livros têm a folha, as colunas e o nome que o vault e o importador esperam")
    void workbooksCarryTheVaultNames() throws IOException {
        invoice(Scope.COMPANY, "FT C/1", "2026-09-01", "100");

        BudgetExcelExportService.ExportFile company = service.export(Scope.COMPANY);
        BudgetExcelExportService.ExportFile quarantine = service.export(Scope.UNIDENTIFIED);

        assertThat(company.fileName()).isEqualTo("Despesas da empresa.xlsx");
        assertThat(quarantine.fileName()).isEqualTo("Faturas por identificar.xlsx");
        assertThat(headersOf(company, "Despesas")).containsExactly("Nº Fatura", "Data", "Produto/Serviço", "Valor",
                "Liquidada", "Metodo Pagamento", "Bizdocs", "Observações", "Fornecedor", "NIF");
        assertThat(headersOf(quarantine, "Por identificar")).containsExactly("Nº Fatura", "Data", "Produto/Serviço", "Valor",
                "Liquidada", "Metodo Pagamento", "Bizdocs", "Observações", "Empreendimento", "Fornecedor", "NIF",
                "Obras possíveis", "Perguntar a", "Aqui desde");
    }

    private static List<String> headersOf(BudgetExcelExportService.ExportFile file, String sheetName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet(sheetName);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            header.forEach(cell -> headers.add(cell.getStringCellValue()));
            return headers;
        }
    }
}
