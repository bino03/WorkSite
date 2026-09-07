import { useEffect } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, DatePicker, Drawer, Input, InputNumber, Select, Space, Typography } from "antd";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { DEFAULT_INVOICE_TYPE, INVOICE_TYPES, joinInvoiceNumber } from "@/components/invoices/invoiceNumber";
import { InvoiceRegisterSchema } from "@/components/invoices/invoiceFormSchema";
import type { InvoiceRegisterForm } from "@/components/invoices/invoiceFormSchema";
import { registerInvoice } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { ConstructionInvoice, InvoiceScope } from "@/types/invoice";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** Vem da página onde o botão foi carregado; não é escolha do utilizador. */
  scope: InvoiceScope;
  /** Só quando `scope === "PROJECT"`. */
  enterpriseId?: string;
  onClose: () => void;
  onCreated: (invoice: ConstructionInvoice) => void;
}

const STATUS_OPTIONS = [
  { value: "TO_REQUEST", label: "Por pedir ao fornecedor" },
  { value: "TO_PRINT", label: "Por imprimir" },
  { value: "MISSING", label: "Sem ficheiro" },
];

const SCOPE_TITLE: Record<InvoiceScope, string> = {
  PROJECT: "Registar fatura sem ficheiro",
  COMPANY: "Registar despesa da empresa",
  UNIDENTIFIED: "Registar fatura por identificar",
};

/**
 * Regista uma fatura que ainda não tem documento — a que está por pedir ao
 * fornecedor ou por imprimir.
 *
 * Até à V24 isto era impossível: a fatura *era* o ficheiro, e sem ficheiro não
 * havia registo nenhum. O ficheiro junta-se depois, pela galeria do detalhe, e
 * é aí que o estado passa a "arquivada" — por isso `ARCHIVED` nem sequer é uma
 * opção aqui (o backend recusa-a com `INVOICE_017`).
 */
