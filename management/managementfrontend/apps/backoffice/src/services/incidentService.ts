import api from "@/api";
import type { IncidentCreatePayload, InvoiceIncident } from "@/types/incident";

/** Por resolver primeiro, depois as mais recentes. */
export async function listIncidents(): Promise<InvoiceIncident[]> {
  return (await api.get("/invoice-incidents")).data;
}

export async function getIncident(id: string): Promise<InvoiceIncident> {
  return (await api.get(`/invoice-incidents/${id}`)).data;
}

export async function createIncident(payload: IncidentCreatePayload): Promise<InvoiceIncident> {
  return (await api.post("/invoice-incidents", payload)).data;
}

/** Marca como resolvida. Idempotente no backend. */
export async function resolveIncident(id: string): Promise<InvoiceIncident> {
  return (await api.post(`/invoice-incidents/${id}/resolve`)).data;
}
