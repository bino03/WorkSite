package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.request.GlobalInvoiceSearchFilter;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceTotalsSummaryDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * O resumo Total / Pago / A pagar do filtro por fornecedor (obra e pesquisa global
 * partilham a conta), e desde 2026-11-27 a parte do "A pagar" já vencida.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceTotalsSummaryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private static final String NIF_30_DAYS = "502693134";
    private static final GlobalInvoiceSearchFilter BY_SUPPLIER =
            new GlobalInvoiceSearchFilter(null, null, null, NIF_30_DAYS, null);

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    @BeforeEach
    void setUp() {
        Supplier supplier = new Supplier();
        supplier.setNif(NIF_30_DAYS);
        supplier.setName("Brivel");
        supplier.setPaymentTermDays(30);
        when(supplierRepository.findAll()).thenReturn(List.of(supplier));
        when(repository.findCreditNotesForAll(any())).thenReturn(List.of());
    }

    private void stub(List<ConstructionInvoice> invoices, Map<UUID, BigDecimal> paid) {
        when(repository.searchEverywhere(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(invoices);
        when(paymentService.paidSums(any())).thenReturn(paid);
    }

    @Test
    @DisplayName("vencido = o que falta pagar das que passaram o prazo; vencer hoje ainda não conta")
    void overdueIsTheRemainingOfPastDueInvoices() {
        // 1 set + 30 = 1 out → vencida, com 40 pagos dos 100
        ConstructionInvoice partialOverdue = invoice("100", NIF_30_DAYS, LocalDate.of(2026, 9, 1));
        // 13 set + 30 = 13 out → vence hoje
        ConstructionInvoice dueToday = invoice("200", NIF_30_DAYS, LocalDate.of(2026, 9, 13));
        // 1 ago + 30 = 31 ago → vencida, mas paga
        ConstructionInvoice paidOverdue = invoice("50", NIF_30_DAYS, LocalDate.of(2026, 8, 1));
        stub(List.of(partialOverdue, dueToday, paidOverdue), Map.of(
                partialOverdue.getId(), new BigDecimal("40"),
                paidOverdue.getId(), new BigDecimal("50")));

        InvoiceTotalsSummaryDTO result = service.searchEverywhereTotals(BY_SUPPLIER, TODAY);

        assertThat(result.outstandingCount()).isEqualTo(2);
        assertThat(result.outstanding()).isEqualByComparingTo("260");
        assertThat(result.overdueCount()).isEqualTo(1);
        assertThat(result.overdue()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("sem NIF, sem ficha ou sem total não vencem")
    void withoutDueDateOrTotalNeverOverdue() {
        ConstructionInvoice noNif = invoice("100", null, LocalDate.of(2026, 1, 1));
        ConstructionInvoice unknownNif = invoice("100", "999999990", LocalDate.of(2026, 1, 1));
        ConstructionInvoice noTotal = invoice(null, NIF_30_DAYS, LocalDate.of(2026, 1, 1));
        stub(List.of(noNif, unknownNif, noTotal), Map.of());

        InvoiceTotalsSummaryDTO result = service.searchEverywhereTotals(BY_SUPPLIER, TODAY);

        assertThat(result.outstandingCount()).isEqualTo(3);
        assertThat(result.withoutTotalCount()).isEqualTo(1);
        assertThat(result.overdueCount()).isZero();
        assertThat(result.overdue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("um vencimento escrito à mão manda sobre o prazo do fornecedor")
    void manualDueDateWins() {
        ConstructionInvoice byHand = invoice("100", null, LocalDate.of(2026, 10, 10));
        byHand.setDueDateOverride(LocalDate.of(2026, 10, 12));
        stub(List.of(byHand), Map.of());

        InvoiceTotalsSummaryDTO result = service.searchEverywhereTotals(BY_SUPPLIER, TODAY);

        assertThat(result.overdueCount()).isEqualTo(1);
        assertThat(result.overdue()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("lista vazia: tudo a zero, incluindo o vencido")
    void emptyIsZero() {
        stub(List.of(), Map.of());

        InvoiceTotalsSummaryDTO result = service.searchEverywhereTotals(BY_SUPPLIER, TODAY);

        assertThat(result.count()).isZero();
        assertThat(result.overdueCount()).isZero();
        assertThat(result.overdue()).isEqualByComparingTo("0");
    }

    private static ConstructionInvoice invoice(String total, String supplierNif, LocalDate invoiceDate) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setScope(ConstructionInvoice.Scope.COMPANY);
        invoice.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        invoice.setTotalAmount(total == null ? null : new BigDecimal(total));
        invoice.setSupplierNif(supplierNif);
        invoice.setInvoiceDate(invoiceDate);
        return invoice;
    }
}
