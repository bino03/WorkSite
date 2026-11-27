package com.management.managementapi.enterprises.service;

import java.util.Locale;

/**
 * Normaliza um número de documento (fatura, recibo) para comparar duplicados:
 * maiúsculas e só letras/dígitos. Cada software de faturação escreve o mesmo
 * número de forma diferente ("FT 2024/123", "FT2024-123", "ft.2024.123"), e
 * sem isto o mesmo documento escrito à mão duas vezes escapava à verificação
 * por (NIF, número). Não se aplica ao ATCUD nem ao checksum: nenhum dos dois é
 * escrito à mão.
 */
final class DocumentNumbers {

    private DocumentNumbers() {
    }

    /** {@code ""} para um número vazio ou só com espaços. */
    static String normalize(String value) {
        return value == null || value.isBlank()
                ? ""
                : value.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
    }
}
