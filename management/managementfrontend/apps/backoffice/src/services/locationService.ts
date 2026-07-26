// services/locationService.ts
import api from "@/api";

export type LocationLite = {
  id: string;
  addressLine1?: string | null;
  postalCode?: string | null;
  city?: string | null;
  country?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  municipality?: string | null;
  parish?: string | null;
};

export type Location = {
  id: string;
  name?: string | null;
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
  createdAt?: string | null;
  updatedAt?: string | null;
};

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based
  size: number;
}

export interface FetchLocationsParams {
  page: number; // 1-based
  size: number;
  q?: string;
  sortBy?: string;
  sortDir?: "asc" | "desc";
  signal?: AbortSignal;
}

export async function searchLocations(q: string, page = 0, size = 20): Promise<LocationLite[]> {
  const { data } = await api.get("/locations", { params: { q, page, size } });
  return data?.content ?? [];
}

function toNumberOrNull(v: unknown): number | null {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

/** normaliza snake_case -> camelCase e garante tipos */
function normalizeLocation(raw: Record<string, unknown>): Location {
  const str = (v: unknown): string | null => (v != null ? String(v) : null);
  return {
    id: String(raw.id),
    addressLine1: str(raw.addressLine1 ?? raw.address_line1),
    addressLine2: str(raw.addressLine2 ?? raw.address_line2),
    postalCode: str(raw.postalCode ?? raw.postal_code),
    city: str(raw.city),
    parish: str(raw.parish),
    country: str(raw.country),
    municipality: str(raw.municipality),
    latitude: toNumberOrNull(raw.latitude),
    longitude: toNumberOrNull(raw.longitude),
    googlePlaceId: str(raw.googlePlaceId ?? raw.google_place_id),
    notes: str(raw.notes),
    createdAt: raw.createdAt != null ? String(raw.createdAt) : (raw.created_at != null ? String(raw.created_at) : undefined),
    updatedAt: raw.updatedAt != null ? String(raw.updatedAt) : (raw.updated_at != null ? String(raw.updated_at) : undefined),
    name: str(raw.name),
  };
}

/** filtro local quando a API devolve um array simples */
function matchesQuery(l: Location, q: string) {
  if (!q) return true;
  const needle = q.trim().toLowerCase();
  if (!needle) return true;

  const hay = [
    l.country ?? "",
    l.city ?? "",
    l.municipality ?? "",
    l.postalCode ?? "",
    l.addressLine1 ?? "",
    l.addressLine2 ?? "",
  ]
    .join(" ")
    .toLowerCase();

  return hay.includes(needle);
}

/** GET /locations com suporte a API paginada ou array simples */
export async function fetchLocations(params: FetchLocationsParams): Promise<PageResponse<Location>> {
  const { page, size, q, sortBy, sortDir, signal } = params;

  const urlParams = new URLSearchParams();
  urlParams.set("page", String(Math.max(0, page - 1))); // API 0-based
  urlParams.set("size", String(size));
  if (q && q.trim()) urlParams.set("q", q.trim());
  if (sortBy) urlParams.set("sortBy", sortBy);
  if (sortDir) urlParams.set("sortDir", sortDir);

  const res = await api.get(`/locations?${urlParams.toString()}`, { signal });
  const data = res.data;

  // caso 1: API paginada
  if (data && typeof data === "object" && Array.isArray(data.content)) {
    const content = (data.content ?? []).map(normalizeLocation);

    return {
      content,
      totalElements:
        typeof data.totalElements === "number" ? data.totalElements : content.length,
      number: typeof data.number === "number" ? data.number : Math.max(0, page - 1),
      size: typeof data.size === "number" ? data.size : size,
      totalPages:
        typeof data.totalPages === "number"
          ? data.totalPages
          : Math.ceil(((typeof data.totalElements === "number" ? data.totalElements : content.length) || 0) / (typeof data.size === "number" ? data.size : size || 1)),
    };
  }

  // caso 2: array simples
  if (Array.isArray(data)) {
    const all = data.map(normalizeLocation);
    const filtered = q ? all.filter((l: Location) => matchesQuery(l, q)) : all;

    const zero = Math.max(0, page - 1);
    const start = zero * size;
    const end = start + size;
    const slice = filtered.slice(start, end);

    return {
      content: slice,
      totalElements: filtered.length,
      totalPages: Math.max(1, Math.ceil(filtered.length / size)),
      number: zero,
      size,
    };
  }

  // fallback
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: Math.max(0, page - 1),
    size,
  };
}
