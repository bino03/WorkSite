import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { Link } from "react-router-dom";
import { Button, Spin } from "antd";
import { CheckCircleOutlined, CheckOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import BlueprintCard from "@/components/common/BlueprintCard";
import { InvoiceDueDateCell } from "@/components/invoices/InvoiceDueDate";
import { PaymentListExportButton } from "@/components/invoices/PaymentListExportButton";
import { listOutstandingByUrgency } from "@/services/invoiceService";
import { markInvoicePaid } from "@/services/paymentService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, OutstandingByUrgency } from "@/types/invoice";

const LINK = "/backoffice/invoices/outstanding?approved=true";
const SHOWN = 5;
const PAGE = 100; // o máximo que o `/outstanding` aceita

/** Todas as aprovadas por liquidar, página a página — o cartão só tem as 5 primeiras. */
async function fetchAllApproved(): Promise<ConstructionInvoice[]> {
  const all: ConstructionInvoice[] = [];
  for (let page = 0; ; page++) {
    const res = await listOutstandingByUrgency({ approvedForPayment: true, page, size: PAGE });
    all.push(...res.invoices);
    if (res.invoices.length < PAGE || all.length >= res.totalElements) return all;
  }
}

/**
 * Página inicial da contabilidade: as faturas aprovadas para pagamento que ainda
 * não estão liquidadas, pela ordem de "A pagar" (vencidas primeiro). É um resumo —
 * pagar uma a uma faz-se em "A pagar", que abre já filtrado por `?approved=true`.
 *
 * Os números do topo são da lista inteira, não só das 5 mostradas.
 *
 * "Marcar como pagas" liquida **todas** as aprovadas (não só as 5): um pagamento
 * por fatura, hoje, por transferência, pelo que falta — o mesmo que o "Marcar como
 * paga" do menu de contexto. As sem total ficam de fora (o backend recusa-as).
 */
export const ApprovedToPayCard: FC = () => {
  const confirm = useConfirm();
  const [data, setData] = useState<OutstandingByUrgency | null>(null);
  const [failed, setFailed] = useState(false);
  const [paying, setPaying] = useState(false);

  const load = useCallback(() => {
    return listOutstandingByUrgency({ approvedForPayment: true, page: 0, size: SHOWN })
      .then(setData)
      .catch(() => setFailed(true));
  }, []);

  useEffect(() => { void load(); }, [load]);

  const payAll = async () => {
    setPaying(true);
    try {
      const invoices = await fetchAllApproved();
      const payable = invoices.filter((i) => i.netAmount != null);
      const paidOn = dayjs().format("YYYY-MM-DD");
      let ok = 0;
      const failedOnes: string[] = [];
      // Em série: uma que falhe não impede as outras, e diz-se qual foi.
      for (const invoice of payable) {
        try {
          await markInvoicePaid(invoice.id, { paidOn, method: "TRANSFERENCIA", amount: null });
          ok++;
        } catch {
          failedOnes.push(invoice.invoiceNumber ?? invoice.supplierName ?? invoice.id);
        }
      }
      const skipped = invoices.length - payable.length;
      if (ok > 0) {
        notificationService.success(`${ok} ${ok === 1 ? "fatura marcada como paga" : "faturas marcadas como pagas"}`);
      }
      if (failedOnes.length > 0) {
        notificationService.error(`Não foi possível marcar ${failedOnes.length}: ${failedOnes.join(", ")}`);
      }
      if (skipped > 0) {
        notificationService.warning(`${skipped} sem total ficaram por pagar — veja-as em "A pagar".`);
      }
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setPaying(false);
      void load();
    }
  };

  const askPayAll = () => {
    if (!data) return;
    const n = data.totalElements;
    confirm({
      title: "Marcar como pagas",
      message:
        `Registar o pagamento ${n === 1 ? "da fatura aprovada" : `das ${n} faturas aprovadas`} ` +
        `(${formatCurrency(data.total)}) hoje, por transferência? Fica um pagamento por fatura, ` +
        `que se pode anular depois em cada uma.`,
      actionLabel: "Marcar como pagas",
      onConfirm: payAll,
    });
  };

  if (failed) return null;

  return (
    <BlueprintCard style={{ padding: "13.6px", marginBottom: "27.2px" }}>
      <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <span className="ind-card-title" style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
          <CheckCircleOutlined style={{ color: "var(--ind-color-accent)" }} />
          Aprovadas para pagamento
        </span>
        {data && data.totalElements > 0 && (
          <span style={{ display: "inline-flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <span className="ind-card-meta">
              {data.totalElements} {data.totalElements === 1 ? "fatura" : "faturas"} · {formatCurrency(data.total)}
              {data.overdueCount > 0 && ` · ${data.overdueCount} vencida${data.overdueCount === 1 ? "" : "s"}`}
            </span>
            {/* Todas as aprovadas, sem filtros — as mesmas que os números ao lado contam. */}
            <PaymentListExportButton size="small" />
            <Button size="small" icon={<CheckOutlined />} loading={paying} onClick={askPayAll}>
              Marcar como pagas
            </Button>
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
