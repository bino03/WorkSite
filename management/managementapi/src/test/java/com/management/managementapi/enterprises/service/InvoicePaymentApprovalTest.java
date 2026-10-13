package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A aprovação para pagamento é um estado da fatura — quem e quando — que se
 * pode retirar sempre, e que só se dá ao que ainda há para pagar.
 */
@ExtendWith(MockitoExtension.class)
class InvoicePaymentApprovalTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AtInvoiceQrService qrService;
    @Mock private InvoiceThumbnailService thumbnailService;
    @Mock private InvoiceCompressionService compressionService;
    @Mock private AuthContext authContext;
    @Mock private NotificationService notifications;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    @DisplayName("aprovar regista quem aprovou e quando")
    void approvingRecordsWhoAndWhen() {
        ConstructionInvoice invoice = unpaidInvoice("100.00");
        when(authContext.currentProfileId()).thenReturn(Optional.of(ADMIN));

        ConstructionInvoice saved = service.setApprovedForPayment(invoice.getId(), true);

        assertThat(saved.isApprovedForPayment()).isTrue();
        assertThat(saved.getApprovedForPaymentBy()).isEqualTo(ADMIN);
        assertThat(saved.getApprovedForPaymentAt()).isNotNull();
    }

    @Test
    @DisplayName("aprovar outra vez não muda quem aprovou primeiro")
    void approvingTwiceKeepsTheFirstApprover() {
        ConstructionInvoice invoice = unpaidInvoice("100.00");
        UUID firstApprover = UUID.randomUUID();
        OffsetDateTime firstTime = OffsetDateTime.now().minusDays(3);
        invoice.setApprovedForPayment(true);
        invoice.setApprovedForPaymentBy(firstApprover);
        invoice.setApprovedForPaymentAt(firstTime);

        ConstructionInvoice saved = service.setApprovedForPayment(invoice.getId(), true);

        assertThat(saved.getApprovedForPaymentBy()).isEqualTo(firstApprover);
        assertThat(saved.getApprovedForPaymentAt()).isEqualTo(firstTime);
    }

    @Test
    @DisplayName("retirar a aprovação limpa quem e quando")
    void withdrawingClearsWhoAndWhen() {
        ConstructionInvoice invoice = unpaidInvoice("100.00");
        invoice.setApprovedForPayment(true);
        invoice.setApprovedForPaymentBy(ADMIN);
        invoice.setApprovedForPaymentAt(OffsetDateTime.now());

        ConstructionInvoice saved = service.setApprovedForPayment(invoice.getId(), false);

        assertThat(saved.isApprovedForPayment()).isFalse();
        assertThat(saved.getApprovedForPaymentBy()).isNull();
        assertThat(saved.getApprovedForPaymentAt()).isNull();
    }

    @Test
    @DisplayName("uma fatura parcialmente paga ainda se aprova")
    void partiallyPaidCanBeApproved() {
        ConstructionInvoice invoice = unpaidInvoice("100.00");
        when(paymentService.paidAmount(invoice.getId())).thenReturn(new BigDecimal("40.00"));
        when(authContext.currentProfileId()).thenReturn(Optional.of(ADMIN));

        assertThat(service.setApprovedForPayment(invoice.getId(), true).isApprovedForPayment()).isTrue();
    }

    @Test
    @DisplayName("uma fatura já liquidada não se aprova, mas a aprovação que tinha pode retirar-se")
    void paidInvoiceCannotBeApprovedButCanBeWithdrawn() {
        ConstructionInvoice invoice = unpaidInvoice("100.00");
        lenient().when(paymentService.paidAmount(invoice.getId())).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.setApprovedForPayment(invoice.getId(), true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_ALREADY_PAID));

        invoice.setApprovedForPayment(true);
        assertThat(service.setApprovedForPayment(invoice.getId(), false).isApprovedForPayment()).isFalse();
    }

    @Test
    @DisplayName("uma nota de crédito não se aprova para pagamento")
    void creditNoteCannotBeApproved() {
        ConstructionInvoice creditNote = unpaidInvoice("100.00");
        creditNote.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        creditNote.setRelatedInvoiceId(UUID.randomUUID());

        assertThatThrownBy(() -> service.setApprovedForPayment(creditNote.getId(), true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_IS_CREDIT_NOTE));
    }

    private ConstructionInvoice unpaidInvoice(String total) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal(total));

        when(repository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        lenient().when(repository.save(any(ConstructionInvoice.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(paymentService.netAmount(invoice)).thenReturn(invoice.getTotalAmount());
        lenient().when(paymentService.paidAmount(invoice.getId())).thenReturn(BigDecimal.ZERO);
        return invoice;
    }
}
