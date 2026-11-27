package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.receipt.InvoiceReceiptSummaryDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptInvoiceLinkDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptPaymentRequestDTO;
import com.management.managementapi.enterprises.model.Payment;
import com.management.managementapi.enterprises.model.SupplierReceiptPayment;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptCreateDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptResponseDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptUpdateDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.InvoiceReceipt;
import com.management.managementapi.enterprises.model.SupplierReceipt;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoiceReceiptRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptPaymentRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 1 dos recibos (notes/roadmap/recibos.md):
 *
 * <ol>
 *   <li>um recibo liga 1 ou N faturas, em obras diferentes;</li>
 *   <li>fornecedores diferentes bloqueiam ({@code RECEIPT_002}) — só o conflito real;</li>
 *   <li>uma NC não se liga ({@code RECEIPT_005}); um duplicado NIF+nº bloqueia ({@code RECEIPT_003});</li>
 *   <li>uma diferença de valor grava e sai em {@code mismatch};</li>
 *   <li>o {@code PUT} substitui as ligações; apagar mantém os pagamentos;</li>
 *   <li>o recibo nunca mexe no estado de pagamento.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierReceiptServiceTest {

    @Mock private SupplierReceiptRepository receiptRepository;
    @Mock private InvoiceReceiptRepository invoiceReceiptRepository;
    @Mock private SupplierReceiptPaymentRepository receiptPaymentRepository;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AuthContext authContext;
    @Mock private SupplierRepository supplierRepository;
    @Mock private PaymentService paymentService;
    @Mock private AtInvoiceQrService qrService;

    private static final UUID RECEIPT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID OBRA_A = UUID.randomUUID();
    private static final UUID OBRA_B = UUID.randomUUID();
    private static final String MODESTO_NIF = "500100200";
    private static final LocalDate DATA = LocalDate.of(2026, 11, 20);

    /** As ligações "gravadas" — o mock devolve-as como a BD devolveria. */
    private final List<InvoiceReceipt> storedLinks = new ArrayList<>();
    private final List<SupplierReceiptPayment> storedPayments = new ArrayList<>();
    /** O que cada fatura já tem pago, para o {@code paidSums} do mock. */
    private final Map<UUID, BigDecimal> paidSoFar = new HashMap<>();
    private SupplierReceiptService service;

    @BeforeEach
    void setUp() {
        when(authContext.currentProfileId()).thenReturn(Optional.of(PROFILE_ID));
        when(storageService.sanitizeFileName(anyString())).thenAnswer(call -> call.getArgument(0));
        when(signedUrls.resolve(anyString(), anyString())).thenReturn("https://signed/recibo.pdf");
        when(receiptRepository.save(any(SupplierReceipt.class))).thenAnswer(call -> {
            SupplierReceipt r = call.getArgument(0);
            if (r.getId() == null) {
                r.setId(RECEIPT_ID);
            }
            return r;
        });
        when(receiptRepository.findDuplicate(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(invoiceReceiptRepository.saveAll(any())).thenAnswer(call -> {
            Iterable<InvoiceReceipt> links = call.getArgument(0);
            links.forEach(storedLinks::add);
            return links;
        });
        when(invoiceReceiptRepository.findByReceiptId(RECEIPT_ID)).thenAnswer(call -> List.copyOf(storedLinks));
        when(invoiceReceiptRepository.findByReceiptIdIn(any())).thenAnswer(call -> List.copyOf(storedLinks));
        when(receiptPaymentRepository.save(any(SupplierReceiptPayment.class))).thenAnswer(call -> {
            storedPayments.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(receiptPaymentRepository.findByReceiptId(any())).thenAnswer(call -> List.copyOf(storedPayments));
        // líquido = total (sem NC); nada pago, salvo o que o teste disser
        when(paymentService.netAmounts(any())).thenAnswer(call -> {
            Collection<ConstructionInvoice> invoices = call.getArgument(0);
            Map<UUID, BigDecimal> out = new HashMap<>();
            invoices.forEach(i -> out.put(i.getId(), i.getTotalAmount()));
            return out;
        });
        when(paymentService.paidSums(any())).thenAnswer(call -> paidSoFar);
        when(paymentService.registerAllocated(any(), any(), anyString(), any(), anyString())).thenAnswer(call -> {
            Payment payment = new Payment();
            payment.setId(UUID.randomUUID());
            return payment;
        });

        service = new SupplierReceiptService(receiptRepository, invoiceReceiptRepository, receiptPaymentRepository,
                invoiceRepository, supplierRepository, profileRepository, paymentService, qrService,
                storageService, signedUrls, authContext);
    }

    // ── auxiliares ──────────────────────────────────────────────

    private ConstructionInvoice invoice(UUID enterpriseId, String nif, String supplierName, String total) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(UUID.randomUUID());
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setSupplierNif(nif);
        inv.setSupplierName(supplierName);
        inv.setInvoiceNumber("FAC " + inv.getId().toString().substring(0, 4));
        inv.setTotalAmount(new BigDecimal(total));
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);
        enterprise.setName(enterpriseId.equals(OBRA_A) ? "Vila Petrus" : "Villa Atrium");
        inv.setEnterprise(enterprise);
        return inv;
    }

    private void known(ConstructionInvoice... invoices) {
        when(invoiceRepository.findAllById(any())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            return Arrays.stream(invoices).filter(i -> ids.contains(i.getId())).toList();
        });
    }

    private static ReceiptInvoiceLinkDTO link(ConstructionInvoice invoice, String amount) {
        return new ReceiptInvoiceLinkDTO(invoice.getId(), new BigDecimal(amount));
    }

    private static SupplierReceiptCreateDTO createDto(String total, ReceiptInvoiceLinkDTO... links) {
        return new SupplierReceiptCreateDTO(List.of(links), "REC 2026V/931", DATA,
                new BigDecimal(total), null, null, null, null);
    }

    private static SupplierReceiptCreateDTO createAndPayDto(String total, ReceiptInvoiceLinkDTO... links) {
        return new SupplierReceiptCreateDTO(List.of(links), "REC 2026V/931", DATA,
                new BigDecimal(total), null, null, null,
                new ReceiptPaymentRequestDTO("TRANSFERENCIA", null, "lote 22062390666"));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "REC_931.pdf", "application/pdf", new byte[] { 1, 2, 3 });
    }

    // ── criar ──────────────────────────────────────────────────

    @Test
    @DisplayName("criar com 1 fatura: herda o fornecedor, normaliza o nº, sobe o ficheiro na pasta da obra")
    void criarComUmaFatura() throws Exception {
        ConstructionInvoice fac = invoice(OBRA_A, MODESTO_NIF, "Modesto Teixeira", "120.00");
        known(fac);

        SupplierReceiptResponseDTO out = service.create(createDto("120.00", link(fac, "120.00")), pdf());

        assertThat(out.supplierNif()).isEqualTo(MODESTO_NIF);
        assertThat(out.supplierName()).isEqualTo("Modesto Teixeira");
        assertThat(out.invoices()).hasSize(1);
        assertThat(out.mismatch()).isEqualByComparingTo("0");
        assertThat(out.fileUrl()).isEqualTo("https://signed/recibo.pdf");
        assertThat(out.registeredBy()).isEqualTo(PROFILE_ID);
        verify(storageService).upload(eq("documents"), startsWith("receipts/" + OBRA_A + "/"),
                eq("application/pdf"), any());
        verify(receiptRepository).save(org.mockito.ArgumentMatchers.argThat(
                r -> "REC2026V931".equals(r.getReceiptNumberNormalized())));
    }

    @Test
    @DisplayName("criar com N faturas em obras diferentes do mesmo fornecedor é aceite")
    void criarComNFaturasEmVariasObras() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, "Modesto Teixeira", "100.00");
        ConstructionInvoice b = invoice(OBRA_B, MODESTO_NIF, "MODESTO TEIXEIRA", "50.00");
        known(a, b);

        SupplierReceiptResponseDTO out = service.create(createDto("150.00", link(a, "100.00"), link(b, "50.00")), pdf());

        assertThat(out.invoices()).extracting(i -> i.enterpriseName()).containsExactly("Vila Petrus", "Villa Atrium");
        assertThat(out.linkedSum()).isEqualByComparingTo("150.00");
        assertThat(out.mismatch()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ligações repetidas no pedido juntam-se e somam o valor")
    void ligacoesRepetidasJuntamSe() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);

        SupplierReceiptResponseDTO out = service.create(createDto("100.00", link(a, "60.00"), link(a, "40.00")), pdf());

        assertThat(out.invoices()).hasSize(1);
        assertThat(out.invoices().get(0).amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("diferença entre o recibo e a soma das faturas grava e sai em mismatch")
    void diferencaDeValorGravaComMismatch() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);

        SupplierReceiptResponseDTO out = service.create(createDto("98.00", link(a, "100.00")), pdf());

        assertThat(out.mismatch()).isEqualByComparingTo("-2.00");
        verify(receiptRepository).save(any(SupplierReceipt.class));
    }

    // ── fornecedor ─────────────────────────────────────────────

    @Test
    @DisplayName("RECEIPT_002: dois NIFs diferentes bloqueiam, e nada sobe para o Storage")
    void fornecedoresDiferentesBloqueiam() throws Exception {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        ConstructionInvoice b = invoice(OBRA_A, "509999999", null, "50.00");
        known(a, b);

        assertThatThrownBy(() -> service.create(createDto("150.00", link(a, "100.00"), link(b, "50.00")), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_MIXED_SUPPLIERS);
        verify(storageService, never()).upload(anyString(), anyString(), anyString(), any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    @DisplayName("RECEIPT_002: sem NIF dos dois lados, compara-se o nome (sem acentos nem maiúsculas)")
    void semNifComparaONome() {
        ConstructionInvoice a = invoice(OBRA_A, null, "Civica Lda", "100.00");
        ConstructionInvoice b = invoice(OBRA_A, null, "Minimáquinas", "50.00");
        known(a, b);

        assertThatThrownBy(() -> service.create(createDto("150.00", link(a, "100.00"), link(b, "50.00")), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_MIXED_SUPPLIERS);

        assertThat(SupplierReceiptService.normalizeName("  Lúisa   ALMEIDA ")).isEqualTo("luisa almeida");
    }

    @Test
    @DisplayName("só conflito real: o NIF da Vilatro e uma fatura sem NIF nem nome não bloqueiam")
    void dadosSujosNaoBloqueiam() {
        ConstructionInvoice brivel = invoice(OBRA_A, "501234567", "Brivel", "100.00");
        ConstructionInvoice comNifDaVilatro = invoice(OBRA_A, SupplierReceiptService.OWN_COMPANY_NIF, "Brivel", "50.00");
        ConstructionInvoice semNada = invoice(OBRA_B, null, null, "20.00");
        known(brivel, comNifDaVilatro, semNada);

        SupplierReceiptResponseDTO out = service.create(
                createDto("170.00", link(brivel, "100.00"), link(comNifDaVilatro, "50.00"), link(semNada, "20.00")), pdf());

        assertThat(out.invoices()).hasSize(3);
        assertThat(out.supplierNif()).isEqualTo("501234567");
    }

    // ── NC, duplicado, fatura inexistente, ficheiro ─────────────

    @Test
    @DisplayName("RECEIPT_005: uma nota de crédito não se liga a um recibo")
    void notaDeCreditoNaoSeLiga() {
        ConstructionInvoice nc = invoice(OBRA_A, MODESTO_NIF, null, "10.00");
        nc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        known(nc);

        assertThatThrownBy(() -> service.create(createDto("10.00", link(nc, "10.00")), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_CREDIT_NOTE);
    }

    @Test
    @DisplayName("RECEIPT_003: mesmo NIF + nº normalizado bloqueia antes de subir o ficheiro")
    void duplicadoBloqueiaAntesDeSubir() throws Exception {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);
        SupplierReceipt existing = new SupplierReceipt();
        existing.setReceiptNumber("REC 2026V-931");
        existing.setReceiptDate(DATA);
        when(receiptRepository.findDuplicate(MODESTO_NIF, "REC2026V931", null)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(createDto("100.00", link(a, "100.00")), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_DUPLICATE);
        verify(storageService, never()).upload(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("fatura inexistente → 404 INVOICE_001; sem ligações → RECEIPT_004")
    void faturaInexistenteESemLigacoes() {
        known();
        ConstructionInvoice ghost = invoice(OBRA_A, MODESTO_NIF, null, "1.00");

        assertThatThrownBy(() -> service.create(createDto("1.00", link(ghost, "1.00")), pdf()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_NOT_FOUND);

        assertThatThrownBy(() -> service.create(createDto("1.00"), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RECEIPT_NO_INVOICES);
    }

    @Test
    @DisplayName("ficheiro obrigatório e só PDF/JPG/PNG")
    void ficheiroObrigatorioEValidado() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);

        assertThatThrownBy(() -> service.create(createDto("100.00", link(a, "100.00")), null))
                .isInstanceOf(FileUploadException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_EMPTY);

        MockMultipartFile docx = new MockMultipartFile("file", "recibo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] { 1 });
        assertThatThrownBy(() -> service.create(createDto("100.00", link(a, "100.00")), docx))
                .isInstanceOf(FileUploadException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    // ── editar, apagar, ver na fatura ───────────────────────────

    @Test
    @DisplayName("PUT substitui as ligações inteiras, sem acrescentar")
    void putSubstituiAsLigacoes() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        ConstructionInvoice b = invoice(OBRA_B, MODESTO_NIF, null, "50.00");
        known(a, b);
        service.create(createDto("100.00", link(a, "100.00")), pdf());
        SupplierReceipt saved = new SupplierReceipt();
        saved.setId(RECEIPT_ID);
        when(receiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(saved));
        org.mockito.Mockito.doAnswer(call -> {
            storedLinks.clear();
            return null;
        }).when(invoiceReceiptRepository).deleteByReceiptId(RECEIPT_ID);

        SupplierReceiptResponseDTO out = service.update(RECEIPT_ID, new SupplierReceiptUpdateDTO(
                List.of(link(b, "50.00")), "REC 2026V/931", DATA, new BigDecimal("50.00"), null, null, null));

        assertThat(out.invoices()).extracting(i -> i.invoiceId()).containsExactly(b.getId());
        verify(invoiceReceiptRepository).deleteByReceiptId(RECEIPT_ID);
    }

    @Test
    @DisplayName("apagar o recibo apaga o ficheiro e não toca nos pagamentos que originou")
    void apagarMantemOsPagamentos() throws Exception {
        SupplierReceipt saved = new SupplierReceipt();
        saved.setId(RECEIPT_ID);
        saved.setReceiptDate(DATA);
        saved.setTotalAmount(new BigDecimal("100.00"));
        saved.setFileBucket("documents");
        saved.setFileKey("receipts/x/abc_REC.pdf");
        when(receiptRepository.findById(RECEIPT_ID)).thenReturn(Optional.of(saved));

        service.delete(RECEIPT_ID);

        verify(receiptRepository).delete(saved);
        verify(storageService).delete("documents", "receipts/x/abc_REC.pdf");
        verify(receiptPaymentRepository, never()).deleteAll(any());
        verify(receiptPaymentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("no detalhe da fatura: valor para esta fatura, 'também cobre' as outras, mismatch do recibo")
    void receiptsForInvoiceMostraTambemCobre() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        ConstructionInvoice b = invoice(OBRA_B, MODESTO_NIF, null, "50.00");
        known(a, b);
        service.create(createDto("160.00", link(a, "100.00"), link(b, "50.00")), pdf());
        when(invoiceReceiptRepository.findByInvoiceId(a.getId()))
                .thenReturn(storedLinks.stream().filter(l -> l.getInvoice() == a).toList());

        List<InvoiceReceiptSummaryDTO> out = service.receiptsForInvoice(a.getId());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).amountForThisInvoice()).isEqualByComparingTo("100.00");
        assertThat(out.get(0).alsoCovers()).extracting(InvoiceReceiptSummaryDTO.CoveredInvoice::invoiceId)
                .containsExactly(b.getId());
        assertThat(out.get(0).alsoCovers().get(0).enterpriseName()).isEqualTo("Villa Atrium");
        assertThat(out.get(0).mismatch()).isEqualByComparingTo("10.00");
    }

    // ── estado de pagamento intacto ─────────────────────────────

    @Test
    @DisplayName("sem createPayment o recibo nunca mexe no estado de pagamento: nem pagamentos nem ligações deles")
    void estadoDePagamentoIntacto() {
        Constructor<?>[] constructors = SupplierReceiptService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        // só o PaymentService escreve pagamentos; o recibo não tem acesso direto às tabelas deles
        assertThat(constructors[0].getParameterTypes())
                .doesNotContain(com.management.managementapi.enterprises.repository.InvoicePaymentRepository.class,
                        com.management.managementapi.enterprises.repository.PaymentRepository.class);

        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);
        service.create(createDto("100.00", link(a, "100.00")), pdf());
        verify(paymentService, never()).registerAllocated(any(), any(), any(), any(), any());
        verify(receiptPaymentRepository, never()).save(any());
        verify(receiptPaymentRepository, never()).saveAll(any());
    }

    // ── fase 2: registar também o pagamento ──────────────────────

    @Test
    @DisplayName("createPayment com 2 por liquidar + 1 paga: paga só as 2, com min(ligação, em falta), e guarda o pagamento")
    @SuppressWarnings("unchecked")
    void createPaymentPagaSoAsPorLiquidar() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        ConstructionInvoice b = invoice(OBRA_A, MODESTO_NIF, null, "80.00");
        ConstructionInvoice paga = invoice(OBRA_A, MODESTO_NIF, null, "50.00");
        known(a, b, paga);
        paidSoFar.put(b.getId(), new BigDecimal("30.00"));   // falta 50
        paidSoFar.put(paga.getId(), new BigDecimal("50.00")); // já paga

        SupplierReceiptResponseDTO out = service.create(
                createAndPayDto("230.00", link(a, "100.00"), link(b, "80.00"), link(paga, "50.00")), pdf());

        org.mockito.ArgumentCaptor<Map<ConstructionInvoice, BigDecimal>> allocations =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(paymentService).registerAllocated(allocations.capture(), eq(DATA), eq("TRANSFERENCIA"),
                eq("lote 22062390666"), startsWith("Pago pelo recibo REC 2026V/931"));
        assertThat(allocations.getValue()).containsOnlyKeys(a, b);
        assertThat(allocations.getValue().get(a)).isEqualByComparingTo("100.00");
        assertThat(allocations.getValue().get(b)).isEqualByComparingTo("50.00");
        assertThat(out.paymentIds()).hasSize(1);
        assertThat(out.warnings()).isEmpty();
    }

    @Test
    @DisplayName("createPayment com faturas em 2 obras: um pagamento por obra")
    void createPaymentUmPorObra() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        ConstructionInvoice b = invoice(OBRA_B, MODESTO_NIF, null, "50.00");
        ConstructionInvoice a2 = invoice(OBRA_A, MODESTO_NIF, null, "10.00");
        known(a, b, a2);

        SupplierReceiptResponseDTO out = service.create(
                createAndPayDto("160.00", link(a, "100.00"), link(b, "50.00"), link(a2, "10.00")), pdf());

        verify(paymentService, org.mockito.Mockito.times(2)).registerAllocated(any(), any(), any(), any(), any());
        verify(paymentService).registerAllocated(
                org.mockito.ArgumentMatchers.argThat(m -> m.keySet().equals(java.util.Set.of(a, a2))),
                any(), any(), any(), any());
        verify(paymentService).registerAllocated(
                org.mockito.ArgumentMatchers.argThat(m -> m.keySet().equals(java.util.Set.of(b))),
                any(), any(), any(), any());
        assertThat(out.paymentIds()).hasSize(2);
    }

    @Test
    @DisplayName("createPayment sem nada por liquidar: grava o recibo, não paga, e avisa")
    void createPaymentSemNadaPorLiquidar() {
        ConstructionInvoice paga = invoice(OBRA_A, MODESTO_NIF, null, "50.00");
        known(paga);
        paidSoFar.put(paga.getId(), new BigDecimal("50.00"));

        SupplierReceiptResponseDTO out = service.create(createAndPayDto("50.00", link(paga, "50.00")), pdf());

        verify(paymentService, never()).registerAllocated(any(), any(), any(), any(), any());
        verify(receiptRepository).save(any(SupplierReceipt.class));
        assertThat(out.paymentIds()).isEmpty();
        assertThat(out.warnings()).singleElement().asString().contains("Nenhuma das faturas estava por liquidar");
    }

    @Test
    @DisplayName("createPayment: uma falha no pagamento propaga-se (a transação desfaz o recibo) e nada fica ligado")
    void falhaNoPagamentoPropaga() {
        ConstructionInvoice a = invoice(OBRA_A, MODESTO_NIF, null, "100.00");
        known(a);
        when(paymentService.registerAllocated(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVOICE_PAYMENT_EXCEEDS_NET));

        assertThatThrownBy(() -> service.create(createAndPayDto("100.00", link(a, "100.00")), pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_PAYMENT_EXCEEDS_NET);
        verify(receiptPaymentRepository, never()).save(any());
    }
}
