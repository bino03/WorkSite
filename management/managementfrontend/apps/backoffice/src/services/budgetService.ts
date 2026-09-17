import api from "@/api";
import { fileNameFromDisposition } from "@/utils/downloadBlob";
import type {
  BudgetExportSheet,
  BudgetExportSummary,
  BudgetImportResult,
  BudgetItemDeleted,
  BudgetItemNode,
  BudgetItemSaveResponse,
  BudgetItemSearchResult,
  BudgetItemUpsert,
  BudgetTree,
  ConstructionExpense,
  ConstructionExpenseUpsert,
  DownloadedFile,
} from "@/types/budget";

/* ========= Árvore de rubricas ========= */

/** Devolve a árvore completa (~200 nós) numa só chamada — não há paginação por nível. */
export async function getBudgetTree(enterpriseId: string): Promise<BudgetTree> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}`);
  return response.data;
}

/**
 * Procura uma rubrica por código (`4.2`) ou por texto (`betão`) — o campo único
 * do ecrã de classificação.
 *
 * Vem do servidor e não de um filtro sobre a {@link getBudgetTree} porque o
 * resultado traz o **caminho completo** e o orçamentado vs. gasto já calculados,
 * que é o que permite escolher a rubrica sem sair do campo. Sem notificação de
 * erro: recalcula-se a cada tecla, e um toast por letra escrita não serve a
 * ninguém.
 */
export async function searchBudgetItems(
  enterpriseId: string,
  query: string,
  limit = 20
): Promise<BudgetItemSearchResult[]> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}/search`, {
    params: { q: query, limit },
    skipErrorNotification: true,
  });
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

/**
 * Elimina a rubrica **e toda a sub-árvore** — soft delete (`deleted_at`), não
 * apaga a linha. Recusa com `BUDGET_013` se houver despesas nalgum nó; a purga
 * real só acontece 30 dias depois, por job agendado no servidor.
 */
export async function deleteBudgetItem(id: string): Promise<void> {
  await api.delete(`/construction-budget/items/${id}`);
}

/** As rubricas eliminadas de um projeto, mais recente primeiro — a zona de recuperação. */
export async function listDeletedBudgetItems(enterpriseId: string): Promise<BudgetItemDeleted[]> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}/deleted`);
  return response.data;
}

/** Repõe a rubrica e a sub-árvore que foi eliminada junto com ela. */
export async function recoverBudgetItem(id: string): Promise<BudgetItemNode> {
  const response = await api.patch(`/construction-budget/items/${id}/recover`);
  return response.data;
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

/**
 * Lançamento **sem documento**.
 *
 * O upload deixou de passar por aqui: uma despesa com fatura nasce de
 * `uploadInvoice` + `allocateInvoice` (`services/invoiceService.ts`), porque a
 * fatura existe antes de se saber a que rubrica pertence.
 */
export async function createExpense(dto: ConstructionExpenseUpsert): Promise<ConstructionExpense> {
  const response = await api.post(`/construction-expenses`, dto);
  return response.data;
}

export async function updateExpense(
  id: string,
  dto: ConstructionExpenseUpsert
): Promise<ConstructionExpense> {
  const response = await api.put(`/construction-expenses/${id}`, dto);
  return response.data;
}

/**
 * Apaga o lançamento. A fatura, se existir, volta à caixa de entrada — é o que
 * se quer quando a classificação estava errada. Para eliminar também o
 * documento, `deleteInvoice`.
 */
export async function deleteExpense(id: string): Promise<void> {
  await api.delete(`/construction-expenses/${id}`);
}

/* ========= Exportação para Excel ========= */

/** O que a exportação vai escrever — contagens e avisos, sem gerar o ficheiro. */
export async function getExportSummary(enterpriseId: string): Promise<BudgetExportSummary> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}/export/summary`);
  return response.data;
}

/**
 * O `.xlsx` da obra com as folhas pedidas. O nome vem do `Content-Disposition`
 * (o backend decide-o: `Despesas - <slug>.xlsx`, com prefixo `TESTE - ` numa obra
 * de teste); `fallback` só serve se o header não chegar.
 */
export async function exportWorkbook(
  enterpriseId: string,
  sheets: BudgetExportSheet[],
  fallbackFileName: string
): Promise<DownloadedFile> {
  const response = await api.get(`/construction-budget/enterprise/${enterpriseId}/export`, {
    params: { sheets: sheets.join(",") },
    responseType: "blob",
  });
  return {
    blob: response.data,
    fileName: fileNameFromDisposition(response.headers["content-disposition"], fallbackFileName),
  };
}
