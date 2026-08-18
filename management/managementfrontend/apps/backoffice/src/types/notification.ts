/** Uma notificação in-app. O texto vem já escrito do backend — ver `V20__notification.sql`. */
export interface AppNotification {
  id: string;
  /** `task_assigned` | `invoice_pending`. Só serve para escolher o ícone. */
  type: string;
  title: string;
  body: string | null;
  /** Rota do frontend para onde a notificação leva. */
  link: string | null;
  entityId: string | null;
  /** Nulo enquanto estiver por ler. */
  readAt: string | null;
  createdAt: string;
}
