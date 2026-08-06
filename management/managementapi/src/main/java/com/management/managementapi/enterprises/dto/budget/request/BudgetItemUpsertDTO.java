package com.management.managementapi.enterprises.dto.budget.request;

import com.management.managementapi.enterprises.model.BudgetRowKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Criação/edição de uma rubrica do orçamento.
 *
 * {@code code} é opcional de propósito: no Excel há títulos, notas e linhas
 * "Alternativa" sem numeração. {@code parentId} a null cria uma rubrica de topo.
 */
public record BudgetItemUpsertDTO(

        @NotNull(message = "Projeto é obrigatório")
        UUID enterpriseId,

        UUID parentId,

        @NotNull(message = "Tipo de linha é obrigatório")
        BudgetRowKind rowKind,

        String code,

        @NotBlank(message = "Descrição é obrigatória")
        String name,

        String unit,

        @PositiveOrZero(message = "Quantidade não pode ser negativa")
        BigDecimal quantity,

        @PositiveOrZero(message = "Preço unitário não pode ser negativo")
        BigDecimal unitPrice,

        @PositiveOrZero(message = "Preço total não pode ser negativo")
        BigDecimal totalPrice,

        String observations,

        LocalDate startDate,

        LocalDate endDate,

        /**
         * Confirmação explícita para propagar a data de início aos ascendentes
         * que ainda não a tenham. O serviço nunca propaga sem isto: devolve o
         * aviso e o frontend pergunta primeiro.
         */
        Boolean propagateStartDate,

        /** Idem para a data de fim. */
        Boolean propagateEndDate
) {}
