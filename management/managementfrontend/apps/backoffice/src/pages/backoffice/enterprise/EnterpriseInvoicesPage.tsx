import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Input } from "antd";
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";

import {
  allocateInvoice,
  deallocateInvoice,
  deleteInvoice,
  listInvoices,
  getInvoice,
  setInvoiceSentToAccountant,
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
import { suggestInvoiceType } from "@/components/invoices/invoiceNumber";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
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
  const [previewInvoiceId, setPreviewInvoiceId] = useState<string | null>(null);
  const [previewInvoice, setPreviewInvoice] = useState<ConstructionInvoice | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
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

  const handleImageClick = async (invoiceId: string) => {
    setPreviewInvoiceId(invoiceId);
    setPreviewLoading(true);
    try {
      const invoice = await getInvoice(invoiceId);
      setPreviewInvoice(invoice);
    } catch (error) {
      ErrorHandler.handle(error);
      setPreviewInvoiceId(null);
    } finally {
      setPreviewLoading(false);
    }
  };

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

  /** Marca todas as selecionadas de uma vez — só marca, nunca desmarca em bloco. */
  const handleBulkSendToAccountant = () => {
    const targets = selectedInvoices;
    confirm({
      title: "Contabilidade",
      message: `Marcar ${targets.length} fatura${targets.length > 1 ? "s" : ""} como enviada${
        targets.length > 1 ? "s" : ""
      } para a contabilidade?`,
      actionLabel: "Marcar como enviadas",
      onConfirm: async () => {
        try {
          // Sequencial, como o handleAllocate: um erro a meio deixa as
          // anteriores marcadas em vez de um estado indefinido.
          for (const invoice of targets) {
            await setInvoiceSentToAccountant(invoice.id, true);
          }
          notificationService.success(
            "Contabilidade",
            targets.length === 1
              ? "Fatura marcada como enviada."
              : `${targets.length} faturas marcadas como enviadas.`
          );
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
          reload();
        }
      },
    });
  };

  const handleSendToAccountant = (invoice: ConstructionInvoice) => {
    const action = invoice.sentToAccountant ? "desmarcar como enviada" : "marcar como enviada";
    confirm({
      message: `${action.charAt(0).toUpperCase() + action.slice(1)} para a contabilidade?`,
      onConfirm: async () => {
        try {
          await setInvoiceSentToAccountant(invoice.id, !invoice.sentToAccountant);
          notificationService.success(
            "Contabilidade",
            invoice.sentToAccountant ? "Marcada como por enviar." : "Marcada como enviada."
          );
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const selectedInvoices = invoices.filter((i) => selectedIds.includes(i.id));
  const suggestedInvoiceType = useMemo(() => suggestInvoiceType(invoices), [invoices]);

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
        {selectedIds.length > 0 && isAdmin() && (
          <Button onClick={handleBulkSendToAccountant}>
            Marcar {selectedIds.length} como enviada{selectedIds.length > 1 ? "s" : ""} à contabilidade
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
        onImageClick={handleImageClick}
        onAllocate={(invoice) => setAllocating([invoice])}
        onDeallocate={handleDeallocate}
        onSendToAccountant={handleSendToAccountant}
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
            // Preencher à mão o "FR"/"FT" de cada fatura é escrita repetida sem
            // ganho nenhum: a drawer já abre com o tipo mais usado nesta obra.
            suggestedInvoiceType={suggestedInvoiceType}
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

          <InvoicePreviewModal
            open={!!previewInvoiceId && !previewLoading}
            onClose={() => {
              setPreviewInvoiceId(null);
              setPreviewInvoice(null);
            }}
            invoiceUrl={previewInvoice?.fileUrl ?? null}
            mimeType={previewInvoice?.mimeType ?? null}
            filename={previewInvoice?.originalFilename ?? null}
          />
        </>
      )}
    </div>
  );
};

export default EnterpriseInvoicesPage;
