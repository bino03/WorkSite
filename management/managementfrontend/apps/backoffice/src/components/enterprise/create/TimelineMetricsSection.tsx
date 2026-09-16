
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DatePicker, InputNumber } from "antd";
import dayjs from "dayjs";

import BlueprintCard from "@/components/common/BlueprintCard";

const FIELD_SIZE = "large" as const;

type Props = {
  /**
   * `totalUnits` não entra no `PATCH .../dates-areas` (é derivado, não editável) — na edição
   * mostra-se desativado, com uma nota. Na criação fica sempre editável (omitir a prop).
   */
  totalUnitsReadOnly?: boolean;
};

export default function TimelineMetricsSection({ totalUnitsReadOnly = false }: Props = {}) {
  const { t } = useTranslation();
  const { control } = useFormContext();

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <BlueprintCard kicker="Cronograma" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div className="field">
            <label>Início</label>
            <Controller
              control={control}
              name="start_date"
              render={({ field }) => (
                <DatePicker
                  value={field.value ? dayjs(field.value) : null}
                  onChange={(date) => field.onChange(date ? date.format('YYYY-MM-DD') : null)}
                  size={FIELD_SIZE}
                  className="w-full"
                  placeholder={t('enterpriseEdit.startDatePlaceholder')}
                  format="DD/MM/YYYY"
                />
              )}
            />
          </div>

          <div className="field">
            <label>Conclusão prevista</label>
            <Controller
              control={control}
              name="completion_date"
              render={({ field }) => (
                <DatePicker
                  value={field.value ? dayjs(field.value) : null}
                  onChange={(date) => field.onChange(date ? date.format('YYYY-MM-DD') : null)}
                  size={FIELD_SIZE}
                  className="w-full"
                  placeholder={t('enterpriseEdit.startDatePlaceholder')}
                  format="DD/MM/YYYY"
                />
              )}
            />
          </div>
        </div>
      </BlueprintCard>

      <BlueprintCard kicker="Métricas de Área" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div className="field">
            <label>Área total (m²)</label>
            <Controller
              control={control}
              name="total_area"
              render={({ field }) => (
                <InputNumber
                  {...field}
                  size={FIELD_SIZE}
                  className="w-full"
                  min={0}
                  step={0.1}
                  placeholder="Ex.: 2500"
                />
              )}
            />
          </div>

          <div className="field">
            <label>Unidades</label>
            <Controller
              control={control}
              name="total_units"
              render={({ field }) => (
                <InputNumber
                  {...field}
                  size={FIELD_SIZE}
                  className="w-full"
                  min={0}
                  placeholder="0"
                  disabled={totalUnitsReadOnly}
                />
              )}
            />
            {totalUnitsReadOnly && (
              <p style={{ color: "var(--ind-neutral-600)", fontSize: 12, marginTop: 4 }}>
                {t('enterpriseDatesAndAreas.unitsHint')}
              </p>
            )}
          </div>
        </div>
        <div className="field">
          <label>Área do terreno (m²)</label>
          <Controller
            control={control}
            name="land_area"
            render={({ field }) => (
              <InputNumber
                {...field}
                size={FIELD_SIZE}
                className="w-full"
                min={0}
                step={0.1}
                placeholder="Ex.: 5000"
              />
            )}
          />
        </div>
      </BlueprintCard>
    </div>
  );
}