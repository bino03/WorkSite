package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
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
    @Mock private SupabaseStorageService storageService;
    @InjectMocks private InvoiceDocumentsExportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();
    private final List<ConstructionInvoice> invoices = new ArrayList<>();
    private final List<ConstructionInvoiceDocument> documents = new ArrayList<>();
    private final Map<String, byte[]> storage = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        when(invoiceRepository.findAllByEnterpriseIdForExport(ENTERPRISE_ID)).thenReturn(invoices);
        when(documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(any())).thenReturn(documents);
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
}
