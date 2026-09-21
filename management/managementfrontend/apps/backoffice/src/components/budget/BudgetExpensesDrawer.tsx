import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Drawer, Empty, Space, Spin, Table, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { FileTextOutlined, PlusOutlined } from "@ant-design/icons";

import { listExpensesByBudgetItem } from "@/services/budgetService";
import { batchAllocateInvoices, setInvoiceSentToAccountant } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency, formatDate } from "@/utils/formatters";
import type { BudgetItemNode, ConstructionExpense } from "@/types/budget";
import type { ConstructionInvoice } from "@/types/invoice";
import type { IncidentInvoiceRef } from "@/types/incident";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import IncidentDrawer from "@/components/invoices/IncidentDrawer";
import { BudgetItemPickerModal } from "@/components/invoices/BudgetItemPickerModal";
import { toIncidentInvoiceRef } from "@/components/invoices/toIncidentInvoiceRef";
import { BudgetExpenseDetailDrawer } from "./BudgetExpenseDetailDrawer";
import { BudgetExpenseFormDrawer } from "./BudgetExpenseFormDrawer";

const ROLE_LABEL: Record<string, string> = { ADMIN: "Administrador", EMPLOYEE: "Funcionário" };

interface Props {
  item: BudgetItemNode | null;
  enterpriseId: string;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
}

