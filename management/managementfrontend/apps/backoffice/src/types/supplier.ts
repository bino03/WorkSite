/**
 * Fornecedores de obra — espelha `enterprises/dto/supplier` do backend.
 *
 * O catálogo existe porque o QR da AT identifica o emitente só pelo NIF: não há
 * campo para o nome da empresa. Sem isto, o nome era escrito à mão uma vez por
 * fatura.
 */

export interface Supplier {
  id: string;
  nif: string;
  name: string;
  notes: string | null;
  /** Faturas com este NIF, em todos os projetos. */
  invoiceCount: number;
}

/** Um NIF que aparece nas faturas e ainda não tem empresa associada. */
export interface UnknownSupplierNif {
  nif: string;
  invoiceCount: number;
  /** Nome já escrito à mão nalguma fatura deste NIF, se existir. */
  suggestedName: string | null;
  lastInvoiceDate: string | null;
}

export interface SupplierUpsert {
  nif: string;
  name: string;
  notes?: string | null;
}

/** `invoicesUpdated` = faturas que estavam sem nome e ficaram com ele. */
export interface SupplierSaveResult {
  supplier: Supplier;
  invoicesUpdated: number;
}
