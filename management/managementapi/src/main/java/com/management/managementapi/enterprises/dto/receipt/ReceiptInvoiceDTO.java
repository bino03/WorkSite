package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Uma fatura ligada a um recibo, no detalhe do recibo. {@code enterpriseName} é null fora de obra. */
public record ReceiptInvoiceDTO(
        UUID invoiceId,
        String invoiceNumber,
        LocalDate invoiceDate,
        String scope,
        UUID enterpriseId,
        String enterpriseName,
        BigDecimal amount
) {}
