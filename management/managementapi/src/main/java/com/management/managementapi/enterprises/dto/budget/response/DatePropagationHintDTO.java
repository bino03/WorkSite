package com.management.managementapi.enterprises.dto.budget.response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Aviso de que um ascendente ficou sem data enquanto o filho já tem uma.
 *
 * O serviço nunca preenche o pai sozinho — devolve isto para o frontend
 * perguntar, e só reaplica com {@code propagateStartDate}/{@code propagateEndDate}
 * a true se o utilizador concordar.
 *
 * @param field {@code START} ou {@code END}
 */
public record DatePropagationHintDTO(
        UUID ancestorId,
        String ancestorCode,
        String ancestorName,
        String field,
        LocalDate value
) {}
