package com.management.managementapi.enterprises.dto.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Um recibo visto <b>do lado de uma fatura</b> (detalhe da fatura): quanto dele
 * é para esta fatura, e que outras faturas cobre — o "também cobre".
 */
public record InvoiceReceiptSummaryDTO(
        UUID receiptId,
        String receiptNumber,
        LocalDate receiptDate,
        BigDecimal totalAmount,
        BigDecimal amountForThisInvoice,
        String fileUrl,
        String fileFilename,
        List<CoveredInvoice> alsoCovers,
        BigDecimal mismatch
) {
    public record CoveredInvoice(UUID invoiceId, String invoiceNumber, String enterpriseName) {}
}
