
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Input, Select, Switch } from "antd";

import BlueprintCard from "@/components/common/BlueprintCard";

const FIELD_SIZE = "large" as const;

const TYPE_OPTIONS = [
  { value: "residential", labelKey: "enterprises.types.residential" },
  { value: "commercial", labelKey: "enterprises.types.commercial" },
  { value: "industrial", labelKey: "enterprises.types.industrial" },
  { value: "mixed_use", labelKey: "enterprises.types.mixed_use" },
];

const STATUS_OPTIONS = [
  { value: "archived", labelKey: "buildings.status.archived" },
  { value: "planning", labelKey: "enterprises.status.planning" },
  { value: "under_construction", labelKey: "enterprises.status.under_construction" },
  { value: "completed", labelKey: "enterprises.status.completed" },
  { value: "active", labelKey: "buildings.status.active" },
];

export default function BasicInfoSection() {
  const { t } = useTranslation();
  const { control, formState: { errors } } = useFormContext();

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <BlueprintCard kicker="Identificação" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div className="field">
          <label>Nome</label>
          <Controller
            control={control}
            name="name"
            render={({ field }) => (
              <Input
                {...field}
                size={FIELD_SIZE}
                placeholder="Nome do empreendimento"
              />
            )}
          />
          {errors.name && (
            <p style={{ color: "var(--ind-neutral-700)", fontSize: 12, marginTop: 4 }}>
              {String(errors.name.message)}
            </p>
          )}
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div className="field">
            <label>Construtora</label>
            <Controller
              control={control}
              name="construction_company"
              render={({ field }) => (
                <Input {...field} size={FIELD_SIZE} placeholder="Empresa construtora" />
              )}
            />
          </div>
          <div className="field">
            <label>Arquiteto</label>
            <Controller
              control={control}
              name="architect"
              render={({ field }) => (
                <Input {...field} size={FIELD_SIZE} placeholder="Atelier ou arquiteto" />
              )}
            />
          </div>
        </div>

        <div className="field">
          <label>Referência Interna</label>
          <Controller
            control={control}
            name="internal_Reference"
            render={({ field }) => (
              <Input {...field} size={FIELD_SIZE} placeholder="Ex.: EMP-2024-001" />
            )}
          />
        </div>
      </BlueprintCard>

      <BlueprintCard kicker="Classificação & Estado" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div className="field">
            <label>Tipo</label>
            <Controller
              control={control}
              name="type"
              render={({ field }) => (
                <Select
                  {...field}
                  size={FIELD_SIZE}
                  placeholder="Seleciona o tipo..."
                  options={TYPE_OPTIONS.map(o => ({ value: o.value, label: t(o.labelKey) }))}
                  showSearch
                  optionFilterProp="label"
                />
              )}
            />
          </div>

          <div className="field">
            <label>Estado</label>
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select
                  {...field}
                  size={FIELD_SIZE}
                  placeholder="Seleciona o estado..."
                  options={STATUS_OPTIONS.map(o => ({ value: o.value, label: t(o.labelKey) }))}
                  showSearch
                  optionFilterProp="label"
                />
              )}
            />
          </div>
        </div>

        <Controller
          control={control}
          name="is_active"
          render={({ field }) => (
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <Switch checked={field.value} onChange={field.onChange} />
              <span style={{ fontSize: 13 }}>
                {field.value ? "Ativo — visível no sistema" : "Inativo — oculto no sistema"}
              </span>
            </div>
          )}
        />
      </BlueprintCard>
    </div>
  );
}