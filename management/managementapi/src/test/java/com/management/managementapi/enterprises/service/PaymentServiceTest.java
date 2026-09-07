package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentRequestDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentResultDTO;
import com.management.managementapi.enterprises.dto.payment.MarkPaidRequestDTO;
import com.management.managementapi.enterprises.dto.payment.PaymentResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.InvoicePayment;
import com.management.managementapi.enterprises.model.Payment;
import com.management.managementapi.enterprises.model.enums.PaymentMethod;
import com.management.managementapi.enterprises.model.enums.PaymentStatus;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.enterprises.repository.PaymentRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * As invariantes da fase 2 que são fáceis de partir sem dar por isso:
 *
 * <ol>
 *   <li>marcar sem valor paga o líquido e a fatura fica {@code PAID};</li>
 *   <li>um valor abaixo do líquido deixa-a {@code PARTIAL} — a única via de parcial nesta fase;</li>
 *   <li>um agregado só junta faturas da mesma obra;</li>
 *   <li>um agregado cujo movimento não chega para a soma não grava nada e diz quais ficam de fora;</li>
 *   <li>anular apaga o pagamento e as ligações — a fatura volta a {@code UNPAID} por derivação.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoicePaymentRepository invoicePaymentRepository;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AuthContext authContext;

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID ENTERPRISE_A = UUID.randomUUID();
    private static final UUID ENTERPRISE_B = UUID.randomUUID();

    private PaymentService service() {
        when(authContext.currentProfileId()).thenReturn(Optional.of(PROFILE_ID));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            Payment p = call.getArgument(0);
            if (p.getId() == null) {
                p.setId(PAYMENT_ID);
            }
            return p;
        });
        when(invoicePaymentRepository.save(any(InvoicePayment.class))).thenAnswer(call -> call.getArgument(0));
        return new PaymentService(paymentRepository, invoicePaymentRepository, invoiceRepository,
                profileRepository, storageService, signedUrls, authContext);
    }

    private ConstructionInvoice invoice(UUID id, UUID enterpriseId, BigDecimal total) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(id);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        inv.setSupplierNif("500100200");
        inv.setInvoiceNumber("FT " + id.toString().substring(0, 4));
        inv.setTotalAmount(total);
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);
        inv.setEnterprise(enterprise);
        return inv;
    }

    private MarkPaidRequestDTO markDto(BigDecimal amount) {
        return new MarkPaidRequestDTO(LocalDate.of(2026, 8, 28), "TRANSFERENCIA", amount, "extrato ABANCA", null);
    }

    // ── deriveStatus (a regra pura) ─────────────────────────────

    @Test
    @DisplayName("deriveStatus: sem pagamentos = UNPAID, abaixo do líquido = PARTIAL, igual = PAID")
    void deriveStatus() {
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), BigDecimal.ZERO)).isEqualTo(PaymentStatus.UNPAID);
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), new BigDecimal("400"))).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), new BigDecimal("1000"))).isEqualTo(PaymentStatus.PAID);
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), new BigDecimal("1200"))).isEqualTo(PaymentStatus.PAID);
    }

    // ── marcar uma fatura ──────────────────────────────────────

    @Test
    @DisplayName("marcar sem valor paga o líquido, regista quem o fez, e a ligação cobre a fatura toda")
    void marcarSemValorPagaOLiquido() {
        when(invoiceRepository.findById(INVOICE_ID))
                .thenReturn(Optional.of(invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000"))));
        when(invoicePaymentRepository.sumPaidByInvoice(INVOICE_ID)).thenReturn(BigDecimal.ZERO);

        PaymentResponseDTO out = service().markAsPaid(INVOICE_ID, markDto(null), null);

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("1000");
        assertThat(payment.getValue().getMethod()).isEqualTo(PaymentMethod.TRANSFERENCIA);
        assertThat(payment.getValue().getRegisteredBy()).isEqualTo(PROFILE_ID);

        ArgumentCaptor<InvoicePayment> link = ArgumentCaptor.forClass(InvoicePayment.class);
        verify(invoicePaymentRepository).save(link.capture());
        assertThat(link.getValue().getAmount()).isEqualByComparingTo("1000");
        assertThat(link.getValue().getInvoice().getId()).isEqualTo(INVOICE_ID);

        assertThat(out.amount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("marcar com um valor abaixo do líquido → a ligação é parcial (PARTIAL deriva daí)")
    void marcarParcial() {
        when(invoiceRepository.findById(INVOICE_ID))
                .thenReturn(Optional.of(invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000"))));
        when(invoicePaymentRepository.sumPaidByInvoice(INVOICE_ID)).thenReturn(BigDecimal.ZERO);

        service().markAsPaid(INVOICE_ID, markDto(new BigDecimal("400")), null);

        ArgumentCaptor<InvoicePayment> link = ArgumentCaptor.forClass(InvoicePayment.class);
        verify(invoicePaymentRepository).save(link.capture());
        assertThat(link.getValue().getAmount()).isEqualByComparingTo("400");
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), new BigDecimal("400")))
                .isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    @DisplayName("marcar uma fatura já paga por inteiro → INVOICE_019, nada gravado")
    void marcarJaPaga() {
        when(invoiceRepository.findById(INVOICE_ID))
                .thenReturn(Optional.of(invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000"))));
        when(invoicePaymentRepository.sumPaidByInvoice(INVOICE_ID)).thenReturn(new BigDecimal("1000"));

        assertThatThrownBy(() -> service().markAsPaid(INVOICE_ID, markDto(null), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_ALREADY_PAID);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("líquido = total − Σ NC: fatura de 1000 com NC de 100 → o pagamento cobre 900 (PAID)")
    void liquidoComNotaDeCredito() {
        when(invoiceRepository.findById(INVOICE_ID))
                .thenReturn(Optional.of(invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000"))));
        when(invoiceRepository.sumCreditNotesFor(INVOICE_ID)).thenReturn(new BigDecimal("100"));
        when(invoicePaymentRepository.sumPaidByInvoice(INVOICE_ID)).thenReturn(BigDecimal.ZERO);

        service().markAsPaid(INVOICE_ID, markDto(null), null);

        ArgumentCaptor<InvoicePayment> link = ArgumentCaptor.forClass(InvoicePayment.class);
        verify(invoicePaymentRepository).save(link.capture());
        assertThat(link.getValue().getAmount()).isEqualByComparingTo("900");
        assertThat(PaymentService.deriveStatus(new BigDecimal("900"), new BigDecimal("900")))
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("uma nota de crédito não se paga → INVOICE_027")
    void notaDeCreditoNaoSePaga() {
        ConstructionInvoice nc = invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("100"));
        nc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> service().markAsPaid(INVOICE_ID, markDto(null), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_IS_CREDIT_NOTE);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("marcar com um valor acima do que falta → INVOICE_020")
    void marcarAcimaDoQueFalta() {
        when(invoiceRepository.findById(INVOICE_ID))
                .thenReturn(Optional.of(invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000"))));
        when(invoicePaymentRepository.sumPaidByInvoice(INVOICE_ID)).thenReturn(new BigDecimal("800"));

        assertThatThrownBy(() -> service().markAsPaid(INVOICE_ID, markDto(new BigDecimal("300")), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_PAYMENT_EXCEEDS_NET);

        verify(paymentRepository, never()).save(any());
    }

    // ── pagamento agregado ─────────────────────────────────────

    @Test
    @DisplayName("3 faturas da mesma obra, 1 movimento com a soma → 1 pagamento, 3 ligações, created")
    void agregadoBate() {
        UUID i1 = UUID.randomUUID();
        UUID i2 = UUID.randomUUID();
        UUID i3 = UUID.randomUUID();
        List<UUID> ids = List.of(i1, i2, i3);
        when(invoiceRepository.findAllById(ids)).thenReturn(List.of(
                invoice(i1, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i2, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i3, ENTERPRISE_A, new BigDecimal("100"))));
        when(invoicePaymentRepository.sumPaidByInvoices(ids)).thenReturn(List.of());

        AggregatePaymentRequestDTO dto = new AggregatePaymentRequestDTO(
                ids, LocalDate.of(2026, 8, 28), "TRANSFERENCIA", new BigDecimal("300"), null, null);

        AggregatePaymentResultDTO out = service().registerAggregate(dto, null);

        assertThat(out.created()).isTrue();
        assertThat(out.leftOut()).isEmpty();
        verify(paymentRepository).save(any(Payment.class));
        verify(invoicePaymentRepository, times(3)).save(any(InvoicePayment.class));
    }

    @Test
    @DisplayName("movimento menor que a soma → nada gravado, e a resposta diz quais ficam de fora")
    void agregadoNaoBate() {
        UUID i1 = UUID.randomUUID();
        UUID i2 = UUID.randomUUID();
        UUID i3 = UUID.randomUUID();
        List<UUID> ids = List.of(i1, i2, i3);
        when(invoiceRepository.findAllById(ids)).thenReturn(List.of(
                invoice(i1, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i2, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i3, ENTERPRISE_A, new BigDecimal("100"))));
        when(invoicePaymentRepository.sumPaidByInvoices(ids)).thenReturn(List.of());

        AggregatePaymentRequestDTO dto = new AggregatePaymentRequestDTO(
                ids, LocalDate.of(2026, 8, 28), "TRANSFERENCIA", new BigDecimal("250"), null, null);

        AggregatePaymentResultDTO out = service().registerAggregate(dto, null);

        assertThat(out.created()).isFalse();
        assertThat(out.selectedTotal()).isEqualByComparingTo("300");
        assertThat(out.movementAmount()).isEqualByComparingTo("250");
        assertThat(out.leftOut()).extracting(l -> l.invoiceId()).containsExactly(i3);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("movimento acima da soma → INVOICE_021, nada gravado")
    void agregadoAcimaDaSoma() {
        UUID i1 = UUID.randomUUID();
        UUID i2 = UUID.randomUUID();
        List<UUID> ids = List.of(i1, i2);
        when(invoiceRepository.findAllById(ids)).thenReturn(List.of(
                invoice(i1, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i2, ENTERPRISE_A, new BigDecimal("100"))));
        when(invoicePaymentRepository.sumPaidByInvoices(ids)).thenReturn(List.of());

        AggregatePaymentRequestDTO dto = new AggregatePaymentRequestDTO(
                ids, LocalDate.of(2026, 8, 28), "TRANSFERENCIA", new BigDecimal("250"), null, null);

        assertThatThrownBy(() -> service().registerAggregate(dto, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_PAYMENT_SUM_MISMATCH);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("agregado com faturas de obras diferentes → INVOICE_022")
    void agregadoCrossObra() {
        UUID i1 = UUID.randomUUID();
        UUID i2 = UUID.randomUUID();
        List<UUID> ids = List.of(i1, i2);
        when(invoiceRepository.findAllById(ids)).thenReturn(List.of(
                invoice(i1, ENTERPRISE_A, new BigDecimal("100")),
                invoice(i2, ENTERPRISE_B, new BigDecimal("100"))));

        AggregatePaymentRequestDTO dto = new AggregatePaymentRequestDTO(
                ids, LocalDate.of(2026, 8, 28), "TRANSFERENCIA", new BigDecimal("200"), null, null);

        assertThatThrownBy(() -> service().registerAggregate(dto, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_PAYMENT_CROSS_ENTERPRISE);

        verify(paymentRepository, never()).save(any());
    }

    // ── anular ─────────────────────────────────────────────────

    @Test
    @DisplayName("anular apaga as ligações e o pagamento — a fatura volta a UNPAID por derivação")
    void anular() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setAmount(new BigDecimal("1000"));
        payment.setMethod(PaymentMethod.TRANSFERENCIA);
        payment.setPaidOn(LocalDate.of(2026, 8, 28));
        InvoicePayment link = new InvoicePayment(payment,
                invoice(INVOICE_ID, ENTERPRISE_A, new BigDecimal("1000")), new BigDecimal("1000"));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(invoicePaymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(List.of(link));

        service().delete(PAYMENT_ID);

        verify(invoicePaymentRepository).deleteAll(List.of(link));
        verify(paymentRepository).delete(payment);
        assertThat(PaymentService.deriveStatus(new BigDecimal("1000"), BigDecimal.ZERO))
                .isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("anular um pagamento que não existe → INVOICE_023")
    void anularInexistente() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_PAYMENT_NOT_FOUND);
    }
}
