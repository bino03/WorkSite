package com.management.managementapi.enterprises.dto.invoice.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os filtros de "A pagar" (2026-11-10). Todos opcionais; aplicam-se em memória
 * depois de ordenar por urgência e antes dos totais e da paginação, por isso
 * "Falta pagar", "Vencido" e "Sem prazo" passam a ser do filtrado.
 *
 * @param enterpriseId       só as faturas desta obra
 * @param company            só as despesas da empresa (exclui-se com {@code enterpriseId})
 * @param supplierNif        só deste fornecedor (NIF exato)
 * @param dueBucket          por vencimento — ver {@link DueBucket}
 * @param approvedForPayment só as aprovadas ({@code true}) ou só as por aprovar ({@code false})
 * @param sentToAccountant   só as enviadas ao contabilista ({@code true}) ou só as por enviar ({@code false})
 *
 * <p>Os da pesquisa avançada (2026-11-13 — o mesmo modal da lista de faturas da obra),
 * com o significado que têm no {@code ConstructionInvoiceRepository.search}:
 *
 * @param q                texto: fornecedor, NIF, nº, ATCUD, notas, nome do ficheiro
 * @param documentStatus   {@code ARCHIVED}, {@code MISSING}, {@code TO_PRINT}, {@code TO_REQUEST}
 * @param paymentStatus    {@code UNPAID} (nada pago) ou {@code PARTIAL}; {@code PAID} nunca casa aqui
 * @param allocationStatus {@code NONE}, {@code PROVISIONAL}, {@code PARTIAL}, {@code COMPLETE}
 * @param from             data da fatura, desde (inclusive)
 * @param to               data da fatura, até (inclusive)
 * @param minAmount        total, desde
 * @param maxAmount        total, até
 * @param budgetItemId     rubrica e sub-árvore; só com {@code enterpriseId}
 */
public record OutstandingFilter(
        UUID enterpriseId,
        boolean company,
        String supplierNif,
        DueBucket dueBucket,
        Boolean approvedForPayment,
        Boolean sentToAccountant,
        String q,
        String documentStatus,
        String paymentStatus,
        String allocationStatus,
        LocalDate from,
        LocalDate to,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        UUID budgetItemId
) {

    public static final OutstandingFilter NONE = new OutstandingFilter(null, false, null, null, null, null);

    /** Só os filtros da barra, sem pesquisa avançada. */
    public OutstandingFilter(UUID enterpriseId, boolean company, String supplierNif, DueBucket dueBucket,
                             Boolean approvedForPayment, Boolean sentToAccountant) {
        this(enterpriseId, company, supplierNif, dueBucket, approvedForPayment, sentToAccountant,
                null, null, null, null, null, null, null, null, null);
    }

    /** O mesmo filtro, só com as aprovadas para pagamento — a lista de pagamentos exportada. */
    public OutstandingFilter onlyApproved() {
        return new OutstandingFilter(enterpriseId, company, supplierNif, dueBucket, Boolean.TRUE, sentToAccountant,
                q, documentStatus, paymentStatus, allocationStatus, from, to, minAmount, maxAmount, budgetItemId);
    }

    /** Algum filtro que obriga a carregar pagamentos, repartições ou documentos. */
    public boolean hasAdvanced() {
        return (q != null && !q.isBlank()) || documentStatus != null || paymentStatus != null
                || allocationStatus != null || from != null || to != null
                || minAmount != null || maxAmount != null || budgetItemId != null;
    }

    /**
     * As faixas de vencimento, cumulativas (decidido a 2026-11-10): "este mês"
     * inclui "esta semana"; nenhuma das duas inclui as já vencidas.
     */
    public enum DueBucket {
        /** Vencimento antes de hoje. */
        OVERDUE,
        /** Vence de hoje a hoje + 7 dias. */
        WEEK,
        /** Vence de hoje a hoje + 30 dias. */
        MONTH,
        /** Sem vencimento (falta o prazo do fornecedor, o NIF ou a data). */
        NONE;

        public boolean matches(LocalDate dueDate, LocalDate today) {
            return switch (this) {
                case OVERDUE -> dueDate != null && dueDate.isBefore(today);
                case WEEK -> dueDate != null && !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(7));
                case MONTH -> dueDate != null && !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(30));
                case NONE -> dueDate == null;
            };
        }
    }
}
