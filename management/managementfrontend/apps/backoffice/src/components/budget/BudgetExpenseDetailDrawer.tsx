import { useState } from "react";
import type { FC } from "react";
import { Button, Drawer } from "antd";
import { EditOutlined } from "@ant-design/icons";

import { useAuth } from "@/hooks/useAuth";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/formatters";
import type { ConstructionExpense } from "@/types/budget";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";

const ROLE_LABEL: Record<string, string> = { ADMIN: "Administrador", EMPLOYEE: "Funcionário" };

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

export const BudgetExpenseDetailDrawer: FC<Props> = ({ expense, open, onClose, onEdit }) => {
  const { isAdmin } = useAuth();
  const [previewOpen, setPreviewOpen] = useState(false);

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        width={480}
        title={
          <div>
            <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Despesa</h6>
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

            <Card kicker="Fatura">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px", fontSize: 14 }}>
                <Fact label="Data">{formatDate(expense.expenseDate)}</Fact>
                <Fact label="Fornecedor (NIF)">{expense.supplierNif ?? "—"}</Fact>
                <Fact label="Nº fatura">{expense.invoiceNumber ?? "—"}</Fact>
                <Fact label="ATCUD">{expense.invoiceAtcud ?? "—"}</Fact>
              </div>

              {expense.hasInvoice ? (
                <Button
                  onClick={() => setPreviewOpen(true)}
                  disabled={!expense.invoiceUrl}
                  title={
                    expense.invoiceUrl
                      ? undefined
                      : "O ficheiro existe mas não foi possível gerar o link de acesso."
                  }
                >
                  {expense.invoiceUrl ? "Ver fatura" : "Fatura indisponível"}
                </Button>
              ) : (
                <div style={{ fontSize: 12, opacity: 0.6 }}>Sem ficheiro de fatura anexado.</div>
              )}
            </Card>

            <Card kicker="Contabilidade">
              <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14, flexWrap: "wrap" }}>
                <span
                  className={`ind-tag ${expense.sentToAccountant ? "ind-tag-accent" : "ind-tag-neutral"}`}
                >
                  {expense.sentToAccountant ? "enviada" : "por enviar"}
                </span>
                {expense.sentToAccountant && expense.sentToAccountantByName && (
                  <span style={{ fontSize: 12, opacity: 0.6 }}>
                    por {expense.sentToAccountantByName}
                    {expense.sentToAccountantByRole
                      ? ` (${ROLE_LABEL[expense.sentToAccountantByRole]})`
                      : ""}
                    {expense.sentToAccountantAt ? ` · ${formatDateTime(expense.sentToAccountantAt)}` : ""}
                  </span>
                )}
              </div>
              {expense.uploadedByName && (
                <span style={{ fontSize: 12, opacity: 0.6 }}>
                  carregado por {expense.uploadedByName}
                  {expense.uploadedAt ? ` · ${formatDateTime(expense.uploadedAt)}` : ""}
                </span>
              )}
            </Card>
          </div>
        )}
      </Drawer>

      <InvoicePreviewModal
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        invoiceUrl={expense?.invoiceUrl ?? null}
        mimeType={expense?.mimeType ?? null}
        filename={expense?.originalFilename ?? null}
      />
    </>
  );
};
