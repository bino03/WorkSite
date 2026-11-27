package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O recibo com as faturas que cobre. {@code mismatch = totalAmount − linkedSum}
 * ({@code 0} quando bate): uma diferença nunca é erro, é um aviso — o recibo
 * pode trazer descontos ou retenções que a app não conhece.
 */
public record SupplierReceiptResponseDTO(
        UUID id,
        String supplierName,
        String supplierNif,
        String receiptNumber,
        LocalDate receiptDate,
        BigDecimal totalAmount,
        String notes,
        String fileUrl,
        String fileFilename,
        String fileMime,
        List<ReceiptInvoiceDTO> invoices,
        BigDecimal linkedSum,
        BigDecimal mismatch,
        List<UUID> paymentIds,
        UUID registeredBy,
        String registeredByName,
        OffsetDateTime registeredAt,
        /** Avisos do pedido que não impediram gravar (ex.: "nenhuma fatura por liquidar"). */
        List<String> warnings
) {}
