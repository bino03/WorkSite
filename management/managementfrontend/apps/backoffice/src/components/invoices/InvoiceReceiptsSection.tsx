import type { FC } from "react";
import { Alert, Button } from "antd";
import { useTranslation } from "react-i18next";

import { formatCurrency, formatDate } from "@/utils/formatters";
import type { InvoiceReceiptSummary } from "@/types/receipt";

interface Props {
  receipts: InvoiceReceiptSummary[];
  canManage: boolean;
  /** Abre outra fatura coberta pelo mesmo recibo. */
  onOpenInvoice: (invoiceId: string) => void;
  onEdit: (receiptId: string) => void;
  onDelete: (receiptId: string) => void;
}

/** Os recibos que cobrem esta fatura. Escondido quando não há nenhum. */
export const InvoiceReceiptsSection: FC<Props> = ({ receipts, canManage, onOpenInvoice, onEdit, onDelete }) => {
  const { t } = useTranslation();
  if (receipts.length === 0) return null;

  return (
    <div className="ind-card" style={{ padding: "13.6px", gap: "10.2px" }}>
      <span className="ind-card-kicker">{t("invoices.receipt.sectionTitle")}</span>

      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {receipts.map((r) => (
          <div
            key={r.receiptId}
            style={{
              border: "1px solid var(--ind-color-divider)",
              borderRadius: 2,
              padding: "8px 10px",
              fontSize: 12,
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
              <span>
                <strong>{r.receiptNumber ?? t("invoices.receipt.noNumber")}</strong> · {formatDate(r.receiptDate)} ·{" "}
                {t("invoices.receipt.total")} {formatCurrency(r.totalAmount)} ·{" "}
                {t("invoices.receipt.forThisInvoice")} <strong>{formatCurrency(r.amountForThisInvoice)}</strong>
              </span>
              {canManage && (
                <span style={{ whiteSpace: "nowrap" }}>
                  <Button size="small" type="link" onClick={() => onEdit(r.receiptId)}>
                    {t("common.edit")}
                  </Button>
                  <Button size="small" type="link" danger onClick={() => onDelete(r.receiptId)}>
                    {t("invoices.receipt.delete")}
                  </Button>
                </span>
              )}
            </div>

            {r.fileUrl && (
              <div style={{ marginTop: 2 }}>
                <a href={r.fileUrl} target="_blank" rel="noreferrer">
                  {r.fileFilename ?? t("invoices.receipt.file")}
                </a>
              </div>
            )}

            {r.alsoCovers.length > 0 && (
              <div style={{ marginTop: 2 }}>
                {t("invoices.receipt.alsoCovers")}:{" "}
                {r.alsoCovers.map((c, idx) => (
                  <span key={c.invoiceId}>
                    {idx > 0 && ", "}
                    <Button
                      type="link"
                      size="small"
                      style={{ padding: 0, height: "auto", fontSize: 12 }}
                      onClick={() => onOpenInvoice(c.invoiceId)}
                    >
                      {c.invoiceNumber ?? "—"}
                    </Button>
                    {c.enterpriseName && <span style={{ opacity: 0.6 }}> ({c.enterpriseName})</span>}
                  </span>
                ))}
              </div>
            )}

            {r.mismatch !== 0 && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 6 }}
                message={t("invoices.receipt.mismatch", { diff: formatCurrency(r.mismatch) })}
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default InvoiceReceiptsSection;
