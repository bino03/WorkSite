
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { DatePicker, InputNumber } from "antd";
import { Calendar } from "lucide-react";
import dayjs from "dayjs";

import SectionCard from "@/components/enterprise/create/ui/SectionCard";
import Label from "@/components/common/Label";

const FIELD_SIZE = "large" as const;

export default function TimelineMetricsSection() {
  const { t } = useTranslation();
  const { control } = useFormContext();

  return (
    <SectionCard title="Cronograma & Métricas" icon={<Calendar size={16} />}>
      {/* Seção: Cronograma */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{ 
          fontSize: '12px', 
          fontWeight: 600, 
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          📅 Cronograma
        </div>
        
        <div style={{ 
          padding: '16px', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            {/* Data de Início */}
            <div className="col-span-12 md:col-span-6">
              <Label>Data de Início</Label>
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
                    style={{ borderRadius: '6px' }}
                    format="DD/MM/YYYY"
                  />
                )}
              />
            </div>

            {/* Data de Conclusão */}
            <div className="col-span-12 md:col-span-6">
              <Label>Data de Conclusão</Label>
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
                    style={{ borderRadius: '6px' }}
                    format="DD/MM/YYYY"
                  />
                )}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Seção: Métricas de Área */}
      <div>
        <div style={{ 
          fontSize: '12px', 
          fontWeight: 600, 
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          📐 Métricas de Área
        </div>
        
        <div style={{ 
          padding: '16px', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            {/* Área Total Construída */}
            <div className="col-span-12 md:col-span-6">
              <Label>Área Total Construída</Label>
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
                    addonAfter="m²"
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
              <p style={{ 
                fontSize: '11px', 
                marginTop: '4px', 
                color: '#8c8c8c'
              }}>
                Área total de construção
              </p>
            </div>

            {/* Área do Terreno */}
            <div className="col-span-12 md:col-span-6">
              <Label>Área do Terreno</Label>
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
                    addonAfter="m²"
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
              <p style={{ 
                fontSize: '11px', 
                marginTop: '4px', 
                color: '#8c8c8c'
              }}>
                Área total do terreno
              </p>
            </div>
          </div>
        </div>
      </div>
    </SectionCard>
  );
}