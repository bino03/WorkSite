package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;

/**
 * O que ainda está por classificar numa obra: quantas faturas e quanto valem.
 * Alimenta o ecrã do orçamento — "Gasto" só conta o que já está numa rubrica,
 * e sem este número ao lado parecia que o dinheiro tinha desaparecido.
 *
 * @param total soma dos {@code total_amount} (faturas sem total contam 0); notas de crédito não entram
 */
public record PendingInvoicesSummaryDTO(
        long count,
        BigDecimal total
) {}
