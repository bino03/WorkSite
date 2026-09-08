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

/** `CREDIT_NOTE` obriga a `relatedInvoiceId` e nasce sempre de uma fatura lançada. */
export type InvoiceDocumentType = "INVOICE" | "CREDIT_NOTE";

/** Só serve à UI para agrupar; nenhuma regra de negócio decide com base nisto. */
export type InvoiceDocumentKind = "ORIGINAL" | "PAGE" | "PHOTO" | "OTHER";

/**
 * Estado de pagamento de uma fatura — **derivado** de Σ(ligações) vs. líquido,
 * nunca guardado. `PARTIAL` só nasce de marcar uma fatura com um valor abaixo
 * do líquido.
 */
export type PaymentStatus = "UNPAID" | "PARTIAL" | "PAID";

/** Método do movimento. Mapa dos valores do Excel em docs/excel-parity §4. */
export type PaymentMethod = "NUMERARIO" | "MULTIBANCO" | "TRANSFERENCIA" | "OUTRO";

/**
 * Um pagamento visto do lado de uma fatura: quanto deste movimento lhe tocou
 * (`amountOnThisInvoice`), o total do movimento (`paymentAmount`) e, num
 * agregado, os números das outras faturas que ele liquidou (`alsoCovers`).
 */
export interface InvoicePaymentSummary {
  paymentId: string;
  paidOn: string;
  method: PaymentMethod;
  amountOnThisInvoice: number;
  paymentAmount: number;
  reference: string | null;
  notes: string | null;
  /** Signed URL — só vem no detalhe. */
  proofUrl: string | null;
  proofFilename: string | null;
  registeredBy: string | null;
  registeredByName: string | null;
  registeredAt: string;
  alsoCovers: string[];
}

/**
 * Uma nota de crédito vista do lado da fatura que ela credita. `totalAmount` é
 * o valor da NC (**positivo**); o líquido da fatura é `total − Σ desses valores`.
 */
export interface CreditNoteRef {
  id: string;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  totalAmount: number | null;
  documentStatus: InvoiceDocumentStatus;
}

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

  // ── pagamento (fase 2) — estado derivado, nunca coluna ──
  paymentStatus: PaymentStatus;
  paidAmount: number;
  /** O que há a pagar: `totalAmount − creditNoteTotal`. */
  netAmount: number | null;
  /** Os movimentos que tocaram esta fatura, do mais antigo ao mais recente. */
  payments: InvoicePaymentSummary[];

  // ── notas de crédito (fase 3) ──
  /** Σ do valor das NC ligadas a esta fatura. Zero quando não tem nenhuma. */
  creditNoteTotal: number;
  /** As NC ligadas a esta fatura, da mais recente para a mais antiga. */
  creditNotes: CreditNoteRef[];

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

// ── pagamentos (fase 2) ──

/** Corpo (parte `payment`) de `POST /construction-invoices/{id}/payments`. */
export interface MarkPaidPayload {
  paidOn: string;
  method: PaymentMethod;
  /** Por omissão paga o que falta liquidar. Abaixo disso → `PARTIAL`. */
  amount?: number | null;
  reference?: string | null;
  notes?: string | null;
}

/** Corpo (parte `payment`) de `POST /construction-invoices/payments` (agregado). */
export interface AggregatePaymentPayload extends MarkPaidPayload {
  invoiceIds: string[];
  /** Obrigatório no agregado: o valor do movimento. */
  amount: number;
}

export interface PaymentAllocation {
  invoiceId: string;
  invoiceNumber: string | null;
  supplierName: string | null;
  amount: number;
}

export interface PaymentResponse {
  id: string;
  paidOn: string;
  method: PaymentMethod;
  amount: number;
  reference: string | null;
  notes: string | null;
  proofUrl: string | null;
  proofFilename: string | null;
  registeredBy: string | null;
  registeredByName: string | null;
  registeredAt: string;
  allocations: PaymentAllocation[];
}

/** Uma fatura que não coube num agregado porque o movimento não chegou. */
export interface LeftOutInvoice {
  invoiceId: string;
  invoiceNumber: string | null;
  supplierName: string | null;
  remaining: number;
}

/**
 * `created: false` → nada foi gravado. Se o movimento foi **menor** que a soma,
 * `leftOut` diz que faturas tirar da seleção.
 */
export interface AggregatePaymentResult {
  created: boolean;
  payment: PaymentResponse | null;
  leftOut: LeftOutInvoice[];
  selectedTotal: number;
  movementAmount: number;
}

// ── notas de crédito (fase 3) ──

/**
 * Uma linha da repartição negativa proposta pelo backend, na proporção das
 * despesas da fatura de origem. `amount` já vem negativo.
 */
export interface ProposedExpense {
  budgetItemId: string;
  budgetItemCode: string | null;
  budgetItemName: string | null;
  amount: number;
}

/**
 * A proposta de repartição de uma NC. `originAllocated: false` = a fatura de
 * origem não tem despesa nenhuma, logo a NC também não gera nenhuma.
 */
export interface CreditNoteSplitPreview {
  originAllocated: boolean;
  /** Soma das linhas propostas — deve ser `-totalAmount` da NC. */
  total: number;
  lines: ProposedExpense[];
}

/** Uma linha confirmada da repartição. O sinal é indiferente: o backend grava negativo. */
export interface CreditNoteExpenseLine {
  budgetItemId: string;
  amount: number;
}

/**
 * Corpo de `POST /construction-invoices/{originId}/credit-notes`.
 *
 * `totalAmount` é **positivo** — o valor da NC; o sinal negativo só aparece nas
 * despesas que ela gera. O âmbito e a obra herdam-se da origem e não se enviam.
 * Na fase 3, `expenses` tem 0 ou 1 linha (a origem só tem uma rubrica).
 */
export interface CreditNoteCreatePayload {
  totalAmount: number;
  invoiceNumber?: string | null;
  invoiceAtcud?: string | null;
  invoiceDate?: string | null;
  /** Por omissão herda o da origem. Diferente → grava com aviso, não bloqueia. */
  supplierNif?: string | null;
  description?: string | null;
  notes?: string | null;
  /** `ARCHIVED` é recusado: uma NC registada à mão não tem ficheiro. */
  documentStatus?: Exclude<InvoiceDocumentStatus, "ARCHIVED"> | null;
  expenses?: CreditNoteExpenseLine[];
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
  /**
   * O campo `D` do QR da AT, em bruto: `"FT"`, `"FS"`, …, ou `"NC"`. Null quando
   * não houve QR. Uma `"NC"` não se regista por aqui — é o fluxo de nota de
   * crédito, a partir da fatura de origem.
   */
  documentType: string | null;
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
  /** `true` = só as por liquidar (pago < líquido, ou ainda sem total). */
  outstanding: boolean | null;
  sentToAccountant: boolean | null;
  from: string | null;
  to: string | null;
  q: string;
  page: number;
  size: number;
}
