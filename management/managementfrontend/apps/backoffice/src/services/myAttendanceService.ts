import api from "@/api";
import type { Absence, Holiday, VacationBalance } from "@/types/attendance";

/*
 * "As minhas férias" (`/attendance/me`, ADMIN ou EMPLOYEE). Só leitura, e sempre de
 * quem tem a sessão — nenhuma destas chamadas leva um id de perfil.
 */

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
