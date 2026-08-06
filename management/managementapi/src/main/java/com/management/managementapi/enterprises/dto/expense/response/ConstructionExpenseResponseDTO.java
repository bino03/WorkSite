package com.management.managementapi.enterprises.dto.expense.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Despesa devolvida ao cliente.
 *
 * {@code invoiceUrl} é sempre uma signed URL gerada no momento da leitura —
 * a chave de storage nunca sai daqui. Como essa geração pode falhar (e nesse
 * caso devolve null), {@code hasInvoice} diz separadamente se há mesmo ficheiro:
 * sem ele, o cliente não conseguiria distinguir "não tem fatura" de
 * "tem, mas não foi possível gerar o link".
 */
public record ConstructionExpenseResponseDTO(
        UUID id,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,

        String name,
        String description,
        LocalDate expenseDate,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String observations,

        // ── identificação da fatura (QR da AT) ──
        String supplierNif,
        String invoiceNumber,
        String invoiceAtcud,

        // ── fatura ──
        boolean hasInvoice,
        String invoiceUrl,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
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
