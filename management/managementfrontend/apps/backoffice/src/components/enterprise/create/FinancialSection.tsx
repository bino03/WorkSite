
import { Controller, useFormContext } from "react-hook-form";
import { InputNumber, Select } from "antd";
import { useTranslation } from "react-i18next";

import BlueprintCard from "@/components/common/BlueprintCard";

const FIELD_SIZE = "large" as const;

export default function FinancialSection() {
  const { control } = useFormContext();
  const { t } = useTranslation();

  const CURRENCY_OPTIONS = [
    { value: "EUR", label: t('enterpriseCreate.financial.currencyEUR') },
    { value: "USD", label: t('enterpriseCreate.financial.currencyUSD') },
    { value: "GBP", label: t('enterpriseCreate.financial.currencyGBP') },
    { value: "CHF", label: t('enterpriseCreate.financial.currencyCHF') },
    { value: "CAD", label: t('enterpriseCreate.financial.currencyCAD') },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <BlueprintCard kicker="Investimento" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "10.2px" }}>
          <div className="field">
            <label>Investimento total (€)</label>
            <Controller
              control={control}
              name="total_investment"
              render={({ field }) => (
                <InputNumber
                  {...field}
                  size={FIELD_SIZE}
                  className="w-full"
                  min={0}
                  step={1000}
                  placeholder="0"
                  formatter={value => `€ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={value => value?.replace(/€\s?|(,*)/g, '') as unknown as number}
                />
              )}
            />
          </div>

          <div className="field">
            <label>{t('enterpriseCreate.financial.currency')}</label>
            <Controller
              control={control}
              name="currency"
              render={({ field }) => (
                <Select
                  {...field}
                  size={FIELD_SIZE}
                  placeholder={t('enterpriseCreate.financial.currencyPlaceholder')}
                  options={CURRENCY_OPTIONS}
                />
              )}
            />
          </div>
        </div>
      </BlueprintCard>

      <BlueprintCard kicker="Valorização" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div className="field">
          <label>Valor atual estimado (€)</label>
          <Controller
            control={control}
            name="current_value"
            render={({ field }) => (
              <InputNumber
                {...field}
                size={FIELD_SIZE}
                className="w-full"
                min={0}
                step={1000}
                placeholder="0"
                formatter={value => `€ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                parser={value => value?.replace(/€\s?|(,*)/g, '') as unknown as number}
              />
            )}
          />
        </div>
      </BlueprintCard>
    </div>
  );
}
