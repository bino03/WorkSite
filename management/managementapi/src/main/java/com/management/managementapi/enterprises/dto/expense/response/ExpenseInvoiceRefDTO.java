package com.management.managementapi.enterprises.dto.expense.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A fatura vista de dentro de uma despesa.
 *
 * Traz só o que a lista de despesas de uma rubrica precisa de mostrar —
 * fornecedor, número, miniatura, estado na contabilidade. O documento completo
 * pede-se a {@code GET /construction-invoices/{id}}, e só quando alguém o abre.
 */
public record ExpenseInvoiceRefDTO(
        UUID id,
        String supplierName,
        String supplierNif,
        String invoiceNumber,
        String invoiceAtcud,
        LocalDate invoiceDate,

        String thumbnailUrl,
        String originalFilename,
        String mimeType,
        Long sizeBytes,

        boolean sentToAccountant,
        String sentToAccountantByName,
        /** `ADMIN` ou `EMPLOYEE` — o cliente traduz o rótulo. */
        String sentToAccountantByRole,
        OffsetDateTime sentToAccountantAt
) {}
