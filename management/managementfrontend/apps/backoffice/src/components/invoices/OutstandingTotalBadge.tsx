import type { FC } from "react";
import { Tooltip } from "antd";

import { formatCurrency } from "@/utils/formatters";
import type { OutstandingInvoicesSummary } from "@/types/invoice";

/**
 * "Falta pagar X" ao lado do filtro "Por liquidar" — a soma sobre a lista
 * inteira do filtro, não só a página (pedido do utilizador a 2026-09-21). As
 * faturas ainda sem total contam como por liquidar mas ninguém sabe quanto
 * valem, por isso ficam avisadas em vez de somadas a zero em silêncio.
 */
export const OutstandingTotalBadge: FC<{ summary: OutstandingInvoicesSummary | null }> = ({ summary }) => {
  if (!summary) return null;

  const unknown = summary.withoutTotalCount;
  return (
    <span style={{ display: "inline-flex", alignItems: "baseline", gap: 6, fontSize: 13 }}>
      <span style={{ opacity: 0.6 }}>Falta pagar</span>
      <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, color: "var(--ind-accent-700)" }}>
        {formatCurrency(summary.total)}
      </span>
      <span style={{ opacity: 0.6 }}>
        em {summary.count} fatura{summary.count === 1 ? "" : "s"}
      </span>
      {unknown > 0 && (
        <Tooltip title="Faturas por liquidar que ainda não têm total — não entram na soma até serem corrigidas.">
          <span className="ind-tag ind-tag-outline" style={{ fontSize: 10, cursor: "help" }}>
            +{unknown} sem total
          </span>
        </Tooltip>
      )}
    </span>
  );
};

export default OutstandingTotalBadge;
