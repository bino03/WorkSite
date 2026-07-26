// src/utils/profile.ts
import type { AccountStatus, Role } from "@/services/profileService";

export function getInitials(name?: string | null): string {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] ?? "" : "";
  return (first + last).toUpperCase() || "?";
}

export function roleToLabel(role: Role): "Administrador" | "Funcionário" {
  return role === "ADMIN" ? "Administrador" : "Funcionário";
}

// AntD <Tag color> — usar presets (sem Tailwind de cor fixa)
export function roleToTagColor(role: Role): string {
  return role === "ADMIN" ? "red" : "blue";
}

export function statusToLabel(status: AccountStatus): "Ativo" | "Bloqueado" | "Eliminado" {
  switch (status) {
    case "unlocked":
      return "Ativo";
    case "blocked":
    case "locked":
      return "Bloqueado";
    case "deleted":
      return "Eliminado";
    default:
      return "Bloqueado";
  }
}

// AntD <Badge status> | color — manter simples e consistente
export function statusToBadge(status: AccountStatus): { status?: "success" | "error" | "default"; color?: string } {
  switch (status) {
    case "unlocked":
      return { status: "success" }; // verde de estado (do AntD)
    case "blocked":
    case "locked":
      return { status: "error" }; // vermelho de estado (do AntD)
    case "deleted":
      return { color: "grey" }; // badge neutro
    default:
      return { status: "default" };
  }
}

export function formatDateISOToPT(iso?: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("pt-PT", { dateStyle: "short", timeStyle: "short" });
}
