package com.management.managementapi.enterprises.dto.budget.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma rubrica encontrada na pesquisa, com o que é preciso para a escolher sem
 * sair do campo de pesquisa.
 *
 * O {@code path} é o que faz a diferença num orçamento real: "Betão" aparece em
 * três capítulos diferentes e o nome sozinho não os distingue. O orçamentado e o
 * gasto vêm juntos porque a pergunta que se faz ao classificar é "ainda cabe
 * aqui?", e ir vê-la a outro ecrã é o que faz as pessoas desistirem e atirarem
 * tudo para o capítulo.
 *
 * @param path      caminho completo, raiz→rubrica, ex.
 *                  {@code "4. Estrutura › 4.2 Lajes › 4.2.1 Betão"}
 * @param chapter   a rubrica ainda tem descendentes que aceitam despesas — quem
 *                  classificar aqui está a classificar "ao capítulo", o que é
 *                  legítimo mas assinalável (docs/faturas-modelo-alvo.md §7)
 */
public record BudgetItemSearchResultDTO(
        UUID id,
        String code,
        String name,
        String path,
        int depth,
        boolean chapter,
        BigDecimal rolledUpBudget,
        BigDecimal spentTotal,
        BigDecimal remaining,
        boolean overBudget
) {}
