package com.management.managementapi.enterprises.dto.incident;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Uma inconsistência devolvida ao cliente. {@code resolvedAt == null} → por resolver. */
public record InvoiceIncidentResponseDTO(
        UUID id,
        String title,
        String body,
        OffsetDateTime resolvedAt,
        UUID resolvedBy,
        String resolvedByName,
        UUID createdBy,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<IncidentInvoiceRefDTO> invoices
) {}
