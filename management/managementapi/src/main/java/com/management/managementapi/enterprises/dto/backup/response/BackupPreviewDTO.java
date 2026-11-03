package com.management.managementapi.enterprises.dto.backup.response;

import java.util.List;

/**
 * O que o backup vai levar. Em ficheiros e não em MB: a tabela de documentos não
 * guarda o tamanho. {@code warnings} diz o que ficou de fora e porquê.
 */
public record BackupPreviewDTO(
        String fileName,
        List<Folder> folders,
        int totalDocuments,
        List<String> warnings
) {

    /** {@code name} é o caminho dentro do zip, por exemplo {@code Vila Aleu - 2026-11-03/Lote A}. */
    public record Folder(
            String name,
            String workbookName,
            int documentCount,
            int invoicesWithoutDocument,
            List<String> warnings
    ) {}
}
