import { useEffect, useState } from "react";
import { Button, Modal, Space, Typography } from "antd";
import { ReasonField } from "./ui/ReasonField";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** O que se vai fazer — muda o título, o texto e o botão. */
  action: "void" | "restore";
  /** A picagem em causa, em texto ("08:02 · Entrada · Vila Petrus"). */
  description: string;
  onCancel: () => void;
  onConfirm: (reason: string) => Promise<void>;
}

const COPY = {
  void: {
    title: "Anular picagem",
    label: "Motivo da anulação",
    body: "A picagem deixa de contar para as horas do dia, mas não é apagada: fica no histórico e pode ser restaurada.",
    action: "Anular",
    danger: true,
  },
  restore: {
    title: "Restaurar picagem",
    label: "Motivo do restauro",
    body: "A picagem volta a contar para as horas do dia.",
    action: "Restaurar",
    danger: false,
  },
} as const;

/**
 * Anular ou restaurar uma picagem, pedindo o motivo. Não usa o `useConfirm()`
 * partilhado porque este não recolhe input — e aqui o motivo é obrigatório
 * (decisão de 2026-10-08), já que é o que explica a mudança no histórico de
 * revisões. O backend aceitaria vazio.
 */
export function TimeEntryReasonModal({ open, action, description, onCancel, onConfirm }: Props) {
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const copy = COPY[action];

  useEffect(() => {
    if (open) setReason("");
  }, [open, action]);

  const confirm = async () => {
    setSaving(true);
    try {
      await onConfirm(reason.trim());
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={copy.title}
      onCancel={onCancel}
      maskClosable={!saving}
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onCancel} disabled={saving}>
            Cancelar
          </Button>
          <Button type="primary" danger={copy.danger} onClick={confirm} loading={saving} disabled={!reason.trim()}>
            {copy.action}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "10.2px" }}>
        <Text strong>{description}</Text>
        <p className="ind-card-body" style={{ margin: 0 }}>
          {copy.body}
        </p>
        <div>
          <label className="block text-sm mb-1" style={{ color: "var(--text-strong)" }}>
            {copy.label} <span className="text-red-500">*</span>
          </label>
          <ReasonField value={reason} onChange={setReason} disabled={saving} />
        </div>
      </div>
    </Modal>
  );
}
