import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { useNavigate } from "react-router-dom";
import { Badge, Dropdown, Empty, Spin } from "antd";
import { BellOutlined, CheckSquareOutlined, FileTextOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import {
  countUnreadNotifications,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/services/notificationInboxService";
import { ErrorHandler } from "@/errors/errorHandler";
import type { AppNotification } from "@/types/notification";

/** O `type` da notificação só existe para isto. Qualquer tipo novo cai no sino. */
const iconFor = (type: string) => {
  if (type === "task_assigned") return <CheckSquareOutlined />;
  if (type === "invoice_pending") return <FileTextOutlined />;
  return <BellOutlined />;
};

/**
 * O sino do header.
 *
 * **Sem polling**, por decisão: o contador é lido ao montar e a lista ao abrir.
 * Uma notificação que chegue com a página aberta só aparece na navegação
 * seguinte — aceitável numa ferramenta interna, e evita um pedido a cada N
 * segundos por cada separador aberto.
 */
export const NotificationBell: FC = () => {
  const navigate = useNavigate();

  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const refreshCount = useCallback(() => {
    // Falhar aqui só custa o contador — não vale um erro na cara de ninguém.
    countUnreadNotifications().then(setUnread).catch(() => setUnread(0));
  }, []);

  useEffect(() => {
    refreshCount();
  }, [refreshCount]);

  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listNotifications());
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (next) {
      loadList();
      refreshCount();
    }
  };

  const handleClick = async (item: AppNotification) => {
    setOpen(false);
    if (!item.readAt) {
      try {
        await markNotificationRead(item.id);
        setUnread((n) => Math.max(0, n - 1));
        setItems((prev) =>
          prev.map((i) => (i.id === item.id ? { ...i, readAt: new Date().toISOString() } : i))
        );
      } catch (error) {
        // Não impede a navegação: o utilizador quer chegar lá, não saber disto.
        ErrorHandler.handle(error);
      }
    }
    if (item.link) navigate(item.link);
  };

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsRead();
      const agora = new Date().toISOString();
      setItems((prev) => prev.map((i) => (i.readAt ? i : { ...i, readAt: agora })));
      setUnread(0);
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const painel = (
    <div
      style={{
        width: 340,
        maxHeight: 420,
        overflowY: "auto",
        background: "var(--ind-color-surface)",
        border: "1px solid var(--ind-color-divider)",
        boxShadow: "var(--ind-shadow-sm)",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "10.2px 13.6px",
          borderBottom: "1px solid var(--ind-color-divider)",
        }}
      >
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 13 }}>
          Notificações
        </span>
        {unread > 0 && (
          <a href="#" style={{ fontSize: 12 }} onClick={(e) => { e.preventDefault(); handleMarkAll(); }}>
            Marcar todas como lidas
          </a>
        )}
      </div>

      <Spin spinning={loading}>
        {items.length === 0 && !loading ? (
          <div style={{ padding: "20.4px" }}>
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Sem notificações" />
          </div>
        ) : (
          items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => handleClick(item)}
              style={{
                display: "flex",
                gap: 10,
                width: "100%",
                textAlign: "left",
                padding: "10.2px 13.6px",
                border: "none",
                borderBottom: "1px solid color-mix(in srgb, var(--ind-color-text) 6%, transparent)",
                cursor: "pointer",
                fontFamily: "inherit",
                // A por ler distingue-se pelo fundo, não só pelo ponto: um ponto
                // pequeno é o tipo de sinal que passa despercebido.
                background: item.readAt ? "transparent" : "var(--ind-accent-100)",
              }}
            >
              <span style={{ opacity: 0.7, marginTop: 2 }}>{iconFor(item.type)}</span>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: "block", fontSize: 13, fontWeight: item.readAt ? 400 : 600 }}>
                  {item.title}
                </span>
                {item.body && (
                  <span style={{ display: "block", fontSize: 12, opacity: 0.7 }}>{item.body}</span>
                )}
                <span style={{ display: "block", fontSize: 11, opacity: 0.5, marginTop: 2 }}>
                  {dayjs(item.createdAt).format("DD/MM/YYYY HH:mm")}
                </span>
              </span>
            </button>
          ))
        )}
      </Spin>
    </div>
  );

  return (
    <Dropdown
      open={open}
      onOpenChange={handleOpenChange}
      trigger={["click"]}
      placement="bottomRight"
      dropdownRender={() => painel}
    >
      <button
        type="button"
        title="Notificações"
        aria-label="Notificações"
        style={{
          background: "none",
          border: "none",
          cursor: "pointer",
          display: "flex",
          padding: 4,
          opacity: 0.75,
          color: "inherit",
        }}
      >
        <Badge count={unread} overflowCount={99} size="small">
          <BellOutlined style={{ fontSize: 16 }} />
        </Badge>
      </button>
    </Dropdown>
  );
};
