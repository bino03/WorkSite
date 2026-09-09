package com.management.managementapi.enterprises.dto.incident;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** Cria uma inconsistência. O corpo é markdown. */
public record InvoiceIncidentCreateDTO(
        @NotBlank String title,
        @NotBlank String body,
        @NotEmpty List<UUID> invoiceIds
) {}