export const InvoiceRegisterDrawer: FC<Props> = ({
  open,
  scope,
  enterpriseId,
  onClose,
  onCreated,
}) => {
  const { t } = useTranslation();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<InvoiceRegisterForm>({
    resolver: zodResolver(InvoiceRegisterSchema),
    mode: "onChange",
    defaultValues: {
      scope,
      enterpriseId: scope === "PROJECT" ? (enterpriseId ?? null) : null,
      supplierName: "",
      supplierNif: "",
      invoiceType: DEFAULT_INVOICE_TYPE,
      invoiceNumber: "",
      invoiceAtcud: "",
      invoiceDate: "",
      totalAmount: null,
      description: "",
      documentStatus: "TO_REQUEST",
      possibleEnterprises: "",
      askWhom: "",
      notes: "",
    },
  });

  // Reabrir a drawer depois de gravar não pode trazer o que lá estava — e o
  // `scope` muda conforme a página que a abriu.
  useEffect(() => {
    if (!open) return;
    reset({
      scope,
      enterpriseId: scope === "PROJECT" ? (enterpriseId ?? null) : null,
      supplierName: "",
      supplierNif: "",
      invoiceType: DEFAULT_INVOICE_TYPE,
      invoiceNumber: "",
      invoiceAtcud: "",
      invoiceDate: "",
      totalAmount: null,
      description: "",
      documentStatus: "TO_REQUEST",
      possibleEnterprises: "",
      askWhom: "",
      notes: "",
    });
  }, [open, scope, enterpriseId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      const created = await registerInvoice({
        scope: values.scope,
        enterpriseId: values.enterpriseId,
        supplierName: values.supplierName.trim() || null,
        supplierNif: values.supplierNif.trim() || null,
        invoiceNumber: joinInvoiceNumber(values.invoiceType, values.invoiceNumber) || null,
        invoiceAtcud: values.invoiceAtcud.trim() || null,
        invoiceDate: values.invoiceDate || null,
        totalAmount: values.totalAmount,
        description: values.description.trim() || null,
        documentStatus: values.documentStatus,
        possibleEnterprises: values.possibleEnterprises.trim() || null,
        askWhom: values.askWhom.trim() || null,
        notes: values.notes.trim() || null,
      });
      notificationService.success("Fatura registada", "O ficheiro junta-se depois, no detalhe.");
      onCreated(created);
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
      title={SCOPE_TITLE[scope]}
      open={open}
      onClose={onClose}
      width={600}
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
            {t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>Estado do documento</label>
          <Controller
            name="documentStatus"
            control={control}
            render={({ field }) => (
              <Select {...field} options={STATUS_OPTIONS} style={{ width: "100%" }} />
            )}
          />
          <div style={{ fontSize: 11, opacity: 0.55, marginTop: 4 }}>
            Passa a "arquivada" sozinho quando o ficheiro for junto.
          </div>
        </div>

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>Descrição</label>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <Input {...field} placeholder="O que é esta despesa" maxLength={500} />
            )}
          />
          {fieldError(errors.description?.message)}
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>Fornecedor</label>
            <Controller
              name="supplierName"
              control={control}
              render={({ field }) => <Input {...field} maxLength={255} />}
            />
            {fieldError(errors.supplierName?.message)}
          </div>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>NIF</label>
            <Controller
              name="supplierNif"
              control={control}
              render={({ field }) => <Input {...field} maxLength={20} />}
            />
            {fieldError(errors.supplierNif?.message)}
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: "10.2px" }}>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>Tipo</label>
            <Controller
              name="invoiceType"
              control={control}
              render={({ field }) => (
                <Select {...field} options={INVOICE_TYPES} style={{ width: "100%" }} />
              )}
            />
          </div>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>Número</label>
            <Controller
              name="invoiceNumber"
              control={control}
              render={({ field }) => <Input {...field} placeholder="2026/114" maxLength={100} />}
            />
            {fieldError(errors.invoiceNumber?.message)}
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>Data</label>
            <Controller
              name="invoiceDate"
              control={control}
              render={({ field }) => (
                <DatePicker
                  value={field.value ? dayjs(field.value) : null}
                  onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
                  format="DD/MM/YYYY"
                  style={{ width: "100%" }}
                />
              )}
            />
            {fieldError(errors.invoiceDate?.message)}
          </div>
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>Total</label>
            <Controller
              name="totalAmount"
              control={control}
              render={({ field }) => (
                <InputNumber
                  value={field.value}
                  onChange={(value) => field.onChange(value ?? null)}
                  min={0}
                  precision={2}
                  style={{ width: "100%" }}
                />
              )}
            />
            {fieldError(errors.totalAmount?.message)}
          </div>
        </div>

        {/* Só a quarentena precisa disto: numa obra já se sabe de quem é. */}
        {scope === "UNIDENTIFIED" && (
          <>
            <div>
              <label style={{ fontSize: 12, opacity: 0.7 }}>Talvez seja de</label>
              <Controller
                name="possibleEnterprises"
                control={control}
                render={({ field }) => (
                  <Input {...field} placeholder="Vila Aleu? Villa Atrium?" maxLength={500} />
                )}
              />
              {fieldError(errors.possibleEnterprises?.message)}
            </div>
            <div>
              <label style={{ fontSize: 12, opacity: 0.7 }}>Perguntar a</label>
              <Controller
                name="askWhom"
                control={control}
                render={({ field }) => <Input {...field} maxLength={255} />}
              />
              {fieldError(errors.askWhom?.message)}
            </div>
          </>
        )}

        <div>
          <label style={{ fontSize: 12, opacity: 0.7 }}>Notas</label>
          <Controller
            name="notes"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={3} maxLength={2000} />}
          />
          {fieldError(errors.notes?.message)}
        </div>

        {fieldError(errors.enterpriseId?.message)}
      </div>
    </Drawer>
  );
};

export default InvoiceRegisterDrawer;
