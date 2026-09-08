package com.management.managementapi.enterprises.dto.invoice.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Classificar várias faturas para a mesma rubrica de uma vez — o gesto de quem
 * está a despachar um lote do mesmo fornecedor.
 */
public record BatchAllocateDTO(

        @NotEmpty(message = "Selecione pelo menos uma fatura")
        List<UUID> invoiceIds,

        @NotNull(message = "Indique a rubrica")
        UUID budgetItemId
) {}
