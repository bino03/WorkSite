package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;

/**
 * O que ainda falta pagar nas faturas por liquidar de uma lista — o número que
 * o filtro "Por liquidar" mostra ao lado dos resultados (pedido do utilizador a
 * 2026-09-21). É calculado sobre <b>todas</b> as faturas do filtro, não só as
 * da página.
 *
 * @param count             faturas por liquidar (a mesma definição do filtro
 *                          {@code outstanding=true}: pago + NC &lt; total, ou
 *                          ainda sem total); notas de crédito não entram
 * @param total             Σ (líquido − pago) das que têm total
 * @param withoutTotalCount quantas ainda não têm total — estão por liquidar mas
 *                          não se sabe quanto, por isso contam 0 em {@code total}
 */
public record OutstandingInvoicesSummaryDTO(
        long count,
        BigDecimal total,
        long withoutTotalCount
) {}
