import { useEffect, useState } from "react";
import type { FC, ReactNode } from "react";
import { Button, Checkbox, DatePicker, Drawer, Input, InputNumber, Select, Space, TreeSelect } from "antd";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { createBudgetItem, updateBudgetItem } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { BudgetItemNode, BudgetItemUpsert, BudgetRowKind, BudgetTree } from "@/types/budget";
import { ancestorsOf, buildParentTreeOptions, describeHints } from "./budgetTree";

interface Props {
  open: boolean;
  enterpriseId: string;
  tree: BudgetTree | null;
  /** `null` = criar. */
  item: BudgetItemNode | null;
  /** Só ao criar: a rubrica-mãe já escolhida (ex.: "nova sub-rubrica" a partir de uma linha). */
  defaultParentId?: string | null;
  onClose: () => void;
  onSaved: () => void;
}

type Values = {
  rowKind: BudgetRowKind;
  code: string;
  name: string;
  unit: string;
  quantity: number | null;
  unitPrice: number | null;
  totalPrice: number | null;
  observations: string;
  startDate: string | null;
  endDate: string | null;
  parentId: string | null;
  propagateStartDate: boolean;
  propagateEndDate: boolean;
};

const ROW_KIND_OPTIONS: { value: BudgetRowKind; label: string }[] = [
  { value: "ITEM", label: "Rubrica" },
  { value: "HEADING", label: "Título (agrupa as seguintes)" },
  { value: "NOTE", label: "Nota de contexto" },
];

function emptyValues(defaultParentId: string | null): Values {
  return {
    rowKind: "ITEM",
    code: "",
    name: "",
    unit: "",
    quantity: null,
    unitPrice: null,
    totalPrice: null,
    observations: "",
    startDate: null,
    endDate: null,
    parentId: defaultParentId,
    propagateStartDate: false,
    propagateEndDate: false,
  };
}

/**
 * Criar/editar uma rubrica — o que ficou de fora do frontend do orçamento em
 * 2026-08-06. `rowKind` fica bloqueado numa rubrica que já tem despesas
 * próprias: mudar de `ITEM` para `HEADING`/`NOTE` deixaria essas despesas
 * penduradas numa linha que já não as aceita.
 */
