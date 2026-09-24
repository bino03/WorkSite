// src/services/enterpriseService.ts
import api from "@/api";

export type EnterpriseStatus = "planning" | "under_construction" | "active" | "completed" | "archived" | "deleted";
export const EnterpriseStatus = {
  PLANNING: "planning" as EnterpriseStatus,
  UNDER_CONSTRUCTION: "under_construction" as EnterpriseStatus,
  ACTIVE: "active" as EnterpriseStatus,
  COMPLETED: "completed" as EnterpriseStatus,
  ARCHIVED: "archived" as EnterpriseStatus,
  DELETED: "deleted" as EnterpriseStatus,
};

export type EnterpriseType = "residential" | "commercial" | "industrial" | "mixed_use" | "land";
export const EnterpriseType = {
  RESIDENTIAL: "residential" as EnterpriseType,
  COMMERCIAL: "commercial" as EnterpriseType,
  INDUSTRIAL: "industrial" as EnterpriseType,
  MIXED_USE: "mixed_use" as EnterpriseType,
  LAND: "land" as EnterpriseType,
};

export interface LocationResponseDTO {
  id: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  parish?: string | null;
  municipality?: string | null;
  country?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  googlePlaceId?: string | null;
  notes?: string | null;
}

export interface MediaResponseDTO {
  id: string;
  type: string;
  bucket: string;
  storageKey: string;
  mimeType?: string | null;
  fileSizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
  durationMs?: number | null;
  altText?: string | null;
  sortOrder?: number | null;
  downloadUrl: string;
}

export interface EnterpriseFullResponseDTO {
  id: string;
  name: string;
  internalReference?: string | null;
  type: EnterpriseType;
  status: EnterpriseStatus;
  description?: string | null;
  startDate?: string | null;
  completionDate?: string | null;
  totalArea?: number | null;
  landArea?: number | null;
  totalUnits?: number | null;
  totalInvestment?: number | null;
  currentValue?: number | null;
  currency?: string | null;
  constructionCompany?: string | null;
  architect?: string | null;
  managerId?: string | null;
  ownerId?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  createdBy?: string | null;
  createdbyName?: string | null;
  updatedBy?: string | null;
  isActive: boolean;
  slug?: string | null;
  isTest?: boolean;
  banner?: string | null;
  location?: LocationResponseDTO | null;
  media?: MediaResponseDTO[];
}

/**
 * Busca uma enterprise por ID
 */
export async function getEnterpriseById(id: string): Promise<EnterpriseFullResponseDTO> {
  const response = await api.get<EnterpriseFullResponseDTO>(`/enterprises/${id}`);
  return response.data;
}

/**
 * Atualiza uma enterprise (placeholder para futuro)
 */
export async function updateEnterprise(
  id: string,
  data: Partial<EnterpriseFullResponseDTO>
): Promise<EnterpriseFullResponseDTO> {
  const response = await api.put<EnterpriseFullResponseDTO>(`/enterprises/${id}`, data);
  return response.data;
}

export const updateEnterpriseOverview = async (enterpriseId: string, data: {
  name: string;
  internalReference?: string | null;
  type: string;
  status: string;
  slug?: string | null;
  isTest?: boolean;
  constructionCompany?: string | null;
  architect?: string | null;
}) => {
  const response = await api.patch(`/enterprise-relations/${enterpriseId}/overview`, data);
  return response.data;
};

export const updateEnterpriseDatesAreas = async (enterpriseId: string, data: {
  landArea?: number | null;
  totalArea?: number | null;
  startDate?: string | null;
  completionDate?: string | null;
}) => {
  // Vivia em `/enterprises/{id}/dates-areas` — essa rota nunca existiu no backend (404). O
  // controlador real é o `EntrepriseRelationsController`, o mesmo do overview e do finance.
  const response = await api.patch(`/enterprise-relations/${enterpriseId}/dates-areas`, data);
  return response.data;
};

export const updateEnterpriseFinance = async (enterpriseId: string, data: {
  totalInvestment?: number | null;
  currentValue?: number | null;
  currency?: string;
}) => {
  const response = await api.patch(`/enterprise-relations/${enterpriseId}/finance`, data);
  return response.data;
};

export interface EnterpriseLocationDTO {
  id: string;
  enterpriseId: string;
  location: LocationResponseDTO;
  isPrimary: boolean;
  sortOrder: number;
  notes?: string | null;
}

/**
 * `id` sozinho → liga a uma localização já existente (os outros campos são ignorados pelo
 * backend nesse caso). Sem `id` → cria uma localização nova a partir dos campos e liga-a.
 */
export const upsertEnterpriseLocation = async (enterpriseId: string, data: {
  id?: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  postalCode?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  municipality?: string | null;
  parish?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  googlePlaceId?: string | null;
  notes?: string | null;
}): Promise<EnterpriseLocationDTO> => {
  const response = await api.post(`/enterprise-relations/${enterpriseId}/location/upsert`, data);
  return response.data;
};

export async function removeEnterpriseLocation(enterpriseId: string): Promise<void> {
  await api.delete(`/enterprise-relations/${enterpriseId}/location`);
}

/**
 * Soft delete de uma enterprise (is_active → false, status → deleted)
 */
export async function deleteEnterprise(id: string): Promise<void> {
  await api.delete(`/enterprises/${id}`);
}

export interface EnterpriseLotOption {
  id: string;
  name: string;
}

export interface EnterpriseOption {
  id: string;
  name: string;
  /** Os lotes vivos do projeto — só vem de `searchEnterprises`; outras fontes de `EnterpriseOption` (ex. `Task.enterprise`) não o têm. */
  lots?: EnterpriseLotOption[];
}

/**
 * Pesquisa leve de projetos (id + nome + lotes) — usada em pickers, ex. ligar uma
 * tarefa a um projeto, ou escolher o destino ao transferir uma fatura.
 */
export async function searchEnterprises(q: string, size = 10): Promise<EnterpriseOption[]> {
  const { data } = await api.get(`/enterprises`, { params: { q, page: 0, size } });
  return (data?.content ?? []).map(
    (e: { id: string; name: string; lots?: EnterpriseLotOption[] }) => ({
      id: e.id,
      name: e.name,
      lots: e.lots ?? [],
    })
  );
}
