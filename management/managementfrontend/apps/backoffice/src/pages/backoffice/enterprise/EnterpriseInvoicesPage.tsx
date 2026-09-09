import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Input, Space } from "antd";
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";

import {
  batchAllocateInvoices,
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
import { InvoiceRegisterDrawer } from "@/components/invoices/InvoiceRegisterDrawer";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import IncidentDrawer from "@/components/invoices/IncidentDrawer";
import { toIncidentInvoiceRef } from "@/components/invoices/toIncidentInvoiceRef";
import AggregatePaymentDrawer from "@/components/invoices/AggregatePaymentDrawer";
import { BudgetItemPickerModal } from "@/components/invoices/BudgetItemPickerModal";
import { suggestInvoiceType } from "@/components/invoices/invoiceNumber";
import { SUPPLIERS_CHANGED_EVENT } from "@/components/suppliers/SuppliersDrawer";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import type { ConstructionInvoice, InvoiceFilters } from "@/types/invoice";
import type { IncidentInvoiceRef } from "@/types/incident";

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
  outstanding: null,
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
  const [registerOpen, setRegisterOpen] = useState(false);
  const [aggregatePayOpen, setAggregatePayOpen] = useState(false);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [incidentInvoices, setIncidentInvoices] = useState<IncidentInvoiceRef[] | null>(null);
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

  // Dar nome a um NIF (drawer de fornecedores, no cabeçalho) reescreve o
  // fornecedor de faturas que estão nesta lista — sem isto os nomes novos só
  // apareciam ao recarregar a página.
  useEffect(() => {
    const reload = () => fetch(filters);
    window.addEventListener(SUPPLIERS_CHANGED_EVENT, reload);
    return () => window.removeEventListener(SUPPLIERS_CHANGED_EVENT, reload);
  }, [fetch, filters]);

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
      // Uma chamada só, e melhor esforço do lado do servidor: era um `for` com
      // um `await` por fatura, que rebentava no primeiro erro e deixava quem
      // estava a ver sem saber quais tinham passado.
      const result = await batchAllocateInvoices(
        allocating.map((invoice) => invoice.id),
        budgetItemId
      );

      if (result.failures.length === 0) {
        notificationService.success(
          "Faturas",
          result.succeeded === 1
            ? "Fatura associada à rubrica."
            : `${result.succeeded} faturas associadas à rubrica.`
        );
      } else {
        // Nem sucesso nem erro: parte passou. Dizer qual falhou e porquê é a
        // única resposta útil — um toast verde esconderia o problema.
        notificationService.warning(
          "Faturas",
          `${result.succeeded} associada(s), ${result.failures.length} não: ` +
            result.failures
              .map((f) => `${f.invoiceNumber ?? "sem número"} (${f.message})`)
              .join("; ")
        );
      }
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
      title: "Desassociar da rubrica",
      message: `Desassociar esta fatura da rubrica "${invoice.budgetItemName}"? O lançamento de ${formatCurrency(
        invoice.totalAmount ?? 0
      )} é removido do orçamento e a fatura volta à caixa de entrada.`,
      actionLabel: "Desassociar",
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
    const targets = sendableSelected;
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
      title: "Contabilidade",
      message: `${action.charAt(0).toUpperCase() + action.slice(1)} para a contabilidade?`,
      actionLabel: invoice.sentToAccountant ? "Desmarcar" : "Marcar como enviada",
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

  /**
   * O checkbox da lista está solto (ver `InvoicesList`): serve três ações com
   * regras opostas — associar só faz sentido no que está **por** associar,
   * pagar e enviar à contabilidade só no que **já** está classificado. Cada
   * botão fica com o seu próprio filtro e mostra a contagem do que lhe serve;
   * quem ficar de fora da seleção simplesmente não conta para esse botão.
   */
  const allocatableSelected = selectedInvoices.filter((i) => !i.allocated && !i.needsReview);
  const payableSelected = selectedInvoices.filter(
    (i) => i.documentType !== "CREDIT_NOTE" && i.netAmount != null && i.paidAmount < i.netAmount
  );
  const sendableSelected = selectedInvoices.filter((i) => !i.sentToAccountant);

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
          <Space>
            {/* A fila de despachar. Fica aqui e não escondida no orçamento
                porque é daqui que se vê quantas estão à espera. */}
            <Button
              onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/classify`)}
            >
              Classificar
            </Button>
            <Button icon={<PlusOutlined />} onClick={() => setRegisterOpen(true)}>
              Registar sem ficheiro
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setUploadOpen(true)}>
              Carregar faturas
            </Button>
          </Space>
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

        <Button
          size="small"
          type={filters.outstanding ? "primary" : "default"}
          onClick={() => applyFilters({ outstanding: filters.outstanding ? null : true })}
        >
          Por liquidar
        </Button>

        {allocatableSelected.length > 0 && (
          <Button type="primary" onClick={() => setAllocating(allocatableSelected)}>
            Associar {allocatableSelected.length} à mesma rubrica
          </Button>
        )}
        {payableSelected.length > 0 && isAdmin() && (
          <Button onClick={() => setAggregatePayOpen(true)}>
            Registar pagamento de {payableSelected.length}
          </Button>
        )}
        {sendableSelected.length > 0 && isAdmin() && (
          <Button onClick={handleBulkSendToAccountant}>
            Marcar {sendableSelected.length} como enviada{sendableSelected.length > 1 ? "s" : ""} à
            contabilidade
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

          <InvoiceRegisterDrawer
            open={registerOpen}
            scope="PROJECT"
            enterpriseId={enterpriseId}
            onClose={() => setRegisterOpen(false)}
            onCreated={reload}
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
            onIncidentSuggested={(invoice) =>
              setIncidentInvoices([toIncidentInvoiceRef(invoice)])
            }
          />

          <IncidentDrawer
            open={incidentInvoices !== null}
            presetInvoices={incidentInvoices ?? []}
            onClose={() => setIncidentInvoices(null)}
            onCreated={() => setIncidentInvoices(null)}
          />

          <AggregatePaymentDrawer
            open={aggregatePayOpen}
            invoices={payableSelected}
            onClose={() => setAggregatePayOpen(false)}
            onDone={reload}
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
