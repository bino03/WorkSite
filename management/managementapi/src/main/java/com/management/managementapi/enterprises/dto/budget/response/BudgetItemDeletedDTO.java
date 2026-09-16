package com.management.managementapi.enterprises.dto.budget.response;

import com.management.managementapi.enterprises.model.BudgetRowKind;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma rubrica eliminada (soft delete) — uma linha na zona de recuperação.
 *
 * {@code purgeAt} é {@code deletedAt + 30 dias}: o momento em que o job agendado
 * ({@code ConstructionBudgetItemPurgeConfig}) a apaga de vez. O cliente calcula
 * "dias restantes" a partir daqui em vez de embutir a regra dos 30 dias.
 */
public record BudgetItemDeletedDTO(
        UUID id,
        String code,
        String name,
        BudgetRowKind rowKind,
        OffsetDateTime deletedAt,
        OffsetDateTime purgeAt
) {}
