package com.management.managementapi.enterprises.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.management.managementapi.enterprises.dto.receipt.ReceiptCandidateInvoiceDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptPreviewDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.model.SupplierReceipt;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoiceReceiptRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptPaymentRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.HeavyWorkGate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 2 dos recibos — o preview, com o leitor de QR verdadeiro sobre um PDF
 * com um QR sintético (o PDF real do Brivel tem dados de terceiros e não vai
 * para o git). O QR imita o {@code RG TFC.2026/819}: NIF do Brivel, 27-08-2026,
 * 4 470,07 €.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierReceiptPreviewTest {

    private static final String BRIVEL_NIF = "501234567";

    private static String qr(String type, String number) {
        return String.join("*",
                "A:" + BRIVEL_NIF, "B:518849651", "C:PT", "D:" + type, "E:N", "F:20260827",
                "G:" + number, "H:0", "I1:0", "N:0.00", "O:4470.07", "Q:abcd", "R:1234");
    }

    @Mock private SupplierReceiptRepository receiptRepository;
    @Mock private InvoiceReceiptRepository invoiceReceiptRepository;
    @Mock private SupplierReceiptPaymentRepository receiptPaymentRepository;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private PaymentService paymentService;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AuthContext authContext;

    private final Map<UUID, BigDecimal> paidSoFar = new HashMap<>();
    private SupplierReceiptService service;

    @BeforeEach
    void setUp() {
        when(receiptRepository.findDuplicate(anyString(), anyString(), any())).thenReturn(Optional.empty());
        Supplier brivel = new Supplier();
        brivel.setName("Brivel");
        when(supplierRepository.findByNif(BRIVEL_NIF)).thenReturn(Optional.of(brivel));
        when(paymentService.netAmounts(any())).thenAnswer(call -> {
            Collection<ConstructionInvoice> invoices = call.getArgument(0);
            Map<UUID, BigDecimal> out = new HashMap<>();
            invoices.forEach(i -> out.put(i.getId(), i.getTotalAmount()));
            return out;
        });
        when(paymentService.paidSums(any())).thenAnswer(call -> paidSoFar);

        AtInvoiceQrService qrService = new AtInvoiceQrService(new WeChatQrCodeService(), new HeavyWorkGate());
        service = new SupplierReceiptService(receiptRepository, invoiceReceiptRepository, receiptPaymentRepository,
                invoiceRepository, supplierRepository, profileRepository, paymentService, qrService,
                storageService, signedUrls, authContext);
    }

    private static ConstructionInvoice invoice(String number, LocalDate date, String total, boolean testObra) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(UUID.randomUUID());
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        inv.setSupplierNif(BRIVEL_NIF);
        inv.setInvoiceNumber(number);
        inv.setInvoiceDate(date);
        inv.setTotalAmount(new BigDecimal(total));
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());
        enterprise.setName(testObra ? "Obra teste" : "Vila Petrus");
        enterprise.setIsTest(testObra);
        inv.setEnterprise(enterprise);
        return inv;
    }

    @Test
    @DisplayName("um QR RG dá nº, data, valor e fornecedor; candidatas = por liquidar + pagas recentes, sem as pagas antigas")
    void previewDeUmRecibo() throws Exception {
        LocalDate today = LocalDate.now();
        ConstructionInvoice aberta = invoice("FT FA.20263/1200", today.minusDays(10), "300.00", false);
        ConstructionInvoice pagaRecente = invoice("FT FA.20263/1172", today.minusMonths(3), "467.65", false);
        ConstructionInvoice pagaAntiga = invoice("FT FA.20243/0001", today.minusMonths(14), "10.00", false);
        ConstructionInvoice abertaAntiga = invoice("FT FA.20243/0002", today.minusMonths(20), "20.00", false);
        paidSoFar.put(pagaRecente.getId(), new BigDecimal("467.65"));
        paidSoFar.put(pagaAntiga.getId(), new BigDecimal("10.00"));
        // a query devolve da mais recente para a mais antiga
        when(invoiceRepository.findReceiptCandidates(BRIVEL_NIF, false))
                .thenReturn(List.of(aberta, pagaRecente, pagaAntiga, abertaAntiga));

        SupplierReceiptPreviewDTO out = service.preview(pdf(qr("RG", "RG TFC.2026/819")), null);

        assertThat(out.qrRead()).isTrue();
        assertThat(out.documentType()).isEqualTo("RG");
        assertThat(out.supplierNif()).isEqualTo(BRIVEL_NIF);
        assertThat(out.supplierName()).isEqualTo("Brivel");
        assertThat(out.receiptNumber()).isEqualTo("RG TFC.2026/819");
        assertThat(out.receiptDate()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(out.totalAmount()).isEqualByComparingTo("4470.07");
        assertThat(out.warnings()).isEmpty();
        // por liquidar primeiro (mais antiga antes), depois as pagas recentes
        assertThat(out.candidateInvoices()).extracting(ReceiptCandidateInvoiceDTO::invoiceNumber)
                .containsExactly("FT FA.20243/0002", "FT FA.20263/1200", "FT FA.20263/1172");
        assertThat(out.candidateInvoices().get(2).paymentStatus()).isEqualTo("PAID");
        assertThat(out.candidateInvoices().get(2).remaining()).isEqualByComparingTo("0");
        assertThat(out.candidateInvoices().get(1).remaining()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("um QR FR (fatura-recibo) avisa que não é um recibo; um nº já gravado avisa do duplicado")
    void frAvisaEDuplicadoAvisa() throws Exception {
        SupplierReceipt existing = new SupplierReceipt();
        existing.setReceiptNumber("FR 2026/5");
        when(receiptRepository.findDuplicate(BRIVEL_NIF, "FR20265", null)).thenReturn(Optional.of(existing));

        SupplierReceiptPreviewDTO out = service.preview(pdf(qr("FR", "FR 2026/5")), null);

        assertThat(out.warnings()).anySatisfy(w -> assertThat(w).contains("não é um recibo (tipo FR)"));
        assertThat(out.warnings()).anySatisfy(w -> assertThat(w).contains("Já existe um recibo"));
    }

    @Test
    @DisplayName("sem QR: avisa, e o NIF vem das faturas já ligadas; obras de teste só se uma ligada é de teste")
    void semQrUsaAsLigadasEIncluiTeste() throws Exception {
        ConstructionInvoice ligadaDeTeste = invoice("FT T/1", LocalDate.now(), "5.00", true);
        when(invoiceRepository.findAllById(List.of(ligadaDeTeste.getId()))).thenReturn(List.of(ligadaDeTeste));
        when(invoiceRepository.findReceiptCandidates(eq(BRIVEL_NIF), anyBoolean())).thenReturn(List.of(ligadaDeTeste));

        MockMultipartFile blank = new MockMultipartFile("file", "recibo.pdf", "application/pdf", blankPdf());
        SupplierReceiptPreviewDTO out = service.preview(blank, List.of(ligadaDeTeste.getId()));

        assertThat(out.qrRead()).isFalse();
        assertThat(out.warnings()).anySatisfy(w -> assertThat(w).contains("Não foi possível ler o QR"));
        assertThat(out.supplierNif()).isEqualTo(BRIVEL_NIF);
        verify(invoiceRepository).findReceiptCandidates(BRIVEL_NIF, true);
        assertThat(out.candidateInvoices()).hasSize(1);
    }

    // ── PDFs sintéticos ─────────────────────────────────────────

    private static MockMultipartFile pdf(String qrContent) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 300, 300);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "png", png);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png.toByteArray(), "qr.png");
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 150f, 400f, 200f, 200f);
            }
            document.save(pdf);
        }
        return new MockMultipartFile("file", "recibo.pdf", "application/pdf", pdf.toByteArray());
    }

    private static byte[] blankPdf() throws Exception {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf);
        }
        return pdf.toByteArray();
    }
}
