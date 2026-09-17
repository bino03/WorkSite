package com.management.managementapi.enterprises.dto.budget.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que a exportação para Excel <i>vai</i> escrever — o passo 2 do modal mostra
 * isto antes do download, para o utilizador saber o que leva e o que falta.
 *
 * @param fileName               nome do {@code .xlsx} que o download vai devolver
 * @param hasBudget              tem rubricas vivas — sem isso "Orçamento inicial" e
 *                               "Orçamento vs Gasto" não são exportáveis
 * @param budgetItemCount        rubricas vivas (todas as linhas da árvore)
 * @param chapterCount           capítulos (rubricas de 1.º nível com índice)
 * @param budgetTotal            soma das folhas do orçamento
 * @param invoiceCount           faturas e notas de crédito da obra
 * @param expenseRowCount        linhas que a folha "Despesas" vai ter
 * @param expensesTotal          soma da coluna Valor dessa folha
 * @param unclassifiedInvoiceCount faturas sem rubrica — saem com a coluna Rubrica vazia
 * @param manualExpenseCount     despesas lançadas à mão, sem fatura — saem sem nº
 * @param creditNoteCount        notas de crédito — saem com valor negativo
 * @param partialPaymentCount    faturas parcialmente pagas — saem por liquidar
 * @param missingNumberCount     faturas sem nº — a coluna "Nº Fatura" fica vazia
 * @param needsReviewCount       faturas sem data ou sem total
 * @param warnings               tudo o que o utilizador deve saber antes de abrir o ficheiro
 */
public record BudgetExportSummaryDTO(
        UUID enterpriseId,
        String enterpriseName,
        boolean isTest,
        String fileName,
        boolean hasBudget,
        int budgetItemCount,
        int chapterCount,
        BigDecimal budgetTotal,
        int invoiceCount,
        int expenseRowCount,
        BigDecimal expensesTotal,
        int unclassifiedInvoiceCount,
        int manualExpenseCount,
        int creditNoteCount,
        int partialPaymentCount,
        int missingNumberCount,
        int needsReviewCount,
        List<String> warnings
) {}
