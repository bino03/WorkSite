import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Button, Drawer, Empty, Space, Spin, Upload } from "antd";
import { DeleteOutlined, InboxOutlined, PaperClipOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import {
  approveAbsence,
  deleteAbsenceDocument,
  getAbsence,
  rejectAbsence,
  uploadAbsenceDocument,
} from "@/services/attendanceService";
import { ABSENCE_STATUS_LABEL, ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AbsenceStatus } from "@/types/attendance";
import { formatBytes, formatLongDate } from "@/utils/formatters";

interface Props {
  /** null enquanto não há ausência aberta. */
  absenceId: string | null;
  onClose: () => void;
  /** Depois de aprovar, recusar ou mexer nos documentos. */
  onChanged: () => void;
}

const STATUS_CLASS: Record<AbsenceStatus, string> = {
  PENDING: "ind-tag-outline",
  APPROVED: "ind-tag-accent",
  REJECTED: "ind-tag-neutral",
};

const HALF_DAY_LABEL = { NONE: "", MORNING: " (manhã)", AFTERNOON: " (tarde)" };

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 8, padding: "6px 0" }}>
      <span className="ind-card-meta" style={{ margin: 0 }}>{label}</span>
      <span style={{ fontSize: 13 }}>{children}</span>
    </div>
  );
}

/**
 * O detalhe de uma ausência: quem, quando, quantos dias úteis, e a decisão.
 * **Só aqui** vêm os justificativos com URL assinado — a lista não os traz, por
 * isso o drawer vai sempre buscar a ausência pelo id em vez de reaproveitar a
 * linha que o abriu.
 */
export function AbsenceDetailDrawer({ absenceId, onClose, onChanged }: Props) {
  const confirm = useConfirm();
  const [absence, setAbsence] = useState<Absence | null>(null);
  const [busy, setBusy] = useState(false);

  const fetchAbsence = useCallback(async () => {
    if (!absenceId) return;
    try {
      setAbsence(await getAbsence(absenceId));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  }, [absenceId]);

  useEffect(() => {
    setAbsence(null);
    void fetchAbsence();
  }, [fetchAbsence]);

  const decide = async (decision: "approve" | "reject") => {
    if (!absence) return;
    setBusy(true);
    try {
      if (decision === "approve") {
        await approveAbsence(absence.id);
        notificationService.success("Ausência aprovada");
      } else {
        await rejectAbsence(absence.id);
        notificationService.success("Ausência recusada");
      }
      await fetchAbsence();
      onChanged();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setBusy(false);
    }
  };

  const upload = async (file: File) => {
    if (!absence) return;
    setBusy(true);
    try {
      await uploadAbsenceDocument(absence.id, file);
      notificationService.success("Justificativo anexado");
      await fetchAbsence();
      onChanged();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setBusy(false);
    }
  };

  const askRemoveDocument = (documentId: string, filename: string) => {
    if (!absence) return;
    confirm({
      message: `Eliminar o justificativo "${filename}"? O ficheiro sai do armazenamento e não volta.`,
      onConfirm: async () => {
        try {
          await deleteAbsenceDocument(absence.id, documentId);
          notificationService.success("Justificativo eliminado");
          await fetchAbsence();
          onChanged();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const period = absence
    ? absence.startsOn === absence.endsOn
      ? `${formatLongDate(absence.startsOn, true)}${HALF_DAY_LABEL[absence.halfDay]}`
      : `${dayjs(absence.startsOn).format("DD/MM/YYYY")} a ${dayjs(absence.endsOn).format("DD/MM/YYYY")}`
    : "";

  return (
    <Drawer
      title={absence ? `${absence.profileName} · ${ABSENCE_TYPE_LABEL[absence.type]}` : "Ausência"}
      open={!!absenceId}
      onClose={onClose}
      width={600}
      footer={
        absence?.status === "PENDING" ? (
          <Space style={{ display: "flex", justifyContent: "flex-end" }}>
            <Button danger onClick={() => decide("reject")} loading={busy}>
              Recusar
            </Button>
            <Button type="primary" onClick={() => decide("approve")} loading={busy}>
              Aprovar
            </Button>
          </Space>
        ) : null
      }
    >
      {!absence ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : (
        <div>
          <span className={`ind-tag ${STATUS_CLASS[absence.status]}`}>
            {ABSENCE_STATUS_LABEL[absence.status]}
          </span>

          <div style={{ marginTop: "13.6px", borderTop: "1px solid var(--ind-color-divider)" }}>
            <Row label="Período">{period}</Row>
            <Row label="Dias úteis">
              <strong>{absence.workingDays}</strong> — já sem fins de semana e feriados
            </Row>
            <Row label="Marcada por">{absence.requestedByName ?? "—"}</Row>
            <Row label="Decidida por">
              {absence.approvedByName
                ? `${absence.approvedByName} · ${dayjs(absence.approvedAt).format("DD/MM/YYYY HH:mm")}`
                : "—"}
            </Row>
            <Row label="Nota">{absence.note ?? "—"}</Row>
          </div>

          <h3 style={{ marginTop: "20.4px", marginBottom: "10.2px" }}>Justificativos</h3>

          {absence.documents.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Sem justificativo anexado." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {absence.documents.map((document) => (
                <div
                  key={document.id}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: 8,
                    padding: "6px 10px",
                    border: "1px solid var(--ind-color-divider)",
                  }}
                >
                  <span style={{ display: "flex", alignItems: "center", gap: 6, minWidth: 0 }}>
                    <PaperClipOutlined style={{ opacity: 0.6 }} />
                    {document.url ? (
                      <a href={document.url} target="_blank" rel="noreferrer">
                        {document.originalFilename}
                      </a>
                    ) : (
                      <span>{document.originalFilename}</span>
                    )}
                    <span className="ind-card-meta" style={{ margin: 0 }}>
                      {formatBytes(document.sizeBytes)}
                    </span>
                  </span>
                  <Button
                    type="text"
                    size="small"
                    icon={<DeleteOutlined />}
                    aria-label={`Eliminar ${document.originalFilename}`}
                    onClick={() => askRemoveDocument(document.id, document.originalFilename)}
                  />
                </div>
              ))}
            </div>
          )}

          <div style={{ marginTop: "13.6px" }}>
            <Upload.Dragger
              accept=".pdf,.jpg,.jpeg,.png"
              maxCount={1}
              showUploadList={false}
              disabled={busy}
              beforeUpload={(picked) => {
                void upload(picked as File);
                return false;
              }}
            >
              <p style={{ margin: 0 }}>
                <InboxOutlined style={{ fontSize: 22, color: "var(--ind-color-accent)" }} />
              </p>
              <p style={{ fontSize: 13, margin: "6px 0 0" }}>Clique ou arraste o justificativo</p>
              <p style={{ fontSize: 11, opacity: 0.6, margin: "4px 0 0" }}>PDF ou imagem</p>
            </Upload.Dragger>
          </div>
        </div>
      )}
    </Drawer>
  );
}
