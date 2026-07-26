// src/services/adminService.ts
import api from "@/api";
import type { Employee, Role } from "@/services/profileService";

export type CreateEmployeePayload = {
  email: string;
  password: string;
  name: string;
  phone?: string | null;
  role: Role;
};

export type SendInvitePayload = {
  email: string;
  phone?: string | null;
  role: Role;
};

export async function createEmployee(payload: CreateEmployeePayload): Promise<Employee> {
  const { data } = await api.post<Employee>("/auth/admin/create", {
    email: payload.email,
    password: payload.password,
    name: payload.name,
    phone: payload.phone ?? null,
    role: payload.role,
  });
  return data;
}

export async function sendEmployeeInvite(payload: SendInvitePayload): Promise<void> {
  await api.post("/auth/admin/invite", {
    email: payload.email,
    phone: payload.phone ?? null,
    role: payload.role,
  });
}

export type InviteListItem = {
  id: string;
  email: string;
  phone?: string;
  role: Role;
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "CANCELLED";
  sentAt: string;
  acceptedAt?: string;
  expiresAt: string;
  invitedBy: string;
  invitedByName: string;
};

export type InviteFilters = {
  status?: "PENDING" | "ACCEPTED" | "EXPIRED" | "CANCELLED";
  role?: Role;
};

export async function listInvites(filters?: InviteFilters): Promise<InviteListItem[]> {
  const params = new URLSearchParams();
  if (filters?.status) params.append("status", filters.status);
  if (filters?.role) params.append("role", filters.role);
  
  const { data } = await api.get<InviteListItem[]>(
    `/auth/admin/invites${params.toString() ? `?${params.toString()}` : ""}`
  );
  return data;
}