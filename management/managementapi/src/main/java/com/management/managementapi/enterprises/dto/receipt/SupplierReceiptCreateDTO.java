package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Parte JSON ({@code receipt}) do {@code POST /supplier-receipts}; o ficheiro
 * vai na parte {@code file}. Fornecedor vazio herda-se das faturas ligadas.
 * Com {@code createPayment}, as ligações por liquidar são pagas — um pagamento
 * por obra, cada fatura com {@code min(valor da ligação, em falta)}.
 */
public record SupplierReceiptCreateDTO(
        @NotEmpty @Valid List<ReceiptInvoiceLinkDTO> invoiceLinks,
        String receiptNumber,
        @NotNull LocalDate receiptDate,
        @NotNull @PositiveOrZero BigDecimal totalAmount,
        String supplierName,
        String supplierNif,
        String notes,
        /** Opcional: paga também as faturas ligadas por liquidar, na mesma transação. */
        @Valid ReceiptPaymentRequestDTO createPayment
) {}
