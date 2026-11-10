package com.management.managementapi.enterprises.dto.invoice.request;

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
 */
public record OutstandingFilter(
        UUID enterpriseId,
        boolean company,
        String supplierNif,
        DueBucket dueBucket,
        Boolean approvedForPayment,
        Boolean sentToAccountant
) {

    public static final OutstandingFilter NONE = new OutstandingFilter(null, false, null, null, null, null);

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
