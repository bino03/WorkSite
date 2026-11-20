import api from "@/api";
import type { TimeDirection, TimeEntry } from "@/types/attendance";

/*
 * Picagem por QR (fase 5 da assiduidade, `/attendance/clock`). As rotas do
 * funcionário (`preview`, `clock`, `me/today`) são `ADMIN` ou `EMPLOYEE` e agem
 * sempre sobre quem tem a sessão — nunca levam um id de perfil. As de `codes` são
 * só `ADMIN`. Sem try/catch: os erros sobem até ao `ErrorHandler` do componente.
 */

export interface ClockPreview {
  enterpriseId: string;
  enterpriseName: string;
  /** O que o próximo toque vai picar: o contrário da última picagem de hoje. */
  nextDirection: TimeDirection;
  today: TimeEntry[];
}

export interface ClockCode {
  enterpriseId: string;
  enterpriseName: string;
  /** O que o QR codifica: `<frontend>/picar?c=<código>`. */
  url: string;
  /** Quando foi gerado o QR atual — muda a cada "Gerar novo QR". */
  generatedAt: string;
}

export async function previewClock(code: string): Promise<ClockPreview> {
  const response = await api.get<ClockPreview>("/attendance/clock/preview", { params: { code }, skipErrorNotification: true });
  return response.data;
}

/** `direction` é o que a página mostrou — o backend recusa com `ATT_023` se já não bater. */
export async function clockIn(code: string, direction: TimeDirection): Promise<TimeEntry> {
  const response = await api.post<TimeEntry>("/attendance/clock", { code, direction }, { skipErrorNotification: true });
  return response.data;
}

export async function myTodayEntries(): Promise<TimeEntry[]> {
  const response = await api.get<TimeEntry[]>("/attendance/clock/me/today");
  return response.data;
}

/** null = a obra ainda não tem QR (204). Ler não cria — cria-se com `generateClockCode`. */
export async function getClockCode(enterpriseId: string): Promise<ClockCode | null> {
  const response = await api.get<ClockCode>(`/attendance/clock/codes/${enterpriseId}`);
  return response.status === 204 ? null : response.data;
}

export async function getClockQrPng(enterpriseId: string): Promise<Blob> {
  const response = await api.get(`/attendance/clock/codes/${enterpriseId}/qr.png`, { responseType: "blob" });
  return response.data as Blob;
}

/** Cria o QR da obra, ou substitui o atual — o antigo deixa de picar logo. */
export async function generateClockCode(enterpriseId: string): Promise<ClockCode> {
  const response = await api.post<ClockCode>(`/attendance/clock/codes/${enterpriseId}`);
  return response.data;
}
