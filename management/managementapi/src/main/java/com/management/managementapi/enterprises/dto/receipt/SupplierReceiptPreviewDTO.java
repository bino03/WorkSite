package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O que o QR do recibo diz, sem gravar nada, e as faturas desse fornecedor que
 * ele pode cobrir: as por liquidar (de qualquer data) e as pagas dos últimos 12
 * meses — o recibo chega muitas vezes depois de as faturas estarem pagas.
 */
public record SupplierReceiptPreviewDTO(
        boolean qrRead,
        String documentType,
        String supplierNif,
        String supplierName,
        String receiptNumber,
        LocalDate receiptDate,
        BigDecimal totalAmount,
        List<String> warnings,
        List<ReceiptCandidateInvoiceDTO> candidateInvoices
) {}
