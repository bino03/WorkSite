import { useCallback, useEffect, useRef, useState } from "react";
import type { CSSProperties, FC, ReactNode } from "react";
import { Button, DatePicker, Drawer, Input, InputNumber, Select, Space, Spin, Tooltip } from "antd";
import { FileTextOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import {
  DEFAULT_INVOICE_TYPE,
  INVOICE_TYPES,
  isKnownInvoiceType,
  joinInvoiceNumber,
  splitInvoiceNumber,
} from "@/components/invoices/invoiceNumber";
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
  /**
   * Tipo de documento a pré-selecionar quando o número está por preencher —
   * o mais usado nas faturas já registadas (ver `suggestInvoiceType`).
   */
  suggestedInvoiceType?: string;
}

type Values = {
  supplierName: string;
  supplierNif: string;
  /** Só o prefixo ("FR"); junta-se ao número na gravação. */
  invoiceType: string;
  /** Só a série/número ("2026/114"), sem o tipo. */
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
  suggestedInvoiceType = DEFAULT_INVOICE_TYPE,
}) => {
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  const [invoice, setInvoice] = useState<ConstructionInvoice | null>(null);
  const [values, setValues] = useState<Values | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  /**
   * Numa ref e não nas dependências do `fetchInvoice`: a sugestão vem da lista
   * do lado, que recarrega a cada gravação — dependê-la traria a fatura outra
   * vez do servidor a meio de uma correção e deitava fora o que já estava
   * escrito. Só interessa o valor no momento em que a fatura abre.
   */
  const suggestedTypeRef = useRef(suggestedInvoiceType);
  useEffect(() => {
    suggestedTypeRef.current = suggestedInvoiceType;
  }, [suggestedInvoiceType]);

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
      // O tipo ("FR", "FT", …) é a única parte do número que se consegue
      // adivinhar — e só quando não há número nenhum, ou seja quando o QR
      // falhou. Com número lido do QR, o que lá está manda: uma fatura cujo
      // número não traz prefixo fica sem prefixo, não ganha um inventado.
      const number = splitInvoiceNumber(data.invoiceNumber);
      setValues({
        supplierName: data.supplierName ?? "",
        supplierNif: data.supplierNif ?? "",
        invoiceType: data.invoiceNumber ? number.type : suggestedTypeRef.current,
        invoiceNumber: number.rest,
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

  /**
   * Quem copia o número inteiro do papel ("FR 2026/114") escreve-o de uma vez
   * na caixa do número — em vez de o deixar lá com o prefixo repetido, o tipo
   * salta sozinho para a lista ao lado. Só com tipos conhecidos: uma série que
   * comece por letras ("AB/12") tem de ficar intacta.
   */
  const handleInvoiceNumberChange = (raw: string) => {
    const { type, rest } = splitInvoiceNumber(raw);
    if (type && rest && isKnownInvoiceType(type)) {
      setValues((prev) => (prev ? { ...prev, invoiceType: type, invoiceNumber: rest } : prev));
      return;
    }
    set("invoiceNumber", raw);
  };

  const handleSave = async () => {
    if (!invoice || !values) return;
    setSaving(true);
    try {
      await updateInvoice(invoice.id, {
        supplierName: values.supplierName || null,
        supplierNif: values.supplierNif || null,
        invoiceNumber: joinInvoiceNumber(values.invoiceType, values.invoiceNumber) || null,
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
      title: "Desassociar da rubrica",
      message: `Desassociar esta fatura da rubrica "${invoice.budgetItemName}"? O lançamento de ${formatCurrency(
        invoice.totalAmount ?? 0
      )} é removido do orçamento e a fatura volta à caixa de entrada.`,
      actionLabel: "Desassociar",
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
              <InvoiceFields
                invoice={invoice}
                values={values}
                editable={isAdmin()}
                onChange={set}
                onInvoiceNumberChange={handleInvoiceNumberChange}
              />
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

/** Duas colunas iguais — o par de campos que anda sempre junto. */
const PAIR: CSSProperties = { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" };

/**
 * Os campos corrigíveis da fatura.
 *
 * Desenhados para o caso mau: o QR não se leu e alguém tem de copiar o papel
 * para aqui. Daí o que aqui está e não é decoração —
 *
 * - **o que falta vem primeiro**: com `needsReview`, a data e o total sobem
 *   para cima de tudo, com um aviso a dizer o que falta e um segundo aviso a
 *   confirmar quando já está preenchido (é uma live region, por isso também se
 *   ouve num leitor de ecrã);
 * - **o tipo do documento escolhe-se, não se escreve**: "FR", "FT", … é a
 *   única parte do número que se consegue adivinhar, e vem já escolhida (ver
 *   `suggestInvoiceType`);
 * - **cada campo tem `label` ligado por `htmlFor`** e um exemplo por baixo, em
 *   vez do `placeholder` que desaparece mal se começa a escrever.
 */
const InvoiceFields: FC<{
  invoice: ConstructionInvoice;
  values: Values;
  editable: boolean;
  onChange: <K extends keyof Values>(key: K, value: Values[K]) => void;
  onInvoiceNumberChange: (raw: string) => void;
}> = ({ invoice, values, editable, onChange, onInvoiceNumberChange }) => {
  const missingDate = !values.invoiceDate;
  const missingTotal = values.totalAmount == null;
  /** Só se assinala o que falta quando é mesmo isso que está a travar a fatura. */
  const flagMissing = invoice.needsReview;

  // Um tipo gravado que não esteja na lista (fornecedor com código próprio)
  // tem de continuar a aparecer escolhido — senão abrir a fatura apagava-o.
  const typeOptions =
    values.invoiceType && !isKnownInvoiceType(values.invoiceType)
      ? [{ value: values.invoiceType, label: values.invoiceType }, ...INVOICE_TYPES]
      : INVOICE_TYPES;

  const supplier = (
    <div style={PAIR}>
      <Field id="invoice-supplier" label="Fornecedor" hint="Nome que vem no topo da fatura.">
        <Input
          id="invoice-supplier"
          size="large"
          value={values.supplierName}
          disabled={!editable}
          onChange={(e) => onChange("supplierName", e.target.value)}
          placeholder="Betão Liz, Lda."
        />
      </Field>
      <Field id="invoice-nif" label="NIF do fornecedor" hint="Nove dígitos.">
        <Input
          id="invoice-nif"
          size="large"
          inputMode="numeric"
          maxLength={9}
          value={values.supplierNif}
          disabled={!editable}
          onChange={(e) => onChange("supplierNif", e.target.value)}
          placeholder="500123456"
        />
      </Field>
    </div>
  );

  const amounts = (
    <div style={PAIR}>
      <Field
        id="invoice-date"
        label="Data da fatura"
        required
        hint="Escreva 05/03/2026 ou escolha no calendário."
      >
        <DatePicker
          id="invoice-date"
          size="large"
          style={{ width: "100%" }}
          format="DD/MM/YYYY"
          placeholder="DD/MM/AAAA"
          disabled={!editable}
          status={flagMissing && missingDate ? "error" : undefined}
          aria-required
          value={values.invoiceDate ? dayjs(values.invoiceDate) : null}
          disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
          onChange={(d) => onChange("invoiceDate", d ? d.format("YYYY-MM-DD") : "")}
        />
      </Field>
      <Field id="invoice-total" label="Total a pagar" required hint="Com IVA incluído.">
        <InputNumber
          id="invoice-total"
          size="large"
          style={{ width: "100%" }}
          step={0.01}
          min={0}
          precision={2}
          addonAfter="€"
          inputMode="decimal"
          disabled={!editable}
          status={flagMissing && missingTotal ? "error" : undefined}
          aria-required
          // Vírgula, como no papel — o AntD continua a aceitar o ponto de quem
          // escreve pelo teclado numérico.
          decimalSeparator=","
          value={values.totalAmount}
          onChange={(v) => onChange("totalAmount", v as number | null)}
        />
      </Field>
    </div>
  );

  const numbers = (
    <div style={PAIR}>
      <div style={{ display: "grid", gridTemplateColumns: "minmax(96px, 0.6fr) 1fr", gap: 6 }}>
        <Field id="invoice-type" label="Tipo">
          <Select
            id="invoice-type"
            size="large"
            style={{ width: "100%" }}
            disabled={!editable}
            allowClear
            value={values.invoiceType || undefined}
            onChange={(value) => onChange("invoiceType", value ?? "")}
            options={typeOptions}
            // O código curto na caixa; a explicação só na lista aberta.
            optionLabelProp="value"
            aria-label="Tipo de documento"
          />
        </Field>
        <Field id="invoice-number" label="Nº da fatura" hint="Só a série e o número.">
          <Input
            id="invoice-number"
            size="large"
            value={values.invoiceNumber}
            disabled={!editable}
            onChange={(e) => onInvoiceNumberChange(e.target.value)}
            placeholder="2026/114"
          />
        </Field>
      </div>
      <Field id="invoice-atcud" label="ATCUD" hint="Impresso por baixo do QR. Pode deixar em branco.">
        <Input
          id="invoice-atcud"
          size="large"
          value={values.invoiceAtcud}
          disabled={!editable}
          onChange={(e) => onChange("invoiceAtcud", e.target.value)}
          placeholder="CSDF7T5H-0114"
        />
      </Field>
    </div>
  );

  return (
    <div className="ind-card" style={{ padding: "13.6px", gap: "13.6px" }}>
      <span className="ind-card-kicker">Dados da fatura</span>

      {flagMissing && (
        <div
          role="status"
          style={{
            borderLeft: `4px solid var(${missingDate || missingTotal ? "--color-error" : "--ind-color-accent"})`,
            background: "var(--ind-color-surface)",
            padding: "10.2px 13.6px",
            fontSize: 14,
            lineHeight: 1.5,
          }}
        >
          {missingDate || missingTotal ? (
            <>
              <strong>O QR desta fatura não foi lido.</strong> Falta preencher{" "}
              {missingDate && missingTotal ? "a data e o total" : missingDate ? "a data" : "o total"} — é
              só isso que impede a fatura de ser associada a uma rubrica. Os outros campos são opcionais.
            </>
          ) : (
            <>
              <strong>Está preenchido.</strong> Prima <em>Guardar</em> para a fatura deixar de estar por
              rever.
            </>
          )}
        </div>
      )}

      {/* Com o QR lido a ordem natural é fornecedor → valores; sem ele, o que
          falta preencher tem de ser a primeira coisa que se vê. */}
      {flagMissing ? (
        <>
          {amounts}
          {supplier}
        </>
      ) : (
        <>
          {supplier}
          {amounts}
        </>
      )}

      {numbers}

      <Field id="invoice-notes" label="Notas">
        <Input.TextArea
          id="invoice-notes"
          rows={2}
          value={values.notes}
          disabled={!editable}
          onChange={(e) => onChange("notes", e.target.value)}
        />
      </Field>

      {(invoice.taxableAmount != null || invoice.taxAmount != null) && (
        <div style={{ fontSize: 12, opacity: 0.7 }}>
          Lido do QR: sem impostos {formatCurrency(invoice.taxableAmount ?? 0)} · impostos{" "}
          {formatCurrency(invoice.taxAmount ?? 0)}
        </div>
      )}
    </div>
  );
};

/**
 * Etiqueta + exemplo, ambos dentro do `<label>` — assim o leitor de ecrã lê a
 * ajuda junto com o nome do campo, em vez de a deixar para trás.
 */
const Field: FC<{
  id: string;
  label: string;
  required?: boolean;
  hint?: string;
  children: ReactNode;
}> = ({ id, label, required, hint, children }) => (
  <div>
    <label htmlFor={id} style={{ display: "block", fontSize: 13, marginBottom: 5 }}>
      {label}
      {required && (
        <>
          <span aria-hidden style={{ color: "var(--color-error)" }}> *</span>
          <span className="sr-only"> (obrigatório)</span>
        </>
      )}
      {hint && (
        <span style={{ display: "block", fontSize: 12, opacity: 0.65, marginTop: 2 }}>{hint}</span>
      )}
    </label>
    {children}
  </div>
);
