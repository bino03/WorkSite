package com.management.managementapi.enterprises.dto.budget.response;

import java.util.UUID;

/**
 * O suficiente de um lote para o listar num seletor (transferir uma fatura,
 * escolher a obra e depois o lote) — sem os números do orçamento, que o
 * {@link BudgetLotDTO} já dá quando fazem falta.
 */
public record BudgetLotOptionDTO(UUID id, String name) {}
