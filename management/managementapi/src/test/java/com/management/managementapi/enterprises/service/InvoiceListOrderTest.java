package com.management.managementapi.enterprises.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/** As listas de faturas abrem pela data da fatura, mais recente primeiro, e as sem data no fim (2026-11-13). */
class InvoiceListOrderTest {

    @Test
    void invoiceDateGoesNullsLastAndKeepsTheTieBreak() {
        Pageable in = PageRequest.of(2, 20, Sort.by(Sort.Order.desc("invoiceDate"), Sort.Order.desc("createdAt")));

        Pageable out = ConstructionInvoiceService.undatedLast(in);

        assertThat(out.getPageNumber()).isEqualTo(2);
        assertThat(out.getPageSize()).isEqualTo(20);
        Sort.Order date = out.getSort().getOrderFor("invoiceDate");
        assertThat(date.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(date.getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
        Sort.Order created = out.getSort().getOrderFor("createdAt");
        assertThat(created.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(created.getNullHandling()).isEqualTo(Sort.NullHandling.NATIVE);
        assertThat(out.getSort().toList()).extracting(Sort.Order::getProperty).containsExactly("invoiceDate", "createdAt");
    }

    @Test
    void ascendingByDateAlsoLeavesUndatedLast() {
        Pageable out = ConstructionInvoiceService.undatedLast(PageRequest.of(0, 20, Sort.by("invoiceDate").ascending()));

        assertThat(out.getSort().getOrderFor("invoiceDate").getNullHandling()).isEqualTo(Sort.NullHandling.NULLS_LAST);
    }

    @Test
    void sortWithoutInvoiceDateIsUntouched() {
        Pageable in = PageRequest.of(0, 20, Sort.by("createdAt").descending());

        assertThat(ConstructionInvoiceService.undatedLast(in)).isSameAs(in);
    }
}
