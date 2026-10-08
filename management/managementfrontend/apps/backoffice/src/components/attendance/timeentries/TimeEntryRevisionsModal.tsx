import { useEffect, useState } from "react";
import { Empty, Modal, Spin, Timeline } from "antd";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { listTimeEntryRevisions } from "@/services/attendanceService";
import type { TimeEntryChange, TimeEntryRevision } from "@/types/attendance";

interface Props {
  open: boolean;
  /** null enquanto não há picagem escolhida. */
  entryId: string | null;
  onClose: () => void;
}

const CHANGE_LABEL: Record<TimeEntryChange, string> = {
  CREATE: "Registada",
  UPDATE: "Corrigida",
  DELETE: "Anulada",
  RESTORE: "Restaurada",
};

const CHANGE_COLOR: Record<TimeEntryChange, string> = {
  CREATE: "green",
  UPDATE: "blue",
  DELETE: "red",
  RESTORE: "gray",
};

/**
 * O rasto de uma picagem. É um histórico — cai do lado do Modal, não do Drawer
 * (ver `design/backoffice-drawers-and-modals.md`).
 *
 * Cada revisão guarda o estado **anterior**, não o novo: é assim que se lê o que
 * mudou sem ter de comparar com a linha seguinte.
 */
export function TimeEntryRevisionsModal({ open, entryId, onClose }: Props) {
  const [revisions, setRevisions] = useState<TimeEntryRevision[] | null>(null);

  useEffect(() => {
    if (!open || !entryId) return;
    setRevisions(null);
    listTimeEntryRevisions(entryId).then(setRevisions).catch(ErrorHandler.handle);
  }, [open, entryId]);

  const previous = (revision: TimeEntryRevision) => {
    const parts: string[] = [];
    if (revision.previousHappenedAt) parts.push(dayjs(revision.previousHappenedAt).format("DD/MM/YYYY HH:mm"));
    if (revision.previousDirection) parts.push(revision.previousDirection === "IN" ? "Entrada" : "Saída");
    if (revision.previousNote) parts.push(`nota: ${revision.previousNote}`);
    return parts.join(" · ");
  };

  return (
    <Modal open={open} title="Histórico da picagem" onCancel={onClose} footer={null}>
      {revisions === null ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : revisions.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Sem revisões: a picagem está como foi registada." />
      ) : (
        <Timeline
          items={revisions.map((revision) => ({
            color: CHANGE_COLOR[revision.change],
            children: (
              <div>
                <strong>{CHANGE_LABEL[revision.change]}</strong> por {revision.changedByName}
                <p className="ind-card-meta" style={{ margin: "2px 0 0" }}>
                  {dayjs(revision.changedAt).format("DD/MM/YYYY HH:mm")}
                </p>
                {revision.reason && <p style={{ margin: "4px 0 0", fontSize: 13 }}>{revision.reason}</p>}
                {previous(revision) && (
                  <p className="ind-card-meta" style={{ margin: "4px 0 0" }}>
                    Antes: {previous(revision)}
                  </p>
                )}
              </div>
            ),
          }))}
        />
      )}
    </Modal>
  );
}
