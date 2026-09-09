import { useEffect, useState } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, Drawer, Input, Radio, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";

import { TransferInvoiceSchema } from "@/components/invoices/transferFormSchema";
import type { TransferInvoiceForm } from "@/components/invoices/transferFormSchema";
import { transferInvoice } from "@/services/invoiceService";
import { searchEnterprises, type EnterpriseOption } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { ConstructionInvoice, InvoiceScope, InvoiceTransferResult } from "@/types/invoice";

const { Text } = Typography;

interface Props {
  open: boolean;
  invoice: ConstructionInvoice | null;
  /** Fluxo da quarentena: "atribuir uma obra" é transferir, com o âmbito fixo em `PROJECT`. */
  lockToProject?: boolean;
  onClose: () => void;
  /** Depois de transferir — o pai fecha o detalhe, recarrega, e trata do `suggestIncident`. */
  onTransferred: (result: InvoiceTransferResult) => void;
}

const SCOPE_LABEL: Record<InvoiceScope, string> = {
  PROJECT: "invoices.transfer.scope.PROJECT",
  COMPANY: "invoices.transfer.scope.COMPANY",
  UNIDENTIFIED: "invoices.transfer.scope.UNIDENTIFIED",
};

/**
 * Transfere uma fatura de âmbito/obra. A razão é obrigatória e o backend apaga
 * as despesas — o aviso e a razão são a paragem consciente, não há `useConfirm`
 * à parte. As notas de crédito ligadas seguem a fatura.
 */
export const TransferInvoiceDrawer: FC<Props> = ({
  open,
  invoice,
  lockToProject = false,
  onClose,
  onTransferred,
}) => {
  const { t } = useTranslation();
  const [options, setOptions] = useState<EnterpriseOption[]>([]);
  const [searching, setSearching] = useState(false);

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<TransferInvoiceForm>({
    resolver: zodResolver(TransferInvoiceSchema),
    mode: "onChange",
    defaultValues: { targetScope: "PROJECT", targetEnterpriseId: null, reason: "" },
  });

  const targetScope = watch("targetScope");

  useEffect(() => {
    if (!open) return;
    setOptions([]);
    reset({ targetScope: "PROJECT", targetEnterpriseId: null, reason: "" });
  }, [open, reset]);

  const runSearch = async (q: string) => {
    if (q.trim().length < 2) {
      setOptions([]);
      return;
    }
    setSearching(true);
    try {
      setOptions(await searchEnterprises(q.trim()));
    } catch {
      setOptions([]);
    } finally {
      setSearching(false);
    }
  };

  const onSubmit = handleSubmit(async (values) => {
    if (!invoice) return;
    try {
      const result = await transferInvoice(invoice.id, {
        targetScope: values.targetScope,
        targetEnterpriseId:
          values.targetScope === "PROJECT" ? values.targetEnterpriseId : null,
        reason: values.reason.trim(),
      });
      notificationService.success(t("invoices.transfer.success"));
      // Se `suggestIncident`, o pai (`InvoiceDetailDrawer` → página) abre o
      // `IncidentDrawer` já com esta fatura.
      onTransferred(result);
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
      title={t("invoices.transfer.drawerTitle")}
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
            {t("invoices.transfer.submit")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Alert
          type="warning"
          showIcon
          message={t("invoices.transfer.warning")}
        />

        {!lockToProject && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldScope")}</label>
            <Controller
              name="targetScope"
              control={control}
              render={({ field }) => (
                <Radio.Group
                  {...field}
                  style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 4 }}
                >
                  <Radio value="PROJECT">{t(SCOPE_LABEL.PROJECT)}</Radio>
                  <Radio value="COMPANY">{t(SCOPE_LABEL.COMPANY)}</Radio>
                  <Radio value="UNIDENTIFIED">{t(SCOPE_LABEL.UNIDENTIFIED)}</Radio>
                </Radio.Group>
              )}
            />
          </div>
        )}

        {targetScope === "PROJECT" && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldEnterprise")}</label>
            <Controller
              name="targetEnterpriseId"
              control={control}
              render={({ field }) => (
                <Select
                  showSearch
                  value={field.value ?? undefined}
                  onChange={(value) => field.onChange(value ?? null)}
                  onSearch={runSearch}
                  filterOption={false}
                  loading={searching}
                  placeholder={t("invoices.transfer.enterprisePlaceholder")}
                  notFoundContent={null}
                  style={{ width: "100%" }}
                  options={options.map((o) => ({ value: o.id, label: o.name }))}
                />
              )}
            />
            {fieldError(errors.targetEnterpriseId?.message)}
          </div>
        )}

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldReason")}</label>
          <Controller
            name="reason"
            control={control}
            render={({ field }) => (
              <Input.TextArea {...field} rows={3} maxLength={1000} showCount />
            )}
          />
          {fieldError(errors.reason?.message)}
        </div>
      </div>
    </Drawer>
  );
};

export default TransferInvoiceDrawer;
