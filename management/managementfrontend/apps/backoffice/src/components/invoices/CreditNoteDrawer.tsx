import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Alert,
  Button,
  DatePicker,
  Drawer,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Typography,
} from "antd";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import BudgetItemPickerModal from "@/components/invoices/BudgetItemPickerModal";
import { CreditNoteSchema } from "@/components/invoices/creditNoteFormSchema";
import type { CreditNoteForm } from "@/components/invoices/creditNoteFormSchema";
import { INVOICE_TYPES, joinInvoiceNumber } from "@/components/invoices/invoiceNumber";
import { createCreditNote, previewCreditNoteSplit } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, InvoiceDocumentStatus } from "@/types/invoice";

const { Text } = Typography;

/** Os mesmos três do `InvoiceRegisterDrawer`: sem ficheiro, `ARCHIVED` não se escolhe. */
const STATUS_OPTIONS: { value: Exclude<InvoiceDocumentStatus, "ARCHIVED">; label: string }[] = [
  { value: "TO_REQUEST", label: "Por pedir ao fornecedor" },
  { value: "TO_PRINT", label: "Por imprimir" },
  { value: "MISSING", label: "Sem ficheiro" },
];

/** Uma linha da repartição, já do lado do utilizador: pode trocar a rubrica e o valor. */
interface SplitLine {
  budgetItemId: string;
  code: string | null;
  name: string | null;
  /** Negativo — é um abatimento. */
  amount: number;
}

const emptyValues: CreditNoteForm = {
  totalAmount: null,
  invoiceType: "NC",
  invoiceNumber: "",
  invoiceAtcud: "",
  invoiceDate: dayjs().format("YYYY-MM-DD"),
  supplierNif: "",
  description: "",
  documentStatus: "MISSING",
  notes: "",
};

interface Props {
  open: boolean;
  /** A fatura que a nota de crédito corrige. Nunca uma NC — o backend recusa. */
  invoice: ConstructionInvoice | null;
  onClose: () => void;
  /** Chamado depois de gravar — o pai recarrega a fatura de origem. */
  onCreated: () => void;
}

/**
 * Registar uma nota de crédito a partir de uma fatura lançada.
 *
 * A NC vive na mesma tabela que as faturas e herda da origem o âmbito, a obra e
 * — por omissão — o NIF; nada disso se pergunta aqui. O que se pergunta é o
 * valor (positivo) e a **repartição**: a despesa negativa que a NC vai lançar na
 * rubrica onde a fatura de origem está associada, proposta pelo backend na
 * proporção dessa fatura e confirmada (ou corrigida) antes de gravar.
 *
 * A proposta recalcula-se a cada mudança do valor, com um atraso curto — daí o
 * `skipErrorNotification` do serviço: um toast por dígito escrito não serve a
 * ninguém, o erro fica no painel.
 */
