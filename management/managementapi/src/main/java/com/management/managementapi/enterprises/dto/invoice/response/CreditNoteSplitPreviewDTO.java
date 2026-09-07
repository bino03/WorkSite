package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Proposta de repartição negativa para uma nota de crédito, calculada na
 * proporção das despesas da fatura de origem. Nada é gravado — o utilizador
 * confirma ou altera as linhas e só depois é que a NC é criada com elas.
 *
 * {@code originAllocated == false} quando a fatura de origem não tem despesa
 * nenhuma: nesse caso a NC também não gera despesas (§6).
 */
public record CreditNoteSplitPreviewDTO(
        boolean originAllocated,
        /** Soma das linhas propostas — deve ser {@code -totalAmount} da NC. */
        BigDecimal total,
        List<ProposedExpenseDTO> lines
) {}
