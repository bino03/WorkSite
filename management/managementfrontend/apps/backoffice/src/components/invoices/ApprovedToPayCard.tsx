import { useEffect, useState } from "react";
import type { FC } from "react";
import { Link } from "react-router-dom";
import { Spin } from "antd";
import { CheckCircleOutlined } from "@ant-design/icons";

import BlueprintCard from "@/components/common/BlueprintCard";
import { InvoiceDueDateCell } from "@/components/invoices/InvoiceDueDate";
import { listOutstandingByUrgency } from "@/services/invoiceService";
import { formatCurrency } from "@/utils/formatters";
import type { OutstandingByUrgency } from "@/types/invoice";

const LINK = "/backoffice/invoices/outstanding?approved=true";
const SHOWN = 5;

/**
 * Página inicial da contabilidade: as faturas aprovadas para pagamento que ainda
 * não estão liquidadas, pela ordem de "A pagar" (vencidas primeiro). É um resumo —
 * pagar faz-se em "A pagar", que abre já filtrado por `?approved=true`.
 *
 * Os números do topo são da lista inteira, não só das 5 mostradas.
 */
export const ApprovedToPayCard: FC = () => {
  const [data, setData] = useState<OutstandingByUrgency | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listOutstandingByUrgency({ approvedForPayment: true, page: 0, size: SHOWN })
      .then((res) => { if (!cancelled) setData(res); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, []);

  if (failed) return null;

  return (
    <BlueprintCard style={{ padding: "13.6px", marginBottom: "27.2px" }}>
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <span className="ind-card-title" style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
          <CheckCircleOutlined style={{ color: "var(--ind-color-accent)" }} />
          Aprovadas para pagamento
        </span>
        {data && data.totalElements > 0 && (
          <span className="ind-card-meta">
            {data.totalElements} {data.totalElements === 1 ? "fatura" : "faturas"} · {formatCurrency(data.total)}
            {data.overdueCount > 0 && ` · ${data.overdueCount} vencida${data.overdueCount === 1 ? "" : "s"}`}
          </span>
        )}
      </div>

      {!data ? (
        <div style={{ padding: "13.6px 0", textAlign: "center" }}><Spin size="small" /></div>
      ) : data.totalElements === 0 ? (
        <p className="ind-card-body" style={{ margin: "8px 0 0" }}>Nada aprovado por pagar.</p>
      ) : (
        <>
          <div style={{ marginTop: 8 }}>
            {data.invoices.map((invoice) => {
              const remaining = Math.max((invoice.netAmount ?? invoice.totalAmount ?? 0) - invoice.paidAmount, 0);
              const owner = invoice.enterpriseId
                ? (data.enterpriseNames[invoice.enterpriseId] ?? "Obra")
                : "Empresa";
              return (
                <Link
                  key={invoice.id}
                  to={LINK}
                  style={{
                    color: "inherit",
                    display: "grid",
                    gridTemplateColumns: "minmax(0, 1fr) auto auto",
                    alignItems: "center",
                    gap: 12,
                    padding: "6px 0",
                    borderTop: "1px solid var(--ind-color-divider)",
                  }}
                >
                  <span style={{ minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {invoice.supplierName ?? invoice.supplierNif ?? "Fornecedor por identificar"}
                    {invoice.invoiceNumber && <span style={{ opacity: 0.6 }}> · {invoice.invoiceNumber}</span>}
                    <span className="ind-card-meta" style={{ display: "block" }}>{owner}</span>
                  </span>
                  <InvoiceDueDateCell invoice={invoice} />
                  <span style={{ fontVariantNumeric: "tabular-nums", textAlign: "right" }}>{formatCurrency(remaining)}</span>
                </Link>
              );
            })}
          </div>
          <Link to={LINK} style={{ display: "inline-block", marginTop: 8, fontSize: 12 }}>
            {data.totalElements > SHOWN ? `Ver as ${data.totalElements} em "A pagar"` : `Abrir em "A pagar"`} →
          </Link>
        </>
      )}
    </BlueprintCard>
  );
};

export default ApprovedToPayCard;
