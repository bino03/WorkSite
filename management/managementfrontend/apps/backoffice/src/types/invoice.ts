/**
 * Faturas de obra — espelha os DTOs de `enterprises/dto/invoice` do backend.
 *
 * A fatura é o **documento**; a despesa é a sua afetação a uma rubrica. Uma
 * fatura sem despesa associada (`allocated: false`) é o que está por classificar
 * — é essa a caixa de entrada.
 */

export interface ConstructionInvoice {
  id: string;
  enterpriseId: string;

  supplierName: string | null;
  supplierNif: string | null;
  invoiceNumber: string | null;
  invoiceAtcud: string | null;
  /** Null quando o QR não foi lido — ver `needsReview`. */
  invoiceDate: string | null;
  totalAmount: number | null;
  taxableAmount: number | null;
  taxAmount: number | null;
  notes: string | null;

  /** Falta a data ou o total; não dá para associar enquanto assim estiver. */
  needsReview: boolean;

  allocated: boolean;
  expenseId: string | null;
  budgetItemId: string | null;
  budgetItemCode: string | null;
  budgetItemName: string | null;

  /** Só vem no detalhe (`getInvoice`) — nas listas é sempre null. */
  fileUrl: string | null;
  thumbnailUrl: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  /** Tamanho do ficheiro que foi carregado, antes de qualquer compressão no servidor. */
  originalSizeBytes: number | null;
  uploadedBy: string | null;
  uploadedByName: string | null;
  uploadedAt: string;

  sentToAccountant: boolean;
  sentToAccountantBy: string | null;
  sentToAccountantByName: string | null;
  sentToAccountantByRole: "ADMIN" | "EMPLOYEE" | null;
  sentToAccountantAt: string | null;

  createdBy: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Correção manual. `taxableAmount`/`taxAmount` não entram aqui: são o que o QR
 * da AT declarou e não se editam — o backend deixa-os intactos.
 */
export interface ConstructionInvoiceUpsert {
  supplierName?: string | null;
  supplierNif?: string | null;
  invoiceNumber?: string | null;
  invoiceAtcud?: string | null;
  invoiceDate?: string | null;
  totalAmount?: number | null;
  notes?: string | null;
}

/** Fatura já registada com o mesmo ATCUD. Aviso, não bloqueio. */
export interface DuplicateInvoiceRef {
  invoiceId: string;
  supplierName: string | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  totalAmount: number | null;
  budgetItemCode: string | null;
  budgetItemName: string | null;
}

export interface InvoiceUploadResult {
  invoice: ConstructionInvoice;
  /** `false` não é erro — é fornecedor estrangeiro, documento antigo ou digitalização má. */
  qrRead: boolean;
  duplicates: DuplicateInvoiceRef[];
  /** Frases já em português, prontas a mostrar. */
  warnings: string[];
}

/**
 * Resultado de ler uma fatura sem a gravar — o "Enviar" do carregamento em
 * duas fases. Nada foi tocado no Storage nem na base de dados.
 */
export interface InvoicePreviewResult {
  qrRead: boolean;
  /**
   * Colide com uma fatura já registada neste projeto. `false` não garante que
   * não haja uma cópia idêntica no mesmo lote ainda por guardar — só compara
   * com o que já está persistido.
   */
  duplicate: boolean;
  /** Frase pronta a mostrar, identificando a fatura com que colide. */
  duplicateMessage: string | null;
  supplierName: string | null;
  supplierNif: string | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  totalAmount: number | null;
  needsReview: boolean;
  warnings: string[];
}

export interface BudgetItemSuggestion {
  budgetItemId: string;
  code: string | null;
  name: string;
}

export interface InvoiceFilters {
  /** `false` = a caixa de entrada (o que está por classificar). */
  allocated: boolean | null;
  needsReview: boolean | null;
  sentToAccountant: boolean | null;
  from: string | null;
  to: string | null;
  q: string;
  page: number;
  size: number;
}
