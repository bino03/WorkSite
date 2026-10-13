import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Tooltip } from "antd";

import {
  deleteInvoice,
  getInvoice,
  listOutstandingByUrgency,
  setInvoiceSentToAccountant,
} from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { DEFAULT_PAGE_SIZE } from "@/config/pagination";
import { InvoicesList } from "@/components/invoices/InvoicesList";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import AggregatePaymentDrawer from "@/components/invoices/AggregatePaymentDrawer";
import { InvoiceNoteModal } from "@/components/invoices/InvoiceNoteModal";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import { useQuickMarkPaid } from "@/hooks/useQuickMarkPaid";
import { canApproveForPayment, useInvoicePaymentApproval } from "@/hooks/useInvoicePaymentApproval";
import { SUPPLIERS_CHANGED_EVENT } from "@/components/suppliers/SuppliersDrawer";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, OutstandingByUrgency } from "@/types/invoice";

/**
 * "A pagar": as faturas por liquidar de todas as obras e da empresa, pela
 * ordem em que convém pagá-las — vencidas primeiro, depois as que vencem mais
 * cedo, e as sem vencimento no fim.
 *
 * A ordem vem do servidor e não se muda aqui: é a razão de ser da página. Para
 * procurar ou filtrar uma fatura há as listas de cada obra.
 *
 * Só `ADMIN` — o gate real é o `@PreAuthorize` do backend.
 */
