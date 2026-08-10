import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Input } from "antd";
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";

import {
  allocateInvoice,
  deallocateInvoice,
  deleteInvoice,
  listInvoices,
} from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatCurrency } from "@/utils/formatters";
import { DEFAULT_PAGE_SIZE } from "@/config/pagination";
import { InvoicesList } from "@/components/invoices/InvoicesList";
import { InvoiceUploadDrawer } from "@/components/invoices/InvoiceUploadDrawer";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import { BudgetItemPickerModal } from "@/components/invoices/BudgetItemPickerModal";
import type { ConstructionInvoice, InvoiceFilters } from "@/types/invoice";

/** Os filtros que se usam de facto — cada um responde a uma pergunta concreta. */
const VIEWS = [
  { key: "pending", label: "Por associar", allocated: false, needsReview: null },
  { key: "review", label: "Por rever", allocated: null, needsReview: true },
  { key: "allocated", label: "Associadas", allocated: true, needsReview: null },
  { key: "all", label: "Todas", allocated: null, needsReview: null },
] as const;

type ViewKey = (typeof VIEWS)[number]["key"];

const initialFilters: InvoiceFilters = {
  allocated: false,
  needsReview: null,
  sentToAccountant: null,
  from: null,
  to: null,
  q: "",
  page: 0,
  size: DEFAULT_PAGE_SIZE,
};

/**
 * Caixa de entrada de faturas do projeto.
 *
 * Abre em "Por associar" porque é essa a pergunta do dia-a-dia: o que entrou e
 * ainda não foi classificado. Carregar não pede nada além dos ficheiros;
 * associar é uma ação da lista, feita depois e em bloco quando dá.
 */
const EnterpriseInvoicesPage: FC = () => {
  const { enterpriseId } = useParams<{ enterpriseId: string }>();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  const [invoices, setInvoices] = useState<ConstructionInvoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [filters, setFilters] = useState<InvoiceFilters>(initialFilters);
  const [view, setView] = useState<ViewKey>("pending");

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  /** Faturas a associar na próxima escolha de rubrica — uma ou várias. */
  const [allocating, setAllocating] = useState<ConstructionInvoice[]>([]);
  const [saving, setSaving] = useState(false);

  const fetch = useCallback(
    async (next: InvoiceFilters) => {
      if (!enterpriseId) return;
      setLoading(true);
      try {
        const page = await listInvoices(enterpriseId, next);
        setInvoices(page.content);
        setTotalElements(page.totalElements);
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setLoading(false);
      }
    },
    [enterpriseId]
  );

  useEffect(() => {
    fetch(filters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enterpriseId]);

  /** Qualquer mudança de filtro volta à primeira página — senão fica-se num vazio. */
  const applyFilters = (changes: Partial<InvoiceFilters>) => {
    const next = { ...filters, ...changes, page: 0 };
    setFilters(next);
    setSelectedIds([]);
    fetch(next);
  };

  const reload = () => {
    setSelectedIds([]);
    fetch(filters);
  };

  const handleView = (invoice: ConstructionInvoice) => setDetailId(invoice.id);

  const handleAllocate = async (budgetItemId: string) => {
    setSaving(true);
    try {
      // Sequencial e não em paralelo: são poucas, e um erro a meio deixa as
      // anteriores gravadas em vez de um estado indefinido.
      for (const invoice of allocating) {
        await allocateInvoice(invoice.id, budgetItemId);
      }
      notificationService.success(
        "Faturas",
        allocating.length === 1
          ? "Fatura associada à rubrica."
          : `${allocating.length} faturas associadas à rubrica.`
      );
      setAllocating([]);
      reload();
    } catch (error) {
      ErrorHandler.handle(error);
      reload();
    } finally {
      setSaving(false);
    }
  };

  const handleDeallocate = (invoice: ConstructionInvoice) => {
    confirm({
      message: `Desassociar esta fatura da rubrica "${invoice.budgetItemName}"? O lançamento de ${formatCurrency(
        invoice.totalAmount ?? 0
      )} é removido do orçamento e a fatura volta à caixa de entrada.`,
      onConfirm: async () => {
        try {
          await deallocateInvoice(invoice.id);
          notificationService.success("Faturas", "Fatura devolvida à caixa de entrada.");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const handleDelete = (invoice: ConstructionInvoice) => {
    const label = invoice.invoiceNumber ?? invoice.originalFilename ?? "esta fatura";
    confirm({
      message: invoice.allocated
        ? `Eliminar ${label}? Está associada à rubrica "${invoice.budgetItemName}" — o lançamento de ${formatCurrency(
            invoice.totalAmount ?? 0
          )} também é removido do orçamento. Esta ação não pode ser desfeita.`
        : `Eliminar ${label}? O documento é apagado do arquivo. Esta ação não pode ser desfeita.`,
      onConfirm: async () => {
        try {
          await deleteInvoice(invoice.id);
          notificationService.success("Faturas", "Fatura eliminada.");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const selectedInvoices = invoices.filter((i) => selectedIds.includes(i.id));

  return (
    <div>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        style={{ paddingLeft: 0, marginBottom: "10.2px" }}
        onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/budget`)}
      >
        Voltar ao orçamento
      </Button>

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-end",
          marginBottom: "20.4px",
        }}
      >
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Obra</h6>
          <h1 style={{ margin: 0 }}>Faturas</h1>
        </div>
        {isAdmin() && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setUploadOpen(true)}>
            Carregar faturas
          </Button>
        )}
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
        <div style={{ display: "flex", gap: 6 }}>
          {VIEWS.map((option) => (
            <Button
              key={option.key}
              size="small"
              type={view === option.key ? "primary" : "default"}
              onClick={() => {
                setView(option.key);
                applyFilters({ allocated: option.allocated, needsReview: option.needsReview });
              }}
            >
              {option.label}
            </Button>
          ))}
        </div>

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

        {selectedIds.length > 0 && (
          <Button type="primary" onClick={() => setAllocating(selectedInvoices)}>
            Associar {selectedIds.length} à mesma rubrica
          </Button>
        )}
      </div>

      <InvoicesList
        invoices={invoices}
        loading={loading}
        pagination={{
          currentPage: filters.page,
          totalElements,
          pageSize: filters.size,
        }}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        onPageChange={(page, size) => {
          const next = { ...filters, page, size };
          setFilters(next);
          fetch(next);
        }}
        onView={handleView}
        onAllocate={(invoice) => setAllocating([invoice])}
        onDeallocate={handleDeallocate}
        onDelete={handleDelete}
      />

      {enterpriseId && (
        <>
          <InvoiceUploadDrawer
            open={uploadOpen}
            enterpriseId={enterpriseId}
            onClose={() => setUploadOpen(false)}
            onUploaded={reload}
          />

          <InvoiceDetailDrawer
            invoiceId={detailId}
            open={!!detailId}
            onClose={() => setDetailId(null)}
            onChanged={reload}
            onAllocate={(invoice) => {
              setDetailId(null);
              setAllocating([invoice]);
            }}
          />

          <BudgetItemPickerModal
            open={allocating.length > 0}
            enterpriseId={enterpriseId}
            count={allocating.length}
            // Só sugere com um fornecedor único; misturar NIFs daria uma
            // sugestão que só serviria a parte da seleção.
            supplierNif={
              new Set(allocating.map((i) => i.supplierNif)).size === 1
                ? allocating[0]?.supplierNif
                : null
            }
            saving={saving}
            onClose={() => setAllocating([])}
            onPick={(item) => handleAllocate(item.id)}
          />
        </>
      )}
    </div>
  );
};

export default EnterpriseInvoicesPage;
