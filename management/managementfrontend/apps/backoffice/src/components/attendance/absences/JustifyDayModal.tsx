import { useEffect, useState } from "react";
import { Button, Input, Modal, Select, Space, Typography } from "antd";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { justifyMissingDay } from "@/services/attendanceService";
import { ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { AbsenceType } from "@/types/attendance";
import { formatLongDate } from "@/utils/formatters";

const { Text } = Typography;

interface Props {
  open: boolean;
  profileId: string;
  profileName: string;
  /** O dia em falta, `YYYY-MM-DD`. */
  day: string;
  onCancel: () => void;
  onDone: () => void;
}

/** Justificar uma falta é dizer o que ela foi — "férias" aqui seria marcar férias, não justificar. */
const TYPES: AbsenceType[] = ["JUSTIFIED", "SICK_LEAVE", "OTHER", "UNJUSTIFIED"];

/**
 * Justificar uma falta de um dia. Por trás é `POST /attendance/absences/justify`,
 * que cria uma ausência **já aprovada** a cobrir esse dia — por isso não passa
 * pelo circuito de pedido/aprovação do `AbsenceUpsertDrawer`.
 *
 * É um Modal e não um Drawer por ser um utilitário curto de dois campos, disparado
 * de uma linha de "Precisa de atenção".
 */
export function JustifyDayModal({ open, profileId, profileName, day, onCancel, onDone }: Props) {
  const [type, setType] = useState<AbsenceType>("JUSTIFIED");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setType("JUSTIFIED");
      setNote("");
    }
  }, [open, day, profileId]);

  const submit = async () => {
    setSaving(true);
    try {
      await justifyMissingDay(profileId, day, type, note.trim() || undefined);
      notificationService.success("Falta justificada");
      onDone();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="Justificar falta"
      onCancel={onCancel}
      maskClosable={!saving}
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onCancel} disabled={saving}>
            Cancelar
          </Button>
          <Button type="primary" onClick={submit} loading={saving}>
            Justificar
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "10.2px" }}>
        <Text strong>
          {profileName} · {formatLongDate(day, true)}
        </Text>
        <p className="ind-card-body" style={{ margin: 0 }}>
          Cria uma ausência <strong>já aprovada</strong> a cobrir este dia — não fica pendente de
          decisão.
        </p>

        <div>
          <label className="block text-sm mb-1" style={{ color: "var(--text-strong)" }}>
            Tipo <span className="text-red-500">*</span>
          </label>
          <Select
            value={type}
            onChange={setType}
            disabled={saving}
            style={{ width: "100%" }}
            options={TYPES.map((value) => ({ value, label: ABSENCE_TYPE_LABEL[value] }))}
          />
        </div>

        <div>
          <label className="block text-sm mb-1" style={{ color: "var(--text-strong)" }}>
            Nota
          </label>
          <Input.TextArea
            value={note}
            onChange={(event) => setNote(event.target.value)}
            rows={2}
            maxLength={500}
            disabled={saving}
            placeholder="O que aconteceu (opcional)"
          />
        </div>
      </div>
    </Modal>
  );
}
