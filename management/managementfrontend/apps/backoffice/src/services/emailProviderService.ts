import api from "@/api";
import type { EmailProvider, EmailProviderUpsert } from "@/types/emailProvider";

const BASE = "/settings/email-providers";

/** Todos os provedores, o predefinido primeiro. */
export async function listEmailProviders(): Promise<EmailProvider[]> {
  const response = await api.get(BASE);
  return response.data;
}

export async function createEmailProvider(dto: EmailProviderUpsert): Promise<EmailProvider> {
  const response = await api.post(BASE, dto);
  return response.data;
}

/** A `password` vazia ou omitida mantém a que está gravada. */
export async function updateEmailProvider(
  id: string,
  dto: EmailProviderUpsert
): Promise<EmailProvider> {
  const response = await api.put(`${BASE}/${id}`, dto);
  return response.data;
}

/** Passa a ser o provedor dos emails do produto; o anterior deixa de o ser. */
export async function setDefaultEmailProvider(id: string): Promise<EmailProvider> {
  const response = await api.patch(`${BASE}/${id}/default`);
  return response.data;
}

export async function setEmailProviderActive(
  id: string,
  active: boolean
): Promise<EmailProvider> {
  const response = await api.patch(`${BASE}/${id}/${active ? "activate" : "deactivate"}`);
  return response.data;
}

/**
 * Envia um email de teste com as credenciais deste provedor — mesmo que não seja
 * o predefinido nem esteja ativo. É a forma de confirmar uma configuração antes
 * de a pôr a servir os convites.
 */
export async function testEmailProvider(id: string, to: string): Promise<void> {
  await api.post(`${BASE}/${id}/test`, { to });
}

export async function deleteEmailProvider(id: string): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
