package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da pesquisa global de faturas (o modal Ctrl+K): só o que é preciso
 * para reconhecer a fatura e abri-la. Leve de propósito — o detalhe vem do
 * {@code GET /construction-invoices/{id}} quando se clica, e assim a pesquisa
 * não assina miniaturas nem carrega repartições a cada tecla.
 *
 * @param enterpriseName nulo nas despesas da empresa e na quarentena
 */
public record InvoiceSearchResultDTO(
        UUID id,
        String documentType,
        String scope,
        UUID enterpriseId,
        String enterpriseName,
        String invoiceNumber,
        String supplierName,
        String supplierNif,
        LocalDate invoiceDate,
        BigDecimal totalAmount
) {}
