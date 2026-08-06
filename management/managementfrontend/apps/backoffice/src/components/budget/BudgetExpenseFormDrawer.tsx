import { useEffect, useState } from "react";
import type { FC } from "react";
import { Button, DatePicker, Drawer, Input, InputNumber, Space, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { createExpense, scanInvoice, updateExpense } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency, formatDate } from "@/utils/formatters";
import { validateInvoiceFile } from "@/components/construction/constructionFormSchemas";
import type {
  BudgetItemNode,
  ConstructionExpense,
  ConstructionExpenseUpsert,
  RegisteredInvoiceRef,
} from "@/types/budget";

interface Props {
  open: boolean;
  enterpriseId: string;
  budgetItem: BudgetItemNode | null;
  expense: ConstructionExpense | null;
  onClose: () => void;
  onSaved: () => void;
}

type Values = {
  name: string;
  description: string;
  expenseDate: string;
  unit: string;
  quantity: number | null;
  unitPrice: number | null;
  totalPrice: number | null;
  observations: string;
  supplierNif: string;
  invoiceNumber: string;
  invoiceAtcud: string;
};

const EMPTY: Values = {
  name: "",
  description: "",
  expenseDate: "",
  unit: "",
  quantity: null,
  unitPrice: null,
  totalPrice: null,
  observations: "",
  supplierNif: "",
  invoiceNumber: "",
  invoiceAtcud: "",
};

/** Campos que a leitura do QR consegue preencher. */
const SCANNED_FIELDS = ["expenseDate", "totalPrice", "supplierNif", "invoiceNumber", "invoiceAtcud"] as const;

