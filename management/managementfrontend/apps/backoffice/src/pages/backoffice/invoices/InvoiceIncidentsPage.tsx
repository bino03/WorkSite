import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Empty, Spin } from "antd";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import { listIncidents, resolveIncident } from "@/services/incidentService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatDate } from "@/utils/formatters";
import type { InvoiceIncident } from "@/types/incident";

/**
 * "Inconsistências" — o que ficou por acertar entre faturas, tipicamente depois
 * de uma transferência. Lista + detalhe (markdown) + "marcar como resolvida".
 * Criar uma faz-se a partir de uma fatura transferida (ver `IncidentDrawer`).
 * Só ADMIN — o gate real é o `@PreAuthorize` do backend.
 */
const InvoiceIncidentsPage: FC = () => {
  const confirm = useConfirm();

  const [incidents, setIncidents] = useState<InvoiceIncident[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listIncidents();
      setIncidents(list);
      setSelectedId((prev) => prev ?? list[0]?.id ?? null);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = incidents.find((i) => i.id === selectedId) ?? null;

  const handleResolve = (incident: InvoiceIncident) => {
    confirm({
      title: "Resolver inconsistência",
      message: `Marcar "${incident.title}" como resolvida?`,
      actionLabel: "Marcar como resolvida",
      onConfirm: async () => {
        try {
          await resolveIncident(incident.id);
          notificationService.success("Inconsistência resolvida.");
          await load();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  return (
    <div>
      <div style={{ marginBottom: "20.4px" }}>
        <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
        <h1 style={{ margin: 0 }}>Inconsistências</h1>
      </div>

      <Spin spinning={loading}>
        {!loading && incidents.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Nada por conciliar — todas as faturas batem certo."
          />
        ) : (
          <div style={{ display: "flex", gap: "20.4px", alignItems: "flex-start" }}>
            {/* Lista */}
            <div style={{ flex: "0 0 320px", display: "flex", flexDirection: "column", gap: 8 }}>
              {incidents.map((incident) => (
                <button
                  key={incident.id}
                  type="button"
                  onClick={() => setSelectedId(incident.id)}
                  className="ind-card"
                  style={{
                    textAlign: "left",
                    padding: "10.2px 13.6px",
                    cursor: "pointer",
                    border:
                      incident.id === selectedId
                        ? "1px solid var(--ind-color-accent)"
                        : "1px solid var(--ind-color-divider)",
                    background: "var(--ind-color-surface)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                    <strong style={{ fontSize: 13 }}>{incident.title}</strong>
                    <span
                      className={`ind-tag ${
                        incident.resolvedAt ? "ind-tag-accent-2" : "ind-tag-outline"
                      }`}
                    >
                      {incident.resolvedAt ? "Resolvida" : "Por resolver"}
                    </span>
                  </div>
                  <div style={{ fontSize: 11, opacity: 0.6, marginTop: 4 }}>
                    {incident.invoices.length} fatura(s) · {formatDate(incident.createdAt)}
                  </div>
                </button>
              ))}
            </div>

            {/* Detalhe */}
            {selected && (
              <div className="ind-card" style={{ flex: 1, padding: "20.4px", minWidth: 0 }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
                  <h2 style={{ margin: 0 }}>{selected.title}</h2>
                  {!selected.resolvedAt && (
                    <Button onClick={() => handleResolve(selected)}>Marcar como resolvida</Button>
                  )}
                </div>

                <div style={{ fontSize: 11, opacity: 0.6, marginTop: 6 }}>
                  Criada por {selected.createdByName ?? "—"} · {formatDate(selected.createdAt)}
                  {selected.resolvedAt && (
                    <>
                      {" "}
                      · Resolvida por {selected.resolvedByName ?? "—"} ·{" "}
                      {formatDate(selected.resolvedAt)}
                    </>
                  )}
                </div>

                <div style={{ margin: "13.6px 0", fontSize: 12 }}>
                  <div style={{ opacity: 0.7, marginBottom: 4 }}>Faturas ligadas</div>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    {selected.invoices.map((inv) => (
                      <span key={inv.id} className="ind-tag ind-tag-neutral">
                        {inv.invoiceNumber ?? inv.id.slice(0, 8)}
                        {inv.supplierName ? ` · ${inv.supplierName}` : ""}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="ind-markdown" style={{ fontSize: 13, lineHeight: 1.55 }}>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{selected.body}</ReactMarkdown>
                </div>
              </div>
            )}
          </div>
        )}
      </Spin>
    </div>
  );
};

export default InvoiceIncidentsPage;
