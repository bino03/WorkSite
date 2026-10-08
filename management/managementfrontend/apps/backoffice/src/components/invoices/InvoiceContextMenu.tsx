// Menu de contexto (botão direito) numa linha da lista de faturas.
// Mesmo padrão do EmployeeContextMenu: posicionado com `fixed`, fecha ao clicar fora / Escape.

import React, { useEffect, useRef } from "react";
import {
  CheckCircleOutlined,
  CommentOutlined,
  EditOutlined,
  LinkOutlined,
  SendOutlined,
  SwapOutlined,
  DisconnectOutlined,
  DollarOutlined,
} from "@ant-design/icons";

import type { ConstructionInvoice } from "@/types/invoice";

interface Props {
  x: number;
  y: number;
  invoice: ConstructionInvoice | null;
  onClose: () => void;
  isAdmin: boolean;
  /** Só numa lista de obra há rubrica a que associar. */
  isProject: boolean;
  isQuarantine: boolean;
  onAllocate: (invoice: ConstructionInvoice) => void;
  onDeallocate: (invoice: ConstructionInvoice) => void;
  onTransfer?: (invoice: ConstructionInvoice) => void;
  onSendToAccountant: (invoice: ConstructionInvoice) => void;
  onRegisterPayment?: (invoice: ConstructionInvoice) => void;
  onMarkPaid?: (invoice: ConstructionInvoice) => void;
  onAddNote?: (invoice: ConstructionInvoice) => void;
}

interface Item {
  icon: React.ReactNode;
  label: string;
  disabled?: boolean;
  onClick: () => void;
}

export const InvoiceContextMenu: React.FC<Props> = ({
  x,
  y,
  invoice,
  onClose,
  isAdmin,
  isProject,
  isQuarantine,
  onAllocate,
  onDeallocate,
  onTransfer,
  onSendToAccountant,
  onRegisterPayment,
  onMarkPaid,
  onAddNote,
}) => {
  const menuRef = useRef<HTMLDivElement>(null);
  const visible = invoice !== null;

  useEffect(() => {
    if (!visible) return;
    const close = () => onClose();
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const timer = setTimeout(() => {
      document.addEventListener("click", close);
      document.addEventListener("contextmenu", close);
      document.addEventListener("keydown", esc);
    }, 0);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("click", close);
      document.removeEventListener("contextmenu", close);
      document.removeEventListener("keydown", esc);
    };
  }, [visible, onClose]);

  useEffect(() => {
    if (!visible || !menuRef.current) return;
    const rect = menuRef.current.getBoundingClientRect();
    if (rect.right > window.innerWidth) menuRef.current.style.left = `${x - rect.width}px`;
    if (rect.bottom > window.innerHeight) menuRef.current.style.top = `${y - rect.height}px`;
  }, [visible, x, y]);

  if (!invoice) return null;

  const isInvoice = invoice.documentType === "INVOICE";
  const payable =
    invoice.documentType !== "CREDIT_NOTE" &&
    invoice.netAmount != null &&
    invoice.paidAmount < invoice.netAmount;

  const run = (fn: (i: ConstructionInvoice) => void) => () => {
    onClose();
    fn(invoice);
  };

  const items: Item[] = [];
  if (isAdmin && isProject) {
    items.push(
      invoice.allocated
        ? { icon: <DisconnectOutlined />, label: "Desassociar", onClick: run(onDeallocate) }
        : {
            icon: <LinkOutlined />,
            label: "Associar",
            disabled: invoice.needsReview,
            onClick: run(onAllocate),
          }
    );
  }
  if (isAdmin && onTransfer && isInvoice) {
    items.push({
      icon: <SwapOutlined />,
      label: isQuarantine ? "Atribuir a uma obra" : "Transferir",
      onClick: run(onTransfer),
    });
  }
  if (isAdmin) {
    items.push({
      icon: <SendOutlined />,
      label: invoice.sentToAccountant ? "Desmarcar envio" : "Marcar como enviada",
      onClick: run(onSendToAccountant),
    });
  }
  if (isAdmin && onRegisterPayment && payable) {
    items.push({
      icon: <DollarOutlined />,
      label: "Registar pagamento…",
      onClick: run(onRegisterPayment),
    });
  }
  if (isAdmin && onMarkPaid && payable) {
    items.push({
      icon: <CheckCircleOutlined />,
      label: "Marcar como pago",
      onClick: run(onMarkPaid),
    });
  }
  if (onAddNote) {
    items.push({
      icon: invoice.notes ? <EditOutlined /> : <CommentOutlined />,
      label: invoice.notes ? "Ver / editar nota" : "Adicionar nota",
      onClick: run(onAddNote),
    });
  }

  if (items.length === 0) return null;

  return (
    <div
      ref={menuRef}
      style={{
        position: "fixed",
        top: y,
        left: x,
        zIndex: 9999,
        minWidth: 210,
        background: "var(--ind-color-surface, white)",
        borderRadius: 10,
        boxShadow: "0 8px 32px rgba(0,0,0,0.14), 0 2px 8px rgba(0,0,0,0.08)",
        border: "1px solid var(--ind-color-divider, #e8e8e8)",
        padding: 6,
      }}
      onClick={(e) => e.stopPropagation()}
    >
      <div
        style={{
          padding: "8px 12px 6px",
          borderBottom: "1px solid var(--ind-color-divider, #f0f0f0)",
          marginBottom: 4,
          fontSize: 12,
          fontWeight: 600,
          maxWidth: 260,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {invoice.supplierName ?? invoice.invoiceNumber ?? "Fatura"}
        {invoice.invoiceNumber && invoice.supplierName ? ` · ${invoice.invoiceNumber}` : ""}
      </div>
      {items.map((item) => (
        <button
          key={item.label}
          type="button"
          disabled={item.disabled}
          onClick={item.disabled ? undefined : item.onClick}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            width: "100%",
            padding: "8px 12px",
            border: "none",
            background: "transparent",
            borderRadius: 6,
            cursor: item.disabled ? "not-allowed" : "pointer",
            fontSize: 13,
            fontWeight: 500,
            textAlign: "left",
            opacity: item.disabled ? 0.45 : 1,
            color: "inherit",
          }}
          onMouseEnter={(e) => {
            if (!item.disabled) e.currentTarget.style.background = "rgba(0,0,0,0.05)";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "transparent";
          }}
        >
          <span style={{ display: "flex", alignItems: "center" }}>{item.icon}</span>
          {item.label}
        </button>
      ))}
    </div>
  );
};

export default InvoiceContextMenu;
