package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceDocumentsScope;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A pasta {@code Faturas\Lançadas\} do vault gerada a partir da app (docs/excel-parity.md
 * §7): o nome de cada ficheiro, o que se preserva e o que se gera, as colisões, e o zip
 * com o livro na raiz.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDocumentsExportServiceTest {

    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private SupabaseStorageService storageService;
    @InjectMocks private InvoiceDocumentsExportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();
    private final List<ConstructionInvoice> invoices = new ArrayList<>();
    private final List<ConstructionInvoiceDocument> documents = new ArrayList<>();
    private final Map<String, byte[]> storage = new LinkedHashMap<>();
    private final List<ConstructionExpense> expenses = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        when(invoiceRepository.findAllByEnterpriseIdForExport(ENTERPRISE_ID)).thenReturn(invoices);
        // os mocks filtram pelos ids recebidos, como os repositórios reais — senão o
        // filtro por âmbito parecia não funcionar (ou pior: parecia funcionar sem funcionar)
        when(documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(any())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return documents.stream().filter(d -> ids.contains(d.getInvoice().getId())).toList();
        });
        when(expenseRepository.findByInvoiceIdIn(any())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            return expenses.stream().filter(e -> ids.contains(e.getInvoice().getId())).toList();
        });
        when(storageService.download(eq("invoices"), any())).thenAnswer(inv -> {
            byte[] content = storage.get(inv.getArgument(1, String.class));
            if (content == null) throw new IOException("404");
            return content;
        });
    }

    // ── o nome (§7) ─────────────────────────────────────────────

    @Test
    @DisplayName("Nome gerado: data da fatura, nº sanitizado sem espaços, fornecedor em CamelCase sem acentos")
    void generatesTheVaultName() {
        ConstructionInvoice invoice = invoice("FR 2026A24/1412", "2026-06-17", "Inoxtubo", "Tubos");
        assertThat(name(invoice, document(invoice, "IMG_1234.jpeg", "image/jpeg")))
                .isEqualTo("20260617_FR2026A24-1412_Inoxtubo.jpeg");

        ConstructionInvoice vilasBoas = invoice("FT FT-V001.04/2605824", "2026-03-02", "Casa Vilas Boas & Cª, Lda", "Tintas");
        assertThat(name(vilasBoas, document(vilasBoas, "scan.pdf", "application/pdf")))
                .isEqualTo("20260302_FTFT-V001.04-2605824_CasaVilasBoasCLda.pdf");

        ConstructionInvoice accented = invoice("FA FA2026A/10", "2026-05-20", "Calinorte (Nuno Miguel Cunha Unipessoal Lda)", "Rufo");
        assertThat(name(accented, document(accented, null, "application/pdf")))
                .isEqualTo("20260520_FAFA2026A-10_CalinorteNunoMiguelCunhaUnipessoalLda.pdf");
    }

    @Test
    @DisplayName("Sem nº é SEM-N com a descrição no lugar do fornecedor; páginas separadas levam _pN; sem data leva a do upload com aviso")
    void specialCases() {
        ConstructionInvoice noNumber = invoice(null, "2026-08-05", "Leroy", "Vassoura de piaçaba");
        assertThat(name(noNumber, document(noNumber, "foto.jpg", "image/jpeg")))
                .isEqualTo("20260805_SEM-N_VassouraDePiacaba.jpg");

        ConstructionInvoice brivel = invoice("FT FA.2026/3-1146", "2026-07-20", "Brivel", "Ferro");
        ConstructionInvoiceDocument page2 = document(brivel, "p2.pdf", "application/pdf");
        page2.setKind(ConstructionInvoiceDocument.Kind.PAGE);
        page2.setPageNumber(2);
        assertThat(name(brivel, page2)).isEqualTo("20260720_FTFA.2026-3-1146_Brivel_p2.pdf");

        ConstructionInvoice noDate = invoice("FT 9", null, "Galp", "Gasóleo");
        ConstructionInvoiceDocument doc = document(noDate, "talao.png", "image/png");
        doc.setUploadedAt(OffsetDateTime.parse("2026-09-01T10:00:00Z"));
        List<String> warnings = new ArrayList<>();
        assertThat(InvoiceDocumentsExportService.vaultFileName(noDate, doc, warnings)).isEqualTo("20260901_FT9_Galp.png");
        assertThat(warnings).singleElement().satisfies(w -> assertThat(w).contains("não tem data"));
    }

    @Test
    @DisplayName("Sem nome de fornecedor usa o NIF; sem nome e sem NIF cai no literal Fornecedor")
    void fallsBackToNifThenToTheLiteral() {
        ConstructionInvoice unknownButWithNif = invoice("FR 100", "2026-09-10", null, "Areia");
        unknownButWithNif.setSupplierNif("518849651");
        assertThat(name(unknownButWithNif, document(unknownButWithNif, "foto.jpg", "image/jpeg")))
                .isEqualTo("20260910_FR100_518849651.jpg");

        ConstructionInvoice noNameNoNif = invoice("FR 101", "2026-09-11", null, "Brita");
        assertThat(name(noNameNoNif, document(noNameNoNif, "foto2.jpg", "image/jpeg")))
                .isEqualTo("20260911_FR101_Fornecedor.jpg");
    }

    @Test
    @DisplayName("O original_filename que já é o do vault mantém-se tal e qual; os outros são gerados")
    void preservesVaultNamesAndRenamesTheRest() {
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("20260617_FR2026A24-1412_Inoxtubo.pdf")).isTrue();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("20260720_FTFA.20263-1146_Brivel_p1.pdf")).isTrue();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("20260306-20260509_MACO9DOCS_Combustivel.pdf")).isTrue();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("20260805_SEM-N_Vassoura_2.jpeg")).isTrue();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("IMG_20260617_1412.jpg")).isFalse();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("20260617_FR 2026A24_Inoxtubo.pdf")).isFalse();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention("Fatura Inoxtubo.pdf")).isFalse();
        assertThat(InvoiceDocumentsExportService.followsVaultConvention(null)).isFalse();

        ConstructionInvoice migrated = invoice("FAC2026/134", "2026-04-01", "Fornecedor Qualquer", "X");
        document(migrated, "20260401_FTFAC2026-134_Fornecedor.pdf", "application/pdf");
        ConstructionInvoice fromApp = invoice("FT 2026/7", "2026-04-02", "Outro Fornecedor", "Y");
        document(fromApp, "WhatsApp Image.jpeg", "image/jpeg");

        InvoiceDocumentsExportService.Plan plan = service.plan(ENTERPRISE_ID);

        assertThat(plan.entries()).extracting(InvoiceDocumentsExportService.Entry::path).containsExactly(
                "Faturas/Lançadas/20260401_FTFAC2026-134_Fornecedor.pdf",
                "Faturas/Lançadas/20260402_FT2026-7_OutroFornecedor.jpeg");
        assertThat(plan.entries()).extracting(InvoiceDocumentsExportService.Entry::renamed).containsExactly(false, true);
        assertThat(plan.toSummary().renamedCount()).isEqualTo(1);
        assertThat(plan.toSummary().documentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Colisões ficam _2, _3 (nunca sobrepor) e vão para os avisos; faturas sem documento contam-se")
    void collisionsGetSuffixes() {
        ConstructionInvoice invoice = invoice("FT 1", "2026-01-05", "Cimpor", "Cimento");
        document(invoice, "a.pdf", "application/pdf");
        document(invoice, "b.pdf", "application/pdf");
        document(invoice, "c.PDF", "application/pdf");
        invoice("FT 2", "2026-01-06", "Sem Documento", "Nada");

        InvoiceDocumentsExportService.Plan plan = service.plan(ENTERPRISE_ID);

        assertThat(plan.entries()).extracting(InvoiceDocumentsExportService.Entry::path).containsExactly(
                "Faturas/Lançadas/20260105_FT1_Cimpor.pdf",
                "Faturas/Lançadas/20260105_FT1_Cimpor_2.pdf",
                "Faturas/Lançadas/20260105_FT1_Cimpor_3.pdf");
        assertThat(plan.warnings()).hasSize(2).allSatisfy(w -> assertThat(w).contains("ficariam com o nome"));
        assertThat(plan.invoicesWithoutDocument()).isEqualTo(1);
    }

    // ── o zip ───────────────────────────────────────────────────

    @Test
    @DisplayName("O zip leva o livro na raiz e os documentos em Faturas/Lançadas/; um que o Storage não devolva fica listado, não aborta")
    void writesTheZipAndReportsMissingFiles() throws Exception {
        ConstructionInvoice invoice = invoice("FT 1", "2026-01-05", "Cimpor", "Cimento");
        document(invoice, "a.pdf", "application/pdf");
        ConstructionInvoiceDocument lost = document(invoice, "b.pdf", "application/pdf");
        storage.remove(lost.getStorageKey());

        InvoiceDocumentsExportService.Plan plan = service.plan(ENTERPRISE_ID);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeZip("Despesas - Vila Teste.xlsx", "xlsx".getBytes(StandardCharsets.UTF_8), plan, out);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsOnlyKeys(
                "Despesas - Vila Teste.xlsx",
                "Faturas/Lançadas/20260105_FT1_Cimpor.pdf",
                "Faturas/Lançadas/_EM-FALTA.txt");
        assertThat(entries.get("Despesas - Vila Teste.xlsx")).isEqualTo("xlsx");
        assertThat(entries.get("Faturas/Lançadas/20260105_FT1_Cimpor.pdf")).isEqualTo("bytes:" + plan.entries().get(0).storageKey());
        assertThat(entries.get("Faturas/Lançadas/_EM-FALTA.txt")).contains("20260105_FT1_Cimpor_2.pdf");
    }

    // ── helpers ─────────────────────────────────────────────────

    private static String name(ConstructionInvoice invoice, ConstructionInvoiceDocument document) {
        return InvoiceDocumentsExportService.vaultFileName(invoice, document, new ArrayList<>());
    }

    private ConstructionInvoice invoice(String number, String date, String supplier, String description) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(date == null ? null : LocalDate.parse(date));
        invoice.setSupplierName(supplier);
        invoice.setDescription(description);
        invoices.add(invoice);
        return invoice;
    }

    // ── âmbito: obra, lote, por classificar ─────────────────────

    /** Classifica a fatura numa rubrica de um lote — é daqui que sai o lote dela. */
    private void classify(ConstructionInvoice invoice, UUID budgetId) {
        ConstructionBudget lot = new ConstructionBudget();
        lot.setId(budgetId);
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(UUID.randomUUID());
        item.setBudget(lot);
        ConstructionExpense expense = new ConstructionExpense();
        expense.setId(UUID.randomUUID());
        expense.setInvoice(invoice);
        expense.setBudgetItem(item);
        expenses.add(expense);
    }

    @Test
    @DisplayName("O zip de um lote leva só os documentos das faturas classificadas em rubricas desse lote")
    void lotScopeKeepsOnlyThatLotsInvoices() {
        UUID lotA = UUID.randomUUID(), lotB = UUID.randomUUID();

        ConstructionInvoice daA = invoice("FT A/1", "2026-09-01", "Casa Dolores", "Tijolo");
        document(daA, "a.pdf", "application/pdf");
        classify(daA, lotA);

        ConstructionInvoice daB = invoice("FT B/1", "2026-09-02", "Leroy", "Cimento");
        document(daB, "b.pdf", "application/pdf");
        classify(daB, lotB);

        ConstructionInvoice semRubrica = invoice("FT X/1", "2026-09-03", "Galp", "Gasóleo");
        document(semRubrica, "x.pdf", "application/pdf");

        var planA = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lotA, "");
        assertThat(planA.entries()).singleElement()
                .satisfies(e -> assertThat(e.path()).isEqualTo("20260901_FTA-1_CasaDolores.pdf"));

        var planB = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lotB, "");
        assertThat(planB.entries()).singleElement()
                .satisfies(e -> assertThat(e.path()).isEqualTo("20260902_FTB-1_Leroy.pdf"));

        // a obra toda continua a levar os três, e na pasta do vault
        var todos = service.plan(ENTERPRISE_ID);
        assertThat(todos.entries()).hasSize(3)
                .allSatisfy(e -> assertThat(e.path()).startsWith("Faturas/Lançadas/"));
    }

    @Test
    @DisplayName("O âmbito \"por classificar\" leva as que não estão em rubrica nenhuma — as que nenhum lote apanha")
    void unclassifiedScopeKeepsTheOnesNoLotWouldCatch() {
        UUID lot = UUID.randomUUID();

        ConstructionInvoice classificada = invoice("FT A/1", "2026-09-01", "Casa Dolores", "Tijolo");
        document(classificada, "a.pdf", "application/pdf");
        classify(classificada, lot);

        ConstructionInvoice porClassificar = invoice("FT X/1", "2026-09-03", "Galp", "Gasóleo");
        document(porClassificar, "x.pdf", "application/pdf");
        ConstructionInvoice outraPorClassificar = invoice("FT X/2", "2026-09-04", "Repsol", "AdBlue");
        document(outraPorClassificar, "x2.pdf", "application/pdf");

        var plan = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.UNCLASSIFIED, null, "");
        assertThat(plan.entries()).hasSize(2)
                .extracting("path")
                .containsExactly("20260903_FTX-1_Galp.pdf", "20260904_FTX-2_Repsol.pdf");

        // e a soma dos âmbitos fecha: 1 do lote + 2 por classificar = os 3 da obra
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lot, "").entries()).hasSize(1);
        assertThat(service.plan(ENTERPRISE_ID).entries()).hasSize(3);
    }

    @Test
    @DisplayName("Uma fatura repartida por dois lotes sai no zip dos dois — o documento é o mesmo")
    void invoiceSplitAcrossLotsAppearsInBoth() {
        UUID lotA = UUID.randomUUID(), lotB = UUID.randomUUID();
        ConstructionInvoice repartida = invoice("FT S/1", "2026-09-05", "Brivel", "Ferro");
        document(repartida, "s.pdf", "application/pdf");
        classify(repartida, lotA);
        classify(repartida, lotB);

        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lotA, "").entries()).hasSize(1);
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lotB, "").entries()).hasSize(1);
        // e não é "por classificar", porque tem rubricas
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.UNCLASSIFIED, null, "").entries()).isEmpty();
    }

    @Test
    @DisplayName("O nome do zip diz o âmbito, senão vários downloads da mesma obra eram indistinguíveis")
    void zipNameCarriesTheScope() {
        Enterprise e = new Enterprise();
        e.setName("Vila Aleu");
        e.setSlug("Vila Aleu");
        UUID lote = UUID.randomUUID();
        var vazio = new InvoiceDocumentsExportService.Plan(List.of(), 0, List.of());
        var seteFicheiros = new InvoiceDocumentsExportService.Plan(
                java.util.Collections.nCopies(7, new InvoiceDocumentsExportService.Entry(
                        "x.pdf", UUID.randomUUID(), "invoices", "k", false)), 0, List.of());

        var name = (java.util.function.BiFunction<InvoiceDocumentsScope, UUID, String>) (s, b) ->
                InvoiceDocumentsExportService.documentsZipName(e, s, b, b == null ? null : "Lote 3", vazio);

        assertThat(name.apply(InvoiceDocumentsScope.ALL, null)).isEqualTo("Faturas - Vila Aleu.zip");
        assertThat(name.apply(InvoiceDocumentsScope.UNCLASSIFIED, null))
                .isEqualTo("Faturas - Vila Aleu - Por classificar.zip");
        // com lote: o nome do lote; sem lote: as associadas de todos os lotes
        assertThat(name.apply(InvoiceDocumentsScope.ASSOCIATED, lote))
                .isEqualTo("Faturas - Vila Aleu - Lote 3.zip");
        assertThat(name.apply(InvoiceDocumentsScope.ASSOCIATED, null))
                .isEqualTo("Faturas - Vila Aleu - Associadas.zip");
        // a seleção não tem nome próprio — leva a contagem, que é o que a distingue
        assertThat(InvoiceDocumentsExportService.documentsZipName(
                e, InvoiceDocumentsScope.SELECTED, null, null, seteFicheiros))
                .isEqualTo("Faturas - Vila Aleu - 7 selecionadas.zip");

        e.setIsTest(true);
        assertThat(name.apply(InvoiceDocumentsScope.ALL, null))
                .isEqualTo("TESTE - Faturas - Vila Aleu.zip");
    }

    @Test
    @DisplayName("Associadas sem lote: as de todos os lotes juntas, e nunca as que estão por classificar")
    void associatedWithoutLotTakesEveryLot() {
        UUID lotA = UUID.randomUUID(), lotB = UUID.randomUUID();

        ConstructionInvoice daA = invoice("FT A/1", "2026-09-01", "Casa Dolores", "Tijolo");
        document(daA, "a.pdf", "application/pdf");
        classify(daA, lotA);
        ConstructionInvoice daB = invoice("FT B/1", "2026-09-02", "Leroy", "Cimento");
        document(daB, "b.pdf", "application/pdf");
        classify(daB, lotB);
        ConstructionInvoice semRubrica = invoice("FT X/1", "2026-09-03", "Galp", "Gasóleo");
        document(semRubrica, "x.pdf", "application/pdf");

        var todasAssociadas = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, null, "");
        assertThat(todasAssociadas.entries()).hasSize(2)
                .extracting("path")
                .containsExactly("20260901_FTA-1_CasaDolores.pdf", "20260902_FTB-1_Leroy.pdf");

        // e os dois eixos fecham: associadas + por classificar = todas
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.UNCLASSIFIED, null, "").entries()).hasSize(1);
        assertThat(service.plan(ENTERPRISE_ID).entries()).hasSize(3);
    }

    @Test
    @DisplayName("Seleção: leva as escolhidas, e ignora ids que não sejam desta obra")
    void selectedTakesOnlyTheChosenOnesOfThisEnterprise() {
        ConstructionInvoice a = invoice("FT A/1", "2026-09-01", "Casa Dolores", "Tijolo");
        document(a, "a.pdf", "application/pdf");
        ConstructionInvoice b = invoice("FT B/1", "2026-09-02", "Leroy", "Cimento");
        document(b, "b.pdf", "application/pdf");
        invoice("FT C/1", "2026-09-03", "Galp", "Gasóleo");

        var plan = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.SELECTED, null,
                List.of(a.getId(), b.getId()), "");
        assertThat(plan.entries()).extracting("path")
                .containsExactly("20260901_FTA-1_CasaDolores.pdf", "20260902_FTB-1_Leroy.pdf");

        // um id de outra obra (ou inventado) não traz nada: o ponto de partida são
        // sempre as faturas desta obra, que é o que fecha a porta ao IDOR
        var intruso = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.SELECTED, null,
                List.of(UUID.randomUUID()), "");
        assertThat(intruso.entries()).isEmpty();
    }

    @Test
    @DisplayName("Seleção vazia é erro, não um zip vazio")
    void selectedWithoutIdsFails() {
        assertThatThrownBy(() -> service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.SELECTED, null, List.of(), ""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVOICE_DOCUMENTS_NO_SELECTION);
    }

    @Test
    @DisplayName("O zip só de documentos não leva livro nenhum, e põe os ficheiros na raiz")
    void documentsOnlyZipHasNoWorkbook() throws Exception {
        ConstructionInvoice invoice = invoice("FT A/1", "2026-09-01", "Casa Dolores", "Tijolo");
        document(invoice, "a.pdf", "application/pdf");

        var plan = service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ALL, null, "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeDocumentsZip(plan, out);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).containsExactly("20260901_FTA-1_CasaDolores.pdf");
        assertThat(names).noneMatch(n -> n.endsWith(".xlsx"));
    }

    private ConstructionInvoiceDocument document(ConstructionInvoice invoice, String originalFilename, String mime) {
        ConstructionInvoiceDocument document = new ConstructionInvoiceDocument();
        document.setId(UUID.randomUUID());
        document.setInvoice(invoice);
        document.setBucket("invoices");
        document.setStorageKey("k/" + document.getId());
        document.setOriginalFilename(originalFilename);
        document.setMimeType(mime);
        document.setUploadedAt(OffsetDateTime.parse("2026-09-01T10:00:00Z"));
        documents.add(document);
        storage.put(document.getStorageKey(), ("bytes:" + document.getStorageKey()).getBytes(StandardCharsets.UTF_8));
        return document;
    }

    @Test
    @DisplayName("Uma fatura por classificar com lote entra no zip desse lote, e só nesse")
    void unclassifiedInvoiceWithLotGoesInThatLotsZip() {
        UUID lot = UUID.randomUUID();
        UUID outroLot = UUID.randomUUID();

        ConstructionInvoice comLote = invoice("FT Y/1", "2026-09-05", "Vilatro", "Cimento");
        document(comLote, "y.pdf", "application/pdf");
        ConstructionBudget doLote = new ConstructionBudget();
        doLote.setId(lot);
        comLote.setBudget(doLote);

        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, lot, "").entries())
                .extracting("path").containsExactly("20260905_FTY-1_Vilatro.pdf");
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.ASSOCIATED, outroLot, "").entries()).isEmpty();
        assertThat(service.plan(ENTERPRISE_ID, InvoiceDocumentsScope.UNCLASSIFIED, null, "").entries()).hasSize(1);
    }
}
