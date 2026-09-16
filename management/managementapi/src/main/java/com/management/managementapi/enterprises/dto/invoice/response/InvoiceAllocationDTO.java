package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma rubrica por onde esta fatura está repartida, e quanto lhe toca.
 *
 * Desde a {@code V32} uma fatura pode ter várias destas: a folha do armazém que
 * traz cimento e ferragens deixa de ter de ir toda para uma rubrica só. Uma
 * fatura por classificar não tem nenhuma; o caso normal tem exatamente uma.
 *
 * {@code amount} é o {@code total_price} da despesa — zero numa fatura ainda sem
 * total (docs/faturas-modelo-alvo.md §7), negativo numa nota de crédito.
 *
 * {@code chapter} é a mesma regra do ecrã "Classificar": a rubrica ainda tem
 * filhas {@code ITEM} — a despesa devia ter ido para uma delas, não para aqui.
 */
public record InvoiceAllocationDTO(
        UUID expenseId,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,
        BigDecimal amount,
        boolean chapter
) {}
