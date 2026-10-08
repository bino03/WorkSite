package com.management.managementapi.enterprises.dto.invoice.request;

import jakarta.validation.constraints.Size;

/** Corpo de {@code PATCH /construction-invoices/{id}/notes}. Vazio apaga a nota. */
public record InvoiceNotesDTO(
        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {
}
