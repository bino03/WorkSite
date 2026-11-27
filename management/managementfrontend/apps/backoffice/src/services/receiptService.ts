import api from "@/api";
import type {
  SupplierReceipt,
  SupplierReceiptCreatePayload,
  SupplierReceiptPreview,
  SupplierReceiptUpdatePayload,
} from "@/types/receipt";

/**
 * Recibos de fornecedor (`ADMIN`, `ACCOUNTANT`). Sem `try/catch`: o `errorCode`
 * sobe intacto até ao `ErrorHandler` do componente.
 */

function jsonPart(value: unknown): Blob {
  return new Blob([JSON.stringify(value)], { type: "application/json" });
}

const MULTIPART = { headers: { "Content-Type": "multipart/form-data" } };

/** Lê o QR do ficheiro e devolve as faturas candidatas. Não grava nada. */
export async function previewReceipt(
  file: File,
  linkedInvoiceIds: string[]
): Promise<SupplierReceiptPreview> {
  const form = new FormData();
  form.append("file", file);
  const params = new URLSearchParams();
  linkedInvoiceIds.forEach((id) => params.append("linkedInvoiceIds", id));
  const { data } = await api.post<SupplierReceiptPreview>(
    `/supplier-receipts/preview?${params.toString()}`,
    form,
    MULTIPART
  );
  return data;
}

export async function createReceipt(
  payload: SupplierReceiptCreatePayload,
  file: File
): Promise<SupplierReceipt> {
  const form = new FormData();
  form.append("receipt", jsonPart(payload));
  form.append("file", file);
  const { data } = await api.post<SupplierReceipt>("/supplier-receipts", form, MULTIPART);
  return data;
}

export async function getReceipt(id: string): Promise<SupplierReceipt> {
  const { data } = await api.get<SupplierReceipt>(`/supplier-receipts/${id}`);
  return data;
}

/** Substitui os metadados e as ligações inteiras; não mexe no ficheiro. */
export async function updateReceipt(
  id: string,
  payload: SupplierReceiptUpdatePayload
): Promise<SupplierReceipt> {
  const { data } = await api.put<SupplierReceipt>(`/supplier-receipts/${id}`, payload);
  return data;
}

export async function replaceReceiptFile(id: string, file: File): Promise<SupplierReceipt> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.put<SupplierReceipt>(`/supplier-receipts/${id}/file`, form, MULTIPART);
  return data;
}

/** Apaga o recibo, as ligações e o ficheiro. Os pagamentos que originou ficam. */
export async function deleteReceipt(id: string): Promise<void> {
  await api.delete(`/supplier-receipts/${id}`);
}
