import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DragEvent, FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, Checkbox, DatePicker, Drawer, Input, InputNumber, Select, Space, Spin, Typography } from "antd";
import { DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { ReceiptSchema } from "@/components/invoices/receiptFormSchema";
import type { ReceiptForm } from "@/components/invoices/receiptFormSchema";
import {
  createReceipt,
  getReceipt,
  previewReceipt,
  replaceReceiptFile,
  updateReceipt,
} from "@/services/receiptService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, PaymentMethod, PaymentStatus } from "@/types/invoice";
import type { ReceiptCandidateInvoice, SupplierReceiptCreatePayload } from "@/types/receipt";

const { Text } = Typography;

const METHODS: PaymentMethod[] = ["TRANSFERENCIA", "MULTIBANCO", "NUMERARIO", "OUTRO"];
const RECEIPT_ACCEPT = "application/pdf,image/jpeg,image/png";

/** Uma fatura ligada ao recibo, com o valor editável. */
interface LinkRow {
  invoiceId: string;
  invoiceNumber: string | null;
  enterpriseName: string | null;
  /** Fatura de obra cujo nome não veio (as pré-ligadas só trazem o id): não dizer "fora de obra". */
  inProject: boolean;
  amount: number;
  /** O que falta pagar; null quando não se sabe (edição de um recibo existente). */
  remaining: number | null;
  paymentStatus: PaymentStatus | null;
}

interface Props {
  open: boolean;
  /** Faturas a ligar à partida (criar). Ignorado em edição. */
  initialInvoices?: ConstructionInvoice[];
  /** Com id, abre em modo edição desse recibo. */
  receiptId?: string | null;
  onClose: () => void;
  onDone: () => void;
}

const round2 = (n: number) => Math.round(n * 100) / 100;

function remainingOf(invoice: ConstructionInvoice): number {
  if (invoice.netAmount == null) return 0;
  return round2(invoice.netAmount - invoice.paidAmount);
}

function rowFromInvoice(i: ConstructionInvoice): LinkRow {
  return {
    invoiceId: i.id,
    invoiceNumber: i.invoiceNumber,
    enterpriseName: null,
    inProject: i.enterpriseId != null,
    amount: i.netAmount ?? i.totalAmount ?? 0,
    remaining: remainingOf(i),
    paymentStatus: i.paymentStatus,
  };
}

function rowFromCandidate(c: ReceiptCandidateInvoice): LinkRow {
  return {
    invoiceId: c.invoiceId,
    invoiceNumber: c.invoiceNumber,
    enterpriseName: c.enterpriseName,
    inProject: c.enterpriseId != null,
    amount: c.netAmount ?? 0,
    remaining: c.remaining,
    paymentStatus: c.paymentStatus,
  };
}

const emptyForm = (total: number, date: string): ReceiptForm => ({
  receiptNumber: "",
  receiptDate: date,
  totalAmount: total,
  supplierName: "",
  supplierNif: "",
  notes: "",
  registerPayment: false,
  method: "TRANSFERENCIA",
  paidOn: date,
  reference: "",
});

/**
 * Recibo de fornecedor: um ficheiro, ligado a N faturas (de várias obras).
 * O QR preenche os campos; a soma das ligações é comparada ao total do recibo,
 * e uma diferença avisa mas nunca bloqueia (descontos, retenções).
 */
export const ReceiptDrawer: FC<Props> = ({ open, initialInvoices, receiptId, onClose, onDone }) => {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const fileRef = useRef<HTMLInputElement>(null);
  const isEdit = !!receiptId;

  const [file, setFile] = useState<File | null>(null);
  const [existingFile, setExistingFile] = useState<{ url: string | null; name: string | null } | null>(null);
  const [links, setLinks] = useState<LinkRow[]>([]);
  const [candidates, setCandidates] = useState<ReceiptCandidateInvoice[]>([]);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [previewing, setPreviewing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [saving, setSaving] = useState(false);

  const {
    control,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors, isValid },
  } = useForm<ReceiptForm>({
    resolver: zodResolver(ReceiptSchema),
    mode: "onChange",
    defaultValues: emptyForm(0, dayjs().format("YYYY-MM-DD")),
  });

  // Abrir (ou trocar de recibo/seleção): recomeça do zero.
  useEffect(() => {
    if (!open) return;
    setFile(null);
    setExistingFile(null);
    setCandidates([]);
    setWarnings([]);
    const today = dayjs().format("YYYY-MM-DD");

    if (receiptId) {
      setLinks([]);
      setLoading(true);
      getReceipt(receiptId)
        .then((r) => {
          setLinks(
            r.invoices.map((i) => ({
              invoiceId: i.invoiceId,
              invoiceNumber: i.invoiceNumber,
              enterpriseName: i.enterpriseName,
              inProject: i.enterpriseId != null,
              amount: i.amount,
              remaining: null,
              paymentStatus: null,
            }))
          );
          setExistingFile({ url: r.fileUrl, name: r.fileFilename });
          reset({
            ...emptyForm(r.totalAmount, r.receiptDate),
            receiptNumber: r.receiptNumber ?? "",
            supplierName: r.supplierName ?? "",
            supplierNif: r.supplierNif ?? "",
            notes: r.notes ?? "",
          });
        })
        .catch((error) => ErrorHandler.handle(error))
        .finally(() => setLoading(false));
      return;
    }

    const rows = (initialInvoices ?? []).map(rowFromInvoice);
    setLinks(rows);
    const withSupplier = (initialInvoices ?? []).find((i) => i.supplierName || i.supplierNif);
    reset({
      ...emptyForm(round2(rows.reduce((s, r) => s + r.amount, 0)), today),
      supplierName: withSupplier?.supplierName ?? "",
      supplierNif: withSupplier?.supplierNif ?? "",
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, receiptId]);

  const linkedSum = useMemo(() => round2(links.reduce((s, r) => s + (r.amount ?? 0), 0)), [links]);
  const total = watch("totalAmount") ?? 0;
  const diff = round2(total - linkedSum);
  const registerPayment = watch("registerPayment");
  const hasOutstanding = !isEdit && links.some((r) => (r.remaining ?? 0) > 0);

  const pickFile = useCallback(
    async (picked: File | null) => {
      setFile(picked);
      if (!picked) return;
      setPreviewing(true);
      try {
        const preview = await previewReceipt(
          picked,
          links.map((l) => l.invoiceId)
        );
        setWarnings(preview.warnings ?? []);
        setCandidates(preview.candidateInvoices ?? []);
        // Em edição o preview só serve para carregar candidatas: não pisa o que lá está.
        if (!isEdit) {
          const opts = { shouldValidate: true, shouldDirty: true };
          if (preview.receiptNumber) setValue("receiptNumber", preview.receiptNumber, opts);
          if (preview.receiptDate) {
            setValue("receiptDate", preview.receiptDate, opts);
            setValue("paidOn", preview.receiptDate, opts);
          }
          if (preview.totalAmount != null) setValue("totalAmount", preview.totalAmount, opts);
          if (preview.supplierName) setValue("supplierName", preview.supplierName, opts);
          if (preview.supplierNif) setValue("supplierNif", preview.supplierNif, opts);
        }
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setPreviewing(false);
      }
    },
    [links, isEdit, setValue]
  );

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragging(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) void pickFile(dropped);
  };

  const available = candidates.filter((c) => !links.some((l) => l.invoiceId === c.invoiceId));

  const candidateLabel = (c: ReceiptCandidateInvoice) => {
    const status =
      c.remaining > 0
        ? t("invoices.receipt.candidateOutstanding", { amount: formatCurrency(c.remaining) })
        : t("invoices.receipt.candidatePaid");
    return `${c.invoiceNumber ?? "—"} · ${c.enterpriseName ?? t("invoices.receipt.noEnterprise")} · ${formatCurrency(
      c.netAmount ?? 0
    )} · ${status}`;
  };

  const addCandidate = (invoiceId: string) => {
    const c = candidates.find((x) => x.invoiceId === invoiceId);
    if (c) setLinks((prev) => [...prev, rowFromCandidate(c)]);
  };

  const setAmount = (invoiceId: string, amount: number) =>
    setLinks((prev) => prev.map((r) => (r.invoiceId === invoiceId ? { ...r, amount } : r)));

  const submit = async (values: ReceiptForm) => {
    setSaving(true);
    try {
      const base = {
        invoiceLinks: links.map((l) => ({ invoiceId: l.invoiceId, amount: l.amount ?? 0 })),
        receiptNumber: values.receiptNumber.trim() || null,
        receiptDate: values.receiptDate,
        totalAmount: values.totalAmount,
        supplierName: values.supplierName.trim() || null,
        supplierNif: values.supplierNif.trim() || null,
        notes: values.notes.trim() || null,
      };
      if (isEdit && receiptId) {
        await updateReceipt(receiptId, base);
        if (file) await replaceReceiptFile(receiptId, file);
        notificationService.success(t("invoices.receipt.updated"));
      } else {
        if (!file) return;
        const payload: SupplierReceiptCreatePayload = {
          ...base,
          createPayment:
            hasOutstanding && values.registerPayment
              ? {
                  method: values.method,
                  paidOn: values.paidOn || values.receiptDate,
                  reference: values.reference.trim() || null,
                }
              : null,
        };
        const created = await createReceipt(payload, file);
        notificationService.success(t("invoices.receipt.created"));
        if (created.warnings?.length) notificationService.warning(created.warnings.join(" "));
      }
      onDone();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const onSave = handleSubmit((values) => {
    if (diff !== 0) {
      confirm({
        title: t("invoices.receipt.mismatchConfirmTitle"),
        message: t("invoices.receipt.mismatchConfirmBody", {
          total: formatCurrency(values.totalAmount),
          sum: formatCurrency(linkedSum),
        }),
        actionLabel: t("invoices.receipt.mismatchConfirmAction"),
        onConfirm: () => submit(values),
      });
      return;
    }
    void submit(values);
  });

  const fieldError = (message?: string) =>
    message ? (
      <Text type="danger" style={{ fontSize: 12 }}>
        {t(message)}
      </Text>
    ) : null;

  const label = (text: string) => <label style={{ fontSize: 12, opacity: 0.7 }}>{text}</label>;
  const canSave = isValid && links.length > 0 && (isEdit || !!file) && !previewing && !loading;

  return (
    <Drawer
      title={isEdit ? t("invoices.receipt.editTitle") : t("invoices.receipt.addTitle")}
      open={open}
      onClose={onClose}
      width={680}
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={saving}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={onSave} loading={saving} disabled={!canSave}>
            {t("common.save")}
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
          {/* Ficheiro ------------------------------------------------------ */}
          <div>
            {label(isEdit ? t("invoices.receipt.fileReplace") : t("invoices.receipt.file"))}
            <input
              ref={fileRef}
              type="file"
              accept={RECEIPT_ACCEPT}
              hidden
              onChange={(e) => {
                void pickFile(e.target.files?.[0] ?? null);
                e.target.value = "";
              }}
            />
            <div
              role="button"
              tabIndex={0}
              onClick={() => fileRef.current?.click()}
              onKeyDown={(e) => e.key === "Enter" && fileRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              style={{
                border: `1px dashed ${dragging ? "var(--ind-accent-700)" : "var(--ind-color-divider)"}`,
                borderRadius: 2,
                padding: "16px 12px",
                textAlign: "center",
                cursor: "pointer",
                fontSize: 13,
              }}
            >
              <UploadOutlined /> {file ? <strong>{file.name}</strong> : t("invoices.receipt.dropHint")}
              {previewing && <Spin size="small" style={{ marginLeft: 8 }} />}
            </div>
            {isEdit && !file && existingFile?.url && (
              <div style={{ fontSize: 12, marginTop: 4 }}>
                <a href={existingFile.url} target="_blank" rel="noreferrer">
                  {existingFile.name ?? t("invoices.receipt.currentFile")}
                </a>
                <span style={{ opacity: 0.6 }}> · {t("invoices.receipt.editFileHint")}</span>
              </div>
            )}
          </div>

          {warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {warnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              }
            />
          )}

          {/* Campos -------------------------------------------------------- */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
            <div>
              {label(t("invoices.receipt.number"))}
              <Controller
                name="receiptNumber"
                control={control}
                render={({ field }) => <Input {...field} maxLength={60} />}
              />
              {fieldError(errors.receiptNumber?.message)}
            </div>
            <div>
              {label(t("invoices.receipt.date"))}
              <Controller
                name="receiptDate"
                control={control}
                render={({ field }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(d) => field.onChange(d ? d.format("YYYY-MM-DD") : "")}
                    format="DD/MM/YYYY"
                    style={{ width: "100%" }}
                  />
                )}
              />
              {fieldError(errors.receiptDate?.message)}
            </div>
            <div>
              {label(t("invoices.receipt.supplierName"))}
              <Controller
                name="supplierName"
                control={control}
                render={({ field }) => <Input {...field} maxLength={255} />}
              />
              {fieldError(errors.supplierName?.message)}
            </div>
            <div>
              {label(t("invoices.receipt.supplierNif"))}
              <Controller
                name="supplierNif"
                control={control}
                render={({ field }) => <Input {...field} maxLength={20} />}
              />
              {fieldError(errors.supplierNif?.message)}
            </div>
          </div>

          <div>
            {label(t("invoices.receipt.total"))}
            <Controller
              name="totalAmount"
              control={control}
              render={({ field }) => (
                <InputNumber
                  value={field.value}
                  onChange={(value) => field.onChange(value ?? 0)}
                  min={0}
                  precision={2}
                  decimalSeparator=","
                  addonAfter="€"
                  style={{ width: "100%" }}
                />
              )}
            />
            {fieldError(errors.totalAmount?.message)}
          </div>

          {/* Faturas ligadas ---------------------------------------------- */}
          <div className="ind-card" style={{ padding: "13.6px", gap: "10.2px" }}>
            <span className="ind-card-kicker">{t("invoices.receipt.linkedInvoices")}</span>

            {links.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {t("invoices.receipt.noLinks")}
              </Text>
            )}

            {links.map((row) => (
              <div
                key={row.invoiceId}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 150px 32px",
                  gap: 8,
                  alignItems: "center",
                  fontSize: 13,
                }}
              >
                <div>
                  <strong>{row.invoiceNumber ?? "—"}</strong>
                  <div style={{ fontSize: 12, opacity: 0.6 }}>
                    {row.enterpriseName ?? (row.inProject ? "" : t("invoices.receipt.noEnterprise"))}
                    {row.paymentStatus &&
                      ` · ${
                        (row.remaining ?? 0) > 0
                          ? t("invoices.receipt.candidateOutstanding", {
                              amount: formatCurrency(row.remaining ?? 0),
                            })
                          : t("invoices.receipt.candidatePaid")
                      }`}
                  </div>
                </div>
                <InputNumber
                  value={row.amount}
                  onChange={(v) => setAmount(row.invoiceId, v ?? 0)}
                  min={0}
                  precision={2}
                  decimalSeparator=","
                  addonAfter="€"
                  style={{ width: "100%" }}
                />
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label={t("common.remove")}
                  onClick={() => setLinks((prev) => prev.filter((r) => r.invoiceId !== row.invoiceId))}
                />
              </div>
            ))}

            <Select
              showSearch
              value={null}
              placeholder={
                candidates.length === 0
                  ? t("invoices.receipt.addInvoiceNeedFile")
                  : t("invoices.receipt.addInvoice")
              }
              disabled={available.length === 0}
              optionFilterProp="label"
              options={available.map((c) => ({ value: c.invoiceId, label: candidateLabel(c) }))}
              onChange={(id) => id && addCandidate(id)}
              style={{ width: "100%" }}
              notFoundContent={t("invoices.receipt.noCandidates")}
            />

            <div style={{ fontSize: 13 }}>
              {t("invoices.receipt.sum")}: <strong>{formatCurrency(linkedSum)}</strong>
              {" · "}
              {t("invoices.receipt.total")}: <strong>{formatCurrency(total)}</strong>
            </div>
            {diff === 0 ? (
              <Text type="success" style={{ fontSize: 12 }}>
                {t("invoices.receipt.sumMatches")}
              </Text>
            ) : (
              <Alert
                type="warning"
                showIcon
                message={t("invoices.receipt.sumDiffers", { diff: formatCurrency(diff) })}
              />
            )}
          </div>

          {/* Pagamento ----------------------------------------------------- */}
          {hasOutstanding && (
            <div className="ind-card" style={{ padding: "13.6px", gap: "10.2px" }}>
              <Controller
                name="registerPayment"
                control={control}
                render={({ field }) => (
                  <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)}>
                    {t("invoices.receipt.registerPayment")}
                  </Checkbox>
                )}
              />
              {registerPayment && (
                <>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {t("invoices.receipt.registerPaymentHint")}
                  </Text>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
                    <div>
                      {label(t("invoices.payment.fieldMethod"))}
                      <Controller
                        name="method"
                        control={control}
                        render={({ field }) => (
                          <Select
                            {...field}
                            style={{ width: "100%" }}
                            options={METHODS.map((m) => ({
                              value: m,
                              label: t(`invoices.payment.method.${m}`),
                            }))}
                          />
                        )}
                      />
                    </div>
                    <div>
                      {label(t("invoices.payment.fieldPaidOn"))}
                      <Controller
                        name="paidOn"
                        control={control}
                        render={({ field }) => (
                          <DatePicker
                            value={field.value ? dayjs(field.value) : null}
                            onChange={(d) => field.onChange(d ? d.format("YYYY-MM-DD") : "")}
                            format="DD/MM/YYYY"
                            disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
                            style={{ width: "100%" }}
                          />
                        )}
                      />
                      {fieldError(errors.paidOn?.message)}
                    </div>
                  </div>
                  <div>
                    {label(t("invoices.payment.fieldReference"))}
                    <Controller
                      name="reference"
                      control={control}
                      render={({ field }) => <Input {...field} maxLength={255} />}
                    />
                    {fieldError(errors.reference?.message)}
                  </div>
                </>
              )}
            </div>
          )}

          <div>
            {label(t("invoices.payment.fieldNotes"))}
            <Controller
              name="notes"
              control={control}
              render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={2000} />}
            />
            {fieldError(errors.notes?.message)}
          </div>
        </div>
      </Spin>
    </Drawer>
  );
};

export default ReceiptDrawer;
