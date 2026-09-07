/**
 * Faturas de obra — espelha os DTOs de `enterprises/dto/invoice` do backend.
 *
 * A fatura é o **registo**; os ficheiros são 0..N documentos seus e a despesa é
 * a sua afetação a uma rubrica. Uma fatura sem despesa associada
 * (`allocated: false`) é o que está por classificar — é essa a caixa de entrada.
 */

/** Onde a fatura vive. `PROJECT` exige obra; os outros dois proíbem-na. */
export type InvoiceScope = "PROJECT" | "COMPANY" | "UNIDENTIFIED";

/**
 * O que se passa com o papel. Segue o papel, não é escolha livre: passa a
 * `ARCHIVED` ao juntar um documento e volta a `MISSING` ao largar todos.
 */
export type InvoiceDocumentStatus = "ARCHIVED" | "MISSING" | "TO_PRINT" | "TO_REQUEST";

/** `CREDIT_NOTE` obriga a `relatedInvoiceId`. As regras da NC são a fase 3. */
export type InvoiceDocumentType = "INVOICE" | "CREDIT_NOTE";

/** Só serve à UI para agrupar; nenhuma regra de negócio decide com base nisto. */
export type InvoiceDocumentKind = "ORIGINAL" | "PAGE" | "PHOTO" | "OTHER";

/**
 * Um ficheiro da fatura. Desde a V24 são 0..N por fatura: a foto tirada na obra
 * e o PDF que o fornecedor mandou depois são o mesmo documento fiscal.
 */
export interface InvoiceDocument {
  id: string;
  /** Signed URL — só vem no detalhe (`getInvoice`). */
  fileUrl: string | null;
  thumbnailUrl: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  originalSizeBytes: number | null;
  kind: InvoiceDocumentKind;
  /** Só para PDFs partidos página a página. */
  pageNumber: number | null;
  uploadedBy: string | null;
  uploadedByName: string | null;
  uploadedAt: string;
}

export interface ConstructionInvoice {
  id: string;
  /** Nulo quando `scope` não é `PROJECT` — despesa da empresa ou quarentena. */
  enterpriseId: string | null;
  scope: InvoiceScope;
  documentType: InvoiceDocumentType;
  /** A fatura que esta nota de crédito corrige. */
  relatedInvoiceId: string | null;
  documentStatus: InvoiceDocumentStatus;
  /** Descrição da despesa, guardada na própria fatura (folha `Despesas`). */
  description: string | null;
  /** Notas livres da quarentena: de quem se suspeita, e a quem perguntar. */
  possibleEnterprises: string | null;
  askWhom: string | null;

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

  /**
   * Os documentos da fatura, do mais antigo para o mais recente. Os campos
   * soltos abaixo descrevem o **primeiro** deles e continuam a existir de
   * propósito — é o que evita reescrever tudo o que já os lê.
   */
  documents: InvoiceDocument[];

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
  description?: string | null;
  /**
   * `ARCHIVED` e `MISSING` não se escolhem à mão — quem os põe é o backend, ao
   * juntar ou largar documentos. Editável só entre os outros dois.
   */
  documentStatus?: Exclude<InvoiceDocumentStatus, "ARCHIVED" | "MISSING"> | null;
  possibleEnterprises?: string | null;
  askWhom?: string | null;
}

/**
 * Registar uma fatura **sem ficheiro** — a que está por pedir ou por imprimir,
 * e a via de entrada das despesas da empresa e das faturas por identificar.
 *
 * `enterpriseId` é obrigatório sse `scope === "PROJECT"`, e proibido nos outros
 * dois casos: é o mesmo check que a base de dados impõe
 * (`ck_invoice_scope_enterprise`), validado no serviço para o erro sair legível.
 */
export interface InvoiceRegisterPayload {
  scope: InvoiceScope;
  enterpriseId?: string | null;
  supplierName?: string | null;
  supplierNif?: string | null;
  invoiceNumber?: string | null;
  invoiceAtcud?: string | null;
  invoiceDate?: string | null;
  totalAmount?: number | null;
  description?: string | null;
  /** `ARCHIVED` é recusado pelo backend: sem ficheiro não há nada arquivado. */
  documentStatus?: Exclude<InvoiceDocumentStatus, "ARCHIVED"> | null;
  possibleEnterprises?: string | null;
  askWhom?: string | null;
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
