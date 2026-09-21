package com.management.managementapi.enterprises.dto.invoice.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os filtros da lista de faturas de uma obra — todos opcionais e cumuláveis.
 * A "pesquisa avançada" do Backoffice (2026-09-21) juntou os oito últimos aos
 * que já existiam; um objeto em vez de dezasseis parâmetros soltos, para a
 * lista e o resumo do que falta pagar receberem exatamente o mesmo.
 *
 * @param allocated        {@code false} = por classificar; {@code true} = já numa rubrica
 * @param needsReview      {@code true} = falta a data ou o total
 * @param outstanding      {@code true} = por liquidar (não paga ou parcial); {@code false} = paga
 * @param sentToAccountant {@code false} = por enviar ao contabilista
 * @param atChapter        {@code true} = alguma linha da repartição ainda está numa rubrica com filhas
 * @param from             data da fatura, inclusive
 * @param to               data da fatura, inclusive
 * @param q                texto livre: fornecedor, NIF, número, ATCUD, nome do ficheiro, notas
 * @param supplierNif      NIF exato do fornecedor
 * @param documentType     {@code INVOICE} ou {@code CREDIT_NOTE}
 * @param documentStatus   {@code ARCHIVED} / {@code MISSING} / {@code TO_PRINT} / {@code TO_REQUEST}
 * @param paymentStatus    {@code UNPAID} / {@code PARTIAL} / {@code PAID} — mais fino do que
 *                         {@code outstanding}, que junta os dois primeiros
 * @param allocationStatus {@code NONE} / {@code PROVISIONAL} / {@code PARTIAL} / {@code COMPLETE}
 *                         — a mesma regra do {@code allocationStatus} da resposta
 * @param minAmount        {@code totalAmount} ≥
 * @param maxAmount        {@code totalAmount} ≤
 * @param budgetItemId     rubrica; apanha também tudo o que está nas sub-rubricas dela
 */
public record InvoiceSearchFilter(
        Boolean allocated,
        Boolean needsReview,
        Boolean outstanding,
        Boolean sentToAccountant,
        Boolean atChapter,
        LocalDate from,
        LocalDate to,
        String q,
        String supplierNif,
        String documentType,
        String documentStatus,
        String paymentStatus,
        String allocationStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        UUID budgetItemId
) {
    /** Só o que a lista tinha antes da pesquisa avançada — para os chamadores antigos e os testes. */
    public static InvoiceSearchFilter basic(Boolean allocated, Boolean needsReview, Boolean outstanding,
                                            Boolean sentToAccountant, Boolean atChapter,
                                            LocalDate from, LocalDate to, String q) {
        return new InvoiceSearchFilter(allocated, needsReview, outstanding, sentToAccountant, atChapter,
                from, to, q, null, null, null, null, null, null, null, null);
    }

    /** O mesmo filtro com {@code outstanding} forçado — o resumo do que falta pagar. */
    public InvoiceSearchFilter onlyOutstanding() {
        return new InvoiceSearchFilter(allocated, needsReview, true, sentToAccountant, atChapter,
                from, to, q, supplierNif, documentType, documentStatus, paymentStatus, allocationStatus,
                minAmount, maxAmount, budgetItemId);
    }
}
