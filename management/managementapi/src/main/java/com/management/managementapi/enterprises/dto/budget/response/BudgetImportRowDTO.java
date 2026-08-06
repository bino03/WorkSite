package com.management.managementapi.enterprises.dto.budget.response;

import com.management.managementapi.enterprises.model.BudgetRowKind;

import java.math.BigDecimal;

/**
 * Uma linha tal como foi interpretada do Excel — serve a pré-visualização do
 * {@code dryRun}, para se ver o que vai entrar antes de gravar.
 *
 * @param excelRow número da linha no Excel (1-based), para se conseguir ir lá conferir
 * @param depth    profundidade na árvore, já com os sub-títulos contados
 */
public record BudgetImportRowDTO(
        int excelRow,
        int depth,
        BudgetRowKind kind,
        String code,
        String parentCode,
        String name,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String observations
) {}
