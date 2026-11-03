package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.backup.request.BackupRequestDTO;
import com.management.managementapi.enterprises.dto.backup.request.BackupRequestDTO.Selection;
import com.management.managementapi.enterprises.dto.backup.response.BackupPreviewDTO;
import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceDocumentsScope;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.service.attendance.AttendanceZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O backup: nomes das pastas e do zip, a pasta por lote, o que fica de fora, e o que
 * acontece quando algo falha a meio. Os planos e os livros têm os seus próprios testes;
 * aqui {@code writeDocuments} é o real, para provar onde cai o {@code _EM-FALTA.txt}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupServiceTest {

    private static final LocalDate SUMMER_DAY = LocalDate.of(2026, 7, 15);
    private static final String DATE = "2026-07-15";

    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ConstructionBudgetItemService budgetService;
    @Mock private BudgetExcelExportService excelExportService;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private UnassignedInvoicesWorkbookService unassignedWorkbookService;
    @Mock private AttendanceZone zone;

    private InvoiceDocumentsExportService documents;
    private BackupService service;

    @BeforeEach
    void setUp() throws IOException {
        documents = spy(new InvoiceDocumentsExportService(invoiceRepository, documentRepository, expenseRepository, storageService));
        service = new BackupService(enterpriseRepository, budgetService, excelExportService, documents,
                unassignedWorkbookService, zone);
        when(zone.today()).thenReturn(SUMMER_DAY);
        when(storageService.download(eq("invoices"), any())).thenReturn("pdf".getBytes(StandardCharsets.UTF_8));
    }

    // ── fixture ────────────────────────────────────────────────

    private Enterprise enterprise(String slug, boolean test) {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());
        enterprise.setName(slug);
        enterprise.setSlug(slug);
        enterprise.setIsTest(test);
        when(enterpriseRepository.findById(enterprise.getId())).thenReturn(Optional.of(enterprise));
        when(budgetService.listLots(enterprise.getId())).thenReturn(List.of());
        stubWorkbooks(enterprise);
        return enterprise;
    }

    private void stubWorkbooks(Enterprise enterprise) {
        when(excelExportService.export(eq(enterprise.getId()), any())).thenReturn(
                new BudgetExcelExportService.ExportFile(BudgetExcelExportService.fileName(enterprise), new byte[] {1}));
        when(excelExportService.exportWithoutLot(enterprise.getId())).thenReturn(
                new BudgetExcelExportService.ExportFile(BudgetExcelExportService.withoutLotFileName(enterprise), new byte[] {1}));
    }

    private BudgetLotDTO lot(Enterprise enterprise, String name, int itemCount) {
        BudgetLotDTO lot = new BudgetLotDTO(UUID.randomUUID(), name, 0, itemCount, BigDecimal.ZERO, BigDecimal.ZERO);
        when(excelExportService.exportLot(eq(lot.id()), any())).thenReturn(new BudgetExcelExportService.ExportFile(
                BudgetExcelExportService.lotFileName(enterprise, name), new byte[] {1}));
        return lot;
    }

    /** Um documento por fatura, com o caminho que o plano real daria. */
    private void wholeEnterpriseHasDocument(Enterprise enterprise, String key) {
        doAnswer(inv -> plan(inv.getArgument(3), key)).when(documents)
                .plan(eq(enterprise.getId()), eq(InvoiceDocumentsScope.ALL), any(), any(String.class));
    }

    private void lotHasDocument(Enterprise enterprise, BudgetLotDTO lot, String key) {
        doAnswer(inv -> plan(inv.getArgument(3), key)).when(documents)
                .plan(eq(enterprise.getId()), eq(InvoiceDocumentsScope.ASSOCIATED), eq(lot.id()), any(String.class));
    }

    private void withoutLotHasDocument(Enterprise enterprise, String key) {
        doAnswer(inv -> plan(inv.getArgument(1), key)).when(documents)
                .planWithoutLot(eq(enterprise.getId()), any(String.class));
    }

    private void withoutEnterpriseHasDocument(ConstructionInvoice.Scope scope, String key) {
        doAnswer(inv -> plan(inv.getArgument(1), key)).when(documents)
                .planWithoutEnterprise(eq(scope), any(String.class));
    }

    private static InvoiceDocumentsExportService.Plan plan(String folder, String key) {
        return new InvoiceDocumentsExportService.Plan(List.of(new InvoiceDocumentsExportService.Entry(
                folder + key + ".pdf", UUID.randomUUID(), "invoices", key, false)), 0, List.of());
    }

    private static BackupRequestDTO select(Selection... selections) {
        return request(List.of(selections), true, true);
    }

    private static BackupRequestDTO request(List<Selection> selections, boolean workbooks, boolean documents) {
        return new BackupRequestDTO(false, selections, false, false, workbooks, documents);
    }

    private static Selection whole(Enterprise enterprise) {
        return new Selection(enterprise.getId(), null);
    }

    private Map<String, String> zipOf(BackupRequestDTO request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.write(service.plan(request), out);
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static void assertRefused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    // ── nomes ──────────────────────────────────────────────────

    @Test
    @DisplayName("A pasta e o zip levam a data do fuso de Lisboa, e uma pasta só dá o zip com o nome dela")
    void folderAndZipCarryTheLisbonDate() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        wholeEnterpriseHasDocument(petrus, "a");

        BackupService.Backup backup = service.plan(select(whole(petrus)));

        assertThat(backup.fileName()).isEqualTo("Vila Petrus - " + DATE + ".zip");
        assertThat(zipOf(select(whole(petrus))).keySet()).containsExactly(
                "Vila Petrus - " + DATE + "/Despesas - Vila Petrus.xlsx",
                "Vila Petrus - " + DATE + "/Faturas/Lançadas/a.pdf");
    }

    @Test
    @DisplayName("Várias pastas dão o zip com o nome do backup")
    void severalFoldersUseTheBackupName() {
        Enterprise petrus = enterprise("Vila Petrus", false);
        Enterprise atrium = enterprise("Villa Atrium", false);
        wholeEnterpriseHasDocument(petrus, "a");
        wholeEnterpriseHasDocument(atrium, "b");

        assertThat(service.plan(select(whole(petrus), whole(atrium))).fileName())
                .isEqualTo("Backup Vilatro - " + DATE + ".zip");
    }

    @Test
    @DisplayName("Duas obras com o mesmo nome limpo não rebentam o zip: a segunda fica (2)")
    void sameFolderNameGetsASuffix() throws IOException {
        Enterprise first = enterprise("Vila", false);
        Enterprise second = enterprise("VILA", false);
        wholeEnterpriseHasDocument(first, "a");
        wholeEnterpriseHasDocument(second, "b");

        assertThat(zipOf(select(whole(first), whole(second))).keySet()).contains(
                "Vila - " + DATE + "/Faturas/Lançadas/a.pdf",
                "VILA - " + DATE + " (2)/Faturas/Lançadas/b.pdf");
    }

    @Test
    @DisplayName("Uma obra de teste escolhida à mão leva o prefixo TESTE")
    void testEnterpriseCarriesThePrefix() {
        Enterprise test = enterprise("Vila Teste", true);
        wholeEnterpriseHasDocument(test, "a");

        assertThat(service.plan(select(whole(test))).fileName()).isEqualTo("TESTE - Vila Teste - " + DATE + ".zip");
    }

    // ── o que entra ────────────────────────────────────────────

    @Test
    @DisplayName("Obra sem orçamento leva só a folha Despesas, em vez de rebentar com BUDGET_027")
    void enterpriseWithoutBudgetOnlyAsksForExpenses() throws IOException {
        Enterprise noBudget = enterprise("Sem Orçamento", false);
        wholeEnterpriseHasDocument(noBudget, "a");

        zipOf(select(whole(noBudget)));

        verify(excelExportService).export(noBudget.getId(), EnumSet.of(BudgetExportSheet.EXPENSES));
    }

    @Test
    @DisplayName("Obra com um lote: uma pasta só, com todas as folhas")
    void singleLotKeepsTheVaultFolder() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        BudgetLotDTO onlyLot = lot(petrus, "Orçamento", 5);
        when(budgetService.listLots(petrus.getId())).thenReturn(List.of(onlyLot));
        wholeEnterpriseHasDocument(petrus, "a");

        assertThat(zipOf(select(whole(petrus))).keySet())
                .contains("Vila Petrus - " + DATE + "/Despesas - Vila Petrus.xlsx");
        verify(excelExportService).export(petrus.getId(), EnumSet.of(
                BudgetExportSheet.BUDGET, BudgetExportSheet.EXPENSES, BudgetExportSheet.COMPARISON));
        verify(excelExportService, never()).exportLot(any(), any());
    }

    @Test
    @DisplayName("Obra com vários lotes: uma pasta por lote, cada uma com o seu Excel e os seus PDFs, mais a \"Sem lote\"")
    void severalLotsGetAFolderEach() throws IOException {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO lotA = lot(aleu, "Lote A", 5);
        BudgetLotDTO lotB = lot(aleu, "Lote B", 3);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(lotA, lotB));
        lotHasDocument(aleu, lotA, "a");
        withoutLotHasDocument(aleu, "s");

        String top = "Vila Aleu - " + DATE + "/";
        assertThat(zipOf(select(whole(aleu))).keySet()).containsExactly(
                top + "Lote A/Orçamento - Vila Aleu - Lote A.xlsx",
                top + "Lote A/Faturas/Lançadas/a.pdf",
                // com orçamento mas sem faturas: leva o Excel na mesma
                top + "Lote B/Orçamento - Vila Aleu - Lote B.xlsx",
                top + "Sem lote/Despesas - Vila Aleu - Sem lote.xlsx",
                top + "Sem lote/Faturas/Lançadas/s.pdf");
        verify(excelExportService, never()).export(any(), any());
    }

    @Test
    @DisplayName("Sem faturas sem lote não há pasta \"Sem lote\", e o nome do zip continua a ser o da obra")
    void noWithoutLotFolderWhenThereAreNone() {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO lotA = lot(aleu, "Lote A", 5);
        BudgetLotDTO lotB = lot(aleu, "Lote B", 3);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(lotA, lotB));

        BackupPreviewDTO preview = service.preview(select(whole(aleu)));

        assertThat(preview.fileName()).isEqualTo("Vila Aleu - " + DATE + ".zip");
        assertThat(preview.folders()).extracting(BackupPreviewDTO.Folder::name).containsExactly(
                "Vila Aleu - " + DATE + "/Lote A", "Vila Aleu - " + DATE + "/Lote B");
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Lote sem orçamento nem faturas não tem pasta, e o preview avisa")
    void emptyLotIsLeftOutWithAWarning() {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO full = lot(aleu, "Lote A", 5);
        BudgetLotDTO empty = lot(aleu, "Lote B", 0);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(full, empty));

        BackupPreviewDTO preview = service.preview(select(whole(aleu)));

        assertThat(preview.folders()).extracting(BackupPreviewDTO.Folder::name)
                .containsExactly("Vila Aleu - " + DATE + "/Lote A");
        assertThat(preview.warnings()).containsExactly(
                "Vila Aleu - " + DATE + "/Lote B: sem orçamento nem faturas, fica de fora.");
    }

    @Test
    @DisplayName("Lote sem rubricas mas com faturas leva só a folha Despesas")
    void lotWithInvoicesButNoBudgetOnlyAsksForExpenses() throws IOException {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO full = lot(aleu, "Lote A", 5);
        BudgetLotDTO noBudget = lot(aleu, "Lote B", 0);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(full, noBudget));
        lotHasDocument(aleu, noBudget, "b");

        zipOf(select(whole(aleu)));

        verify(excelExportService).exportLot(noBudget.id(), EnumSet.of(BudgetExportSheet.EXPENSES));
    }

    @Test
    @DisplayName("Um lote escolhido à mão tem a pasta de topo só dele, e avisa das faturas sem lote")
    void chosenLotGetsItsOwnTopFolder() {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO lotA = lot(aleu, "Lote A", 5);
        BudgetLotDTO lotB = lot(aleu, "Lote B", 3);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(lotA, lotB));
        withoutLotHasDocument(aleu, "s");

        BackupPreviewDTO preview = service.preview(select(new Selection(aleu.getId(), lotA.id())));

        assertThat(preview.folders()).singleElement().satisfies(folder -> {
            assertThat(folder.name()).isEqualTo("Vila Aleu - Lote A - " + DATE);
            assertThat(folder.workbookName()).isEqualTo("Orçamento - Vila Aleu - Lote A.xlsx");
            assertThat(folder.warnings()).containsExactly("A obra tem faturas sem lote: só saem no backup da obra inteira.");
        });
    }

    @Test
    @DisplayName("\"Tudo\" leva as obras reais, a empresa e a quarentena, e deixa de fora as obras de teste")
    void allLeavesTestEnterprisesOut() {
        Enterprise real = enterprise("Vila Petrus", false);
        Enterprise test = enterprise("Vila Teste", true);
        when(enterpriseRepository.findByIsActiveTrue()).thenReturn(List.of(test, real));
        wholeEnterpriseHasDocument(real, "a");
        wholeEnterpriseHasDocument(test, "b");
        withoutEnterpriseHasDocument(ConstructionInvoice.Scope.COMPANY, "c");
        withoutEnterpriseHasDocument(ConstructionInvoice.Scope.UNIDENTIFIED, "u");

        BackupPreviewDTO preview = service.preview(new BackupRequestDTO(true, null, false, false, true, true));

        assertThat(preview.folders()).extracting(BackupPreviewDTO.Folder::name).containsExactly(
                "Vila Petrus - " + DATE, "Despesas da empresa - " + DATE, "Faturas por identificar - " + DATE);
        assertThat(preview.fileName()).isEqualTo("Backup Vilatro - " + DATE + ".zip");
    }

    @Test
    @DisplayName("A empresa usa Faturas/Lançadas e a quarentena só Lançadas, como no vault")
    void companyAndQuarantineFoldersFollowTheVault() throws IOException {
        withoutEnterpriseHasDocument(ConstructionInvoice.Scope.COMPANY, "c");
        withoutEnterpriseHasDocument(ConstructionInvoice.Scope.UNIDENTIFIED, "u");
        when(unassignedWorkbookService.export(ConstructionInvoice.Scope.COMPANY)).thenReturn(
                new BudgetExcelExportService.ExportFile(UnassignedInvoicesWorkbookService.COMPANY_FILE_NAME, new byte[] {1}));
        when(unassignedWorkbookService.export(ConstructionInvoice.Scope.UNIDENTIFIED)).thenReturn(
                new BudgetExcelExportService.ExportFile(UnassignedInvoicesWorkbookService.UNIDENTIFIED_FILE_NAME, new byte[] {1}));

        Map<String, String> zip = zipOf(new BackupRequestDTO(false, List.of(), true, true, true, true));

        assertThat(zip.keySet()).containsExactly(
                "Despesas da empresa - " + DATE + "/Despesas da empresa.xlsx",
                "Despesas da empresa - " + DATE + "/Faturas/Lançadas/c.pdf",
                "Faturas por identificar - " + DATE + "/Faturas por identificar.xlsx",
                "Faturas por identificar - " + DATE + "/Lançadas/u.pdf");
    }

    @Test
    @DisplayName("Empresa ou quarentena sem faturas não têm pasta, e o preview avisa")
    void emptyCompanyAndQuarantineAreLeftOutWithAWarning() {
        BackupPreviewDTO preview = service.preview(new BackupRequestDTO(false, List.of(), true, true, true, true));

        assertThat(preview.folders()).isEmpty();
        assertThat(preview.warnings()).containsExactly(
                "Despesas da empresa: sem faturas, fica de fora.",
                "Faturas por identificar: sem faturas, fica de fora.");
    }

    @Test
    @DisplayName("Sem Excel, a pasta não leva livro e nenhum é gerado")
    void documentsOnly() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        wholeEnterpriseHasDocument(petrus, "a");

        Map<String, String> zip = zipOf(request(List.of(whole(petrus)), false, true));

        assertThat(zip.keySet()).containsExactly("Vila Petrus - " + DATE + "/Faturas/Lançadas/a.pdf");
        verify(excelExportService, never()).export(any(), any());
    }

    @Test
    @DisplayName("Sem Ficheiros, a pasta não leva PDFs")
    void workbooksOnly() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        wholeEnterpriseHasDocument(petrus, "a");

        Map<String, String> zip = zipOf(request(List.of(whole(petrus)), true, false));

        assertThat(zip.keySet()).containsExactly("Vila Petrus - " + DATE + "/Despesas - Vila Petrus.xlsx");
        verify(storageService, never()).download(any(), any());
    }

    // ── recusas ────────────────────────────────────────────────

    @Test
    @DisplayName("Recusa: sem seleção, sem Excel nem ficheiros, ou sem nada para descarregar")
    void refusals() {
        Enterprise petrus = enterprise("Vila Petrus", false);

        assertRefused(() -> service.preview(request(List.of(), true, true)), ErrorCode.BACKUP_NO_SELECTION);
        assertRefused(() -> service.preview(request(null, true, true)), ErrorCode.BACKUP_NO_SELECTION);
        assertRefused(() -> service.preview(request(List.of(whole(petrus)), false, false)), ErrorCode.BACKUP_NO_CONTENT_TYPE);
        // sem orçamento nem faturas
        assertRefused(() -> service.plan(select(whole(petrus))), ErrorCode.BACKUP_NOTHING_TO_DOWNLOAD);
    }

    @Test
    @DisplayName("Recusa: a obra e um lote dela ao mesmo tempo, ou um lote de outra obra")
    void refusesDuplicatedOrForeignLot() {
        Enterprise aleu = enterprise("Vila Aleu", false);
        BudgetLotDTO lotA = lot(aleu, "Lote A", 5);
        when(budgetService.listLots(aleu.getId())).thenReturn(List.of(lotA));

        assertRefused(() -> service.preview(select(whole(aleu), new Selection(aleu.getId(), lotA.id()))),
                ErrorCode.BACKUP_INVALID_SELECTION);
        assertRefused(() -> service.preview(select(new Selection(aleu.getId(), UUID.randomUUID()))),
                ErrorCode.BACKUP_INVALID_SELECTION);
    }

    @Test
    @DisplayName("Recusa: obra que não existe")
    void refusesUnknownEnterprise() {
        UUID unknown = UUID.randomUUID();
        when(enterpriseRepository.findById(unknown)).thenReturn(Optional.empty());

        assertRefused(() -> service.preview(select(new Selection(unknown, null))), ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND);
    }

    // ── falhas a meio ──────────────────────────────────────────

    @Test
    @DisplayName("Um documento que o Storage não devolve fica no _EM-FALTA.txt da pasta dele")
    void missingDocumentIsReportedInItsOwnFolder() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        Enterprise atrium = enterprise("Villa Atrium", false);
        wholeEnterpriseHasDocument(petrus, "falta");
        wholeEnterpriseHasDocument(atrium, "existe");
        when(storageService.download("invoices", "falta")).thenThrow(new IOException("404"));

        Map<String, String> zip = zipOf(select(whole(petrus), whole(atrium)));

        assertThat(zip.keySet()).contains(
                "Vila Petrus - " + DATE + "/Faturas/Lançadas/_EM-FALTA.txt",
                "Villa Atrium - " + DATE + "/Faturas/Lançadas/existe.pdf");
        assertThat(zip.get("Vila Petrus - " + DATE + "/Faturas/Lançadas/_EM-FALTA.txt")).contains("falta.pdf");
        assertThat(zip.keySet()).doesNotContain("Villa Atrium - " + DATE + "/Faturas/Lançadas/_EM-FALTA.txt");
    }

    @Test
    @DisplayName("Um Excel que falha vai para o _ERROS.txt e o resto do backup continua")
    void failedWorkbookGoesToTheErrorsReport() throws IOException {
        Enterprise broken = enterprise("Vila Partida", false);
        Enterprise petrus = enterprise("Vila Petrus", false);
        wholeEnterpriseHasDocument(broken, "a");
        wholeEnterpriseHasDocument(petrus, "b");
        when(excelExportService.export(eq(broken.getId()), any()))
                .thenThrow(new BusinessException(ErrorCode.BUDGET_EXPORT_WRITE_ERROR));

        Map<String, String> zip = zipOf(select(whole(broken), whole(petrus)));

        assertThat(zip.keySet()).contains(
                "Vila Partida - " + DATE + "/Faturas/Lançadas/a.pdf",
                "Vila Petrus - " + DATE + "/Despesas - Vila Petrus.xlsx");
        assertThat(zip.get(BackupService.ERRORS_REPORT)).contains("Despesas - Vila Partida.xlsx");
    }

    @Test
    @DisplayName("Sem falhas não há _ERROS.txt")
    void noErrorsReportWithoutErrors() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        wholeEnterpriseHasDocument(petrus, "a");

        assertThat(zipOf(select(whole(petrus))).keySet()).doesNotContain(BackupService.ERRORS_REPORT);
    }

    @Test
    @DisplayName("Quando o browser cancela, a escrita falha e o servidor para de ir ao Storage")
    void cancelledDownloadStopsTheWork() throws IOException {
        Enterprise petrus = enterprise("Vila Petrus", false);
        List<InvoiceDocumentsExportService.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(new InvoiceDocumentsExportService.Entry(
                    "x/Faturas/Lançadas/" + i + ".pdf", UUID.randomUUID(), "invoices", "k" + i, false));
        }
        doAnswer(inv -> new InvoiceDocumentsExportService.Plan(entries, 0, List.of())).when(documents)
                .plan(eq(petrus.getId()), eq(InvoiceDocumentsScope.ALL), any(), any(String.class));
        when(storageService.download(eq("invoices"), startsWith("k"))).thenReturn(new byte[1024]);
        BackupService.Backup backup = service.plan(select(whole(petrus)));

        OutputStream closedByTheBrowser = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        assertThatThrownBy(() -> service.write(backup, closedByTheBrowser)).isInstanceOf(IOException.class);
        verify(storageService, atMost(1)).download(eq("invoices"), startsWith("k"));
    }
}
