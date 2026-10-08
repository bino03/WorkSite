import api from "@/api";
import { fileNameFromDisposition } from "@/utils/downloadBlob";
import { normalizeSpringPage } from "@/utils/springPage";
import type { SpringPage } from "@/utils/springPage";
import type { DownloadedFile } from "@/types/budget";
import type {
  Absence,
  AbsenceStatus,
  AbsenceType,
  AbsenceUpsert,
  AttendanceMonthReport,
  AttendanceSummary,
  Employment,
  EmploymentCreate,
  EmploymentTermCreate,
  Holiday,
  HolidayUpsert,
  TimeEntry,
  TimeEntryRevision,
  TimeEntryUpsert,
  VacationBalance,
  WorkSchedule,
  WorkScheduleUpsert,
} from "@/types/attendance";

/*
 * O módulo de assiduidade (espaço Equipa). Tudo `ADMIN` no backend. Sem
 * try/catch: os erros sobem intactos até ao `ErrorHandler` do componente.
 */

// ── Horários ──────────────────────────────────────────────

/** Os horários vivos, por nome. São poucos: pede-se tudo de uma vez. */
export async function listWorkSchedules(): Promise<WorkSchedule[]> {
  const response = await api.get(`/attendance/work-schedules`, { params: { size: 200 } });
  return normalizeSpringPage<WorkSchedule>(response.data).content;
}

export async function listDeletedWorkSchedules(): Promise<WorkSchedule[]> {
  const response = await api.get(`/attendance/work-schedules/deleted`);
  return response.data;
}

export async function createWorkSchedule(dto: WorkScheduleUpsert): Promise<WorkSchedule> {
  const response = await api.post(`/attendance/work-schedules`, dto);
  return response.data;
}

/** Recusado com `SCHED_007` se o horário já esteve atribuído — cria-se outro. */
export async function updateWorkSchedule(id: string, dto: WorkScheduleUpsert): Promise<WorkSchedule> {
  const response = await api.put(`/attendance/work-schedules/${id}`, dto);
  return response.data;
}

export async function deleteWorkSchedule(id: string): Promise<void> {
  await api.delete(`/attendance/work-schedules/${id}`);
}

export async function restoreWorkSchedule(id: string): Promise<WorkSchedule> {
  const response = await api.post(`/attendance/work-schedules/${id}/restore`);
  return response.data;
}

// ── Feriados ──────────────────────────────────────────────

export async function listHolidays(from?: string, to?: string): Promise<Holiday[]> {
  const response = await api.get(`/attendance/holidays`, { params: from && to ? { from, to } : undefined });
  return response.data;
}

export async function createHoliday(dto: HolidayUpsert): Promise<Holiday> {
  const response = await api.post(`/attendance/holidays`, dto);
  return response.data;
}

export async function updateHoliday(id: string, dto: HolidayUpsert): Promise<Holiday> {
  const response = await api.put(`/attendance/holidays/${id}`, dto);
  return response.data;
}

/** Apagar a sério: um feriado errado não tem de ficar guardado. */
export async function deleteHoliday(id: string): Promise<void> {
  await api.delete(`/attendance/holidays/${id}`);
}

// ── Emprego ───────────────────────────────────────────────

export async function listEmployments(): Promise<Employment[]> {
  const response = await api.get(`/attendance/employments`);
  return response.data;
}

/** `ATT_001` (404) quando o funcionário ainda não tem ficha de emprego. */
export async function getEmployment(profileId: string): Promise<Employment> {
  const response = await api.get(`/attendance/employments/${profileId}`);
  return response.data;
}

export async function createEmployment(dto: EmploymentCreate): Promise<Employment> {
  const response = await api.post(`/attendance/employments`, dto);
  return response.data;
}

export async function updateEmploymentDates(
  profileId: string,
  hiredAt: string,
  endedAt: string | null
): Promise<Employment> {
  const response = await api.put(`/attendance/employments/${profileId}/dates`, null, {
    params: endedAt ? { hiredAt, endedAt } : { hiredAt },
  });
  return response.data;
}

/** O único caminho para mudar horário ou dias de férias: a partir de uma data. */
export async function addEmploymentTerm(profileId: string, dto: EmploymentTermCreate): Promise<Employment> {
  const response = await api.post(`/attendance/employments/${profileId}/terms`, dto);
  return response.data;
}

// ── Picagens ──────────────────────────────────────────────

export async function listTimeEntries(
  profileId: string,
  page: number,
  size: number
): Promise<SpringPage<TimeEntry>> {
  const response = await api.get(`/attendance/time-entries`, { params: { profileId, page, size } });
  return normalizeSpringPage<TimeEntry>(response.data);
}

export async function listTimeEntriesForDay(profileId: string, day: string): Promise<TimeEntry[]> {
  const response = await api.get(`/attendance/time-entries/day`, { params: { profileId, day } });
  return response.data;
}

