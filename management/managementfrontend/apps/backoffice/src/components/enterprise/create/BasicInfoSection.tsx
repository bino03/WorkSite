
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Input, Select, Switch } from "antd";
import { Building } from "lucide-react";

import SectionCard from "@/components/enterprise/create/ui/SectionCard";
import Label from "@/components/common/Label";

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
    <SectionCard title={t('buildingCreate.steps.basicInfo')} icon={<Building size={16} />}>
      {/* Seção: Identificação */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{ 
          fontSize: '12px', 
          fontWeight: 600, 
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          🏢 Identificação
        </div>
        
        <div style={{ 
          padding: '16px', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            {/* Nome do Empreendimento */}
            <div className="col-span-12 md:col-span-8">
              <Label required>Nome do Empreendimento</Label>
              <Controller
                control={control}
                name="name"
                render={({ field }) => (
                  <Input
                    {...field}
                    size={FIELD_SIZE}
                    placeholder="Ex.: Residencial Solar, Centro Comercial Vista Alegre"
                    allowClear
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
              {errors.name && (
                <p className="text-red-500 text-xs mt-1">
                  {String(errors.name.message)}
                </p>
              )}
            </div>

            {/* Referência Interna */}
            <div className="col-span-12 md:col-span-4">
              <Label>Referência Interna</Label>
              <Controller
                control={control}
                name="internal_Reference"
                render={({ field }) => (
                  <Input
                    {...field}
                    size={FIELD_SIZE}
                    placeholder="Ex.: EMP-2024-001"
                    allowClear
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Seção: Classificação */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{ 
          fontSize: '12px', 
          fontWeight: 600, 
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          📊 Classificação
        </div>
        
        <div style={{ 
          padding: '16px', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4">
            {/* Tipo */}
            <div className="col-span-12 md:col-span-6">
              <Label required>Tipo de Empreendimento</Label>
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
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
            </div>

            {/* Status */}
            <div className="col-span-12 md:col-span-6">
              <Label required>Status do Projeto</Label>
              <Controller
                control={control}
                name="status"
                render={({ field }) => (
                  <Select
                    {...field}
                    size={FIELD_SIZE}
                    placeholder="Seleciona o status..."
                    options={STATUS_OPTIONS.map(o => ({ value: o.value, label: t(o.labelKey) }))}
                    showSearch
                    optionFilterProp="label"
                    style={{ borderRadius: '6px' }}
                  />
                )}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Seção: Estado */}
      <div>
        <div style={{ 
          fontSize: '12px', 
          fontWeight: 600, 
          color: '#595959',
          marginBottom: '12px',
          textTransform: 'uppercase',
          letterSpacing: '0.5px'
        }}>
          ⚙️ Estado
        </div>
        
        <div style={{ 
          padding: '16px', 
          backgroundColor: 'white', 
          borderRadius: '8px', 
          border: '1px solid #e8e8e8'
        }}>
          <div className="grid grid-cols-12 gap-4 items-center">
            <div className="col-span-12">
              <Controller
                control={control}
                name="is_active"
                render={({ field }) => (
                  <div className="flex items-center gap-3">
                    <Switch
                      checked={field.value}
                      onChange={field.onChange}
                      size="default"
                    />
                    <span style={{ fontWeight: 500, color: '#262626' }}>
                      Empreendimento Ativo
                    </span>
                    <span style={{ 
                      fontSize: '12px', 
                      color: '#8c8c8c',
                      marginLeft: '8px'
                    }}>
                      {field.value ? 'Visível no sistema' : 'Oculto no sistema'}
                    </span>
                  </div>
                )}
              />
            </div>
          </div>
        </div>
      </div>
    </SectionCard>
  );
}