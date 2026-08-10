package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Referência curta a uma fatura já registada com o mesmo ATCUD.
 *
 * O suficiente para quem carregou decidir se é o mesmo papel duas vezes: quem
 * emitiu, quando, quanto, e se já foi classificada nalguma rubrica
 * ({@code budgetItemName} a null significa que também essa está por associar).
 */
public record DuplicateInvoiceRefDTO(
        UUID invoiceId,
        String supplierName,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        String budgetItemCode,
        String budgetItemName
) {}