export const BudgetExpensesDrawer: FC<Props> = ({ item, enterpriseId, open, onClose, onChanged }) => {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const [expenses, setExpenses] = useState<ConstructionExpense[]>([]);
  const [loading, setLoading] = useState(false);
  /** Detalhe de um gasto **sem** fatura — o único caso em que este drawer ainda serve. */
  const [detail, setDetail] = useState<ConstructionExpense | null>(null);
  /**
   * Uma despesa com fatura abre o mesmo detalhe da lista de faturas
   * (`InvoiceDetailDrawer`): correção manual, líquido e NC ligadas, pagamentos,
   * transferência, reassociar. O `BudgetExpenseDetailDrawer` só mostrava o
   * documento e ficava aquém (apontado pelo utilizador a 2026-09-21).
   */
  const [invoiceDetailId, setInvoiceDetailId] = useState<string | null>(null);
  const [reallocating, setReallocating] = useState<ConstructionInvoice | null>(null);
  const [reallocSaving, setReallocSaving] = useState(false);
  const [incidentInvoices, setIncidentInvoices] = useState<IncidentInvoiceRef[] | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ConstructionExpense | null>(null);

  const fetchExpenses = useCallback(async () => {
    if (!item) return;
    setLoading(true);
    try {
      setExpenses(await listExpensesByBudgetItem(item.id));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [item]);

  useEffect(() => {
    if (open) fetchExpenses();
  }, [open, fetchExpenses]);

  /** A caixa de entrada abre já filtrada pelo que está por associar. */
  const goToInvoices = () => navigate(`/backoffice/empreendimentos/${enterpriseId}/invoices`);

  /** O estado na contabilidade é da fatura, não do lançamento. */
  const toggleAccountant = async (expense: ConstructionExpense) => {
    if (!expense.invoice) return;
    try {
      await setInvoiceSentToAccountant(expense.invoice.id, !expense.invoice.sentToAccountant);
      notificationService.success(
        "Contabilidade",
        expense.invoice.sentToAccountant ? "Marcada como por enviar." : "Marcada como enviada."
      );
      fetchExpenses();
      onChanged();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const refresh = () => {
    fetchExpenses();
    onChanged();
  };

  /** Mover a fatura para outra rubrica — o mesmo `batchAllocate` da lista de faturas, com uma só. */
  const handleReallocate = async (budgetItemId: string) => {
    if (!reallocating) return;
    setReallocSaving(true);
    try {
      const result = await batchAllocateInvoices([reallocating.id], budgetItemId);
      if (result.failures.length === 0) {
        notificationService.success("Faturas", "Fatura associada à rubrica.");
      } else {
        notificationService.warning("Faturas", result.failures[0]?.message ?? "Não foi possível associar.");
      }
      setReallocating(null);
      refresh();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setReallocSaving(false);
    }
  };

  const columns: ColumnsType<ConstructionExpense> = [
    {
      title: "Nome",
      dataIndex: "name",
      render: (name: string) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{name}</span>
      ),
    },
    { title: "Data", dataIndex: "expenseDate", width: 108, render: (d: string) => formatDate(d) },
    {
      title: "Fornecedor",
      dataIndex: ["invoice", "supplierName"],
      width: 160,
      render: (_: unknown, row) =>
        row.invoice?.supplierName ?? row.invoice?.supplierNif ?? "—",
    },
    {
      title: "Valor",
      dataIndex: "totalPrice",
      width: 120,
      render: (v: number) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
          {formatCurrency(v)}
        </span>
      ),
    },
    {
      title: "Fatura",
      dataIndex: "invoice",
      width: 84,
      // A miniatura já vem na resposta e identifica o documento de relance;
      // o ficheiro completo só é pedido ao abrir o detalhe.
      render: (_: unknown, row) =>
        row.invoice ? (
          row.invoice.thumbnailUrl ? (
            <img
              src={row.invoice.thumbnailUrl}
              alt={row.invoice.originalFilename ?? "fatura"}
              loading="lazy"
              style={{
                width: 32,
                height: 42,
                objectFit: "cover",
                display: "block",
                border: "1px solid var(--ind-color-divider)",
              }}
            />
          ) : (
            <FileTextOutlined style={{ color: "var(--ind-color-accent)" }} />
          )
        ) : (
          <span style={{ fontSize: 11, opacity: 0.55 }}>sem documento</span>
        ),
    },
    {
      title: "Contabilidade",
      dataIndex: "invoice",
      width: 150,
      render: (_: unknown, row) => {
        // Sem fatura não há papel para enviar ao contabilista.
        if (!row.invoice) return <span style={{ fontSize: 11, opacity: 0.45 }}>—</span>;

        const sent = row.invoice.sentToAccountant;
        const tag = (
          <span
            className={`ind-tag ${sent ? "ind-tag-accent" : "ind-tag-neutral"}`}
            style={{ cursor: isAdmin() ? "pointer" : "default" }}
            onClick={(e) => {
              e.stopPropagation();
              if (isAdmin()) toggleAccountant(row);
            }}
          >
            {sent ? "enviada" : "por enviar"}
          </span>
        );

        if (!sent || !row.invoice.sentToAccountantByName) return tag;

        return (
          <Tooltip
            title={
              <div style={{ fontSize: 12 }}>
                <div style={{ opacity: 0.7 }}>Enviado por</div>
                <div style={{ fontWeight: 600 }}>{row.invoice.sentToAccountantByName}</div>
                {row.invoice.sentToAccountantByRole && (
                  <div style={{ opacity: 0.65 }}>
                    {ROLE_LABEL[row.invoice.sentToAccountantByRole]}
                  </div>
                )}
              </div>
            }
          >
            {tag}
          </Tooltip>
        );
      },
    },
  ];

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        width={900}
        title={
          <div>
            <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Rubrica {item?.code ?? "—"}</h6>
            <h2 style={{ margin: 0 }}>{item?.name}</h2>
          </div>
        }
      >
        {item && (
          <div style={{ display: "flex", flexDirection: "column", gap: "20.4px" }}>
            <div className="ind-card ind-blueprint ind-elev-sm" style={{ padding: "13.6px" }}>
              <i className="ind-corner tl" />
              <i className="ind-corner tr" />
              <i className="ind-corner bl" />
              <i className="ind-corner br" />
              <span className="ind-card-kicker">Rubrica</span>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr 1fr",
                  gap: "10.2px",
                  fontSize: 14,
                }}
              >
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Orçamentado</div>
                  {formatCurrency(item.rolledUpBudget)}
                </div>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Gasto</div>
                  <span style={{ color: "#b53333" }}>{formatCurrency(item.spentTotal)}</span>
                </div>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Restante</div>
                  {formatCurrency(item.remaining)}
                </div>
              </div>
            </div>

            <div>
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: "10.2px",
                }}
              >
                <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Despesas</h6>
                {isAdmin() && (
                  <Space>
                    {/* Caminho principal: a fatura entra pela caixa de entrada e
                        é aí que se escolhe a rubrica. */}
                    <Button type="primary" icon={<FileTextOutlined />} onClick={goToInvoices}>
                      Associar fatura
                    </Button>
                    <Button
                      icon={<PlusOutlined />}
                      onClick={() => {
                        setEditing(null);
                        setFormOpen(true);
                      }}
                    >
                      Gasto sem fatura
                    </Button>
                  </Space>
                )}
              </div>

              <Spin spinning={loading}>
                <Table<ConstructionExpense>
                  rowKey="id"
                  columns={columns}
                  dataSource={expenses}
                  pagination={false}
                  size="small"
                  onRow={(row) => ({
                    onClick: () => (row.invoice ? setInvoiceDetailId(row.invoice.id) : setDetail(row)),
                    style: { cursor: "pointer" },
                  })}
                  locale={{
                    emptyText: (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="Sem despesas lançadas nesta rubrica."
                      />
                    ),
                  }}
                />
              </Spin>
            </div>
          </div>
        )}
      </Drawer>

      <BudgetExpenseDetailDrawer
        expense={detail}
        open={!!detail}
        onClose={() => setDetail(null)}
        onEdit={(expense) => {
          setDetail(null);
          setEditing(expense);
          setFormOpen(true);
        }}
      />

      <InvoiceDetailDrawer
        invoiceId={invoiceDetailId}
        open={!!invoiceDetailId}
        onClose={() => setInvoiceDetailId(null)}
        onChanged={refresh}
        onAllocate={(invoice) => {
          setInvoiceDetailId(null);
          setReallocating(invoice);
        }}
        onIncidentSuggested={(invoice) => setIncidentInvoices([toIncidentInvoiceRef(invoice)])}
      />

      <BudgetItemPickerModal
        open={reallocating !== null}
        enterpriseId={enterpriseId}
        supplierNif={reallocating?.supplierNif}
        saving={reallocSaving}
        onClose={() => setReallocating(null)}
        onPick={(target) => handleReallocate(target.id)}
      />

      <IncidentDrawer
        open={incidentInvoices !== null}
        presetInvoices={incidentInvoices ?? []}
        onClose={() => setIncidentInvoices(null)}
        onCreated={() => setIncidentInvoices(null)}
      />

      <BudgetExpenseFormDrawer
        open={formOpen}
        budgetItem={item}
        expense={editing}
        onClose={() => setFormOpen(false)}
        onGoToInvoices={goToInvoices}
        onSaved={() => {
          setFormOpen(false);
          fetchExpenses();
          onChanged();
        }}
      />
    </>
  );
};
