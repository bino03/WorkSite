package com.management.managementapi.enterprises.dto.invoice.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Uma linha da repartição: quanto da fatura vai para esta rubrica.
 *
 * Não leva nome — a despesa herda o da fatura (fornecedor, número, ficheiro),
 * que é o que se reconhece na lista da rubrica. É por isso que a {@code V32}
 * deixou {@code construction_expense.name} nullable.
 *
 * {@code amount} pode ser null numa fatura ainda sem total: a linha nasce a zero.
 */
public record InvoiceSplitLineDTO(

        @NotNull(message = "Indique a rubrica")
        UUID budgetItemId,

        BigDecimal amount
) {}
