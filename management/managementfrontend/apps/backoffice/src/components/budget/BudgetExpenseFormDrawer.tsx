import { useEffect, useState } from "react";
import type { FC } from "react";
import { Button, DatePicker, Drawer, Input, InputNumber, Space } from "antd";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { createExpense, updateExpense } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { BudgetItemNode, ConstructionExpense, ConstructionExpenseUpsert } from "@/types/budget";

interface Props {
  open: boolean;
  budgetItem: BudgetItemNode | null;
  expense: ConstructionExpense | null;
  onClose: () => void;
  onSaved: () => void;
  /** Leva à caixa de entrada, que é por onde entram as despesas com documento. */
  onGoToInvoices: () => void;
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
};

/**
 * Lançamento **sem documento** numa rubrica.
 *
 * O upload de fatura saiu daqui: uma despesa com documento nasce da caixa de
 * entrada (carregar → associar), porque a fatura existe antes de se saber a
 * que rubrica pertence. O que resta é o caso genuinamente manual — mão de
 * obra própria, acerto, gasto sem papel.
 */
export const BudgetExpenseFormDrawer: FC<Props> = ({
  open,
  budgetItem,
  expense,
  onClose,
  onSaved,
  onGoToInvoices,
}) => {
  const { t } = useTranslation();
  const [values, setValues] = useState<Values>(EMPTY);
  const [saving, setSaving] = useState(false);

  /** Editar um lançamento que veio de uma fatura só permite mexer na medição. */
  const fromInvoice = !!expense?.invoice;

  useEffect(() => {
    if (!open) return;
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
          }
        : EMPTY
    );
  }, [open, expense]);

  const set = <K extends keyof Values>(key: K, value: Values[K]) =>
    setValues((prev) => ({ ...prev, [key]: value }));

  const submit = async () => {
    if (!budgetItem) return;
    if (!values.name.trim() || !values.expenseDate || values.totalPrice == null) {
      notificationService.error("Despesa", "Preencha o nome, a data e o valor total.");
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
    };

    setSaving(true);
    try {
      if (expense) await updateExpense(expense.id, dto);
      else await createExpense(dto);
      notificationService.success("Despesa", expense ? "Despesa atualizada." : "Despesa criada.");
      onSaved();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Despesa</h6>
          <h2 style={{ margin: 0 }}>{expense ? "Editar despesa" : "Nova despesa sem fatura"}</h2>
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
        {!expense && (
          <div className="ind-card" style={{ padding: "10.2px", gap: 6 }}>
            <span style={{ fontSize: 13 }}>
              Este formulário é para gastos <b>sem documento</b>.
            </span>
            <span style={{ fontSize: 12, opacity: 0.7 }}>
              Para lançar uma fatura, carregue-a nas Faturas do projeto e associe-a a esta
              rubrica — o QR da AT preenche os campos por si.
            </span>
            <Button size="small" style={{ alignSelf: "flex-start" }} onClick={onGoToInvoices}>
              Ir às faturas
            </Button>
          </div>
        )}

        {fromInvoice && (
          <div className="ind-card" style={{ padding: "10.2px" }}>
            <span style={{ fontSize: 12, opacity: 0.75 }}>
              O nome, a data e o valor vêm da fatura associada — corrija-os no detalhe da fatura
              para os dois ficarem a dizer o mesmo.
            </span>
          </div>
        )}

        <Field label="Nome" required>
          <Input
            value={values.name}
            disabled={fromInvoice}
            onChange={(e) => set("name", e.target.value)}
            placeholder="Mão de obra própria — semana 12"
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
          <Field label="Data" required>
            <DatePicker
              style={{ width: "100%" }}
              format="DD/MM/YYYY"
              disabled={fromInvoice}
              value={values.expenseDate ? dayjs(values.expenseDate) : null}
              disabledDate={(d) => d && d.isAfter(dayjs(), "day")}
              onChange={(d) => set("expenseDate", d ? d.format("YYYY-MM-DD") : "")}
            />
          </Field>
          <Field label="Valor total (€)" required>
            <InputNumber
              style={{ width: "100%" }}
              step={0.01}
              min={0}
              disabled={fromInvoice}
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
