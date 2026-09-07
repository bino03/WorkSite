import { useEffect, useMemo, useRef, useState } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, Input, InputNumber, Select, Space, Typography } from "antd";
import { UploadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { MarkPaidSchema } from "@/components/invoices/paymentFormSchema";
import type { MarkPaidForm } from "@/components/invoices/paymentFormSchema";
import { markInvoicePaid } from "@/services/paymentService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { ConstructionInvoice, PaymentMethod } from "@/types/invoice";

const { Text } = Typography;

const METHODS: PaymentMethod[] = ["TRANSFERENCIA", "MULTIBANCO", "NUMERARIO", "OUTRO"];
const PROOF_ACCEPT = "application/pdf,image/jpeg,image/png";

interface Props {
  open: boolean;
  invoice: ConstructionInvoice | null;
  onClose: () => void;
  /** Chamado depois de gravar — o pai recarrega a fatura. */
  onPaid: () => void;
}

/**
 * Marca **uma** fatura como paga: data, método, referência e prova opcional.
 * O valor vem preenchido com o que falta liquidar; abaixo disso a fatura fica
 * `PARTIAL`. Uma fatura sem total não pode ser paga — o backend recusa.
 */
export const MarkPaidDrawer: FC<Props> = ({ open, invoice, onClose, onPaid }) => {
  const { t } = useTranslation();
  const proofRef = useRef<HTMLInputElement>(null);
  const [proof, setProof] = useState<File | null>(null);

  const remaining = useMemo(() => {
    if (!invoice || invoice.netAmount == null) return null;
    return Math.round((invoice.netAmount - invoice.paidAmount) * 100) / 100;
  }, [invoice]);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<MarkPaidForm>({
    resolver: zodResolver(MarkPaidSchema),
    mode: "onChange",
    defaultValues: {
      paidOn: dayjs().format("YYYY-MM-DD"),
      method: "TRANSFERENCIA",
      amount: remaining,
      reference: "",
      notes: "",
    },
  });

  useEffect(() => {
    if (!open) return;
    setProof(null);
    reset({
      paidOn: dayjs().format("YYYY-MM-DD"),
      method: "TRANSFERENCIA",
      amount: remaining,
      reference: "",
      notes: "",
    });
  }, [open, remaining, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!invoice) return;
    try {
      await markInvoicePaid(
        invoice.id,
        {
          paidOn: values.paidOn,
          method: values.method,
          amount: values.amount ?? undefined,
          reference: values.reference.trim() || null,
          notes: values.notes.trim() || null,
        },
        proof
      );
      notificationService.success(t("invoices.payment.success"));
      onPaid();
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

  const noTotal = !invoice || invoice.netAmount == null;

  return (
    <Drawer
      title={t("invoices.payment.drawerTitle")}
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button
            type="primary"
            onClick={onSubmit}
            loading={isSubmitting}
            disabled={!isValid || noTotal}
          >
            {t("invoices.payment.markPaid")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        {noTotal && (
          <Alert
            type="warning"
            showIcon
            message={t("invoices.formErrors.totalRequired", "Preencha o total da fatura antes de a marcar como paga.")}
          />
        )}

        {invoice && remaining != null && (
          <div style={{ fontSize: 13, opacity: 0.75 }}>
            {t("invoices.payment.selectedTotal")}: <strong>{formatCurrency(remaining)}</strong>
            {invoice.paidAmount > 0 && (
              <> · {t("invoices.payment.status.PARTIAL")}: {formatCurrency(invoice.paidAmount)}</>
            )}
          </div>
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
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldAmount")}</label>
          <Controller
            name="amount"
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
            {t("invoices.payment.fieldAmountHint")}
          </div>
          {fieldError(errors.amount?.message)}
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.payment.fieldReference")}</label>
          <Controller
            name="reference"
            control={control}
            render={({ field }) => (
              <Input {...field} placeholder="extrato ABANCA 28-08-2026" maxLength={255} />
            )}
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

export default MarkPaidDrawer;
