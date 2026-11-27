package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma fatura do fornecedor que o recibo pode cobrir, no preview. {@code remaining}
 * é o que falta pagar (0 nas pagas); {@code netAmount} é null sem total.
 */
public record ReceiptCandidateInvoiceDTO(
        UUID invoiceId,
        String invoiceNumber,
        LocalDate invoiceDate,
        String scope,
        UUID enterpriseId,
        String enterpriseName,
        BigDecimal netAmount,
        BigDecimal paidAmount,
        BigDecimal remaining,
        String paymentStatus
) {}
