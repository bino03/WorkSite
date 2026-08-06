import { createContext, useCallback, useContext, useState } from "react";
import type { ReactNode } from "react";
import { Modal, Button } from "antd";

interface ConfirmOptions {
  title?: string;
  message: string;
  actionLabel?: string;
  onConfirm: () => void | Promise<void>;
}

interface ConfirmDialogContextType {
  confirm: (options: ConfirmOptions) => void;
}

const ConfirmDialogContext = createContext<ConfirmDialogContextType | undefined>(undefined);

/**
 * Shared confirm dialog for every destructive action (delete, logout) — a
 * single stateful component so the same look/behavior serves all of them.
 * See notes/design-briefs redesign handoff, "Confirm dialog (shared)".
 */
export const ConfirmDialogProvider = ({ children }: { children: ReactNode }) => {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("Confirmar eliminação");
  const [message, setMessage] = useState("");
  const [actionLabel, setActionLabel] = useState("Eliminar");
  const [action, setAction] = useState<(() => void | Promise<void>) | null>(null);
  const [running, setRunning] = useState(false);

  const confirm = useCallback((options: ConfirmOptions) => {
    setTitle(options.title ?? "Confirmar eliminação");
    setMessage(options.message);
    setActionLabel(options.actionLabel ?? "Eliminar");
    setAction(() => options.onConfirm);
    setOpen(true);
  }, []);

  const close = () => {
    setOpen(false);
    setAction(null);
  };

  const run = async () => {
    if (!action) return;
    setRunning(true);
    try {
      await action();
    } finally {
      setRunning(false);
      close();
    }
  };

  return (
    <ConfirmDialogContext.Provider value={{ confirm }}>
      {children}
      <Modal
        open={open}
        onCancel={close}
        closable={false}
        footer={null}
        centered
        width={440}
        styles={{
          content: {
            background: "var(--ind-color-bg)",
            border: "1px solid var(--ind-color-divider)",
            boxShadow: "var(--ind-shadow-lg)",
            padding: "13.6px",
          },
          mask: { background: "color-mix(in srgb, #2b2b2d 50%, transparent)" },
        }}
      >
        <i className="ind-corner tl" />
        <i className="ind-corner tr" />
        <i className="ind-corner bl" />
        <i className="ind-corner br" />
        <div style={{ display: "flex", flexDirection: "column", gap: "10.2px" }}>
          <div style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 20 }}>
            {title}
          </div>
          <div style={{ fontSize: 14, opacity: 0.85 }}>{message}</div>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "6.8px", marginTop: "6.8px" }}>
            <Button onClick={close}>Cancelar</Button>
            <Button type="primary" loading={running} onClick={run}>
              {actionLabel}
            </Button>
          </div>
        </div>
      </Modal>
    </ConfirmDialogContext.Provider>
  );
};

export const useConfirm = () => {
  const ctx = useContext(ConfirmDialogContext);
  if (!ctx) {
    throw new Error("useConfirm must be used within ConfirmDialogProvider");
  }
  return ctx.confirm;
};
