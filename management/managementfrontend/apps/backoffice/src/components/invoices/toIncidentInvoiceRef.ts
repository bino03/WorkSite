import type { ConstructionInvoice } from "@/types/invoice";
import type { IncidentInvoiceRef } from "@/types/incident";

/** A fatura reduzida ao que o `IncidentDrawer` mostra como chip. */
export function toIncidentInvoiceRef(invoice: ConstructionInvoice): IncidentInvoiceRef {
  return {
    id: invoice.id,
    invoiceNumber: invoice.invoiceNumber,
    supplierName: invoice.supplierName,
    scope: invoice.scope,
    enterpriseId: invoice.enterpriseId,
  };
}
