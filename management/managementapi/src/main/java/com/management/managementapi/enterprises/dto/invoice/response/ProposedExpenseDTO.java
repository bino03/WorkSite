package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha proposta da repartição negativa de uma nota de crédito — a rubrica
 * e o valor (negativo) sugeridos, na proporção da fatura de origem. O
 * utilizador confirma ou altera antes de gravar.
 */
public record ProposedExpenseDTO(
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,
        BigDecimal amount
) {}
