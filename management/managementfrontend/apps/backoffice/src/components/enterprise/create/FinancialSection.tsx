
import { Controller, useFormContext } from "react-hook-form";
import { InputNumber, Select } from "antd";
import { DollarSign } from "lucide-react";
import { useTranslation } from "react-i18next";

import SectionCard from "@/components/enterprise/create/ui/SectionCard";
import Label from "@/components/common/Label";

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
    <SectionCard title={t('enterpriseCreate.financial.sectionTitle')} icon={<DollarSign size={16} />}>
      {/* Seção: Investimento */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{
          fontSize: '12px',
          fontWeight: 600,
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          {t('enterpriseCreate.financial.investmentSection')}
        </div>

        <div style={{
          padding: '16px',
          backgroundColor: 'white',
          borderRadius: '8px',
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            <div className="col-span-12 md:col-span-3">
              <Label>{t('enterpriseCreate.financial.totalInvestment')}</Label>
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
                    placeholder="Ex.: 5000000"
                    formatter={value => `€ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={value => value?.replace(/€\s?|(,*)/g, '') as number}
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
              <p style={{ fontSize: '11px', marginTop: '4px', color: '#8c8c8c' }}>
                {t('enterpriseCreate.financial.totalInvestmentHint')}
              </p>
            </div>

            <div className="col-span-12 md:col-span-4">
              <Label>{t('enterpriseCreate.financial.currency')}</Label>
              <Controller
                control={control}
                name="currency"
                render={({ field }) => (
                  <Select
                    {...field}
                    size={FIELD_SIZE}
                    placeholder={t('enterpriseCreate.financial.currencyPlaceholder')}
                    options={CURRENCY_OPTIONS}
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Seção: Valorização */}
      <div>
        <div style={{
          fontSize: '12px',
          fontWeight: 600,
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          {t('enterpriseCreate.financial.appreciationSection')}
        </div>

        <div style={{
          padding: '16px',
          backgroundColor: 'white',
          borderRadius: '8px',
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            <div className="col-span-12">
              <Label>{t('enterpriseCreate.financial.currentValue')}</Label>
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
                    placeholder="Ex.: 7500000"
                    formatter={value => `€ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={value => value?.replace(/€\s?|(,*)/g, '') as number}
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
              <p style={{ fontSize: '11px', marginTop: '4px', color: '#8c8c8c' }}>
                {t('enterpriseCreate.financial.currentValueHint')}
              </p>
            </div>
          </div>
        </div>
      </div>
    </SectionCard>
  );
}
