package com.management.managementapi.enterprises.dto.budget.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * A ordem dos lotes de um projeto, de uma vez: o primeiro id fica na primeira
 * aba. Vai a lista inteira e não "este lote para a posição N" porque o
 * {@code sortOrder} dos lotes não é contíguo (apagar um deixa buraco, e os
 * criados pela V39 têm todos 0) — uma posição só não chega para dizer onde fica.
 */
public record BudgetLotOrderDTO(

        @NotEmpty(message = "A ordem dos lotes é obrigatória")
        List<@NotNull UUID> budgetIds
) {}
