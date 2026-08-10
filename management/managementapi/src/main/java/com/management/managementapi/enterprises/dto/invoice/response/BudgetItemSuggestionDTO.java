package com.management.managementapi.enterprises.dto.invoice.response;

import java.util.UUID;

/**
 * Rubrica sugerida no momento de associar uma fatura.
 *
 * Sai do histórico do fornecedor: se as faturas deste NIF já foram para a
 * rubrica X, é quase certo que esta também vai. É o que reduz a associação a um
 * clique a partir da segunda fatura do mesmo fornecedor.
 */
public record BudgetItemSuggestionDTO(
        UUID budgetItemId,
        String code,
        String name
) {}
