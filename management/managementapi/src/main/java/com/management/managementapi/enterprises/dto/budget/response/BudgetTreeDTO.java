package com.management.managementapi.enterprises.dto.budget.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Árvore completa do orçamento de um projeto, com os totais do topo já somados.
 *
 * @param overBudgetCount  quantas rubricas estão acima do orçamento. Conta só as
 *                         <b>mais acima na árvore</b> — se um capítulo derrapou,
 *                         conta o capítulo e não também cada rubrica lá dentro,
 *                         senão a mesma derrapagem era contada várias vezes
 * @param overBudgetAmount total dessa derrapagem, pela mesma regra
 * @param missingInvoiceCount despesas do projeto sem ficheiro de fatura anexado
 * @param pendingAccountantCount despesas por enviar para a contabilidade
 * @param pendingAccountantTotal valor dessas despesas
 */
public record BudgetTreeDTO(
        UUID enterpriseId,
        BigDecimal budgetTotal,
        BigDecimal spentTotal,
        BigDecimal remaining,
        BigDecimal percentSpent,
        int itemCount,
        int expenseCount,
        int overBudgetCount,
        BigDecimal overBudgetAmount,
        int missingInvoiceCount,
        int pendingAccountantCount,
        BigDecimal pendingAccountantTotal,
        List<BudgetItemNodeDTO> roots
) {}
