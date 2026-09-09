package com.management.managementapi.enterprises.dto.incident;

import java.util.UUID;

/** Uma fatura ligada a uma inconsistência — o suficiente para a lista voltar a ela. */
public record IncidentInvoiceRefDTO(
        UUID id,
        String invoiceNumber,
        String supplierName,
        String scope,
        UUID enterpriseId
) {}
