import { useState } from "react";
import type { FC } from "react";
import { Button, Drawer } from "antd";
import { EditOutlined } from "@ant-design/icons";

import { useAuth } from "@/hooks/useAuth";
import { getInvoice } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
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
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [loadingFile, setLoadingFile] = useState(false);

  /**
   * A lista de despesas só traz a miniatura da fatura. O link assinado do
   * documento completo é pedido aqui, ao clicar — não vale a pena assinar o
   * PDF de cada linha quando quase nenhum é aberto.
   */
  const openPreview = async () => {
    if (!expense?.invoice) return;
    setLoadingFile(true);
    try {
      const invoice = await getInvoice(expense.invoice.id);
      setFileUrl(invoice.fileUrl);
      setPreviewOpen(true);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoadingFile(false);
    }
  };

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
                <Fact label="Fornecedor">
                  {expense.invoice?.supplierName ?? expense.invoice?.supplierNif ?? "—"}
                </Fact>
                <Fact label="Nº fatura">{expense.invoice?.invoiceNumber ?? "—"}</Fact>
                <Fact label="ATCUD">{expense.invoice?.invoiceAtcud ?? "—"}</Fact>
              </div>

              {expense.invoice ? (
                <div style={{ display: "flex", gap: "10.2px", alignItems: "center" }}>
                  {expense.invoice.thumbnailUrl && (
                    <img
                      src={expense.invoice.thumbnailUrl}
                      alt={expense.invoice.originalFilename ?? "fatura"}
                      style={{
                        width: 48,
                        height: 64,
                        objectFit: "cover",
                        border: "1px solid var(--ind-color-divider)",
                      }}
                    />
                  )}
                  <Button onClick={openPreview} loading={loadingFile}>
                    Ver fatura
                  </Button>
                </div>
              ) : (
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  Lançamento registado à mão, sem documento.
                </div>
              )}
            </Card>

            {expense.invoice && (
              <Card kicker="Contabilidade">
                <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14, flexWrap: "wrap" }}>
                  <span
                    className={`ind-tag ${
                      expense.invoice.sentToAccountant ? "ind-tag-accent" : "ind-tag-neutral"
                    }`}
                  >
                    {expense.invoice.sentToAccountant ? "enviada" : "por enviar"}
                  </span>
                  {expense.invoice.sentToAccountant && expense.invoice.sentToAccountantByName && (
                    <span style={{ fontSize: 12, opacity: 0.6 }}>
                      por {expense.invoice.sentToAccountantByName}
                      {expense.invoice.sentToAccountantByRole
                        ? ` (${ROLE_LABEL[expense.invoice.sentToAccountantByRole]})`
                        : ""}
                      {expense.invoice.sentToAccountantAt
                        ? ` · ${formatDateTime(expense.invoice.sentToAccountantAt)}`
                        : ""}
                    </span>
                  )}
                </div>
              </Card>
            )}
          </div>
        )}
      </Drawer>

      <InvoicePreviewModal
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        invoiceUrl={fileUrl}
        mimeType={expense?.invoice?.mimeType ?? null}
        filename={expense?.invoice?.originalFilename ?? null}
      />
    </>
  );
};
