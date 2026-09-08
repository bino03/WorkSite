package com.management.managementapi.enterprises.dto.invoice.response;

import java.util.List;
import java.util.UUID;

/**
 * O que aconteceu a cada fatura de um lote.
 *
 * Melhor esforço, não tudo-ou-nada: as que dão passam, as que falham são
 * listadas com o motivo. É o mesmo princípio do carregamento de ficheiros, que
 * já é deliberadamente por-ficheiro — ninguém perde quatro classificações boas
 * por causa de uma fatura que outro separador entretanto classificou.
 *
 * @param succeeded quantas foram classificadas
 * @param failures  as que não foram, com o código de erro que explica porquê
 */
public record BatchAllocateResultDTO(
        int succeeded,
        List<Failure> failures
) {

    /**
     * @param errorCode o mesmo código que a chamada individual daria
     *                  ({@code INVOICE_ALREADY_ALLOCATED}, {@code INVOICE_SCOPE_NOT_ALLOCATABLE}, …)
     * @param message   frase em português, pronta a mostrar na linha da fatura
     */
    public record Failure(
            UUID invoiceId,
            String invoiceNumber,
            String errorCode,
            String message
    ) {}
}
