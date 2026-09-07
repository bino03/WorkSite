package com.management.managementapi.enterprises.model.enums;

/**
 * Estado de pagamento de uma fatura. <b>Derivado, nunca coluna</b> — uma coluna
 * a duplicar isto divergiria à primeira edição de um pagamento.
 *
 * <ul>
 *   <li>{@link #UNPAID} — sem ligações a pagamentos;</li>
 *   <li>{@link #PARTIAL} — Σ das ligações &lt; líquido da fatura;</li>
 *   <li>{@link #PAID} — Σ das ligações = líquido da fatura.</li>
 * </ul>
 *
 * Líquido = {@code total_amount} até à fase 3 (notas de crédito), onde passa a
 * {@code total_amount − Σ notas de crédito}.
 */
public enum PaymentStatus {
    UNPAID,
    PARTIAL,
    PAID
}
