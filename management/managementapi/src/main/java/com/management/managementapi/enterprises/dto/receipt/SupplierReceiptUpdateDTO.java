package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * {@code PUT /supplier-receipts/{id}}: metadados e as ligações inteiras, que
 * substituem as anteriores (nunca acrescentam). Não mexe no ficheiro nem nos
 * pagamentos que o recibo originou.
 */
public record SupplierReceiptUpdateDTO(
        @NotEmpty @Valid List<ReceiptInvoiceLinkDTO> invoiceLinks,
        String receiptNumber,
        @NotNull LocalDate receiptDate,
        @NotNull @PositiveOrZero BigDecimal totalAmount,
        String supplierName,
        String supplierNif,
        String notes
) {}
