import api from "@/api";
import type {
  BudgetItemSuggestion,
  ConstructionInvoice,
  ConstructionInvoiceUpsert,
  CreditNoteCreatePayload,
  CreditNoteSplitPreview,
  InvoiceFilters,
  InvoicePreviewResult,
  InvoiceRegisterPayload,
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

/**
 * Regista uma fatura **sem ficheiro** — a que ainda está por pedir ou por
 * imprimir. Única entrada de fatura que é JSON e não multipart.
 */
export async function registerInvoice(
  payload: InvoiceRegisterPayload
): Promise<ConstructionInvoice> {
  const response = await api.post(`/construction-invoices/register`, payload);
  return response.data;
}

/**
 * Junta mais um documento a uma fatura já registada. Ao contrário de
 * {@link replaceInvoiceFile}, que substitui, este acrescenta — a foto tirada na
 * obra e o PDF do fornecedor são o mesmo documento fiscal.
 *
 * Se o QR do ficheiro novo divergir do que a fatura já tem, o backend avisa em
 * `warnings` e **não sobrepõe** nada: só preenche os campos vazios.
 */
export async function addInvoiceDocument(
  invoiceId: string,
  file: File
): Promise<InvoiceUploadResult> {
  const form = new FormData();
  form.append("file", file);

  const response = await api.post(`/construction-invoices/${invoiceId}/documents`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
}

/**
 * Tira **um** documento da fatura. Se era o último, a fatura volta ao estado
 * "sem ficheiro" — quem o decide é o backend.
 */
export async function deleteInvoiceDocument(
  invoiceId: string,
  documentId: string
): Promise<void> {
  await api.delete(`/construction-invoices/${invoiceId}/documents/${documentId}`);
}

/**
 * A repartição negativa que uma NC deste valor proporia sobre esta fatura,
 * na proporção das despesas dela. Nada é gravado — é a proposta que o
 * utilizador confirma ou altera antes de chamar {@link createCreditNote}.
 *
 * Recalcula-se a cada mudança do valor, por isso o erro fica para a drawer
 * mostrar inline: um toast por dígito escrito não serve a ninguém.
 */
export async function previewCreditNoteSplit(
  originId: string,
  amount: number
): Promise<CreditNoteSplitPreview> {
  const response = await api.get(`/construction-invoices/${originId}/credit-notes/split-preview`, {
    params: { amount },
    skipErrorNotification: true,
  });
  return response.data;
}

/**
 * Regista uma nota de crédito a partir de uma fatura já lançada. O âmbito, a
 * obra e (por omissão) o NIF herdam da origem — não se enviam.
 *
 * Devolve a **NC criada**, não a fatura de origem: quem precisar do líquido
 * atualizado tem de voltar a ler a origem com {@link getInvoice}.
 */
export async function createCreditNote(
  originId: string,
  payload: CreditNoteCreatePayload
): Promise<ConstructionInvoice> {
  const response = await api.post(`/construction-invoices/${originId}/credit-notes`, payload);
  return response.data;
}

/**
 * A quarentena: faturas que chegaram e ainda não se sabe de quem são. Vêm da
 * mais antiga para a mais recente — quanto mais tempo lá está, mais urgente é.
 * Só `ADMIN`.
 */
export async function listUnidentifiedInvoices(
  params: { q?: string; outstanding?: boolean; page: number; size: number }
): Promise<InvoicePage> {
  const response = await api.get(`/construction-invoices/unidentified`, { params });
  return normalizePage(response.data);
}

/** Despesas da empresa: faturas sem obra, que não entram em orçamento nenhum. Só `ADMIN`. */
export async function listCompanyInvoices(
  params: { q?: string; outstanding?: boolean; page: number; size: number }
): Promise<InvoicePage> {
  const response = await api.get(`/construction-invoices/company`, { params });
  return normalizePage(response.data);
}

export async function listInvoices(
  enterpriseId: string,
  filters: InvoiceFilters
): Promise<InvoicePage> {
  const params = new URLSearchParams();
  if (filters.allocated !== null) params.set("allocated", String(filters.allocated));
  if (filters.needsReview !== null) params.set("needsReview", String(filters.needsReview));
  if (filters.outstanding !== null) params.set("outstanding", String(filters.outstanding));
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
