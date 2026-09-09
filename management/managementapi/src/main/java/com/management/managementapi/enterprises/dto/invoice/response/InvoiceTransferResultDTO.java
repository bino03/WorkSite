package com.management.managementapi.enterprises.dto.invoice.response;

/**
 * O resultado de uma transferência: a fatura já no novo âmbito, e se vale a pena
 * propor a criação de um incidente ("Inconsistências"). {@code suggestIncident}
 * fica {@code true} quando a fatura trazia repartição, pagamentos ou notas de
 * crédito — os casos em que a transferência mexe em mais do que um rótulo.
 */
public record InvoiceTransferResultDTO(
        ConstructionInvoiceResponseDTO invoice,
        boolean suggestIncident
) {}
