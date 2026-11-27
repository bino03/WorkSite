package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Uma fatura que o recibo cobre e quanto dele é para ela. */
public record ReceiptInvoiceLinkDTO(
        @NotNull UUID invoiceId,
        @NotNull @PositiveOrZero BigDecimal amount
) {}
