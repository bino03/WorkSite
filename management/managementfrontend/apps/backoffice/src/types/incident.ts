import type { InvoiceScope } from "@/types/invoice";

/** Uma fatura ligada a uma inconsistência. */
export interface IncidentInvoiceRef {
  id: string;
  invoiceNumber: string | null;
  supplierName: string | null;
  scope: InvoiceScope;
  enterpriseId: string | null;
}

/** `resolvedAt == null` → por resolver. `body` é markdown. */
export interface InvoiceIncident {
  id: string;
  title: string;
  body: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolvedByName: string | null;
  createdBy: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  invoices: IncidentInvoiceRef[];
}

/** Corpo de `POST /invoice-incidents`. */
export interface IncidentCreatePayload {
  title: string;
  body: string;
  invoiceIds: string[];
}
