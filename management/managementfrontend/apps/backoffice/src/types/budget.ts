/**
 * Orçamento de obra — espelha os DTOs de `enterprises/dto/budget` e
 * `enterprises/dto/expense` do backend.
 */

/** Papel da linha no Excel de origem. Só `ITEM` aceita despesas. */
export type BudgetRowKind = "ITEM" | "HEADING" | "NOTE";

export interface BudgetItemNode {
  id: string;
  parentId: string | null;
  rowKind: BudgetRowKind;
  acceptsExpenses: boolean;
  /** Índice tal como está no Excel ("4.2.1"). Null em títulos, notas e alternativas. */
  code: string | null;
  sortOrder: number;
  depth: number;
  name: string;
  unit: string | null;
  quantity: number | null;
  unitPrice: number | null;
  /** Valor escrito na linha do Excel. Null nas alternativas não escolhidas. */
  totalPrice: number | null;
  observations: string | null;
  startDate: string | null;
  endDate: string | null;

  /** Soma das folhas com preço da sub-árvore. Nunca null. */
  rolledUpBudget: number;
  budgetMismatch: boolean;
  /** `totalPrice - rolledUpBudget`. **Null significa "não há como comparar"**, ≠ 0 = confere. */
  budgetVariance: number | null;
  spentTotal: number;
  remaining: number;
  percentSpent: number | null;
  overBudget: boolean;
  expenseCount: number;
  ownExpenseCount: number;
  missingInvoiceCount: number;
  pendingAccountantCount: number;
  pendingAccountantTotal: number;

  createdAt: string;
  updatedAt: string;
  children: BudgetItemNode[];
}

export interface BudgetTree {
  enterpriseId: string;
  enterpriseName: string;
  budgetTotal: number;
  spentTotal: number;
  remaining: number;
  percentSpent: number | null;
  itemCount: number;
  expenseCount: number;
  /** Conta só a rubrica mais acima de cada ramo em derrapagem — não duplica. */
  overBudgetCount: number;
  overBudgetAmount: number;
  missingInvoiceCount: number;
  pendingAccountantCount: number;
  pendingAccountantTotal: number;
  roots: BudgetItemNode[];
}

export interface BudgetItemUpsert {
  enterpriseId: string;
  parentId?: string | null;
  rowKind: BudgetRowKind;
  code?: string | null;
  name: string;
  unit?: string | null;
  quantity?: number | null;
  unitPrice?: number | null;
  totalPrice?: number | null;
  observations?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  /** Confirmação explícita: sem isto o backend avisa em vez de propagar. */
  propagateStartDate?: boolean;
  propagateEndDate?: boolean;
}

export interface DatePropagationHint {
  ancestorId: string;
  ancestorCode: string | null;
  ancestorName: string;
  field: "START" | "END";
  value: string;
}

export interface BudgetItemSaveResponse {
  item: BudgetItemNode;
  datePropagationHints: DatePropagationHint[];
}

/* ========= Despesas ========= */

/**
 * A fatura vista de dentro de uma despesa. O documento completo pede-se a
 * `getInvoice(id)`, e só quando alguém o abre — ver `types/invoice.ts`.
 */
export interface ExpenseInvoiceRef {
  id: string;
  supplierName: string | null;
  supplierNif: string | null;
  invoiceNumber: string | null;
  invoiceAtcud: string | null;
  invoiceDate: string | null;

  thumbnailUrl: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  sizeBytes: number | null;

  sentToAccountant: boolean;
  sentToAccountantByName: string | null;
  sentToAccountantByRole: "ADMIN" | "EMPLOYEE" | null;
  sentToAccountantAt: string | null;
}

export interface ConstructionExpense {
  id: string;
  budgetItemId: string;
  budgetItemCode: string | null;
  budgetItemName: string;

  name: string;
  description: string | null;
  /** Data da **fatura**, não a de registo. */
  expenseDate: string;
  unit: string | null;
  quantity: number | null;
  unitPrice: number | null;
  totalPrice: number;
  observations: string | null;

  /** Null quando o lançamento foi feito à mão, sem documento. */
  invoice: ExpenseInvoiceRef | null;

  createdBy: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Lançamento **sem documento**. Com fatura, o caminho é carregá-la na caixa de
 * entrada e associá-la — ver `services/invoiceService.ts`.
 */
export interface ConstructionExpenseUpsert {
  budgetItemId: string;
  name: string;
  description?: string | null;
  expenseDate: string;
  unit?: string | null;
  quantity?: number | null;
  unitPrice?: number | null;
  totalPrice: number;
  observations?: string | null;
}

/* ========= Importação de Excel ========= */

export interface BudgetImportRow {
  excelRow: number;
  depth: number;
  kind: BudgetRowKind;
  code: string | null;
  parentCode: string | null;
  name: string;
  unit: string | null;
  quantity: number | null;
  unitPrice: number | null;
  totalPrice: number | null;
  observations: string | null;
}

export interface BudgetImportResult {
  dryRun: boolean;
  sheetName: string;
  itemCount: number;
  headingCount: number;
  noteCount: number;
  parsedTotal: number;
  excelTotal: number | null;
  totalDifference: number | null;
  /** Frases já em português, prontas a mostrar — não são códigos. */
  warnings: string[];
  rows: BudgetImportRow[];
}