const OutstandingInvoicesPage: FC = () => {
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  const [data, setData] = useState<OutstandingByUrgency | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [detailId, setDetailId] = useState<string | null>(null);
  const [payInvoices, setPayInvoices] = useState<ConstructionInvoice[] | null>(null);
  const [noteInvoice, setNoteInvoice] = useState<ConstructionInvoice | null>(null);
  const [previewInvoice, setPreviewInvoice] = useState<ConstructionInvoice | null>(null);

  const fetch = useCallback(async (nextPage: number, nextSize: number) => {
    setLoading(true);
    try {
      setData(await listOutstandingByUrgency({ page: nextPage, size: nextSize }));
      // Pagar ou aprovar tira linhas da página: uma seleção antiga apontaria
      // para faturas que já cá não estão.
      setSelectedIds([]);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetch(0, DEFAULT_PAGE_SIZE);
  }, [fetch]);

  const reload = useCallback(() => void fetch(page, pageSize), [fetch, page, pageSize]);

  // O prazo de um fornecedor muda o vencimento — e portanto a ordem desta lista.
  useEffect(() => {
    window.addEventListener(SUPPLIERS_CHANGED_EVENT, reload);
    return () => window.removeEventListener(SUPPLIERS_CHANGED_EVENT, reload);
  }, [reload]);

  const markPaid = useQuickMarkPaid(reload);
  const paymentApproval = useInvoicePaymentApproval(reload);

  const handleOpenPreview = async (invoiceId: string) => {
    try {
      // A lista só traz a miniatura; o link assinado do documento vem do detalhe.
      setPreviewInvoice(await getInvoice(invoiceId));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const handleSendToAccountant = (invoice: ConstructionInvoice) => {
    confirm({
      title: "Contabilidade",
      message: invoice.sentToAccountant
        ? "Desmarcar esta fatura como enviada à contabilidade?"
        : "Marcar esta fatura como enviada à contabilidade?",
      actionLabel: invoice.sentToAccountant ? "Desmarcar" : "Marcar como enviada",
      onConfirm: async () => {
        try {
          await setInvoiceSentToAccountant(invoice.id, !invoice.sentToAccountant);
          notificationService.success("Contabilidade atualizada");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const handleDelete = (invoice: ConstructionInvoice) => {
    confirm({
      message: `Eliminar esta fatura${invoice.invoiceNumber ? ` (${invoice.invoiceNumber})` : ""}? Os documentos vão atrás.`,
      onConfirm: async () => {
        try {
          await deleteInvoice(invoice.id);
          notificationService.success("Fatura eliminada");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const invoices = data?.invoices ?? [];
  const selected = invoices.filter((invoice) => selectedIds.includes(invoice.id));
  const approvableSelected = selected.filter(canApproveForPayment);
  const payableSelected = selected.filter(
    (invoice) => invoice.netAmount != null && invoice.paidAmount < invoice.netAmount
  );
  // Um pagamento agregado é de uma obra só (ou só de despesas da empresa): o
  // backend recusa a mistura com INVOICE_PAYMENT_CROSS_ENTERPRISE, e aqui é
  // onde ela é mais fácil de fazer sem querer.
  const sameOwner = new Set(payableSelected.map((invoice) => invoice.enterpriseId ?? "company")).size <= 1;

  return (
    <div>
      <div style={{ marginBottom: "20.4px" }}>
        <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
        <h1 style={{ margin: 0 }}>A pagar</h1>
        <p style={{ fontSize: 13, opacity: 0.7, margin: "6.8px 0 0", maxWidth: 720 }}>
          As faturas por liquidar de todas as obras e da empresa, pela ordem do vencimento: as vencidas
          primeiro, as sem prazo no fim. O vencimento vem do prazo do fornecedor (menu → Fornecedores)
          ou do que estiver escrito à mão na fatura.
        </p>
      </div>

      {data && (
        <div
          style={{
            display: "flex",
            gap: "20.4px",
            flexWrap: "wrap",
            alignItems: "baseline",
            marginBottom: "13.6px",
            fontSize: 13,
          }}
        >
          <Figure label="Falta pagar" value={formatCurrency(data.total)} detail={countLabel(data.totalElements)} />
          <Figure
            label="Vencido"
            value={formatCurrency(data.overdueTotal)}
            detail={countLabel(data.overdueCount)}
            strong={data.overdueCount > 0}
          />
          <Figure label="Sem prazo" value={String(data.withoutDueDateCount)} detail="no fim da lista" />
        </div>
      )}

      {isAdmin() && selected.length > 0 && (
        <div style={{ display: "flex", gap: "10.2px", flexWrap: "wrap", marginBottom: "13.6px" }}>
          {approvableSelected.length > 0 && (
            <Button onClick={() => paymentApproval.approveMany(approvableSelected)}>
              Aprovar {approvableSelected.length} para pagamento
            </Button>
          )}
          {payableSelected.length > 0 && (
            <Tooltip
              title={
                sameOwner
                  ? undefined
                  : "Um pagamento agregado é de uma obra só. Selecione faturas da mesma obra, ou pague uma a uma."
              }
            >
              <Button disabled={!sameOwner} onClick={() => setPayInvoices(payableSelected)}>
                Registar pagamento de {payableSelected.length}
              </Button>
            </Tooltip>
          )}
        </div>
      )}

      {!loading && data && invoices.length === 0 && (
        <p style={{ fontSize: 12, opacity: 0.6 }}>Não há faturas por liquidar.</p>
      )}

      <InvoicesList
        invoices={invoices}
        loading={loading}
        // Linhas de obras diferentes e da empresa: sem coluna de rubrica nem
        // "Associar", que só fazem sentido dentro de uma obra.
        scope="COMPANY"
        enterpriseNames={data?.enterpriseNames ?? {}}
        pagination={{ currentPage: page, totalElements: data?.totalElements ?? 0, pageSize }}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onPageChange={(nextPage, nextSize) => {
          setPage(nextPage);
          setPageSize(nextSize);
          void fetch(nextPage, nextSize);
        }}
        onView={(invoice) => setDetailId(invoice.id)}
        onImageClick={(invoiceId) => void handleOpenPreview(invoiceId)}
        onAllocate={() => undefined}
        onDeallocate={() => undefined}
        onSendToAccountant={handleSendToAccountant}
        onDelete={handleDelete}
        onRegisterPayment={(invoice) => setPayInvoices([invoice])}
        onTogglePaymentApproval={paymentApproval.toggle}
        onMarkPaid={markPaid}
        onAddNote={setNoteInvoice}
      />

      <InvoiceDetailDrawer
        invoiceId={detailId}
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        onChanged={reload}
        onAllocate={() => undefined}
      />

      <AggregatePaymentDrawer
        open={payInvoices !== null}
        invoices={payInvoices ?? []}
        onClose={() => setPayInvoices(null)}
        onDone={reload}
      />

      <InvoiceNoteModal invoice={noteInvoice} onClose={() => setNoteInvoice(null)} onSaved={reload} />

      <InvoicePreviewModal
        open={previewInvoice !== null}
        onClose={() => setPreviewInvoice(null)}
        invoiceUrl={previewInvoice?.fileUrl ?? null}
        mimeType={previewInvoice?.mimeType ?? null}
        filename={previewInvoice?.originalFilename ?? null}
      />
    </div>
  );
};

const countLabel = (count: number) => `em ${count} fatura${count === 1 ? "" : "s"}`;

/** Um número do resumo, no mesmo desenho do "Falta pagar" das outras listas. */
const Figure: FC<{ label: string; value: string; detail: string; strong?: boolean }> = ({
  label,
  value,
  detail,
  strong = false,
}) => (
  <span style={{ display: "inline-flex", alignItems: "baseline", gap: 6 }}>
    <span style={{ opacity: 0.6 }}>{label}</span>
    <span
      style={{
        fontFamily: "var(--ind-font-heading)",
        fontWeight: 600,
        color: strong ? "var(--ind-accent-700)" : undefined,
      }}
    >
      {value}
    </span>
    <span style={{ opacity: 0.6 }}>{detail}</span>
  </span>
);

export default OutstandingInvoicesPage;
