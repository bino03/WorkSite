import { useEffect } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Drawer, Input, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";

import { IncidentSchema } from "@/components/invoices/incidentFormSchema";
import type { IncidentForm } from "@/components/invoices/incidentFormSchema";
import { createIncident } from "@/services/incidentService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { IncidentInvoiceRef } from "@/types/incident";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** Faturas a ligar — normalmente a que acabou de ser transferida. Não vazio. */
  presetInvoices: IncidentInvoiceRef[];
  /** Texto inicial do corpo — ex. a razão da transferência. */
  presetBody?: string;
  onClose: () => void;
  onCreated: () => void;
}

/**
 * Registar uma inconsistência a partir de uma ou mais faturas — tipicamente a que
 * acabou de ser transferida. As faturas ligadas vêm fixas; o utilizador escreve
 * o título e o corpo (markdown).
 */
export const IncidentDrawer: FC<Props> = ({
  open,
  presetInvoices,
  presetBody = "",
  onClose,
  onCreated,
}) => {
  const { t } = useTranslation();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<IncidentForm>({
    resolver: zodResolver(IncidentSchema),
    mode: "onChange",
    defaultValues: { title: "", body: "", invoiceIds: [] },
  });

  useEffect(() => {
    if (!open) return;
    reset({
      title: "",
      body: presetBody,
      invoiceIds: presetInvoices.map((i) => i.id),
    });
  }, [open, presetBody, presetInvoices, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await createIncident({
        title: values.title.trim(),
        body: values.body.trim(),
        invoiceIds: values.invoiceIds,
      });
      notificationService.success(t("incidents.createSuccess"));
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

  return (
    <Drawer
      title={t("incidents.drawerTitle")}
      open={open}
      onClose={onClose}
      width="min(560px, 94vw)"
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
            {t("incidents.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("incidents.fieldInvoices")}</label>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>
            {presetInvoices.map((inv) => (
              <span key={inv.id} className="ind-tag ind-tag-neutral">
                {inv.invoiceNumber ?? inv.id.slice(0, 8)}
                {inv.supplierName ? ` · ${inv.supplierName}` : ""}
              </span>
            ))}
          </div>
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("incidents.fieldTitle")}</label>
          <Controller
            name="title"
            control={control}
            render={({ field }) => <Input {...field} maxLength={200} />}
          />
          {fieldError(errors.title?.message)}
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("incidents.fieldBody")}</label>
          <Controller
            name="body"
            control={control}
            render={({ field }) => (
              <Input.TextArea {...field} rows={6} maxLength={10000} showCount />
            )}
          />
          <div style={{ fontSize: 11, opacity: 0.55, marginTop: 4 }}>
            {t("incidents.bodyHint")}
          </div>
          {fieldError(errors.body?.message)}
        </div>
      </div>
    </Drawer>
  );
};

export default IncidentDrawer;
