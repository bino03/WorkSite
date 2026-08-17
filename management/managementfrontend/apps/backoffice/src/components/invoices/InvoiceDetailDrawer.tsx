import { useCallback, useEffect, useState } from "react";
import type { FC, ReactNode } from "react";
import { Button, DatePicker, Drawer, Input, InputNumber, Space, Spin, Tooltip } from "antd";
import { FileTextOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import {
  deallocateInvoice,
  deleteInvoice,
  getInvoice,
  setInvoiceSentToAccountant,
  updateInvoice,
} from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { parseApiError } from "@/utils/apiError";
import { formatBytes, formatCurrency, formatDate, savingsPercent } from "@/utils/formatters";
import type { ConstructionInvoice } from "@/types/invoice";

const ROLE_LABEL: Record<string, string> = { ADMIN: "Administrador", EMPLOYEE: "Funcionário" };

/**
 * ErrorCode de `dto/error/ErrorCode.java` — as três chaves de duplicado que
 * `rejectIfDuplicate` pode acusar ao corrigir os campos à mão (ver
 * `ConstructionInvoiceService.update`). O checksum não muda numa correção
 * manual, mas fica incluído por segurança: mais vale reconhecer de mais do
 * que deixar cair no aviso genérico.
 */
const DUPLICATE_ERROR_CODES = new Set(["INVOICE_010", "INVOICE_011", "INVOICE_012"]);

interface Props {
  invoiceId: string | null;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
  onAllocate: (invoice: ConstructionInvoice) => void;
}

type Values = {
  supplierName: string;
  supplierNif: string;
  invoiceNumber: string;
  invoiceAtcud: string;
  invoiceDate: string;
  totalAmount: number | null;
  notes: string;
};

/**
 * Detalhe de uma fatura: o documento, os campos corrigíveis e a associação.
 *
 * É a única chamada que traz o link assinado do documento completo — as listas
 * mostram só a miniatura. Por isso os dados são pedidos ao abrir, e não
 * herdados da linha.
 */
export const InvoiceDetailDrawer: FC<Props> = ({
  invoiceId,
  open,
  onClose,
  onChanged,
  onAllocate,
}) => {
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  const [invoice, setInvoice] = useState<ConstructionInvoice | null>(null);
  const [values, setValues] = useState<Values | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  const fetchInvoice = useCallback(async () => {
    if (!invoiceId) return;
    setLoading(true);
    // Limpa já a fatura anterior: trocar de linha na lista sem fechar a
    // drawer muda só o `invoiceId`, não desmonta o componente — sem isto, o
    // `needsReview` (e o `fileUrl`) da fatura antiga ficavam a decidir a
    // pré-visualização até a resposta chegar, abrindo/fechando o painel
    // sozinho e pedindo um ficheiro que já não interessa.
    setInvoice(null);
    setValues(null);
    setPreviewOpen(false);
    try {
      const data = await getInvoice(invoiceId);
      setInvoice(data);
      setValues({
        supplierName: data.supplierName ?? "",
        supplierNif: data.supplierNif ?? "",
        invoiceNumber: data.invoiceNumber ?? "",
        invoiceAtcud: data.invoiceAtcud ?? "",
        invoiceDate: data.invoiceDate ?? "",
        totalAmount: data.totalAmount,
        notes: data.notes ?? "",
      });
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [invoiceId]);

  useEffect(() => {
    if (open) fetchInvoice();
  }, [open, fetchInvoice]);

  const set = <K extends keyof Values>(key: K, value: Values[K]) =>
    setValues((prev) => (prev ? { ...prev, [key]: value } : prev));

  const handleSave = async () => {
    if (!invoice || !values) return;
    setSaving(true);
    try {
      await updateInvoice(invoice.id, {
        supplierName: values.supplierName || null,
        supplierNif: values.supplierNif || null,
        invoiceNumber: values.invoiceNumber || null,
        invoiceAtcud: values.invoiceAtcud || null,
        invoiceDate: values.invoiceDate || null,
        totalAmount: values.totalAmount,
        notes: values.notes || null,
      });
      notificationService.success("Fatura", "Dados atualizados.");
      await fetchInvoice();
      onChanged();
    } catch (error) {
      const apiError = parseApiError(error);
      if (apiError && DUPLICATE_ERROR_CODES.has(apiError.errorCode)) {
        // O ErrorHandler genérico mostraria só um toast — aqui a correção
        // óbvia é apagar esta fatura (a redundante), por isso oferece-se logo.
        confirm({
          title: "Fatura duplicada",
          message: `${apiError.message} Esta fatura já está registada no sistema. Queres apagá-la?`,
          actionLabel: "Apagar fatura",
          onConfirm: async () => {
            try {
              await deleteInvoice(invoice.id);
              notificationService.success("Fatura", "Fatura eliminada.");
              onChanged();
              onClose();
            } catch (deleteError) {
              ErrorHandler.handle(deleteError);
            }
          },
        });
      } else {
        ErrorHandler.handle(error);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDeallocate = () => {
    if (!invoice) return;
    confirm({
      message: `Desassociar esta fatura da rubrica "${invoice.budgetItemName}"? O lançamento de ${formatCurrency(
        invoice.totalAmount ?? 0
      )} é removido do orçamento e a fatura volta à caixa de entrada.`,
      onConfirm: async () => {
        try {
          await deallocateInvoice(invoice.id);
          notificationService.success("Fatura", "Fatura devolvida à caixa de entrada.");
          await fetchInvoice();
          onChanged();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const handleAccountant = () => {
    if (!invoice) return;
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
          await fetchInvoice();
          onChanged();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const savings = savingsPercent(invoice?.originalSizeBytes ?? null, invoice?.sizeBytes ?? null);
  // Falta o essencial para associar — mostra o documento ao lado dos campos
  // logo de início, para preencher a olhar para ele sem andar a abrir e
  // fechar o modal de pré-visualização a cada campo.
  const showInlinePreview = invoice?.needsReview ?? false;

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        width={showInlinePreview ? "min(1500px, 96vw)" : 640}
        styles={showInlinePreview ? { body: { padding: "13.6px 20.4px" } } : undefined}
        destroyOnClose
        title={
          <div>
            <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Fatura</h6>
            <h2 style={{ margin: 0 }}>
              {invoice?.invoiceNumber ?? invoice?.originalFilename ?? "—"}
            </h2>
          </div>
        }
        footer={
          isAdmin() && (
            <Space style={{ display: "flex", justifyContent: "flex-end" }}>
              <Button onClick={onClose} disabled={saving}>
                Fechar
              </Button>
              <Button type="primary" onClick={handleSave} loading={saving} disabled={!values}>
                Guardar
              </Button>
            </Space>
          )
        }
      >
        <Spin spinning={loading}>
          {invoice && values && (
            <div style={{ display: "flex", gap: "10.2px", alignItems: "flex-start" }}>
              {showInlinePreview && (
                <div
                  style={{
                    flex: "1 1 60%",
                    position: "sticky",
                    top: 0,
                    borderRadius: 2,
                    overflow: "hidden",
                    maxHeight: "calc(100vh - 140px)",
                    overflowY: "auto",
                    background: "var(--ind-color-surface)",
                  }}
                >
                  {invoice.fileUrl ? (
                    invoice.mimeType?.startsWith("image/") ? (
                      <img
                        src={invoice.fileUrl}
                        alt={invoice.originalFilename ?? "fatura"}
                        style={{ width: "100%", display: "block" }}
                      />
                    ) : (
                      <embed
                        src={invoice.fileUrl}
                        type="application/pdf"
                        style={{ width: "100%", height: "calc(100vh - 140px)", display: "block" }}
                      />
                    )
                  ) : (
                    <div style={{ padding: 24, fontSize: 13, opacity: 0.6 }}>
                      Sem pré-visualização disponível.
                    </div>
                  )}
                </div>
              )}

              <div
                style={{
                  flex: "1 1 40%",
                  minWidth: 0,
                  display: "flex",
                  flexDirection: "column",
                  gap: "13.6px",
                }}
              >
              {/* Documento ------------------------------------------------ */}
              <div className="ind-card ind-blueprint ind-elev-sm" style={{ padding: "13.6px" }}>
                <i className="ind-corner tl" />
                <i className="ind-corner tr" />
                <i className="ind-corner bl" />
                <i className="ind-corner br" />
                <span className="ind-card-kicker">Documento</span>
                <div style={{ display: "flex", gap: "13.6px", alignItems: "flex-start" }}>
                  <button
                    type="button"
                    onClick={() => setPreviewOpen(true)}
                    style={{
                      padding: 0,
                      border: "1px solid var(--ind-color-divider)",
                      background: "var(--ind-color-surface)",
                      cursor: "pointer",
                      flexShrink: 0,
                    }}
                  >
                    {invoice.thumbnailUrl ? (
                      <img
                        src={invoice.thumbnailUrl}
                        alt={invoice.originalFilename ?? "fatura"}
                        style={{ width: 96, height: 128, objectFit: "cover", display: "block" }}
                      />
                    ) : (
                      <span
                        style={{
                          width: 96,
                          height: 128,
                          display: "grid",
                          placeItems: "center",
                          color: "var(--ind-color-accent)",
                        }}
                      >
                        <FileTextOutlined style={{ fontSize: 28 }} />
                      </span>
                    )}
                  </button>

                  <div style={{ fontSize: 13, minWidth: 0 }}>
                    <div style={{ wordBreak: "break-all" }}>{invoice.originalFilename ?? "—"}</div>
                    <div style={{ fontSize: 11, opacity: 0.6, marginTop: 4 }}>
                      {formatBytes(invoice.sizeBytes)}
                      {savings != null && (
                        <> · comprimido de {formatBytes(invoice.originalSizeBytes)} (−{savings}%)</>
                      )}
                    </div>
                    <div style={{ fontSize: 11, opacity: 0.6 }}>
                      Carregada por {invoice.uploadedByName ?? "—"} · {formatDate(invoice.uploadedAt)}
                    </div>
                    <Button size="small" style={{ marginTop: 8 }} onClick={() => setPreviewOpen(true)}>
                      Ver documento
                    </Button>
                  </div>
                </div>
              </div>

              {/* Estado --------------------------------------------------- */}
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                {invoice.allocated ? (
                  <span className="ind-tag ind-tag-accent">
                    {invoice.budgetItemCode ? `${invoice.budgetItemCode} · ` : ""}
                    {invoice.budgetItemName}
                  </span>
                ) : (
                  <span className="ind-tag ind-tag-outline">por associar</span>
                )}

                {invoice.needsReview && (
                  <Tooltip title="Falta a data ou o total — preencha antes de associar.">
                    <span className="ind-tag ind-tag-neutral">por rever</span>
                  </Tooltip>
                )}

                {isAdmin() && (
                  <span
                    className={`ind-tag ${invoice.sentToAccountant ? "ind-tag-accent-2" : "ind-tag-neutral"}`}
                    style={{ cursor: "pointer" }}
                    onClick={handleAccountant}
                  >
                    contabilidade: {invoice.sentToAccountant ? "enviada" : "por enviar"}
                  </span>
                )}
              </div>

              {invoice.sentToAccountant && invoice.sentToAccountantByName && (
                <div style={{ fontSize: 11, opacity: 0.6, marginTop: -12 }}>
                  Enviada por {invoice.sentToAccountantByName}
                  {invoice.sentToAccountantByRole
                    ? ` (${ROLE_LABEL[invoice.sentToAccountantByRole]})`
                    : ""}{" "}
                  · {formatDate(invoice.sentToAccountantAt)}
                </div>
              )}

              {isAdmin() && (
                <div style={{ display: "flex", gap: 8 }}>
                  {invoice.allocated ? (
                    <Button onClick={handleDeallocate}>Desassociar da rubrica</Button>
                  ) : (
                    <Tooltip
                      title={
                        invoice.needsReview
                          ? "Preencha a data e o total antes de associar."
                          : undefined
                      }
                    >
                      <Button
                        type="primary"
                        disabled={invoice.needsReview}
                        onClick={() => onAllocate(invoice)}
                      >
                        Associar a uma rubrica
                      </Button>
                    </Tooltip>
                  )}
                </div>
              )}

              {/* Campos --------------------------------------------------- */}
              <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
                  <Field label="Fornecedor">
                    <Input
                      value={values.supplierName}
                      disabled={!isAdmin()}
                      onChange={(e) => set("supplierName", e.target.value)}
                      placeholder="Betão Liz, Lda."
                    />
                  </Field>
                  <Field label="NIF">
                    <Input
                      value={values.supplierNif}
                      disabled={!isAdmin()}
                      onChange={(e) => set("supplierNif", e.target.value)}
                    />
                  </Field>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
                  <Field label="Data da fatura" required>
                    <DatePicker
                      style={{ width: "100%" }}
                      format="DD/MM/YYYY"
                      disabled={!isAdmin()}
                      value={values.invoiceDate ? dayjs(values.invoiceDate) : null}
                      disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
                      onChange={(d) => set("invoiceDate", d ? d.format("YYYY-MM-DD") : "")}
                    />
                  </Field>
                  <Field label="Total (€)" required>
                    <InputNumber
                      style={{ width: "100%" }}
                      step={0.01}
                      min={0}
                      disabled={!isAdmin()}
                      value={values.totalAmount}
                      onChange={(v) => set("totalAmount", v as number | null)}
                    />
                  </Field>
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
                  <Field label="Nº da fatura">
                    <Input
                      value={values.invoiceNumber}
                      disabled={!isAdmin()}
                      onChange={(e) => set("invoiceNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="ATCUD">
                    <Input
                      value={values.invoiceAtcud}
                      disabled={!isAdmin()}
                      onChange={(e) => set("invoiceAtcud", e.target.value)}
                    />
                  </Field>
                </div>

                <Field label="Notas">
                  <Input.TextArea
                    rows={2}
                    value={values.notes}
                    disabled={!isAdmin()}
                    onChange={(e) => set("notes", e.target.value)}
                  />
                </Field>

                {(invoice.taxableAmount != null || invoice.taxAmount != null) && (
                  <div style={{ fontSize: 11, opacity: 0.6 }}>
                    Lido do QR: sem impostos {formatCurrency(invoice.taxableAmount ?? 0)} · impostos{" "}
                    {formatCurrency(invoice.taxAmount ?? 0)}
                  </div>
                )}
              </div>
              </div>
            </div>
          )}
        </Spin>
      </Drawer>

      <InvoicePreviewModal
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        invoiceUrl={invoice?.fileUrl ?? null}
        mimeType={invoice?.mimeType ?? null}
        filename={invoice?.originalFilename ?? null}
      />
    </>
  );
};

const Field: FC<{ label: string; required?: boolean; children: ReactNode }> = ({
  label,
  required,
  children,
}) => (
  <div>
    <label style={{ display: "block", fontSize: 12, marginBottom: 5, opacity: 0.7 }}>
      {label}
      {required && <span style={{ color: "#b53333" }}> *</span>}
    </label>
    {children}
  </div>
);