export async function listDeletedTimeEntries(profileId: string): Promise<TimeEntry[]> {
  const response = await api.get(`/attendance/time-entries/deleted`, { params: { profileId } });
  return response.data;
}

export async function listTimeEntryRevisions(id: string): Promise<TimeEntryRevision[]> {
  const response = await api.get(`/attendance/time-entries/${id}/revisions`);
  return response.data;
}

export async function registerTimeEntry(dto: TimeEntryUpsert): Promise<TimeEntry> {
  const response = await api.post(`/attendance/time-entries`, dto);
  return response.data;
}

export async function correctTimeEntry(id: string, dto: TimeEntryUpsert): Promise<TimeEntry> {
  const response = await api.put(`/attendance/time-entries/${id}`, dto);
  return response.data;
}

/** Só soft-delete: o registo legal não se apaga. */
export async function voidTimeEntry(id: string, reason: string): Promise<void> {
  await api.delete(`/attendance/time-entries/${id}`, { params: { reason } });
}

export async function restoreTimeEntry(id: string, reason: string): Promise<TimeEntry> {
  const response = await api.post(`/attendance/time-entries/${id}/restore`, null, { params: { reason } });
  return response.data;
}

// ── Ausências ─────────────────────────────────────────────

export async function listAbsences(profileId: string, from: string, to: string): Promise<Absence[]> {
  const response = await api.get(`/attendance/absences`, { params: { profileId, from, to } });
  return response.data;
}

/** Os dados do mapa de equipa; o mapa desenha-se no frontend. */
export async function listTeamAbsences(from: string, to: string, status?: AbsenceStatus): Promise<Absence[]> {
  const response = await api.get(`/attendance/absences/team`, {
    params: status ? { from, to, status } : { from, to },
  });
  return response.data;
}

export async function listPendingAbsences(): Promise<Absence[]> {
  const response = await api.get(`/attendance/absences/pending`);
  return response.data;
}

/** O detalhe traz os justificativos com URL assinado; a lista não. */
export async function getAbsence(id: string): Promise<Absence> {
  const response = await api.get(`/attendance/absences/${id}`);
  return response.data;
}

export async function getVacationBalance(profileId: string, year?: number): Promise<VacationBalance> {
  const response = await api.get(`/attendance/absences/balance`, {
    params: year ? { profileId, year } : { profileId },
  });
  return response.data;
}

/** Nasce sempre `PENDING`; aprovar é um passo à parte. */
export async function createAbsence(dto: AbsenceUpsert): Promise<Absence> {
  const response = await api.post(`/attendance/absences`, dto);
  return response.data;
}

export async function updateAbsence(id: string, dto: AbsenceUpsert): Promise<Absence> {
  const response = await api.put(`/attendance/absences/${id}`, dto);
  return response.data;
}

export async function approveAbsence(id: string): Promise<Absence> {
  const response = await api.post(`/attendance/absences/${id}/approve`);
  return response.data;
}

export async function rejectAbsence(id: string): Promise<Absence> {
  const response = await api.post(`/attendance/absences/${id}/reject`);
  return response.data;
}

/** Justificar uma falta = criar uma ausência já aprovada que cobre o dia. */
export async function justifyMissingDay(
  profileId: string,
  day: string,
  type: AbsenceType,
  note?: string
): Promise<Absence> {
  const response = await api.post(`/attendance/absences/justify`, null, {
    params: note ? { profileId, day, type, note } : { profileId, day, type },
  });
  return response.data;
}

export async function deleteAbsence(id: string): Promise<void> {
  await api.delete(`/attendance/absences/${id}`);
}

export async function restoreAbsence(id: string): Promise<Absence> {
  const response = await api.post(`/attendance/absences/${id}/restore`);
  return response.data;
}

export async function uploadAbsenceDocument(id: string, file: File): Promise<void> {
  const form = new FormData();
  form.append("file", file);
  await api.post(`/attendance/absences/${id}/documents`, form);
}

export async function deleteAbsenceDocument(id: string, documentId: string): Promise<void> {
  await api.delete(`/attendance/absences/${id}/documents/${documentId}`);
}

// ── Resumos e relatórios ──────────────────────────────────

/** `month` em "YYYY-MM". */
export async function getMonthSummary(profileId: string, month: string): Promise<AttendanceSummary> {
  const response = await api.get(`/attendance/summary/month`, { params: { profileId, month } });
  return response.data;
}

export async function getMonthReport(month: string): Promise<AttendanceMonthReport> {
  const response = await api.get(`/attendance/reports/month`, { params: { month } });
  return response.data;
}

/** O `.xlsx` do mês para auditoria; o nome vem do `Content-Disposition`. */
export async function exportMonthReport(month: string): Promise<DownloadedFile> {
  const response = await api.get(`/attendance/reports/month/export`, {
    params: { month },
    responseType: "blob",
  });
  return {
    blob: response.data,
    fileName: fileNameFromDisposition(response.headers["content-disposition"], `Assiduidade - ${month}.xlsx`),
  };
}
