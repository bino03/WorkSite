package com.management.managementapi.enterprises.dto.invoice.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Uma linha da repartição de uma nota de crédito: quanto dela abate a uma
 * rubrica. O sinal é irrelevante — o serviço grava sempre negativo.
 *
 * Na fase 3 há no máximo <b>uma</b> destas por NC (a origem só tem uma rubrica,
 * por causa de {@code uq_expense_invoice}). A repartição por várias rubricas é
 * a fase 4.
 */
public record CreditNoteExpenseLineDTO(

        @NotNull(message = "Indique a rubrica")
        UUID budgetItemId,

        @NotNull(message = "Indique o valor a abater")
        BigDecimal amount
) {}
