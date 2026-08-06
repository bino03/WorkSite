package com.management.managementapi.enterprises.dto.budget.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de uma importação de orçamento.
 *
 * @param dryRun          true quando nada foi gravado
 * @param parsedTotal     soma das folhas com preço — o total efectivo do que foi lido
 * @param excelTotal      valor da linha "TOTAL" do Excel, quando existe
 * @param totalDifference {@code parsedTotal - excelTotal}; diferente de zero é
 *                        avisado mas não bloqueia (os orçamentos reais têm
 *                        arredondamentos e erros de folha de cálculo)
 * @param warnings        índices duplicados, células de texto em colunas
 *                        numéricas, linhas ignoradas
 */
public record BudgetImportResultDTO(
        boolean dryRun,
        String sheetName,
        int itemCount,
        int headingCount,
        int noteCount,
        BigDecimal parsedTotal,
        BigDecimal excelTotal,
        BigDecimal totalDifference,
        List<String> warnings,
        List<BudgetImportRowDTO> rows
) {}
