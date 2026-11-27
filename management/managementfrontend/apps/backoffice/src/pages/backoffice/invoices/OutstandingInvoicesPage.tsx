import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { useSearchParams } from "react-router-dom";
import { Badge, Button, Input, Tooltip } from "antd";
import { FilterOutlined, SearchOutlined } from "@ant-design/icons";

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
import ReceiptDrawer from "@/components/invoices/ReceiptDrawer";
import { InvoiceNoteModal } from "@/components/invoices/InvoiceNoteModal";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import { useQuickMarkPaid } from "@/hooks/useQuickMarkPaid";
import { canApproveForPayment, useInvoicePaymentApproval } from "@/hooks/useInvoicePaymentApproval";
import { SUPPLIERS_CHANGED_EVENT } from "@/components/suppliers/SuppliersDrawer";
import { formatCurrency } from "@/utils/formatters";
import { useIsMobile } from "@/hooks/useIsMobile";
import { ALL_WORKS, WorkFilterSelect, type WorkFilter } from "@/components/invoices/InvoiceFilterSelects";
import { InvoiceFiltersModal } from "@/components/invoices/InvoiceFiltersModal";
import { clearedInvoiceFilters, countActiveInvoiceFilters } from "@/components/invoices/invoiceFilters";
import { PaymentListExportButton } from "@/components/invoices/PaymentListExportButton";
import { EMPTY_INVOICE_FILTERS } from "@/types/invoice";
import type {
  ConstructionInvoice,
  InvoiceFilters,
  OutstandingByUrgency,
  OutstandingByUrgencyParams,
} from "@/types/invoice";

/**
 * Os filtros da página como parâmetros de "A pagar" — a lista e a exportação usam
 * os mesmos, para o ficheiro ter o que está no ecrã. Sem `approvedForPayment`:
 * a lista junta-o, a exportação força-o a true.
 */
const outstandingParams = (
  current: InvoiceFilters,
  currentWork: WorkFilter
): Omit<OutstandingByUrgencyParams, "page" | "size" | "approvedForPayment"> => {
  const q = current.q.trim();
  return {
    enterpriseId: currentWork.enterpriseId ?? undefined,
    company: currentWork.scope === "COMPANY" ? true : undefined,
    supplierNif: current.supplierNif ?? undefined,
    dueBucket: current.dueBucket ?? undefined,
    sentToAccountant: current.sentToAccountant ?? undefined,
    q: q || undefined,
    documentStatus: current.documentStatus ?? undefined,
    paymentStatus: current.paymentStatus ?? undefined,
    allocationStatus: current.allocationStatus ?? undefined,
    from: current.from ?? undefined,
    to: current.to ?? undefined,
    minAmount: current.minAmount ?? undefined,
    maxAmount: current.maxAmount ?? undefined,
    // A rubrica é de uma obra; sem obra o backend recusa-a.
    budgetItemId: currentWork.enterpriseId ? (current.budgetItemId ?? undefined) : undefined,
  };
};

