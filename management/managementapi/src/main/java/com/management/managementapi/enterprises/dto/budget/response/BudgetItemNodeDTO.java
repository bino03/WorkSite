package com.management.managementapi.enterprises.dto.budget.response;

import com.management.managementapi.enterprises.model.BudgetRowKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nó da árvore de orçamento, já com os totais agregados da sub-árvore para o
 * frontend poder mostrar o progresso sem fazer contas nem chamadas extra.
 *
 * @param totalPrice     valor tal como está no Excel (pode ser null nas
 *                       alternativas que não foram escolhidas)
 * @param rolledUpBudget soma de {@code totalPrice} das <b>folhas</b> da
 *                       sub-árvore — somar todos os nós duplicaria, porque o
 *                       Excel já guarda o total do capítulo na própria linha
 * @param budgetMismatch true quando um nó de grupo tem {@code totalPrice} que
 *                       não bate certo com {@code rolledUpBudget}
 * @param budgetVariance de quanto é essa divergência
 *                       ({@code totalPrice - rolledUpBudget}); null quando não
 *                       há como comparar — sem total próprio ou sem descendentes
 *                       com preço
 * @param spentTotal     soma das despesas lançadas em toda a sub-árvore
 * @param percentSpent   {@code spentTotal / rolledUpBudget}, null se não houver
 *                       orçamento contra o qual comparar
 * @param overBudget     {@code spentTotal > rolledUpBudget} — a rubrica já
 *                       gastou mais do que tinha orçamentado
 * @param missingInvoiceCount despesas da sub-árvore sem ficheiro de fatura anexado
 * @param pendingAccountantCount despesas da sub-árvore ainda por enviar para a
 *                       contabilidade
 * @param pendingAccountantTotal valor dessas despesas — 3 faturas tanto podem
 *                       ser 200 € como 80.000 €
 */
public record BudgetItemNodeDTO(
        UUID id,
        UUID parentId,
        BudgetRowKind rowKind,
        boolean acceptsExpenses,
        String code,
        int sortOrder,
        int depth,
        String name,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String observations,
        LocalDate startDate,
        LocalDate endDate,

        // ── agregados da sub-árvore ──
        BigDecimal rolledUpBudget,
        boolean budgetMismatch,
        BigDecimal budgetVariance,
        BigDecimal spentTotal,
        BigDecimal remaining,
        BigDecimal percentSpent,
        boolean overBudget,
        int expenseCount,
        int ownExpenseCount,
        int missingInvoiceCount,
        int pendingAccountantCount,
        BigDecimal pendingAccountantTotal,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        List<BudgetItemNodeDTO> children
) {}
