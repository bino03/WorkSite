package com.management.managementapi.enterprises.dto.backup.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * O que entra num backup. Com {@code all}, a API ignora {@code selections} e os dois
 * {@code include} de âmbito e resolve ela própria o âmbito, para uma obra criada depois
 * da pré-visualização não ficar de fora.
 */
public record BackupRequestDTO(
        boolean all,
        List<@Valid Selection> selections,
        boolean includeCompany,
        boolean includeUnidentified,
        boolean includeWorkbooks,
        boolean includeDocuments
) {

    /** Uma obra inteira ou, com {@code budgetId}, um só lote dela. */
    public record Selection(@NotNull UUID enterpriseId, UUID budgetId) {}
}
