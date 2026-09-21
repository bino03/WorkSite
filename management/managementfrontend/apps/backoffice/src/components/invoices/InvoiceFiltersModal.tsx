import { useEffect, useState } from "react";
import type { FC, ReactNode } from "react";
import { Button, DatePicker, InputNumber, Modal, Radio, Select } from "antd";
import { CloseOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";

import { listSuppliers, listUnknownSupplierNifs } from "@/services/supplierService";
import { RubricSearchField } from "@/components/invoices/RubricSearchField";
import type { Supplier, UnknownSupplierNif } from "@/types/supplier";
import type { InvoiceFilters } from "@/types/invoice";
import { ADVANCED_INVOICE_FILTER_KEYS } from "@/types/invoice";
import { clearedInvoiceFilters, countActiveInvoiceFilters } from "@/components/invoices/invoiceFilters";

interface Props {
  open: boolean;
  enterpriseId: string;
  filters: InvoiceFilters;
  onClose: () => void;
  /** Só as chaves da pesquisa avançada; a página junta-as ao resto e recarrega. */
  onApply: (changes: Partial<InvoiceFilters>) => void;
}

/** Como o utilizador pensa no pagamento — mapeia para `outstanding` + `paymentStatus`. */
type PaymentChoice = "ALL" | "OUTSTANDING" | "UNPAID" | "PARTIAL" | "PAID";

const PAYMENT_OPTIONS: { value: PaymentChoice; label: string }[] = [
  { value: "ALL", label: "Todas" },
  { value: "OUTSTANDING", label: "Por liquidar" },
  { value: "UNPAID", label: "Nada pago" },
  { value: "PARTIAL", label: "Parcialmente pagas" },
  { value: "PAID", label: "Pagas" },
];

const ACCOUNTANT_OPTIONS = [
  { value: "ALL", label: "Todas" },
  { value: "SENT", label: "Enviadas" },
  { value: "PENDING", label: "Por enviar" },
];

const DOCUMENT_TYPE_OPTIONS = [
  { value: "ALL", label: "Todos" },
  { value: "INVOICE", label: "Faturas" },
  { value: "CREDIT_NOTE", label: "Notas de crédito" },
];

const DOCUMENT_STATUS_OPTIONS = [
  { value: "ARCHIVED", label: "Com ficheiro" },
  { value: "MISSING", label: "Sem ficheiro" },
  { value: "TO_PRINT", label: "Por imprimir" },
  { value: "TO_REQUEST", label: "Por pedir ao fornecedor" },
];

const ALLOCATION_OPTIONS = [
  { value: "ALL", label: "Todas" },
  { value: "NONE", label: "Por associar" },
  { value: "PROVISIONAL", label: "Provisória (sem total)" },
  { value: "PARTIAL", label: "Repartição incompleta" },
  { value: "COMPLETE", label: "Repartição completa" },
];

const DATE_PRESETS: { label: string; value: [Dayjs, Dayjs] }[] = [
  { label: "Este mês", value: [dayjs().startOf("month"), dayjs().endOf("month")] },
  {
    label: "Mês passado",
    value: [dayjs().subtract(1, "month").startOf("month"), dayjs().subtract(1, "month").endOf("month")],
  },
  { label: "Este ano", value: [dayjs().startOf("year"), dayjs().endOf("year")] },
  { label: "Ano passado", value: [dayjs().subtract(1, "year").startOf("year"), dayjs().subtract(1, "year").endOf("year")] },
];

/** Uma entrada do Select de fornecedor: do catálogo, ou um NIF visto nas faturas sem ficha. */
interface SupplierOption {
  nif: string;
  name: string | null;
}

function mergeSupplierOptions(catalog: Supplier[], unknown: UnknownSupplierNif[]): SupplierOption[] {
  const known = new Set(catalog.map((s) => s.nif));
  return [
    ...catalog.map((s) => ({ nif: s.nif, name: s.name })),
    ...unknown.filter((u) => !known.has(u.nif)).map((u) => ({ nif: u.nif, name: u.suggestedName })),
  ];
}

/** Um NIF português: 9 dígitos. Escrito à mão, serve mesmo que não esteja em lista nenhuma. */
const NIF_RE = /^\d{9}$/;

function paymentChoiceOf(filters: InvoiceFilters): PaymentChoice {
  if (filters.paymentStatus) return filters.paymentStatus;
  if (filters.outstanding === true) return "OUTSTANDING";
  if (filters.outstanding === false) return "PAID";
  return "ALL";
}

/**
 * A pesquisa avançada da lista de faturas (pedido do utilizador a 2026-09-21).
 *
 * Um modal centrado com os filtros por grupo — fornecedor e documento, estado,
 * datas e valores, rubrica. Edita uma cópia local e só devolve ao "Aplicar";
 * a página não mostra chips, só o ícone com a contagem e o "Limpar filtros".
 * Cada campo responde a uma pergunta que alguém faz de facto ("o que tenho da
 * Casa do Betão?", "o que falta mandar ao contabilista?", "tudo o que foi para
 * a 4.2?") — os campos fiscais (IVA, base) e os derivados ficaram de fora.
 */
export const InvoiceFiltersModal: FC<Props> = ({ open, enterpriseId, filters, onClose, onApply }) => {
  const [draft, setDraft] = useState<InvoiceFilters>(filters);
  const [suppliers, setSuppliers] = useState<SupplierOption[]>([]);
  const [suppliersLoading, setSuppliersLoading] = useState(false);
  const [supplierQuery, setSupplierQuery] = useState("");

  useEffect(() => {
    if (!open) return;
    setDraft(filters);
    // O catálogo é pequeno (dezenas); pede-se uma vez e filtra-se no cliente.
    // Junta-se os NIFs vistos em faturas que ainda não têm ficha — um
    // fornecedor novo aparece nas faturas semanas antes de alguém lhe dar nome,
    // e sem isto o filtro não o encontrava (visto na verificação de 2026-09-21).
    setSuppliersLoading(true);
    Promise.all([listSuppliers(), listUnknownSupplierNifs().catch(() => [] as UnknownSupplierNif[])])
      .then(([catalog, unknown]) => setSuppliers(mergeSupplierOptions(catalog, unknown)))
      .catch(() => setSuppliers([]))
      .finally(() => setSuppliersLoading(false));
  }, [open, filters]);

  const set = <K extends keyof InvoiceFilters>(key: K, value: InvoiceFilters[K]) =>
    setDraft((prev) => ({ ...prev, [key]: value }));

  const setPayment = (choice: PaymentChoice) => {
    setDraft((prev) => ({
      ...prev,
      outstanding: choice === "OUTSTANDING" ? true : null,
      paymentStatus: choice === "UNPAID" || choice === "PARTIAL" || choice === "PAID" ? choice : null,
    }));
  };

  const apply = () => {
    const changes: Partial<InvoiceFilters> = {};
    for (const key of ADVANCED_INVOICE_FILTER_KEYS) {
      (changes as Record<string, unknown>)[key] = draft[key];
    }
    changes.supplierName = draft.supplierName;
    changes.budgetItemLabel = draft.budgetItemLabel;
    onApply(changes);
    onClose();
  };

  const clear = () => setDraft((prev) => ({ ...prev, ...clearedInvoiceFilters() }));

  const dateRange: [Dayjs, Dayjs] | null =
    draft.from && draft.to ? [dayjs(draft.from), dayjs(draft.to)] : null;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      closable={false}
      footer={null}
      centered
      destroyOnClose
      width="min(760px, 94vw)"
      styles={{
        content: {
          background: "var(--ind-color-bg)",
          border: "1px solid var(--ind-color-divider)",
          boxShadow: "var(--ind-shadow-lg)",
          padding: 0,
          maxHeight: "90vh",
          display: "flex",
          flexDirection: "column",
        },
        body: { display: "flex", flexDirection: "column", minHeight: 0, flex: 1 },
        mask: { background: "color-mix(in srgb, #2b2b2d 50%, transparent)" },
      }}
    >
      <i className="ind-corner tl" />
      <i className="ind-corner tr" />
      <i className="ind-corner bl" />
      <i className="ind-corner br" />

      <div
        style={{
          padding: "20.4px 20.4px 13.6px",
          borderBottom: "1px solid var(--ind-color-divider)",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          flex: "none",
        }}
      >
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: "0 0 2px" }}>Faturas</h6>
          <h2 style={{ margin: 0 }}>Pesquisa avançada</h2>
        </div>
        <Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="Fechar" />
      </div>

      <div
        style={{
          padding: "13.6px 20.4px",
          overflowY: "auto",
          flex: 1,
          minHeight: 0,
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
          gap: "13.6px 20.4px",
          alignContent: "start",
        }}
      >
        <Section title="Fornecedor & documento">
          <Field label="Fornecedor">
            <Select
              showSearch
              allowClear
              loading={suppliersLoading}
              value={draft.supplierNif ?? undefined}
              placeholder="Nome ou NIF…"
              optionFilterProp="label"
              onSearch={setSupplierQuery}
              options={[
                ...suppliers.map((s) => ({
                  value: s.nif,
                  label: s.name ? `${s.name} · ${s.nif}` : `${s.nif} · (sem nome)`,
                })),
                // Um NIF escrito à mão que não está em lista nenhuma serve na mesma.
                ...(NIF_RE.test(supplierQuery.trim()) && !suppliers.some((s) => s.nif === supplierQuery.trim())
                  ? [{ value: supplierQuery.trim(), label: `${supplierQuery.trim()} · usar este NIF` }]
                  : []),
              ]}
              onChange={(nif) => {
                const chosen = suppliers.find((s) => s.nif === nif);
                setDraft((prev) => ({
                  ...prev,
                  supplierNif: nif ?? null,
                  supplierName: chosen?.name ?? null,
                }));
              }}
              style={{ width: "100%" }}
            />
          </Field>
          <Field label="Tipo">
            <Radio.Group
              size="small"
              optionType="button"
              buttonStyle="solid"
              options={DOCUMENT_TYPE_OPTIONS}
              value={draft.documentType ?? "ALL"}
              onChange={(e) =>
                set("documentType", e.target.value === "ALL" ? null : e.target.value)
              }
            />
          </Field>
          <Field label="Documento">
            <Select
              allowClear
              placeholder="Qualquer"
              value={draft.documentStatus ?? undefined}
              options={DOCUMENT_STATUS_OPTIONS}
              onChange={(v) => set("documentStatus", v ?? null)}
              style={{ width: "100%" }}
            />
          </Field>
        </Section>

        <Section title="Estado">
          <Field label="Pagamento">
            <Radio.Group
              size="small"
              optionType="button"
              buttonStyle="solid"
              options={PAYMENT_OPTIONS}
              value={paymentChoiceOf(draft)}
              onChange={(e) => setPayment(e.target.value as PaymentChoice)}
            />
          </Field>
          <Field label="Contabilidade">
            <Radio.Group
              size="small"
              optionType="button"
              buttonStyle="solid"
              options={ACCOUNTANT_OPTIONS}
              value={
                draft.sentToAccountant === null ? "ALL" : draft.sentToAccountant ? "SENT" : "PENDING"
              }
              onChange={(e) =>
                set(
                  "sentToAccountant",
                  e.target.value === "ALL" ? null : e.target.value === "SENT"
                )
              }
            />
          </Field>
          <Field label="Classificação">
            <Radio.Group
              size="small"
              optionType="button"
              buttonStyle="solid"
              options={ALLOCATION_OPTIONS}
              value={draft.allocationStatus ?? "ALL"}
              onChange={(e) =>
                set("allocationStatus", e.target.value === "ALL" ? null : e.target.value)
              }
            />
          </Field>
        </Section>

        <Section title="Datas & valores">
          <Field label="Data da fatura">
            <DatePicker.RangePicker
              value={dateRange}
              format="DD/MM/YYYY"
              placeholder={["De", "Até"]}
              presets={DATE_PRESETS}
              allowEmpty={[true, true]}
              onChange={(range) =>
                setDraft((prev) => ({
                  ...prev,
                  from: range?.[0] ? range[0].format("YYYY-MM-DD") : null,
                  to: range?.[1] ? range[1].format("YYYY-MM-DD") : null,
                }))
              }
              style={{ width: "100%" }}
            />
          </Field>
          <Field label="Valor (€)">
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <InputNumber
                placeholder="de"
                min={0}
                decimalSeparator=","
                value={draft.minAmount}
                onChange={(v) => set("minAmount", v ?? null)}
                style={{ flex: 1 }}
              />
              <span style={{ opacity: 0.5 }}>–</span>
              <InputNumber
                placeholder="a"
                min={0}
                decimalSeparator=","
                value={draft.maxAmount}
                onChange={(v) => set("maxAmount", v ?? null)}
                style={{ flex: 1 }}
              />
            </div>
          </Field>
        </Section>

        <Section title="Rubrica" hint="Apanha também tudo o que está nas sub-rubricas.">
          {draft.budgetItemId ? (
            <div
              className="ind-card ind-blueprint"
              style={{
                padding: "8px 10px",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: 8,
                borderColor: "var(--ind-color-accent)",
              }}
            >
              <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 13, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {draft.budgetItemLabel}
              </span>
              <Button
                size="small"
                type="text"
                onClick={() => setDraft((prev) => ({ ...prev, budgetItemId: null, budgetItemLabel: null }))}
              >
                Trocar
              </Button>
            </div>
          ) : (
            <RubricSearchField
              enterpriseId={enterpriseId}
              selectedId={null}
              onPick={(item) =>
                setDraft((prev) => ({
                  ...prev,
                  budgetItemId: item.id,
                  budgetItemLabel: item.code ? `${item.code} · ${item.name}` : item.name,
                }))
              }
            />
          )}
        </Section>
      </div>

      <div
        style={{
          padding: "13.6px 20.4px",
          borderTop: "1px solid var(--ind-color-divider)",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "10.2px",
          flex: "none",
        }}
      >
        <Button type="text" onClick={clear} disabled={countActiveInvoiceFilters(draft) === 0}>
          Limpar tudo
        </Button>
        <div style={{ display: "flex", gap: "6.8px" }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="primary" onClick={apply}>
            Aplicar
          </Button>
        </div>
      </div>
    </Modal>
  );
};

const Section: FC<{ title: string; hint?: string; children: ReactNode }> = ({ title, hint, children }) => (
  <div style={{ display: "flex", flexDirection: "column", gap: "10.2px", minWidth: 0 }}>
    <div>
      <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>{title}</h6>
      {hint && <div style={{ fontSize: 11, opacity: 0.55, marginTop: 2 }}>{hint}</div>}
    </div>
    {children}
  </div>
);

const Field: FC<{ label: string; children: ReactNode }> = ({ label, children }) => (
  <div>
    <label style={{ fontSize: 12, opacity: 0.7, display: "block", marginBottom: 4 }}>{label}</label>
    {children}
  </div>
);

export default InvoiceFiltersModal;
