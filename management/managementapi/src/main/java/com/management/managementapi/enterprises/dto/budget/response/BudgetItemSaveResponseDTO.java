package com.management.managementapi.enterprises.dto.budget.response;

import java.util.List;

/**
 * Resultado de criar/editar uma rubrica: o nó gravado mais os avisos de datas
 * por propagar, para o frontend poder perguntar antes de aplicar.
 */
public record BudgetItemSaveResponseDTO(
        BudgetItemNodeDTO item,
        List<DatePropagationHintDTO> datePropagationHints
) {}
