package com.management.managementapi.enterprises.dto.invoice.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um ficheiro de uma fatura. As URLs são sempre signed URLs geradas na leitura
 * — a chave de storage nunca sai daqui.
 *
 * {@code fileUrl} só vem preenchido no detalhe: assinar o documento completo de
 * cada linha de uma lista de 20 faturas seria trabalho deitado fora, porque
 * quase nenhum chega a ser aberto.
 */
public record InvoiceDocumentDTO(
        UUID id,
        String fileUrl,
        String thumbnailUrl,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        /** Tamanho antes da compressão no browser; null quando o cliente não o reportou. */
        Long originalSizeBytes,
        /** ORIGINAL, PAGE, PHOTO ou OTHER — só para a UI ordenar. */
        String kind,
        Integer pageNumber,
        UUID uploadedBy,
        String uploadedByName,
        OffsetDateTime uploadedAt
) {}
