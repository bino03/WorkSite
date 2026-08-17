import api from "@/api";
import type {
  Supplier,
  SupplierSaveResult,
  SupplierUpsert,
  UnknownSupplierNif,
} from "@/types/supplier";

/** O catálogo, por ordem alfabética. `q` filtra por nome ou NIF. */
export async function listSuppliers(q?: string): Promise<Supplier[]> {
  const response = await api.get(`/suppliers`, { params: q?.trim() ? { q: q.trim() } : undefined });
  return response.data;
}

/**
 * Os NIFs vistos nas faturas que ainda não têm empresa associada, do mais
 * frequente para o menos — a lista de trabalho do ecrã de fornecedores.
 */
export async function listUnknownSupplierNifs(): Promise<UnknownSupplierNif[]> {
  const response = await api.get(`/suppliers/unknown-nifs`);
  return response.data;
}

/**
 * Dá nome a um NIF. O backend preenche também as faturas desse NIF que estejam
 * sem nome — é o `invoicesUpdated` da resposta.
 */
export async function createSupplier(dto: SupplierUpsert): Promise<SupplierSaveResult> {
  const response = await api.post(`/suppliers`, dto);
  return response.data;
}

export async function updateSupplier(
  id: string,
  dto: SupplierUpsert
): Promise<SupplierSaveResult> {
  const response = await api.put(`/suppliers/${id}`, dto);
  return response.data;
}

/** Tira do catálogo. O nome já escrito nas faturas fica lá. */
export async function deleteSupplier(id: string): Promise<void> {
  await api.delete(`/suppliers/${id}`);
}
