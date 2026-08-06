import api from "@/api";
import type {
  BudgetImportResult,
  BudgetItemNode,
  BudgetItemSaveResponse,
  BudgetItemUpsert,
  BudgetTree,
  ConstructionExpense,
  ConstructionExpenseUpsert,
  InvoiceScanResult,
} from "@/types/budget";

/* ========= Árvore de rubricas ========= */

/** Devolve a árvore completa (~200 nós) numa só chamada — não há paginação por nível. */
export async function getBudgetTree(enterpriseId: string): Promise<BudgetTree> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}`);
  return response.data;
}

export async function getBudgetItem(id: string): Promise<BudgetItemNode> {
  const response = await api.get(`/construction-budget/items/${id}`);
  return response.data;
}

export async function createBudgetItem(dto: BudgetItemUpsert): Promise<BudgetItemSaveResponse> {
  const response = await api.post(`/construction-budget/items`, dto);
  return response.data;
}

export async function updateBudgetItem(
  id: string,
  dto: BudgetItemUpsert
): Promise<BudgetItemSaveResponse> {
  const response = await api.put(`/construction-budget/items/${id}`, dto);
  return response.data;
}

/** Reordena entre irmãos e/ou muda de rubrica-mãe. Ambos os parâmetros são opcionais. */
export async function moveBudgetItem(
  id: string,
  parentId?: string | null,
  sortOrder?: number
): Promise<BudgetItemNode> {
  const response = await api.patch(`/construction-budget/items/${id}/move`, null, {
    params: { parentId: parentId ?? undefined, sortOrder },
  });
  return response.data;
}

/** Elimina a rubrica **e toda a sub-árvore**, despesas incluídas. */
export async function deleteBudgetItem(id: string): Promise<void> {
  await api.delete(`/construction-budget/items/${id}`);
}

/**
 * Importa um orçamento em .xlsx.
 *
 * `dryRun` é `true` por omissão no backend: devolve o que seria criado, sem gravar.
 * A gravação exige `replace` quando o projeto já tem orçamento.
 */
export async function importBudget(
  enterpriseId: string,
  file: File,
  dryRun = true,
  replace = false
): Promise<BudgetImportResult> {
  const form = new FormData();
  form.append("file", file);
  const response = await api.post(`/construction-budget/enterprise/${enterpriseId}/import`, form, {
    params: { dryRun, replace },
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}

/* ========= Despesas ========= */

export async function listExpensesByBudgetItem(budgetItemId: string): Promise<ConstructionExpense[]> {
  const response = await api.get(`/construction-expenses/budget-item/${budgetItemId}`);
  return response.data;
}

export async function getExpenseById(id: string): Promise<ConstructionExpense> {
  const response = await api.get(`/construction-expenses/${id}`);
  return response.data;
}

export async function createExpense(
  dto: ConstructionExpenseUpsert,
  invoiceFile?: File
): Promise<ConstructionExpense> {
  const form = new FormData();
  form.append("expenseData", new Blob([JSON.stringify(dto)], { type: "application/json" }));
  if (invoiceFile) form.append("invoiceFile", invoiceFile);
  const response = await api.post(`/construction-expenses`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}

export async function updateExpense(
  id: string,
  dto: ConstructionExpenseUpsert,
  invoiceFile?: File
): Promise<ConstructionExpense> {
  const form = new FormData();
  form.append("expenseData", new Blob([JSON.stringify(dto)], { type: "application/json" }));
  if (invoiceFile) form.append("invoiceFile", invoiceFile);
  const response = await api.put(`/construction-expenses/${id}`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}

export async function setSentToAccountant(id: string, sent: boolean): Promise<ConstructionExpense> {
  const response = await api.patch(`/construction-expenses/${id}/accountant`, null, {
    params: { sent },
  });
  return response.data;
}

export async function deleteExpense(id: string): Promise<void> {
  await api.delete(`/construction-expenses/${id}`);
}

/**
 * Lê o QR code da AT de uma fatura e devolve os campos para pré-preencher o
 * formulário. Não grava nada — o utilizador confirma antes de criar a despesa.
 */
export async function scanInvoice(enterpriseId: string, invoiceFile: File): Promise<InvoiceScanResult> {
  const form = new FormData();
  form.append("invoiceFile", invoiceFile);
  const response = await api.post(`/construction-expenses/scan-invoice`, form, {
    params: { enterpriseId },
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}
