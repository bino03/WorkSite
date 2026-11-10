import { useEffect, useState } from "react";
import type { CSSProperties, FC } from "react";
import { Select } from "antd";

import { listSuppliers, listUnknownSupplierNifs } from "@/services/supplierService";
import { listEnterprisesForBackup } from "@/services/enterpriseService";
import type { Supplier, UnknownSupplierNif } from "@/types/supplier";
import type { InvoiceScope } from "@/types/invoice";

/**
 * Os selects pequenos das listas que atravessam obras — a pesquisa global e
 * "A pagar" (2026-11-10). Cada um carrega a sua lista uma vez ao montar.
 */

/** Uma entrada do Select de fornecedor: do catálogo, ou um NIF visto nas faturas sem ficha. */
export interface SupplierOption {
  nif: string;
  name: string | null;
}

/**
 * O catálogo mais os NIFs vistos em faturas que ainda não têm ficha — um
 * fornecedor novo aparece nas faturas semanas antes de alguém lhe dar nome, e
 * sem isto o filtro não o encontrava (visto na verificação de 2026-09-21).
 */
export function mergeSupplierOptions(catalog: Supplier[], unknown: UnknownSupplierNif[]): SupplierOption[] {
  const known = new Set(catalog.map((s) => s.nif));
  return [
    ...catalog.map((s) => ({ nif: s.nif, name: s.name })),
    ...unknown.filter((u) => !known.has(u.nif)).map((u) => ({ nif: u.nif, name: u.suggestedName })),
  ];
}

export async function loadSupplierOptions(): Promise<SupplierOption[]> {
  const [catalog, unknown] = await Promise.all([
    listSuppliers(),
    listUnknownSupplierNifs().catch(() => [] as UnknownSupplierNif[]),
  ]);
  return mergeSupplierOptions(catalog, unknown);
}

interface SupplierFilterSelectProps {
  value: string | null;
  onChange: (nif: string | null) => void;
  style?: CSSProperties;
  size?: "small" | "middle";
  /**
   * As opções já dadas por quem chama — em "A pagar", só os fornecedores com
   * faturas por liquidar (pedido do utilizador, 2026-11-10). Sem elas, o
   * catálogo inteiro mais os NIFs sem ficha.
   */
  options?: SupplierOption[];
}

/** Fornecedor por NIF, com pesquisa por nome ou NIF. Vazio = todos. */
export const SupplierFilterSelect: FC<SupplierFilterSelectProps> = ({
  value,
  onChange,
  style,
  size = "small",
  options: givenOptions,
}) => {
  const [loadedOptions, setLoadedOptions] = useState<SupplierOption[]>([]);
  const [loading, setLoading] = useState(givenOptions === undefined);

  useEffect(() => {
    if (givenOptions !== undefined) return;
    loadSupplierOptions()
      .then(setLoadedOptions)
      .catch(() => setLoadedOptions([]))
      .finally(() => setLoading(false));
  }, [givenOptions]);

  const options = givenOptions ?? loadedOptions;

  return (
    <Select
      allowClear
      showSearch
      size={size}
      placeholder="Fornecedor"
      loading={loading}
      value={value ?? undefined}
      onChange={(nif?: string) => onChange(nif ?? null)}
      optionFilterProp="label"
      popupMatchSelectWidth={false}
      style={style}
      options={options.map((o) => ({ value: o.nif, label: o.name ? `${o.name} · ${o.nif}` : o.nif }))}
    />
  );
};

/** O que o select de obra escolhe: uma obra, ou um âmbito sem obra. Os dois a null = todas. */
export interface WorkFilter {
  enterpriseId: string | null;
  scope: Extract<InvoiceScope, "COMPANY" | "UNIDENTIFIED"> | null;
}

export const ALL_WORKS: WorkFilter = { enterpriseId: null, scope: null };

const COMPANY_VALUE = "__COMPANY__";
const UNIDENTIFIED_VALUE = "__UNIDENTIFIED__";

interface WorkFilterSelectProps {
  value: WorkFilter;
  onChange: (value: WorkFilter) => void;
  /** "Por identificar" só faz sentido onde a quarentena aparece (não em "A pagar"). */
  includeUnidentified?: boolean;
  style?: CSSProperties;
  size?: "small" | "middle";
}

/**
 * Um só select para "onde": as despesas da empresa, a quarentena e as obras
 * (sem as de teste, que estas listas não mostram). Excluem-se umas às outras.
 */
export const WorkFilterSelect: FC<WorkFilterSelectProps> = ({ value, onChange, includeUnidentified = false, style, size = "small" }) => {
  const [works, setWorks] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listEnterprisesForBackup()
      .then((all) => setWorks(all.filter((e) => !e.isTest).map((e) => ({ id: e.id, name: e.name }))))
      .catch(() => setWorks([]))
      .finally(() => setLoading(false));
  }, []);

  const selected =
    value.scope === "COMPANY"
      ? COMPANY_VALUE
      : value.scope === "UNIDENTIFIED"
        ? UNIDENTIFIED_VALUE
        : (value.enterpriseId ?? undefined);

  const handleChange = (next?: string) => {
    if (next === COMPANY_VALUE) onChange({ enterpriseId: null, scope: "COMPANY" });
    else if (next === UNIDENTIFIED_VALUE) onChange({ enterpriseId: null, scope: "UNIDENTIFIED" });
    else onChange({ enterpriseId: next ?? null, scope: null });
  };

  return (
    <Select
      allowClear
      showSearch
      size={size}
      placeholder="Obra"
      loading={loading}
      value={selected}
      onChange={handleChange}
      optionFilterProp="label"
      popupMatchSelectWidth={false}
      style={style}
      options={[
        { value: COMPANY_VALUE, label: "Despesas da empresa" },
        ...(includeUnidentified ? [{ value: UNIDENTIFIED_VALUE, label: "Por identificar" }] : []),
        ...works.map((w) => ({ value: w.id, label: w.name })),
      ]}
    />
  );
};