export const BudgetExpenseFormDrawer: FC<Props> = ({
  open,
  enterpriseId,
  budgetItem,
  expense,
  onClose,
  onSaved,
}) => {
  const { t } = useTranslation();
  const [values, setValues] = useState<Values>(EMPTY);
  const [file, setFile] = useState<File | null>(null);
  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [scanState, setScanState] = useState<"none" | "ok" | "failed">("none");
  const [scannedKeys, setScannedKeys] = useState<string[]>([]);
  const [duplicates, setDuplicates] = useState<RegisteredInvoiceRef[]>([]);

  useEffect(() => {
    if (!open) return;
    setFile(null);
    setScanState("none");
    setScannedKeys([]);
    setDuplicates([]);
    setValues(
      expense
        ? {
            name: expense.name,
            description: expense.description ?? "",
            expenseDate: expense.expenseDate,
            unit: expense.unit ?? "",
            quantity: expense.quantity,
            unitPrice: expense.unitPrice,
            totalPrice: expense.totalPrice,
            observations: expense.observations ?? "",
            supplierNif: expense.supplierNif ?? "",
            invoiceNumber: expense.invoiceNumber ?? "",
            invoiceAtcud: expense.invoiceAtcud ?? "",
          }
        : EMPTY
    );
  }, [open, expense]);

  const set = <K extends keyof Values>(key: K, value: Values[K]) =>
    setValues((prev) => ({ ...prev, [key]: value }));

  /**
   * Escolher o ficheiro dispara a leitura do QR. Só preenche campos **vazios** —
   * nunca sobrepõe o que a pessoa já escreveu.
   */
  const handleFile = async (picked: File) => {
    const invalid = validateInvoiceFile(picked);
    if (invalid) {
      notificationService.error("Fatura", t(invalid));
      return;
    }
    setFile(picked);
    setScanning(true);
    setScanState("none");
    try {
      const result = await scanInvoice(enterpriseId, picked);
      setDuplicates(result.alreadyRegistered ?? []);

      if (!result.read) {
        setScanState("failed");
        return;
      }

      const filled: string[] = [];
      setValues((prev) => {
        const next = { ...prev };
        if (!next.expenseDate && result.invoiceDate) {
          next.expenseDate = result.invoiceDate;
          filled.push("expenseDate");
        }
        if (next.totalPrice == null && result.totalAmount != null) {
          next.totalPrice = result.totalAmount;
          filled.push("totalPrice");
        }
        if (!next.supplierNif && result.issuerNif) {
          next.supplierNif = result.issuerNif;
          filled.push("supplierNif");
        }
        if (!next.invoiceNumber && result.documentNumber) {
          next.invoiceNumber = result.documentNumber;
          filled.push("invoiceNumber");
        }
        if (!next.invoiceAtcud && result.atcud) {
          next.invoiceAtcud = result.atcud;
          filled.push("invoiceAtcud");
        }
        return next;
      });
      setScannedKeys(filled);
      setScanState("ok");

      result.warnings?.forEach((w) => notificationService.warning("Fatura", w));
    } catch (error) {
      ErrorHandler.handle(error);
      setScanState("failed");
    } finally {
      setScanning(false);
    }
  };

  const submit = async () => {
    if (!budgetItem) return;
    if (!values.name.trim() || !values.expenseDate || values.totalPrice == null) {
      notificationService.error("Despesa", "Preencha o nome, a data da fatura e o preço total.");
      return;
    }

    const dto: ConstructionExpenseUpsert = {
      budgetItemId: budgetItem.id,
      name: values.name.trim(),
      description: values.description || null,
      expenseDate: values.expenseDate,
      unit: values.unit || null,
      quantity: values.quantity,
      unitPrice: values.unitPrice,
      totalPrice: values.totalPrice,
      observations: values.observations || null,
      supplierNif: values.supplierNif || null,
      invoiceNumber: values.invoiceNumber || null,
      invoiceAtcud: values.invoiceAtcud || null,
    };

    setSaving(true);
    try {
      if (expense) await updateExpense(expense.id, dto, file ?? undefined);
      else await createExpense(dto, file ?? undefined);
      notificationService.success("Despesa", expense ? "Despesa atualizada." : "Despesa criada.");
      onSaved();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const scannedLabel = (key: string) => (scannedKeys.includes(key) ? " · lido do QR" : "");

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={600}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Despesa</h6>
          <h2 style={{ margin: 0 }}>{expense ? "Editar despesa" : "Nova despesa"}</h2>
        </div>
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={saving}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={submit} loading={saving}>
            {t("common.save")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        {/* O ficheiro é o primeiro campo porque é a entrada que produz as outras. */}
        <Upload.Dragger
          accept=".pdf,.jpg,.jpeg,.png"
          maxCount={1}
          showUploadList={false}
          beforeUpload={(picked) => {
            handleFile(picked as File);
            return false;
          }}
        >
          <p style={{ margin: 0 }}>
            <InboxOutlined style={{ fontSize: 20, color: "var(--ind-color-accent)" }} />
          </p>
          <p style={{ fontSize: 13, margin: "6px 0 0" }}>
            {file
              ? file.name
              : "Anexar fatura (PDF, JPEG ou PNG até 25 MB) — a leitura do QR pré-preenche os campos"}
          </p>
        </Upload.Dragger>

        {scanning && <span style={{ fontSize: 12, opacity: 0.7 }}>A ler o QR da fatura…</span>}

        {scanState === "ok" && scannedKeys.length > 0 && (
          <span className="ind-tag ind-tag-accent" style={{ width: "fit-content" }}>
            {scannedKeys.length} de {SCANNED_FIELDS.length} campos preenchidos pela leitura do QR — pode
            corrigir
          </span>
        )}

        {/* Não é erro: fornecedor estrangeiro, documento antigo ou digitalização má. */}
        {scanState === "failed" && (
          <div style={{ fontSize: 12, opacity: 0.7 }}>
            Não foi encontrado QR legível — preencha os campos manualmente.
          </div>
        )}

        {duplicates.length > 0 && (
          <div
            className="ind-card ind-blueprint"
            style={{ padding: "10.2px", gap: 6, borderColor: "var(--ind-color-accent)" }}
          >
            <i className="ind-corner tl" />
            <i className="ind-corner tr" />
            <i className="ind-corner bl" />
            <i className="ind-corner br" />
            <span className="ind-card-kicker">Esta fatura já está lançada</span>
            {duplicates.map((d) => (
              <div key={d.expenseId} style={{ fontSize: 13 }}>
                {d.budgetItemName}
                {d.budgetItemCode ? ` (${d.budgetItemCode})` : ""} · {formatDate(d.expenseDate)} ·{" "}
                {formatCurrency(d.totalPrice)}
              </div>
            ))}
            <div style={{ fontSize: 11, opacity: 0.7 }}>
              Repartir uma fatura por várias rubricas é normal — confirme que não é o mesmo lançamento a
              duplicar.
            </div>
          </div>
        )}

        <Field label="Nome" required>
          <Input
            value={values.name}
            onChange={(e) => set("name", e.target.value)}
            placeholder="Fatura Betão Liz"
          />
        </Field>

        <Field label="Descrição">
          <Input
            value={values.description}
            onChange={(e) => set("description", e.target.value)}
            placeholder="Opcional"
          />
        </Field>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <Field label={`Data da fatura${scannedLabel("expenseDate")}`} required>
            <DatePicker
              style={{ width: "100%" }}
              format="DD/MM/YYYY"
              value={values.expenseDate ? dayjs(values.expenseDate) : null}
              disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
              onChange={(d) => set("expenseDate", d ? d.format("YYYY-MM-DD") : "")}
            />
          </Field>
          <Field label={`Preço total (€)${scannedLabel("totalPrice")}`} required>
            <InputNumber
              style={{ width: "100%" }}
              step={0.01}
              min={0}
              value={values.totalPrice}
              onChange={(v) => set("totalPrice", v as number | null)}
            />
          </Field>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10.2px" }}>
          <Field label="Un.">
            <Input value={values.unit} onChange={(e) => set("unit", e.target.value)} />
          </Field>
          <Field label="Quantidade">
            <InputNumber
              style={{ width: "100%" }}
              min={0}
              value={values.quantity}
              onChange={(v) => set("quantity", v as number | null)}
            />
          </Field>
          <Field label="Preço Un.">
            <InputNumber
              style={{ width: "100%" }}
              step={0.01}
              min={0}
              value={values.unitPrice}
              onChange={(v) => set("unitPrice", v as number | null)}
            />
          </Field>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10.2px" }}>
          <Field label={`Fornecedor (NIF)${scannedLabel("supplierNif")}`}>
            <Input value={values.supplierNif} onChange={(e) => set("supplierNif", e.target.value)} />
          </Field>
          <Field label={`Nº fatura${scannedLabel("invoiceNumber")}`}>
            <Input value={values.invoiceNumber} onChange={(e) => set("invoiceNumber", e.target.value)} />
          </Field>
          <Field label={`ATCUD${scannedLabel("invoiceAtcud")}`}>
            <Input value={values.invoiceAtcud} onChange={(e) => set("invoiceAtcud", e.target.value)} />
          </Field>
        </div>

        <Field label="Observações">
          <Input value={values.observations} onChange={(e) => set("observations", e.target.value)} />
        </Field>
      </div>
    </Drawer>
  );
};

const Field: FC<{ label: string; required?: boolean; children: React.ReactNode }> = ({
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
