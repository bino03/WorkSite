package com.management.managementapi.enterprises.dto.expense.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Criação/edição de uma despesa. Leva os mesmos campos de medição da rubrica
 * para se poder comparar o gasto real com o orçamentado.
 */
public record ConstructionExpenseUpsertDTO(

        @NotNull(message = "Rubrica do orçamento é obrigatória")
        UUID budgetItemId,

        @NotBlank(message = "Nome da despesa é obrigatório")
        String name,

        String description,

        /** Data da fatura. Obrigatória: é sobre ela que assentam os mapas mensais. */
        @NotNull(message = "Data da fatura é obrigatória")
        @PastOrPresent(message = "A data da fatura não pode ser no futuro")
        LocalDate expenseDate,

        String unit,

        @PositiveOrZero(message = "Quantidade não pode ser negativa")
        BigDecimal quantity,

        @PositiveOrZero(message = "Preço unitário não pode ser negativo")
        BigDecimal unitPrice,

        @NotNull(message = "Preço total é obrigatório")
        @Positive(message = "Preço total tem de ser positivo")
        BigDecimal totalPrice,

        String observations
) {}
