package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;

/**
 * Total, pago e a pagar de uma lista de faturas — o resumo que aparece quando
 * se filtra por fornecedor (pedido do utilizador a 2026-11-16). Calculado sobre
 * <b>todas</b> as faturas do filtro, não só as da página. As notas de crédito
 * não contam como linhas: abatem ao líquido da fatura a que estão ligadas.
 *
 * @param count             faturas (sem NC)
 * @param total             Σ líquido (total − NC) das que têm total
 * @param paidCount         faturas liquidadas: pagas por inteiro, ou anuladas por NC
 *                          (líquido − pago ≤ 0, a definição do filtro {@code outstanding})
 * @param paid              Σ pago em todas — inclui o pago das parciais, para
 *                          {@code total = paid + outstanding}
 * @param outstandingCount  faturas por liquidar (não pagas, parciais, ou sem total)
 * @param outstanding       Σ (líquido − pago) das por liquidar que têm total
 * @param withoutTotalCount quantas não têm total — contam em {@code outstandingCount}
 *                          mas valem 0 nas somas
 * @param overdueCount      das por liquidar com total, quantas já passaram o vencimento
 *                          (o mesmo {@code InvoiceDueDate} da página "A pagar": sem NIF,
 *                          sem ficha, sem prazo ou sem data não vencem; vencer hoje ainda não é vencida)
 * @param overdue           Σ (líquido − pago) dessas — uma parte de {@code outstanding}
 */
public record InvoiceTotalsSummaryDTO(
        long count,
        BigDecimal total,
        long paidCount,
        BigDecimal paid,
        long outstandingCount,
        BigDecimal outstanding,
        long withoutTotalCount,
        long overdueCount,
        BigDecimal overdue
) {}