/**
 * "A pagar": as faturas por liquidar de todas as obras e da empresa, pela
 * ordem em que convém pagá-las — vencidas primeiro, depois as que vencem mais
 * cedo, e as sem vencimento no fim.
 *
 * A ordem vem do servidor e não se muda aqui: é a razão de ser da página. Os
 * filtros estreitam a lista sem mudar a ordem, e os números do topo passam a ser
 * do filtrado: "quanto devo à Brivel, e quanto disso está vencido?". Ficam só na
 * página — saindo, perdem-se, como nas outras listas.
 *
 * A barra é a da lista de faturas da obra (pedido do utilizador, 2026-11-13):
 * pesquisa de texto, o select de obra no lugar do de lote, e o botão "Filtros"
 * com o mesmo modal (`variant="outstanding"`, que acrescenta vencimento e
 * aprovação). Até aí eram cinco selects soltos.
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
  const [filters, setFilters] = useState<InvoiceFilters>(NO_FILTERS);
  /** Só obra ou "Despesas da empresa": a quarentena não entra nesta lista. */
  const [work, setWork] = useState<WorkFilter>(ALL_WORKS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [detailId, setDetailId] = useState<string | null>(null);
  const [payInvoices, setPayInvoices] = useState<ConstructionInvoice[] | null>(null);
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [noteInvoice, setNoteInvoice] = useState<ConstructionInvoice | null>(null);
  const [previewInvoice, setPreviewInvoice] = useState<ConstructionInvoice | null>(null);

  const fetch = useCallback(
    async (nextPage: number, nextSize: number, current: InvoiceFilters, currentWork: WorkFilter) => {
      setLoading(true);
      try {
        setData(
          await listOutstandingByUrgency({
            ...outstandingParams(current, currentWork),
            approvedForPayment: current.approvedForPayment ?? undefined,
            page: nextPage,
            size: nextSize,
          }),
        );
        // Pagar ou aprovar tira linhas da página: uma seleção antiga apontaria
        // para faturas que já cá não estão.
        setSelectedIds([]);
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  // `?approved=true` abre já nas aprovadas para pagamento — é para onde apontam o
  // aviso `invoice_approved` e o cartão da página inicial. Depende do parâmetro, não
  // só da montagem: clicar no aviso estando já nesta página tem de refiltrar.
  const [searchParams] = useSearchParams();
  const onlyApproved = searchParams.get("approved") === "true";
  useEffect(() => {
    const initial = onlyApproved ? { ...NO_FILTERS, approvedForPayment: true } : NO_FILTERS;
    setFilters(initial);
    setWork(ALL_WORKS);
    setPage(0);
    void fetch(0, DEFAULT_PAGE_SIZE, initial, ALL_WORKS);
  }, [fetch, onlyApproved]);

  const reload = useCallback(() => void fetch(page, pageSize, filters, work), [fetch, page, pageSize, filters, work]);

  /** Um filtro novo volta à primeira página: a página 3 do filtrado pode nem existir. */
  const applyFilters = (changes: Partial<InvoiceFilters>) => {
    const next = { ...filters, ...changes };
    setFilters(next);
    setPage(0);
    void fetch(0, pageSize, next, work);
  };

  /** Trocar de obra larga a rubrica: era da árvore da obra anterior. */
  const changeWork = (nextWork: WorkFilter) => {
    const next = { ...filters, budgetItemId: null, budgetItemLabel: null };
    setWork(nextWork);
    setFilters(next);
    setPage(0);
    void fetch(0, pageSize, next, nextWork);
  };

  const activeFilterCount = countActiveInvoiceFilters(filters);
  const filtered =
    activeFilterCount > 0 || filters.q.trim() !== "" || work.enterpriseId !== null || work.scope !== null;

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
    (invoice) => invoice.netAmount != null && invoice.paidAmount < invoice.netAmount,
  );
  // Um pagamento agregado é de uma obra só (ou só de despesas da empresa): o
  // backend recusa a mistura com INVOICE_PAYMENT_CROSS_ENTERPRISE, e aqui é
  // onde ela é mais fácil de fazer sem querer.
  const sameOwner = new Set(payableSelected.map((invoice) => invoice.enterpriseId ?? "company")).size <= 1;
  /** O recibo aceita pagas, mas não notas de crédito (RECEIPT_005); sem a restrição de obra do pagamento. */
  const receiptSelected = selected.filter((i) => i.documentType !== "CREDIT_NOTE");

  return (
    <div>
      <div style={{ marginBottom: "20.4px" }}>
        <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
        <h1 style={{ margin: 0 }}>A pagar</h1>
        <p
          style={{
            fontSize: 13,
            opacity: 0.7,
            margin: "6.8px 0 0",
            maxWidth: 720,
          }}
        >
          As faturas por liquidar de todas as obras e da empresa, pela ordem do vencimento: as vencidas primeiro, as sem
          prazo no fim. O vencimento vem do prazo do fornecedor (menu → Fornecedores) ou do que estiver escrito à mão na
          fatura.
        </p>
      </div>

      <div
        style={{
          display: "flex",
          gap: "10.2px",
          alignItems: "center",
          flexWrap: "wrap",
          marginBottom: "13.6px",
        }}
      >
        {/* A mesma barra da lista de faturas da obra: texto, o eixo de navegação
            (aqui a obra, lá o lote) fora do modal e do "Limpar filtros", e o resto
            no modal "Filtros" com a contagem. */}
        <Input
          placeholder="Fornecedor, NIF, nº da fatura…"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={filters.q}
          onChange={(e) => setFilters((prev) => ({ ...prev, q: e.target.value }))}
          onPressEnter={() => applyFilters({})}
          allowClear
          onClear={() => applyFilters({ q: "" })}
          style={{ maxWidth: 320 }}
        />
        <Button onClick={() => applyFilters({})}>Pesquisar</Button>

        <WorkFilterSelect size="middle" value={work} onChange={changeWork} style={{ width: isMobile ? "100%" : 220 }} />

        <Badge count={activeFilterCount} size="small" color="var(--ind-color-accent)">
          <Button
            icon={<FilterOutlined />}
            type={activeFilterCount > 0 ? "primary" : "default"}
            onClick={() => setFiltersOpen(true)}
          >
            Filtros
          </Button>
        </Badge>
        {activeFilterCount > 0 && (
          <Button type="text" size="small" onClick={() => applyFilters(clearedInvoiceFilters())}>
            Limpar filtros
          </Button>
        )}

        {/* Só as aprovadas para pagamento, com os filtros que estão no ecrã (o
            "aprovadas" é forçado no backend, seja qual for o filtro). */}
        <div style={{ marginLeft: isMobile ? undefined : "auto" }}>
          <PaymentListExportButton params={outstandingParams(filters, work)} />
        </div>
      </div>

      <InvoiceFiltersModal
        open={filtersOpen}
        variant="outstanding"
        enterpriseId={work.enterpriseId}
        filters={filters}
        onClose={() => setFiltersOpen(false)}
        onApply={applyFilters}
      />

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
        <div
          style={{
            display: "flex",
            gap: "10.2px",
            flexWrap: "wrap",
            marginBottom: "13.6px",
          }}
        >
          {approvableSelected.length > 0 && (
            <Button onClick={() => paymentApproval.approveMany(approvableSelected)}>
              Aprovar {approvableSelected.length} para pagamento
            </Button>
          )}
          {receiptSelected.length > 0 && (
            <Button onClick={() => setReceiptOpen(true)}>Adicionar recibo</Button>
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
        pagination={{
          currentPage: page,
          totalElements: data?.totalElements ?? 0,
          pageSize,
        }}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onPageChange={(nextPage, nextSize) => {
          setPage(nextPage);
          setPageSize(nextSize);
          void fetch(nextPage, nextSize, filters, work);
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

      <ReceiptDrawer
        open={receiptOpen}
        initialInvoices={receiptSelected}
        onClose={() => setReceiptOpen(false)}
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

/** O ponto de partida: nada ligado. `page`/`size` do `InvoiceFilters` não se usam aqui — a página tem os seus. */
const NO_FILTERS: InvoiceFilters = {
  ...EMPTY_INVOICE_FILTERS,
  size: DEFAULT_PAGE_SIZE,
};

const countLabel = (count: number) => `em ${count} fatura${count === 1 ? "" : "s"}`;

/** Um número do resumo, no mesmo desenho do "Falta pagar" das outras listas. */
const Figure: FC<{
  label: string;
  value: string;
  detail: string;
  strong?: boolean;
}> = ({ label, value, detail, strong = false }) => (
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
