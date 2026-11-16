import type { FC } from "react";
import { Tooltip } from "antd";

import { formatCurrency } from "@/utils/formatters";
import type { InvoiceTotalsSummary } from "@/types/invoice";

const plural = (n: number) => `${n} fatura${n === 1 ? "" : "s"}`;

/**
 * "Total · Pago · A pagar" ao filtrar por fornecedor (pedido do utilizador a
 * 2026-11-16) — número e soma sobre a lista inteira do filtro, não só a página.
 * O "Pago" inclui o já pago das parciais, para as três somas baterem
 * (total = pago + a pagar); a contagem do "Pago" é das pagas por inteiro.
 */
export const InvoiceTotalsBadge: FC<{
  summary: InvoiceTotalsSummary | null;
  /** Lista só de por liquidar: o "Pago" seria sempre 0 (ou só o das parciais) e sai (pedido de 2026-11-16). */
  hidePaid?: boolean;
}> = ({ summary, hidePaid = false }) => {
  if (!summary) return null;

  const item = (label: string, amount: number, count: number, color?: string) => (
    <span style={{ display: "inline-flex", alignItems: "baseline", gap: 6 }}>
      <span style={{ opacity: 0.6 }}>{label}</span>
      <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, color }}>{formatCurrency(amount)}</span>
      <span style={{ opacity: 0.6 }}>({plural(count)})</span>
    </span>
  );

  return (
    <span style={{ display: "inline-flex", flexWrap: "wrap", alignItems: "baseline", gap: 14, fontSize: 13 }}>
      {item("Total", summary.total, summary.count)}
      {!hidePaid && (
        <Tooltip title="Soma tudo o que já foi pago, incluindo a parte paga das faturas parciais. O número é das faturas liquidadas — pagas por inteiro, ou anuladas por nota de crédito.">
          <span style={{ cursor: "help" }}>{item("Pago", summary.paid, summary.paidCount)}</span>
        </Tooltip>
      )}
      {item("A pagar", summary.outstanding, summary.outstandingCount, "var(--ind-accent-700)")}
      {summary.withoutTotalCount > 0 && (
        <Tooltip title="Faturas que ainda não têm total — contam como a pagar mas não entram nas somas até serem corrigidas.">
          <span className="ind-tag ind-tag-outline" style={{ fontSize: 10, cursor: "help" }}>
            +{summary.withoutTotalCount} sem total
          </span>
        </Tooltip>
      )}
    </span>
  );
};

export default InvoiceTotalsBadge;
