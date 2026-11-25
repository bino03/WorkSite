import api from "@/api";
import type {
  Absence,
  AttendanceSummary,
  Holiday,
  MyVacationRequest,
  VacationBalance,
  VacationRequestPreview,
} from "@/types/attendance";

/*
 * "As minhas férias e horas" (`/attendance/me`, ADMIN ou EMPLOYEE), sempre de quem tem
 * a sessão — nenhuma destas chamadas leva um id de perfil. A única escrita é pedir
 * férias (fica pendente) e anular um pedido ainda por decidir.
 */

export async function myMonthSummary(month: string): Promise<AttendanceSummary> {
  const response = await api.get<AttendanceSummary>("/attendance/me/summary/month", { params: { month } });
  return response.data;
}

export async function previewVacationRequest(
  startsOn: string,
  endsOn: string,
  halfDay: MyVacationRequest["halfDay"]
): Promise<VacationRequestPreview> {
  const response = await api.get<VacationRequestPreview>("/attendance/me/vacation-requests/preview", {
    params: { startsOn, endsOn, halfDay },
  });
  return response.data;
}

export async function requestVacation(request: MyVacationRequest): Promise<Absence> {
  const response = await api.post<Absence>("/attendance/me/vacation-requests", request);
  return response.data;
}

export async function cancelVacationRequest(id: string): Promise<void> {
  await api.delete(`/attendance/me/vacation-requests/${id}`);
}

export async function myAbsences(from: string, to: string): Promise<Absence[]> {
  const response = await api.get<Absence[]>("/attendance/me/absences", { params: { from, to } });
  return response.data;
}

export async function myVacationBalance(year: number): Promise<VacationBalance> {
  const response = await api.get<VacationBalance>("/attendance/me/vacation-balance", { params: { year } });
  return response.data;
}

export async function holidaysBetween(from: string, to: string): Promise<Holiday[]> {
  const response = await api.get<Holiday[]>("/attendance/me/holidays", { params: { from, to } });
  return response.data;
}
