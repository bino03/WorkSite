import api from "@/api";
import type { AppNotification } from "@/types/notification";

/**
 * As notificações in-app do próprio utilizador.
 *
 * Não confundir com `services/general/notificationService.tsx`, que são os
 * toasts do Ant Design — esses vivem no browser e desaparecem em 4,5 s. Estas
 * estão na base de dados e sobrevivem ao refresh. Daí o nome diferente.
 *
 * O destinatário nunca vai no pedido: o backend tira-o sempre do token.
 */

interface Page<T> {
  content: T[];
  totalElements: number;
}

export async function listNotifications(page = 0, size = 20): Promise<AppNotification[]> {
  const response = await api.get<Page<AppNotification>>("/notifications", { params: { page, size } });
  return response.data.content ?? [];
}

export async function countUnreadNotifications(): Promise<number> {
  const response = await api.get<{ count: number }>("/notifications/unread-count");
  return response.data.count ?? 0;
}

export async function markNotificationRead(id: string): Promise<AppNotification> {
  const response = await api.patch<AppNotification>(`/notifications/${id}/read`);
  return response.data;
}

/** Devolve quantas passaram a lidas. */
export async function markAllNotificationsRead(): Promise<number> {
  const response = await api.patch<{ updated: number }>("/notifications/read-all");
  return response.data.updated ?? 0;
}
