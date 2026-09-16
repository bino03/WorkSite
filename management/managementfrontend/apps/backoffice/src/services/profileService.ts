// src/services/profileService.ts
import api from "@/api";

/* ===========================
   Tipos
=========================== */
export type Role = "ADMIN" | "EMPLOYEE";
export type AccountStatus = "unlocked" | "locked" | "blocked" | "deleted";

export type MyProfile = {
  /** Alguns backends não devolvem id no /profile/myprofile; por isso deixo opcional */
  id?: string;
  name: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  accountStatus: AccountStatus;
  createdAt: string;
  updatedAt: string;
};

export type Employee = {
  id: string;
  authUserId: string;
  name: string;
  email: string;
  phoneNumber: string | null;
  role: Role;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
  /** Vem do backend: esta linha é o próprio utilizador autenticado. */
  me: boolean;
};

export type AssignableEmployee = {
  id: string;
  name: string;
  email: string;
  role: Role;
};

export type NamePhoneUpdateRequest = { name: string; phoneNumber: string };
export type EmailUpdateRequest = { email: string };
export type PasswordUpdateRequest = { currentPassword: string; newPassword: string };

/* ===========================
   Helpers
=========================== */
const isBlank = (v: unknown) => v === null || v === undefined || String(v).trim() === "";

function assertNonEmpty(value: unknown, label: string) {
  if (isBlank(value)) throw new Error(`${label} não pode estar vazio`);
}

function assertEmail(value: string) {
  assertNonEmpty(value, "Email");
  const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  if (!ok) throw new Error("Email inválido");
}

function assertPassword(value: string, label = "Palavra-passe") {
  assertNonEmpty(value, label);
  if (value.length < 6) throw new Error(`${label} deve ter pelo menos 6 caracteres`);
}

/** Get auth user ID from context instead of localStorage */
export function getAuthUserIdFromContext(authUserId?: string): string | null {
  return authUserId ?? null;
}

/* ===========================
   Perfil (me)
=========================== */
export async function getMyProfile(): Promise<MyProfile> {
  const { data } = await api.get<MyProfile>("/profile/myprofile");
  return data;
}

/** Atualiza nome e telefone (PUT /profile/updateNamePhone) */
export async function updateNamePhone(payload: NamePhoneUpdateRequest): Promise<void> {
  assertNonEmpty(payload.name, "Nome");
  assertNonEmpty(payload.phoneNumber, "Telefone");

  await api.put("/profile/updateNamePhone", payload, {
    headers: { "Content-Type": "application/json" },
  });
}

/** Atualiza email (PUT /profile/updateEmail) */
export async function updateEmail(payload: EmailUpdateRequest): Promise<void> {
  assertEmail(payload.email);

  await api.put("/profile/updateEmail", payload, {
    headers: { "Content-Type": "application/json" },
  });
}

/** Atualiza password (PUT /profile/updatePassword) */
export async function updatePassword(payload: PasswordUpdateRequest): Promise<void> {
  assertPassword(payload.currentPassword, "Palavra-passe atual");
  assertPassword(payload.newPassword, "Nova palavra-passe");
  if (payload.currentPassword === payload.newPassword) {
    throw new Error("A nova palavra-passe deve ser diferente da atual");
  }

  await api.put("/profile/updatePassword", payload, {
    headers: { "Content-Type": "application/json" },
  });
}


/* ===========================
   Admin/Legacy (se usares noutras telas)
=========================== */
export async function getEmployee(id: string): Promise<Employee> {
  const { data } = await api.get<Employee>(`/employees/${id}`);
  return data;
}

export async function updateEmployee(
  id: string,
  payload: Partial<Pick<Employee, "name" | "email" | "phoneNumber" | "role">>
): Promise<Partial<Employee>> {
  const { data } = await api.put<Partial<Employee>>(`/employees/${id}`, payload);
  return data;
}

export async function patchAccountStatus(params: {
  profileId: string;
  status: AccountStatus;
}): Promise<void> {
  const { profileId, status } = params;
  if (status === "blocked") {
    await api.patch(`/employees/${profileId}/block`);
  } else if (status === "unlocked") {
    await api.patch(`/employees/${profileId}/unblock`);
  }
}

export async function markAccountDeleted(profileId: string): Promise<void> {
  await api.delete(`/employees/${profileId}`);
}

/** Lista simplificada de utilizadores (ADMIN/EMPLOYEE) para pickers de atribuição — sem paginação */
export async function listAssignableUsers(): Promise<AssignableEmployee[]> {
  const { data } = await api.get<AssignableEmployee[]>("/employees/assignable");
  return data;
}
