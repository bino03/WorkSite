import { useEffect, useMemo, useRef, useState } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, Input, InputNumber, Select, Space, Typography } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { AggregatePaymentSchema } from "@/components/invoices/paymentFormSchema";
import type { AggregatePaymentForm } from "@/components/invoices/paymentFormSchema";
import { registerAggregatePayment } from "@/services/paymentService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, LeftOutInvoice, PaymentMethod } from "@/types/invoice";

const { Text } = Typography;

const METHODS: PaymentMethod[] = ["TRANSFERENCIA", "MULTIBANCO", "NUMERARIO", "OUTRO"];
const PROOF_ACCEPT = "application/pdf,image/jpeg,image/png";

interface Props {
  open: boolean;
  /** As faturas selecionadas na lista — todas da mesma obra. */
  invoices: ConstructionInvoice[];
  onClose: () => void;
  onDone: () => void;
}

function remainingOf(invoice: ConstructionInvoice): number {
  if (invoice.netAmount == null) return 0;
  return Math.round((invoice.netAmount - invoice.paidAmount) * 100) / 100;
}

/**
 * Um movimento que liquida várias faturas. A soma do que falta pagar nas
 * faturas escolhidas aparece ao vivo; se o valor do movimento não bater, nada é
 * gravado e a app diz quais faturas tirar da seleção.
 */
export const AggregatePaymentDrawer: FC<Props> = ({ open, invoices, onClose, onDone }) => {
  const { t } = useTranslation();
  const proofRef = useRef<HTMLInputElement>(null);
  const [proof, setProof] = useState<File | null>(null);
  const [leftOut, setLeftOut] = useState<LeftOutInvoice[]>([]);

  const selectedTotal = useMemo(
    () => Math.round(invoices.reduce((sum, i) => sum + remainingOf(i), 0) * 100) / 100,
    [invoices]
  );
  const differentSuppliers = useMemo(
    () => new Set(invoices.map((i) => i.supplierNif ?? "")).size > 1,
    [invoices]
  );

  const {
    control,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<AggregatePaymentForm>({
    resolver: zodResolver(AggregatePaymentSchema),
    mode: "onChange",
    defaultValues: {
      paidOn: dayjs().format("YYYY-MM-DD"),
      method: "TRANSFERENCIA",
      amount: selectedTotal,
      reference: "",
      notes: "",
    },
  });

  useEffect(() => {
    if (!open) return;
    setProof(null);
    setLeftOut([]);
    reset({
      paidOn: dayjs().format("YYYY-MM-DD"),
      method: "TRANSFERENCIA",
      amount: selectedTotal,
      reference: "",
      notes: "",
    });
  }, [open, selectedTotal, reset]);

  const amount = watch("amount");
  const diff = Math.round(((amount ?? 0) - selectedTotal) * 100) / 100;

  const onSubmit = handleSubmit(async (values) => {
    // A lista das que ficam de fora é a resposta a **este** envio. Sem a
    // limpar, um envio seguinte que falhe por outro motivo deixava no ecrã o
    // aviso do envio anterior a contradizer o erro novo.
    setLeftOut([]);
    try {
      const result = await registerAggregatePayment(
        {
          invoiceIds: invoices.map((i) => i.id),
          paidOn: values.paidOn,
          method: values.method,
          amount: values.amount,
          reference: values.reference.trim() || null,
          notes: values.notes.trim() || null,
        },
        proof
      );
      if (result.created) {
        notificationService.success(t("invoices.payment.success"));
        onDone();
        onClose();
      } else {
        setLeftOut(result.leftOut);
      }
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

  return (
    <Drawer
      title={t("invoices.payment.aggregateDrawerTitle")}
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
            {t("invoices.payment.registerAggregate")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <div style={{ fontSize: 13 }}>
          {t("invoices.payment.selectedCount", { count: invoices.length })} ·{" "}
          {t("invoices.payment.selectedTotal")}: <strong>{formatCurrency(selectedTotal)}</strong>
        </div>

        {differentSuppliers && (
          <Alert type="info" showIcon message={t("invoices.payment.differentSupplierWarning")} />
        )}

        {leftOut.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message={t("invoices.payment.leftOutTitle")}
            description={
              <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
                {leftOut.map((l) => (
                  <li key={l.invoiceId}>
                    {l.invoiceNumber ?? l.supplierName ?? l.invoiceId} — {formatCurrency(l.remaining)}
                  </li>
                ))}
              </ul>
            }
          />
        )}

        {diff !== 0 && (
          <Text type={diff > 0 ? "danger" : "warning"} style={{ fontSize: 12 }}>
            {diff > 0
              ? t("invoices.payment.surplus", { amount: formatCurrency(diff) })
              : t("invoices.payment.shortfall", { amount: formatCurrency(-diff) })}
          </Text>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldPaidOn")}</label>
            <Controller
              name="paidOn"
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
            {fieldError(errors.paidOn?.message)}
          </div>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldMethod")}</label>
            <Controller
              name="method"
              control={control}
              render={({ field }) => (
                <Select
                  {...field}
                  style={{ width: "100%" }}
                  options={METHODS.map((m) => ({ value: m, label: t(`invoices.payment.method.${m}`) }))}
                />
              )}
            />
          </div>
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.movementAmount")}</label>
          <Controller
            name="amount"
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
          {fieldError(errors.amount?.message)}
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldReference")}</label>
          <Controller
            name="reference"
            control={control}
            render={({ field }) => <Input {...field} maxLength={255} />}
          />
          {fieldError(errors.reference?.message)}
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldNotes")}</label>
          <Controller
            name="notes"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={2000} />}
          />
          {fieldError(errors.notes?.message)}
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldProof")}</label>
          <div>
            <input
              ref={proofRef}
              type="file"
              accept={PROOF_ACCEPT}
              hidden
              onChange={(e) => setProof(e.target.files?.[0] ?? null)}
            />
            <Button icon={<UploadOutlined />} onClick={() => proofRef.current?.click()}>
              {proof ? proof.name : t("common.select", "Escolher ficheiro")}
            </Button>
            {proof && (
              <Button type="link" onClick={() => setProof(null)}>
                {t("common.remove", "Remover")}
              </Button>
            )}
          </div>
        </div>
      </div>
    </Drawer>
  );
};

export default AggregatePaymentDrawer;
