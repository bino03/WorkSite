import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Drawer, Input, InputNumber, Button, Space, message, Typography, Upload } from "antd";
import { InboxOutlined, FileTextOutlined } from "@ant-design/icons";
import { Receipt } from "lucide-react";
import {
  expenseFormSchema,
  validateInvoiceFile,
  type ExpenseFormValues,
} from "@/components/construction/constructionFormSchemas";
import {
  createExpense,
  updateExpense,
  getExpenseById,
} from "@/services/constructionService";
import type { ConstructionExpenseResponseDTO } from "@/types/construction";

const { Dragger } = Upload;
const { Text } = Typography;

interface ConstructionExpenseUpsertDrawerProps {
  open: boolean;
  onClose: () => void;
  subStageId: string;
  expenseId?: string | null;
  onSaved: (expense: ConstructionExpenseResponseDTO) => void;
}

const defaultValues: ExpenseFormValues = { name: "", price: undefined as unknown as number };

export default function ConstructionExpenseUpsertDrawer({
  open,
  onClose,
  subStageId,
  expenseId,
  onSaved,
}: ConstructionExpenseUpsertDrawerProps) {
  const { t } = useTranslation();
  const isEditing = Boolean(expenseId);

  const [invoiceFile, setInvoiceFile] = useState<File | null>(null);
  const [invoiceError, setInvoiceError] = useState<string | null>(null);
  const [existingInvoice, setExistingInvoice] = useState<{ url: string; mimeType: string | null; filename: string | null } | null>(null);
  const [showUploader, setShowUploader] = useState(true);

  const {
    control,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseFormValues>({
    resolver: zodResolver(expenseFormSchema),
    mode: "onChange",
    defaultValues,
  });

  useEffect(() => {
    if (!open) return;
    setInvoiceFile(null);
    setInvoiceError(null);
    if (expenseId) {
      getExpenseById(expenseId).then((expense) => {
        reset({ name: expense.name, price: expense.price });
        const invoice = expense.invoiceUrl
          ? { url: expense.invoiceUrl, mimeType: expense.mimeType, filename: expense.originalFilename }
          : null;
        setExistingInvoice(invoice);
        setShowUploader(!invoice);
      });
    } else {
      reset(defaultValues);
      setExistingInvoice(null);
      setShowUploader(true);
      setTimeout(() => setFocus("name"), 0);
    }
  }, [open, expenseId, reset, setFocus]);

  const onSubmit = async (values: ExpenseFormValues) => {
    const dto = { name: values.name, price: values.price, subStageId };
    if (expenseId) {
      const updated = await updateExpense(expenseId, dto, invoiceFile ?? undefined);
      message.success(t("constructionExpenses.updated"));
      onSaved(updated);
      onClose();
    } else {
      const created = await createExpense(dto, invoiceFile ?? undefined);
      message.success(t("constructionExpenses.created"));
      onSaved(created);
      reset(defaultValues);
      setInvoiceFile(null);
      setInvoiceError(null);
      setFocus("name");
    }
  };

  return (
    <Drawer
      width={600}
      title={
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Receipt size={18} />
          <span>{isEditing ? t("constructionExpenses.editTitle") : t("constructionExpenses.createTitle")}</span>
        </div>
      }
      open={open}
      onClose={onClose}
      destroyOnClose
      footer={
        <Space style={{ width: "100%", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" loading={isSubmitting} onClick={handleSubmit(onSubmit)}>
            {isEditing ? t("common.save") : t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div>
          <Text strong>{t("constructionExpenses.fields.name")}</Text>
          <Controller
            name="name"
            control={control}
            render={({ field }) => (
              <Input {...field} size="large" placeholder={t("constructionExpenses.fields.namePlaceholder")} style={{ marginTop: 6 }} />
            )}
          />
          {errors.name && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {t(errors.name.message as string)}
            </Text>
          )}
        </div>

        <div>
          <Text strong>{t("constructionExpenses.fields.price")}</Text>
          <Controller
            name="price"
            control={control}
            render={({ field }) => (
              <InputNumber
                {...field}
                size="large"
                className="w-full"
                min={0}
                step={100}
                placeholder={t("constructionExpenses.fields.pricePlaceholder")}
                formatter={(value) => (value !== undefined && value !== null ? `€ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ",") : "")}
                parser={(value) => (value ? (value.replace(/€\s?|(,*)/g, "") as unknown as number) : (0 as unknown as number))}
                style={{ marginTop: 6, width: "100%" }}
              />
            )}
          />
          {errors.price && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {t(errors.price.message as string)}
            </Text>
          )}
        </div>

        <div>
          <Text strong>{t("constructionExpenses.fields.invoice")}</Text>

          {existingInvoice && !showUploader && (
            <div style={{ marginTop: 6 }}>
              <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 8 }}>
                {t("constructionExpenses.fields.currentInvoice")}: {existingInvoice.filename}
              </Text>
              {existingInvoice.mimeType?.startsWith("image/") ? (
                <img src={existingInvoice.url} alt={existingInvoice.filename ?? "invoice"} style={{ maxWidth: "100%", borderRadius: 8 }} />
              ) : (
                <embed src={existingInvoice.url} type="application/pdf" style={{ width: "100%", height: 320, borderRadius: 8 }} />
              )}
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 8 }}>
                <a href={existingInvoice.url} target="_blank" rel="noreferrer" style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                  <FileTextOutlined /> {t("constructionExpenses.fields.openInvoice")}
                </a>
                <Button size="small" onClick={() => setShowUploader(true)}>
                  {t("constructionExpenses.fields.changeDocument")}
                </Button>
              </div>
            </div>
          )}

          {showUploader && (
            <div style={{ marginTop: 6 }}>
              <Dragger
                beforeUpload={(file) => {
                  const error = validateInvoiceFile(file);
                  if (error) {
                    setInvoiceError(error);
                    message.error(t(error));
                    return Upload.LIST_IGNORE;
                  }
                  setInvoiceError(null);
                  setInvoiceFile(file);
                  return false;
                }}
                onRemove={() => setInvoiceFile(null)}
                fileList={invoiceFile ? [{ uid: "invoice", name: invoiceFile.name, status: "done" }] : []}
                maxCount={1}
                accept=".pdf,.jpg,.jpeg,.png"
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">{t("constructionExpenses.fields.invoiceDragHint")}</p>
                <p className="ant-upload-hint">{t("constructionExpenses.fields.invoiceFormats")}</p>
              </Dragger>
              {invoiceError && (
                <Text type="danger" style={{ fontSize: 12 }}>
                  {t(invoiceError)}
                </Text>
              )}
              {existingInvoice && (
                <Button
                  type="link"
                  size="small"
                  style={{ paddingLeft: 0, marginTop: 4 }}
                  onClick={() => {
                    setInvoiceFile(null);
                    setInvoiceError(null);
                    setShowUploader(false);
                  }}
                >
                  {t("constructionExpenses.fields.keepCurrentDocument")}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </Drawer>
  );
}
