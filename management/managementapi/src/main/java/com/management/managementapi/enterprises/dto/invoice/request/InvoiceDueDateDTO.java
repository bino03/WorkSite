package com.management.managementapi.enterprises.dto.invoice.request;

import java.time.LocalDate;

/**
 * Corpo de {@code PATCH /construction-invoices/{id}/due-date}. Null apaga o
 * vencimento manual e a fatura volta ao calculado pelo prazo do fornecedor.
 */
public record InvoiceDueDateDTO(
        LocalDate dueDate
) {
}
