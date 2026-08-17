import api from "@/api";
import type {
  BudgetItemSuggestion,
  ConstructionInvoice,
  ConstructionInvoiceUpsert,
  InvoiceFilters,
  InvoicePreviewResult,
  InvoiceUploadResult,
} from "@/types/invoice";

export interface InvoicePage {
  content: ConstructionInvoice[];
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Normaliza a página do Spring.
 *
 * Coexistem duas formas nas respostas desta API — a plana
 * (`{content, number, totalElements}`, lida por `EnterprisesList`) e a
 * embrulhada (`{content, page: {...}}`, lida por `useTasks`). Aceitar as duas
 * custa cinco linhas e poupa um bug que só aparece em produção.
 */
function normalizePage(data: unknown): InvoicePage {
  const raw = data as Record<string, unknown>;
  const meta = (raw.page ?? raw) as Record<string, unknown>;
  return {
    content: (raw.content as ConstructionInvoice[]) ?? [],
    size: Number(meta.size ?? 0),
    number: Number(meta.number ?? 0),
    totalElements: Number(meta.totalElements ?? 0),
    totalPages: Number(meta.totalPages ?? 0),
  };
}

/**
 * Lê o QR e verifica duplicados sem gravar nada — o "Enviar" do carregamento
 * em duas fases. Não sobe nada ao Storage nem grava na base de dados; só
 * depois de rever o resultado é que se chama {@link uploadInvoice} ("Guardar").
 */
export async function previewInvoice(
  enterpriseId: string,
  file: File
): Promise<InvoicePreviewResult> {
  const form = new FormData();
  form.append("file", file);

  const response = await api.post(`/construction-invoices/preview`, form, {
    params: { enterpriseId },
    headers: { "Content-Type": "multipart/form-data" },
    // O erro pertence à linha do ficheiro; dez falhas não são dez toasts.
    skipErrorNotification: true,
  });
  return response.data;
}

/**
 * Grava uma fatura, enviando sempre o ficheiro original — é o "Guardar" do
 * carregamento em duas fases, chamado só depois de {@link previewInvoice} ter
 * mostrado o que o ficheiro trouxe.
 *
 * A compressão acontece no servidor, e só depois de o QR da AT ser lido com
 * sucesso — ler antes de comprimir já fez a diferença entre uma fatura
 * legível e uma "por rever". Ver `InvoiceCompressionService` no backend.
 *
 * Não há endpoint de lote de propósito: o cliente chama isto uma vez por
 * ficheiro, para que cada um tenha o seu resultado e uma falha não estrague
 * os restantes.
 */
export async function uploadInvoice(
  enterpriseId: string,
  file: File
): Promise<InvoiceUploadResult> {
  const form = new FormData();
  form.append("file", file);

  const response = await api.post(`/construction-invoices`, form, {
    params: { enterpriseId },
    headers: { "Content-Type": "multipart/form-data" },
    // O erro pertence à linha do ficheiro; dez falhas não são dez toasts.
    skipErrorNotification: true,
  });
  return response.data;
}

export async function listInvoices(
  enterpriseId: string,
  filters: InvoiceFilters
): Promise<InvoicePage> {
  const params = new URLSearchParams();
  if (filters.allocated !== null) params.set("allocated", String(filters.allocated));
  if (filters.needsReview !== null) params.set("needsReview", String(filters.needsReview));
  if (filters.sentToAccountant !== null)
    params.set("sentToAccountant", String(filters.sentToAccountant));
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.q.trim()) params.set("q", filters.q.trim());
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));

  const response = await api.get(
    `/construction-invoices/enterprise/${enterpriseId}?${params.toString()}`
  );
  return normalizePage(response.data);
}

/** Quantas faturas estão por associar — o contador no ecrã do orçamento. */
export async function countPendingInvoices(enterpriseId: string): Promise<number> {
  const response = await api.get(`/construction-invoices/enterprise/${enterpriseId}/pending-count`);
  return response.data;
}

/** Única chamada que traz o link assinado do documento completo. */
export async function getInvoice(id: string): Promise<ConstructionInvoice> {
  const response = await api.get(`/construction-invoices/${id}`);
  return response.data;
}

export async function updateInvoice(
  id: string,
  dto: ConstructionInvoiceUpsert
): Promise<ConstructionInvoice> {
  const response = await api.put(`/construction-invoices/${id}`, dto);
  return response.data;
}

export async function replaceInvoiceFile(id: string, file: File): Promise<InvoiceUploadResult> {
  const form = new FormData();
  form.append("file", file);

  const response = await api.post(`/construction-invoices/${id}/file`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}

/** Liga a fatura a uma rubrica, criando o lançamento no orçamento. */
export async function allocateInvoice(
  id: string,
  budgetItemId: string
): Promise<ConstructionInvoice> {
  const response = await api.patch(`/construction-invoices/${id}/allocate`, null, {
    params: { budgetItemId },
  });
  return response.data;
}

/** Devolve a fatura à caixa de entrada, apagando o lançamento. */
export async function deallocateInvoice(id: string): Promise<ConstructionInvoice> {
  const response = await api.delete(`/construction-invoices/${id}/allocate`);
  return response.data;
}

export async function setInvoiceSentToAccountant(
  id: string,
  sent: boolean
): Promise<ConstructionInvoice> {
  const response = await api.patch(`/construction-invoices/${id}/accountant`, null, {
    params: { sent },
  });
  return response.data;
}

export async function deleteInvoice(id: string): Promise<void> {
  await api.delete(`/construction-invoices/${id}`);
}

/**
 * Rubrica onde as faturas deste fornecedor costumam ser lançadas.
 * `null` quando ainda não há histórico (o backend responde 204).
 */
export async function suggestBudgetItem(
  enterpriseId: string,
  supplierNif: string
): Promise<BudgetItemSuggestion | null> {
  const response = await api.get(`/construction-invoices/enterprise/${enterpriseId}/suggestion`, {
    params: { supplierNif },
  });
  return response.status === 204 ? null : response.data;
}
