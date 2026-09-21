package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSearchFilter;
import com.management.managementapi.enterprises.dto.invoice.response.OutstandingInvoicesSummaryDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O total que falta pagar no filtro "Por liquidar" (pedido do utilizador a
 * 2026-09-21): Σ (total − NC − pago) sobre todas as faturas do filtro, não só
 * as da página. As sem total contam à parte, porque não se sabe quanto valem.
 */
@ExtendWith(MockitoExtension.class)
class OutstandingSummaryTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("Soma líquido − pago de todas as faturas do filtro; as sem total contam à parte")
    void sumsRemainingAcrossTheWholeFilter() {
        UUID enterpriseId = UUID.randomUUID();
        ConstructionInvoice a = invoice("100");   // 100, paga 30 → faltam 70
        ConstructionInvoice b = invoice("50");    // 50, NC de 20, paga 0 → faltam 30
        ConstructionInvoice c = invoice(null);    // sem total → conta, mas vale 0
        ConstructionInvoice nc = invoice("20");
        nc.setRelatedInvoiceId(b.getId());

        when(repository.search(eq(enterpriseId), any(), any(), eq(true), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(a, b, c)));
        when(paymentService.paymentsForInvoices(any(), anyBoolean()))
                .thenReturn(Map.of(a.getId(), List.of(payment("30"))));
        when(repository.findCreditNotesForAll(any())).thenReturn(List.of(nc));

        OutstandingInvoicesSummaryDTO summary =
                service.outstandingSummary(enterpriseId, InvoiceSearchFilter.basic(null, null, null, null, null, null, null, "  "));

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.total()).isEqualByComparingTo("100");
        assertThat(summary.withoutTotalCount()).isEqualTo(1);

        // Sem paginação e com outstanding=true, sempre — é o que faz o número bater com a lista inteira.
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(enterpriseId), any(), any(), eq(true), any(), any(), any(), any(), any(),
                eq(null), any(), any(), any(), any(), any(), any(), any(), eq(false), any(), pageable.capture());
        assertThat(pageable.getValue().isUnpaged()).isTrue();
    }

    @Test
    @DisplayName("Quarentena/empresa: a mesma conta pela lista por âmbito")
    void byScopeUsesTheScopedList() {
        ConstructionInvoice a = invoice("80");
        when(repository.searchByScope(eq(ConstructionInvoice.Scope.COMPANY), eq(true), eq("acme"), any()))
                .thenReturn(new PageImpl<>(List.of(a)));
        when(paymentService.paymentsForInvoices(any(), anyBoolean())).thenReturn(Map.of());
        when(repository.findCreditNotesForAll(any())).thenReturn(List.of());

        OutstandingInvoicesSummaryDTO summary =
                service.outstandingSummaryByScope(ConstructionInvoice.Scope.COMPANY, " acme ");

        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.total()).isEqualByComparingTo("80");
        assertThat(summary.withoutTotalCount()).isZero();
    }

    private static ConstructionInvoice invoice(String total) {
        ConstructionInvoice i = new ConstructionInvoice();
        i.setId(UUID.randomUUID());
        i.setTotalAmount(total == null ? null : new BigDecimal(total));
        return i;
    }

    private static InvoicePaymentSummaryDTO payment(String amount) {
        return new InvoicePaymentSummaryDTO(UUID.randomUUID(), null, null, new BigDecimal(amount),
                new BigDecimal(amount), null, null, null, null, null, null, null, List.of());
    }
}
