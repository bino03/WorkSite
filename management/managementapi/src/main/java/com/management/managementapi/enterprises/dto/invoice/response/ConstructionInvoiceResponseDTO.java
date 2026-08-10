package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fatura devolvida ao cliente.
 *
 * As URLs são sempre signed URLs geradas na leitura — a chave de storage nunca
 * sai daqui.
 *
 * <ul>
 *   <li>{@code thumbnailUrl} vem em todas as respostas: é o que a lista mostra.</li>
 *   <li>{@code fileUrl} só vem no detalhe ({@code GET /construction-invoices/{id}}).
 *       Assinar o documento completo de cada linha de uma lista de 20 seria
 *       trabalho deitado fora — quase nenhum é aberto.</li>
 * </ul>
 *
 * {@code allocated} e {@code needsReview} são derivados e não colunas: com uma
 * fatura por rubrica, um estado guardado só arriscava ficar dessincronizado.
 */
public record ConstructionInvoiceResponseDTO(
        UUID id,
        UUID enterpriseId,

        // ── identificação (QR da AT, corrigível à mão) ──
        String supplierName,
        String supplierNif,
        String invoiceNumber,
        String invoiceAtcud,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        String notes,

        /** Falta a data ou o total — não dá para associar enquanto assim estiver. */
        boolean needsReview,

        // ── afetação à rubrica ──
        boolean allocated,
        UUID expenseId,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,

        // ── documento ──
        String fileUrl,
        String thumbnailUrl,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        /** Tamanho antes da compressão no browser; null quando o cliente não o reportou. */
        Long originalSizeBytes,
        UUID uploadedBy,
        String uploadedByName,
        OffsetDateTime uploadedAt,

        // ── contabilidade ──
        boolean sentToAccountant,
        UUID sentToAccountantBy,
        String sentToAccountantByName,
        /** `ADMIN` ou `EMPLOYEE` — o cliente traduz o rótulo. */
        String sentToAccountantByRole,
        OffsetDateTime sentToAccountantAt,

        UUID createdBy,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
