package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Referência leve a uma nota de crédito, do ponto de vista da fatura que ela
 * credita. {@code totalAmount} é o valor da NC (positivo); o líquido da fatura
 * é {@code total − Σ desses valores}.
 */
public record CreditNoteRefDTO(
        UUID id,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        String documentStatus
) {}