export const BudgetItemDrawer: FC<Props> = ({
  open,
  enterpriseId,
  tree,
  item,
  defaultParentId = null,
  onClose,
  onSaved,
}) => {
  const { t } = useTranslation();
  const [values, setValues] = useState<Values>(emptyValues(defaultParentId));
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setValues(
      item
        ? {
            rowKind: item.rowKind,
            code: item.code ?? "",
            name: item.name,
            unit: item.unit ?? "",
            quantity: item.quantity,
            unitPrice: item.unitPrice,
            totalPrice: item.totalPrice,
            observations: item.observations ?? "",
            startDate: item.startDate,
            endDate: item.endDate,
            parentId: item.parentId,
            propagateStartDate: false,
            propagateEndDate: false,
          }
        : emptyValues(defaultParentId)
    );
  }, [open, item, defaultParentId]);

  const set = <K extends keyof Values>(key: K, value: Values[K]) =>
    setValues((prev) => ({ ...prev, [key]: value }));

  const roots = tree?.roots ?? [];
  const ancestorsMissing = {
    start: ancestorsOf(roots, values.parentId).filter((a) => !a.startDate),
    end: ancestorsOf(roots, values.parentId).filter((a) => !a.endDate),
  };
  const showPropagateStart = !!values.startDate && ancestorsMissing.start.length > 0;
  const showPropagateEnd = !!values.endDate && ancestorsMissing.end.length > 0;

  const parentOptions = buildParentTreeOptions(roots, item?.id);
  const rowKindLocked = !!item && item.ownExpenseCount > 0;

  const submit = async () => {
    if (!values.name.trim()) {
      notificationService.error("Rubrica", "O nome é obrigatório.");
      return;
    }

    const dto: BudgetItemUpsert = {
      enterpriseId,
      parentId: values.parentId,
      rowKind: values.rowKind,
      code: values.code.trim() || null,
      name: values.name.trim(),
      unit: values.unit.trim() || null,
      quantity: values.quantity,
      unitPrice: values.unitPrice,
      totalPrice: values.totalPrice,
      observations: values.observations.trim() || null,
      startDate: values.startDate,
      endDate: values.endDate,
      propagateStartDate: values.propagateStartDate,
      propagateEndDate: values.propagateEndDate,
    };

    setSaving(true);
    try {
      const response = item ? await updateBudgetItem(item.id, dto) : await createBudgetItem(dto);
      if (response.datePropagationHints.length > 0) {
        notificationService.info(
          "Datas",
          `As rubricas ${describeHints(response.datePropagationHints)} continuam sem data. Reabra e marque a opção para as preencher.`
        );
      } else {
        notificationService.success("Rubrica", item ? "Rubrica atualizada." : "Rubrica criada.");
      }
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
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Rubrica</h6>
          <h2 style={{ margin: 0 }}>{item ? "Editar rubrica" : "Nova rubrica"}</h2>
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
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <Field label="Tipo">
            <Select
              value={values.rowKind}
              onChange={(v) => set("rowKind", v)}
              options={ROW_KIND_OPTIONS}
              disabled={rowKindLocked}
            />
            {rowKindLocked && (
              <span style={{ fontSize: 11, opacity: 0.6 }}>
                Já tem despesas próprias — não pode deixar de ser rubrica.
              </span>
            )}
          </Field>
          <Field label="Índice (Art.)">
            <Input
              value={values.code}
              onChange={(e) => set("code", e.target.value)}
              placeholder="Ex.: 4.2.1"
            />
          </Field>
        </div>

        <Field label="Descrição" required>
          <Input value={values.name} onChange={(e) => set("name", e.target.value)} />
        </Field>

        <Field label="Rubrica-mãe">
          <TreeSelect
            value={values.parentId ?? undefined}
            onChange={(v) => set("parentId", v ?? null)}
            treeData={parentOptions}
            allowClear
            showSearch
            treeDefaultExpandAll
            placeholder="Topo (sem mãe)"
            treeNodeFilterProp="title"
            style={{ width: "100%" }}
          />
        </Field>

        {values.rowKind === "ITEM" && (
          <>
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
            <Field label="Preço total">
              <InputNumber
                style={{ width: "100%" }}
                step={0.01}
                min={0}
                value={values.totalPrice}
                onChange={(v) => set("totalPrice", v as number | null)}
              />
            </Field>
          </>
        )}

        <Field label="Observações">
          <Input value={values.observations} onChange={(e) => set("observations", e.target.value)} />
        </Field>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <Field label="Data de início">
            <DatePicker
              style={{ width: "100%" }}
              format="DD/MM/YYYY"
              value={values.startDate ? dayjs(values.startDate) : null}
              onChange={(d) => set("startDate", d ? d.format("YYYY-MM-DD") : null)}
            />
          </Field>
          <Field label="Data de fim">
            <DatePicker
              style={{ width: "100%" }}
              format="DD/MM/YYYY"
              value={values.endDate ? dayjs(values.endDate) : null}
              onChange={(d) => set("endDate", d ? d.format("YYYY-MM-DD") : null)}
            />
          </Field>
        </div>

        {showPropagateStart && (
          <Checkbox
            checked={values.propagateStartDate}
            onChange={(e) => set("propagateStartDate", e.target.checked)}
          >
            <span style={{ fontSize: 13 }}>
              aplicar também às rubricas acima ({ancestorsMissing.start.map((a) => a.code ?? a.name).join(", ")})
            </span>
          </Checkbox>
        )}
        {showPropagateEnd && (
          <Checkbox
            checked={values.propagateEndDate}
            onChange={(e) => set("propagateEndDate", e.target.checked)}
          >
            <span style={{ fontSize: 13 }}>
              aplicar também às rubricas acima ({ancestorsMissing.end.map((a) => a.code ?? a.name).join(", ")})
            </span>
          </Checkbox>
        )}
      </div>
    </Drawer>
  );
};

const Field: FC<{ label: string; required?: boolean; children: ReactNode }> = ({
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
