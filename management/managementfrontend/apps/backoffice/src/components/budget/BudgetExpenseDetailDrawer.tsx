import type { FC } from "react";
import { Button, Drawer } from "antd";
import { EditOutlined } from "@ant-design/icons";

import { useAuth } from "@/hooks/useAuth";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/formatters";
import type { ConstructionExpense } from "@/types/budget";

interface Props {
  expense: ConstructionExpense | null;
  open: boolean;
  onClose: () => void;
  onEdit: (expense: ConstructionExpense) => void;
}

const Card: FC<{ kicker: string; children: React.ReactNode }> = ({ kicker, children }) => (
  <div className="ind-card ind-blueprint ind-elev-sm" style={{ padding: "13.6px", gap: "10.2px" }}>
    <i className="ind-corner tl" />
    <i className="ind-corner tr" />
    <i className="ind-corner bl" />
    <i className="ind-corner br" />
    <span className="ind-card-kicker">{kicker}</span>
    {children}
  </div>
);

const Fact: FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div>
    <div style={{ fontSize: 11, opacity: 0.55 }}>{label}</div>
    {children}
  </div>
);

/**
 * Detalhe de um gasto lançado à mão, **sem fatura**. Uma despesa com fatura
 * abre o `InvoiceDetailDrawer` da lista de faturas (desde 2026-09-21), por isso
 * este drawer já não mostra fornecedor, documento nem contabilidade — tudo isso
 * é da fatura, e aqui não há nenhuma.
 */
export const BudgetExpenseDetailDrawer: FC<Props> = ({ expense, open, onClose, onEdit }) => {
  const { isAdmin } = useAuth();

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={480}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Gasto sem fatura</h6>
          <h2 style={{ margin: 0 }}>{expense?.name}</h2>
        </div>
      }
      extra={
        isAdmin() && expense ? (
          <Button type="text" icon={<EditOutlined />} onClick={() => onEdit(expense)}>
            Editar
          </Button>
        ) : null
      }
    >
      {expense && (
        <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
          <p style={{ margin: 0, fontSize: 13, opacity: 0.8 }}>
            {expense.description || "Sem descrição."}
          </p>

          <Card kicker="Medição">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10.2px", fontSize: 14 }}>
              <Fact label="Un.">{expense.unit ?? "—"}</Fact>
              <Fact label="Quantidade">{expense.quantity ?? "—"}</Fact>
              <Fact label="Preço Un.">
                {expense.unitPrice == null ? "—" : formatCurrency(expense.unitPrice)}
              </Fact>
            </div>
            <div style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 20 }}>
              {formatCurrency(expense.totalPrice)}
            </div>
          </Card>

          <Card kicker="Lançamento">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px", fontSize: 14 }}>
              <Fact label="Data">{formatDate(expense.expenseDate)}</Fact>
              <Fact label="Lançado por">{expense.createdByName ?? "—"}</Fact>
              <Fact label="Criado">{formatDateTime(expense.createdAt)}</Fact>
              <Fact label="Atualizado">{formatDateTime(expense.updatedAt)}</Fact>
            </div>
            <div style={{ fontSize: 12, opacity: 0.6 }}>
              Registado à mão, sem documento. Se a fatura entretanto chegar, carrega-a na caixa de
              entrada e associa-a a esta rubrica — este lançamento apaga-se depois.
            </div>
          </Card>
        </div>
      )}
    </Drawer>
  );
};
