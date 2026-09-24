package com.management.managementapi.enterprises.dto.budget.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Um lote (edifício) do projeto, com os números do seu orçamento — o suficiente
 * para as abas da página do orçamento, sem carregar a árvore de cada um.
 */
public record BudgetLotDTO(
        UUID id,
        String name,
        int sortOrder,
        int itemCount,
        BigDecimal budgetTotal,
        BigDecimal spentTotal
) {}
