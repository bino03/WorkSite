package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Um pagamento visto <b>do lado de uma fatura</b>: quanto deste movimento tocou
 * a esta fatura ({@code amountOnThisInvoice}), o total do movimento
 * ({@code paymentAmount}) e, quando o pagamento é agregado, os números das
 * outras faturas que ele liquidou ({@code alsoCovers}) — é o que deixa a UI
 * dizer "pago em 28-08-2026, junto com FT A e FT B" sem ninguém escrever isso à
 * mão.
 */
public record InvoicePaymentSummaryDTO(
        UUID paymentId,
        LocalDate paidOn,
        String method,
        BigDecimal amountOnThisInvoice,
        BigDecimal paymentAmount,
        String reference,
        String notes,
        String proofUrl,
        String proofFilename,
        UUID registeredBy,
        String registeredByName,
        OffsetDateTime registeredAt,
        List<String> alsoCovers
) {}