export const CreditNoteDrawer: FC<Props> = ({ open, invoice, onClose, onCreated }) => {
  const { t } = useTranslation();

  const [lines, setLines] = useState<SplitLine[]>([]);
  const [originAllocated, setOriginAllocated] = useState(true);
  const [previewing, setPreviewing] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [pickerFor, setPickerFor] = useState<number | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<CreditNoteForm>({
    resolver: zodResolver(CreditNoteSchema),
    mode: "onChange",
    defaultValues: emptyValues,
  });

  const amount = watch("totalAmount");

  useEffect(() => {
    if (!open) return;
    setLines([]);
    setOriginAllocated(true);
    setPreviewError(null);
    setPickerFor(null);
    reset({ ...emptyValues, supplierNif: invoice?.supplierNif ?? "" });
  }, [open, invoice, reset]);

  const loadSplit = useCallback(
    async (originId: string, value: number) => {
      setPreviewing(true);
      setPreviewError(null);
      try {
        const preview = await previewCreditNoteSplit(originId, value);
        setOriginAllocated(preview.originAllocated);
        setLines(
          preview.lines.map((line) => ({
            budgetItemId: line.budgetItemId,
            code: line.budgetItemCode,
            name: line.budgetItemName,
            amount: line.amount,
          }))
        );
      } catch (error) {
        setPreviewError(ErrorHandler.getMessage(error));
        setLines([]);
      } finally {
        setPreviewing(false);
      }
    },
    []
  );

  // A cada valor novo, uma proposta nova. O atraso é o que evita uma chamada
  // por tecla enquanto o valor está a ser escrito.
  useEffect(() => {
    if (!open || !invoice) return;
    if (amount == null || amount <= 0) {
      setLines([]);
      return;
    }
    const timer = window.setTimeout(() => void loadSplit(invoice.id, amount), 400);
    return () => window.clearTimeout(timer);
  }, [open, invoice, amount, loadSplit]);

  const splitSum = useMemo(
    () => Math.round(lines.reduce((sum, line) => sum + line.amount, 0) * 100) / 100,
    [lines]
  );

  /** A soma tem de ser o simétrico do valor da NC. Fora disso grava, mas desalinhada. */
  const splitMismatch =
    amount != null && lines.length > 0 && Math.abs(splitSum + amount) > 0.005;

  /** O líquido da origem já desconta as NC anteriores — é esse o tecto sensato. */
  const exceedsNet =
    amount != null && invoice?.netAmount != null && amount > invoice.netAmount;

  const setLineAmount = (index: number, value: number) =>
    setLines((prev) =>
      prev.map((line, i) => (i === index ? { ...line, amount: value } : line))
    );

  const onSubmit = handleSubmit(async (values) => {
    if (!invoice || values.totalAmount == null) return;
    try {
      await createCreditNote(invoice.id, {
        totalAmount: values.totalAmount,
        invoiceNumber: joinInvoiceNumber(values.invoiceType, values.invoiceNumber) || null,
        invoiceAtcud: values.invoiceAtcud || null,
        invoiceDate: values.invoiceDate || null,
        supplierNif: values.supplierNif || null,
        description: values.description || null,
        notes: values.notes || null,
        documentStatus: values.documentStatus,
        expenses: lines.map((line) => ({
          budgetItemId: line.budgetItemId,
          amount: line.amount,
        })),
      });
      notificationService.success(t("invoices.creditNote.success"));
      onCreated();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  });

  const fieldError = (message?: string) =>
    message ? (
      <Text type="danger" style={{ fontSize: 12 }}>
        {t(message)}
      </Text>
    ) : null;

  const label = (text: string) => (
    <label style={{ fontSize: 12, opacity: 0.7 }}>{text}</label>
  );

  return (
    <>
      <Drawer
        title={t("invoices.creditNote.drawerTitle")}
        open={open}
        onClose={onClose}
        width={600}
        destroyOnClose
        footer={
          <Space style={{ display: "flex", justifyContent: "flex-end" }}>
            <Button onClick={onClose} disabled={isSubmitting}>
              {t("common.cancel")}
            </Button>
            <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
              {t("invoices.creditNote.register")}
            </Button>
          </Space>
        }
      >
        <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
          {invoice && (
            <div style={{ fontSize: 13, opacity: 0.75 }}>
              {t("invoices.creditNote.ofInvoice", {
                number: invoice.invoiceNumber ?? "—",
              })}
              {invoice.totalAmount != null && (
                <> · {formatCurrency(invoice.totalAmount)}</>
              )}
              {invoice.netAmount != null && invoice.creditNoteTotal > 0 && (
                <>
                  {" "}
                  · {t("invoices.creditNote.netTitle")}: {formatCurrency(invoice.netAmount)}
                </>
              )}
            </div>
          )}

          <div>
            {label(t("invoices.creditNote.fieldAmount"))}
            <Controller
              name="totalAmount"
              control={control}
              render={({ field }) => (
                <InputNumber
                  value={field.value}
                  onChange={(value) => field.onChange(value ?? null)}
                  min={0}
                  precision={2}
                  decimalSeparator=","
                  addonAfter="€"
                  style={{ width: "100%" }}
                />
              )}
            />
            <div style={{ fontSize: 11, opacity: 0.55, marginTop: 4 }}>
              {t("invoices.creditNote.fieldAmountHint")}
            </div>
            {fieldError(errors.totalAmount?.message)}
          </div>

          {exceedsNet && invoice?.netAmount != null && (
            <Alert
              type="warning"
              showIcon
              message={t("invoices.creditNote.exceedsNet", {
                amount: formatCurrency(invoice.netAmount),
              })}
            />
          )}

          {/* Repartição ------------------------------------------------- */}
          <div className="ind-card" style={{ padding: "13.6px", gap: "10.2px" }}>
            <span className="ind-card-kicker">{t("invoices.creditNote.splitTitle")}</span>
            <div style={{ fontSize: 12, opacity: 0.65 }}>
              {t("invoices.creditNote.splitHint")}
            </div>

            <Spin spinning={previewing}>
              {previewError && <Alert type="error" showIcon message={previewError} />}

              {!previewError && !originAllocated && (
                <Alert type="info" showIcon message={t("invoices.creditNote.splitNotAllocated")} />
              )}

              {lines.map((line, index) => (
                <div
                  key={line.budgetItemId}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr minmax(140px, auto)",
                    gap: "10.2px",
                    alignItems: "center",
                  }}
                >
                  <Button
                    block
                    style={{ textAlign: "left" }}
                    disabled={!invoice?.enterpriseId}
                    onClick={() => setPickerFor(index)}
                  >
                    {line.code ? `${line.code} · ` : ""}
                    {line.name ?? "—"}
                  </Button>
                  <InputNumber
                    value={line.amount}
                    onChange={(value) => setLineAmount(index, value ?? 0)}
                    max={0}
                    precision={2}
                    decimalSeparator=","
                    addonAfter="€"
                    style={{ width: "100%" }}
                  />
                </div>
              ))}

              {lines.length > 0 && (
                <div style={{ fontSize: 13, marginTop: "10.2px" }}>
                  {t("invoices.creditNote.splitSum")}: <strong>{formatCurrency(splitSum)}</strong>
                </div>
              )}
            </Spin>

            {splitMismatch && (
              <Alert type="warning" showIcon message={t("invoices.creditNote.splitMismatch")} />
            )}
          </div>

          {/* Identificação do documento -------------------------------- */}
          <div style={{ display: "grid", gridTemplateColumns: "minmax(96px, 0.6fr) 1fr", gap: "10.2px" }}>
            <div>
              {label(t("invoices.creditNote.fieldNumber"))}
              <Controller
                name="invoiceType"
                control={control}
                render={({ field }) => (
                  <Select
                    {...field}
                    style={{ width: "100%" }}
                    options={INVOICE_TYPES}
                    optionLabelProp="value"
                    aria-label={t("invoices.creditNote.fieldNumber")}
                  />
                )}
              />
            </div>
            <div>
              {label(" ")}
              <Controller
                name="invoiceNumber"
                control={control}
                render={({ field }) => <Input {...field} placeholder="2026/17" maxLength={100} />}
              />
              {fieldError(errors.invoiceNumber?.message)}
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
            <div>
              {label(t("invoices.creditNote.fieldDate"))}
              <Controller
                name="invoiceDate"
                control={control}
                render={({ field }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
                    format="DD/MM/YYYY"
                    disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
                    style={{ width: "100%" }}
                  />
                )}
              />
              {fieldError(errors.invoiceDate?.message)}
            </div>
            <div>
              {label(t("invoices.creditNote.fieldAtcud"))}
              <Controller
                name="invoiceAtcud"
                control={control}
                render={({ field }) => (
                  <Input {...field} placeholder="CSDF7T5H-0017" maxLength={100} />
                )}
              />
              {fieldError(errors.invoiceAtcud?.message)}
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
            <div>
              {label(t("invoices.creditNote.fieldNif"))}
              <Controller
                name="supplierNif"
                control={control}
                render={({ field }) => (
                  <Input {...field} inputMode="numeric" maxLength={20} placeholder="500123456" />
                )}
              />
              <div style={{ fontSize: 11, opacity: 0.55, marginTop: 4 }}>
                {t("invoices.creditNote.fieldNifHint")}
              </div>
              {fieldError(errors.supplierNif?.message)}
            </div>
            <div>
              {label(t("invoices.creditNote.fieldDocumentStatus"))}
              <Controller
                name="documentStatus"
                control={control}
                render={({ field }) => (
                  <Select {...field} style={{ width: "100%" }} options={STATUS_OPTIONS} />
                )}
              />
            </div>
          </div>

          <div>
            {label(t("invoices.creditNote.fieldDescription"))}
            <Controller
              name="description"
              control={control}
              render={({ field }) => <Input {...field} maxLength={500} />}
            />
            {fieldError(errors.description?.message)}
          </div>

          <div>
            {label(t("invoices.creditNote.fieldNotes"))}
            <Controller
              name="notes"
              control={control}
              render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={2000} />}
            />
            {fieldError(errors.notes?.message)}
          </div>
        </div>
      </Drawer>

      {invoice?.enterpriseId && (
        <BudgetItemPickerModal
          open={pickerFor !== null}
          enterpriseId={invoice.enterpriseId}
          supplierNif={invoice.supplierNif}
          onClose={() => setPickerFor(null)}
          onPick={(item) => {
            setLines((prev) =>
              prev.map((line, i) =>
                i === pickerFor
                  ? { ...line, budgetItemId: item.id, code: item.code, name: item.name }
                  : line
              )
            );
            setPickerFor(null);
          }}
        />
      )}
    </>
  );
};

export default CreditNoteDrawer;
