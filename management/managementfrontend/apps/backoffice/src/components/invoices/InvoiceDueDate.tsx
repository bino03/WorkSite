import { useState } from "react";
import type { FC } from "react";
import { Button, DatePicker, Tooltip } from "antd";
import dayjs from "dayjs";

import { setInvoiceDueDate } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatDate } from "@/utils/formatters";
import type { ConstructionInvoice, InvoiceDueDateBasis } from "@/types/invoice";

/**
 * Porque é que a fatura não tem vencimento. Cada causa resolve-se num sítio
 * diferente, e "sem prazo" sozinho deixava a adivinhar qual.
 */
const MISSING_REASON: Partial<Record<InvoiceDueDateBasis, string>> = {
  NO_SUPPLIER_NIF: "A fatura não tem NIF do fornecedor — escreva o vencimento à mão no detalhe.",
  SUPPLIER_NOT_IN_CATALOG: "O NIF desta fatura ainda não está no catálogo de fornecedores.",
  NO_PAYMENT_TERM: "O fornecedor não tem prazo de pagamento definido.",
  NO_INVOICE_DATE: "A fatura não tem data de onde contar o prazo.",
};

/** Uma fatura paga já não vence, e uma nota de crédito nunca venceu. */
export function isOverdue(invoice: ConstructionInvoice): boolean {
  return (
    invoice.dueDate != null &&
    invoice.paymentStatus !== "PAID" &&
    dayjs(invoice.dueDate).isBefore(dayjs(), "day")
  );
}

/** O vencimento de uma linha da lista: a data, "vencida" quando passou, ou "sem prazo" com o porquê. */
export const InvoiceDueDateCell: FC<{ invoice: ConstructionInvoice }> = ({ invoice }) => {
  if (invoice.dueDateBasis === "NOT_APPLICABLE") {
    return <span style={{ opacity: 0.4 }}>—</span>;
  }
  if (invoice.dueDate == null) {
    return (
      <Tooltip title={MISSING_REASON[invoice.dueDateBasis]}>
        <span className="ind-tag ind-tag-neutral">sem prazo</span>
      </Tooltip>
    );
  }
  const overdue = isOverdue(invoice);
  return (
    <Tooltip
      title={
        invoice.dueDateBasis === "MANUAL"
          ? "Vencimento escrito à mão nesta fatura."
          : "Data da fatura + prazo do fornecedor."
      }
    >
      <span style={{ display: "inline-flex", gap: 6, alignItems: "center", whiteSpace: "nowrap" }}>
        <span style={{ opacity: invoice.paymentStatus === "PAID" ? 0.5 : 1 }}>
          {formatDate(invoice.dueDate)}
        </span>
        {overdue && <span className="ind-tag ind-tag-accent" style={{ fontSize: 10 }}>vencida</span>}
      </span>
    </Tooltip>
  );
};

interface FieldProps {
  invoice: ConstructionInvoice;
  editable: boolean;
  onChanged: () => void;
}

/**
 * O vencimento no detalhe da fatura. Grava-se sozinho, fora do "Guardar" do
 * formulário: é um PATCH próprio, para a correção dos outros campos nunca o
 * apagar por omissão.
 */
export const InvoiceDueDateField: FC<FieldProps> = ({ invoice, editable, onChanged }) => {
  const [saving, setSaving] = useState(false);

  if (invoice.dueDateBasis === "NOT_APPLICABLE") {
    return null;
  }

  const save = async (dueDate: string | null) => {
    setSaving(true);
    try {
      await setInvoiceDueDate(invoice.id, dueDate);
      notificationService.success(
        "Vencimento",
        dueDate ? "Vencimento guardado." : "Vencimento manual retirado."
      );
      onChanged();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 12 }}>
        <span style={{ opacity: 0.75 }}>Vencimento:</span>
        {editable ? (
          <DatePicker
            size="small"
            format="DD/MM/YYYY"
            placeholder="sem prazo"
            aria-label="Vencimento da fatura"
            allowClear={false}
            disabled={saving}
            value={invoice.dueDate ? dayjs(invoice.dueDate) : null}
            onChange={(date) => date && save(date.format("YYYY-MM-DD"))}
          />
        ) : (
          <strong>{invoice.dueDate ? formatDate(invoice.dueDate) : "sem prazo"}</strong>
        )}
        {isOverdue(invoice) && <span className="ind-tag ind-tag-accent">vencida</span>}
        {editable && invoice.dueDateOverride && (
          <Button type="link" size="small" disabled={saving} onClick={() => save(null)}>
            Voltar ao prazo do fornecedor
          </Button>
        )}
      </div>
      <div style={{ fontSize: 11, opacity: 0.6 }}>
        {invoice.dueDateBasis === "MANUAL" && "Escrito à mão nesta fatura."}
        {invoice.dueDateBasis === "SUPPLIER_TERM" && "Data da fatura + prazo do fornecedor."}
        {MISSING_REASON[invoice.dueDateBasis]}
      </div>
    </div>
  );
};
