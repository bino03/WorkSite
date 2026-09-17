package com.management.managementapi.enterprises.dto.budget.request;

/**
 * As folhas que o utilizador pode escolher ao exportar uma obra para Excel.
 *
 * A folha "Rubricas" não está aqui de propósito: é gerada sempre que se pede
 * {@link #COMPARISON}, porque o painel só funciona com a {@code TabelaRubricas}
 * ao lado (as fórmulas somam por lá). Pelo mesmo motivo, {@code COMPARISON}
 * arrasta {@link #EXPENSES} — sem {@code TabelaDespesas} as fórmulas dariam
 * {@code #NAME?}.
 */
public enum BudgetExportSheet {
    /** "Orçamento inicial" — a árvore de rubricas, no formato que o importador lê. */
    BUDGET,
    /** "Despesas" — a {@code TabelaDespesas} do vault (faturas, pagamentos, rubrica). */
    EXPENSES,
    /** "Orçamento vs Gasto" + "Rubricas" — o painel por capítulo, todo em fórmulas. */
    COMPARISON
}
