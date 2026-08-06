package com.management.managementapi.enterprises.model;

/**
 * Papel de cada linha do orçamento, tal como aparece no Excel da obra.
 *
 * <ul>
 *   <li>{@link #ITEM} — rubrica normal. Nem sempre tem código: as linhas
 *       "Alternativa em ..." não são numeradas mas são elas que trazem o preço
 *       efectivo quando a rubrica numerada acima ficou sem total.</li>
 *   <li>{@link #HEADING} — sub-título sem numeração ("Paredes", "Pavimentos",
 *       "Tectos"). Agrupa todas as rubricas seguintes até ao título seguinte,
 *       por isso na árvore é o pai delas.</li>
 *   <li>{@link #NOTE} — nota de contexto entre parêntesis, colada à rubrica
 *       anterior ("(considerado material escavável a balde)").</li>
 * </ul>
 */
public enum BudgetRowKind {
    ITEM,
    HEADING,
    NOTE;

    /** Só as rubricas normais aceitam despesas — títulos e notas não. */
    public boolean acceptsExpenses() {
        return this == ITEM;
    }
}
