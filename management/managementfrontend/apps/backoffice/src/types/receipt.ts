import type { InvoiceScope, PaymentMethod, PaymentStatus } from "@/types/invoice";

/**
 * Recibos de fornecedor (`/supplier-receipts`). Um recibo liquida N faturas,
 * possivelmente de várias obras, e **não** muda o estado de pagamento — isso
 * continua a sair só dos `payments`. Contrato em docs/api.md.
 */

/** Um recibo visto do lado de uma fatura (detalhe da fatura). */
export interface InvoiceReceiptSummary {
  receiptId: string;
  receiptNumber: string | null;
  receiptDate: string;
  totalAmount: number;
  amountForThisInvoice: number;
  fileUrl: string | null;
  fileFilename: string | null;
  alsoCovers: { invoiceId: string; invoiceNumber: string | null; enterpriseName: string | null }[];
  /** `totalAmount − Σ ligações`; 0 quando bate. */
  mismatch: number;
}

export interface ReceiptInvoiceLink {
  invoiceId: string;
  amount: number;
}

export interface ReceiptPaymentRequest {
  method: PaymentMethod;
  paidOn: string | null;
  reference: string | null;
}

export interface SupplierReceiptCreatePayload {
  invoiceLinks: ReceiptInvoiceLink[];
  receiptNumber: string | null;
  receiptDate: string;
  totalAmount: number;
  supplierName: string | null;
  supplierNif: string | null;
  notes: string | null;
  createPayment?: ReceiptPaymentRequest | null;
}

export type SupplierReceiptUpdatePayload = Omit<SupplierReceiptCreatePayload, "createPayment">;

export interface ReceiptInvoice {
  invoiceId: string;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  scope: InvoiceScope;
  enterpriseId: string | null;
  enterpriseName: string | null;
  amount: number;
}

export interface SupplierReceipt {
  id: string;
  supplierName: string | null;
  supplierNif: string | null;
  receiptNumber: string | null;
  receiptDate: string;
  totalAmount: number;
  notes: string | null;
  fileUrl: string | null;
  fileFilename: string | null;
  fileMime: string | null;
  invoices: ReceiptInvoice[];
  linkedSum: number;
  mismatch: number;
  paymentIds: string[];
  registeredBy: string | null;
  registeredByName: string | null;
  registeredAt: string;
  warnings: string[];
}

export interface ReceiptCandidateInvoice {
  invoiceId: string;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  scope: InvoiceScope;
  enterpriseId: string | null;
  enterpriseName: string | null;
  netAmount: number | null;
  paidAmount: number;
  remaining: number;
  paymentStatus: PaymentStatus;
}

export interface SupplierReceiptPreview {
  qrRead: boolean;
  documentType: string | null;
  supplierNif: string | null;
  supplierName: string | null;
  receiptNumber: string | null;
  receiptDate: string | null;
  totalAmount: number | null;
  warnings: string[];
  candidateInvoices: ReceiptCandidateInvoice[];
}
