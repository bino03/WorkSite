import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Select, Tooltip } from "antd";

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
import { useIsMobile } from "@/hooks/useIsMobile";
import {
  ALL_WORKS,
  SupplierFilterSelect,
  WorkFilterSelect,
  type WorkFilter,
} from "@/components/invoices/InvoiceFilterSelects";
import type { ConstructionInvoice, DueBucket, OutstandingByUrgency } from "@/types/invoice";

/**
 * "A pagar": as faturas por liquidar de todas as obras e da empresa, pela
 * ordem em que convém pagá-las — vencidas primeiro, depois as que vencem mais
 * cedo, e as sem vencimento no fim.
 *
 * A ordem vem do servidor e não se muda aqui: é a razão de ser da página. Os
 * filtros (obra, fornecedor, vencimento, aprovação, contabilidade — 2026-11-10)
 * estreitam a lista sem mudar a ordem, e os números do topo passam a ser do
 * filtrado: "quanto devo à Brivel, e quanto disso está vencido?". Ficam só na
 * página — saindo, perdem-se, como nas outras listas.
 *
 * Só `ADMIN` e `ACCOUNTANT` — o gate real é o `@PreAuthorize` do backend.
 */
const OutstandingInvoicesPage: FC = () => {
  const { canManageWorks } = useAuth();
  const confirm = useConfirm();
  const isMobile = useIsMobile();

  const [data, setData] = useState<OutstandingByUrgency | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [filters, setFilters] = useState<OutstandingFilters>(NO_FILTERS);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [detailId, setDetailId] = useState<string | null>(null);
  const [payInvoices, setPayInvoices] = useState<ConstructionInvoice[] | null>(null);
  const [noteInvoice, setNoteInvoice] = useState<ConstructionInvoice | null>(null);
  const [previewInvoice, setPreviewInvoice] = useState<ConstructionInvoice | null>(null);

  const fetch = useCallback(async (nextPage: number, nextSize: number, current: OutstandingFilters) => {
    setLoading(true);
    try {
      setData(
        await listOutstandingByUrgency({
          enterpriseId: current.work.enterpriseId ?? undefined,
          company: current.work.scope === "COMPANY" ? true : undefined,
          supplierNif: current.supplierNif ?? undefined,
          dueBucket: current.dueBucket ?? undefined,
          approvedForPayment: toBoolean(current.approval),
          sentToAccountant: toBoolean(current.accountant),
          page: nextPage,
          size: nextSize,
        })
      );
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
    void fetch(0, DEFAULT_PAGE_SIZE, NO_FILTERS);
  }, [fetch]);

  const reload = useCallback(() => void fetch(page, pageSize, filters), [fetch, page, pageSize, filters]);

  /** Um filtro novo volta à primeira página: a página 3 do filtrado pode nem existir. */
  const changeFilters = (changes: Partial<OutstandingFilters>) => {
    const next = { ...filters, ...changes };
    setFilters(next);
    setPage(0);
    void fetch(0, pageSize, next);
  };

  const filtered = hasAnyFilter(filters);

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

      <div style={{ display: "flex", gap: "10.2px", alignItems: "center", flexWrap: "wrap", marginBottom: "13.6px" }}>
        <WorkFilterSelect
          size="middle"
          value={filters.work}
          onChange={(work) => changeFilters({ work })}
          style={{ width: isMobile ? "100%" : 220 }}
        />
        <SupplierFilterSelect
          size="middle"
          options={data?.suppliers ?? []}
          value={filters.supplierNif}
          onChange={(supplierNif) => changeFilters({ supplierNif })}
          style={{ width: isMobile ? "100%" : 240 }}
        />
        <Select
          allowClear
          placeholder="Vencimento"
          value={filters.dueBucket ?? undefined}
          onChange={(dueBucket?: DueBucket) => changeFilters({ dueBucket: dueBucket ?? null })}
          options={DUE_OPTIONS}
          style={{ width: isMobile ? "100%" : 170 }}
        />
        <Select
          allowClear
          placeholder="Aprovação"
          value={filters.approval ?? undefined}
          onChange={(approval?: YesNo) => changeFilters({ approval: approval ?? null })}
          options={APPROVAL_OPTIONS}
          style={{ width: isMobile ? "100%" : 170 }}
        />
        <Select
          allowClear
          placeholder="Contabilidade"
          value={filters.accountant ?? undefined}
          onChange={(accountant?: YesNo) => changeFilters({ accountant: accountant ?? null })}
          options={ACCOUNTANT_OPTIONS}
          style={{ width: isMobile ? "100%" : 170 }}
        />
        {filtered && (
          <Button type="link" size="small" onClick={() => changeFilters(NO_FILTERS)}>
            Limpar filtros
          </Button>
        )}
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

      {canManageWorks() && selected.length > 0 && (
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
        <p style={{ fontSize: 12, opacity: 0.6 }}>
          {filtered ? "Nenhuma fatura por liquidar com estes filtros." : "Não há faturas por liquidar."}
        </p>
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
          void fetch(nextPage, nextSize, filters);
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

type YesNo = "YES" | "NO";

interface OutstandingFilters {
  /** Só obra ou "Despesas da empresa": a quarentena não entra nesta lista. */
  work: WorkFilter;
  supplierNif: string | null;
  dueBucket: DueBucket | null;
  approval: YesNo | null;
  accountant: YesNo | null;
}

const NO_FILTERS: OutstandingFilters = {
  work: ALL_WORKS,
  supplierNif: null,
  dueBucket: null,
  approval: null,
  accountant: null,
};

function hasAnyFilter(filters: OutstandingFilters): boolean {
  return (
    filters.work.enterpriseId !== null ||
    filters.work.scope !== null ||
    filters.supplierNif !== null ||
    filters.dueBucket !== null ||
    filters.approval !== null ||
    filters.accountant !== null
  );
}

const toBoolean = (value: YesNo | null): boolean | undefined =>
  value === null ? undefined : value === "YES";

/** Cumulativas: "Vence este mês" inclui a semana; nenhuma inclui as vencidas. */
const DUE_OPTIONS: { value: DueBucket; label: string }[] = [
  { value: "OVERDUE", label: "Vencidas" },
  { value: "WEEK", label: "Vence em 7 dias" },
  { value: "MONTH", label: "Vence em 30 dias" },
  { value: "NONE", label: "Sem prazo" },
];

const APPROVAL_OPTIONS: { value: YesNo; label: string }[] = [
  { value: "YES", label: "Aprovadas" },
  { value: "NO", label: "Por aprovar" },
];

const ACCOUNTANT_OPTIONS: { value: YesNo; label: string }[] = [
  { value: "YES", label: "Enviadas à contabilidade" },
  { value: "NO", label: "Por enviar" },
];

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
