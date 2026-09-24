package com.management.managementapi.enterprises.dto.budget.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Criar/renomear um lote (edifício) do projeto. {@code sortOrder} a null põe um
 * lote novo no fim e deixa a ordem de um existente como está.
 */
public record BudgetLotUpsertDTO(

        @NotBlank(message = "Nome do lote é obrigatório")
        @Size(max = 80, message = "Nome do lote demasiado longo")
        String name,

        Integer sortOrder
) {}
