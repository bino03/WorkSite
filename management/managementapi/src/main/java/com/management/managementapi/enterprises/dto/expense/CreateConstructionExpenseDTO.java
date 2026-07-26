package com.management.managementapi.enterprises.dto.expense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateConstructionExpenseDTO(
    @NotBlank(message = "Nome da despesa é obrigatório") String name,
    @NotNull(message = "Preço é obrigatório") @Positive(message = "Preço deve ser positivo") BigDecimal price,
    @NotNull(message = "Sub-etapa é obrigatória") UUID subStageId
) {}
