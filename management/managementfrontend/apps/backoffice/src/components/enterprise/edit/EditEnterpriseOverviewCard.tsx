// src/components/enterprises/edit/EditEnterpriseOverviewCard.tsx
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Card,
  Row,
  Col,
  Input,
  Select,
  Button,
  message,
  Form,
  Typography,
} from "antd";
import {
  BuildOutlined,
  NumberOutlined,
  HomeOutlined,
  CheckCircleOutlined,
} from "@ant-design/icons";
import api from "@/api";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";

const { Text } = Typography;
const { TextArea } = Input;

// Enums e opções (usando i18n keys)
const ENTERPRISE_TYPE_OPTIONS = [
  { value: "residential", labelKey: "enterprises.types.residential" },
  { value: "commercial", labelKey: "enterprises.types.commercial" },
  { value: "industrial", labelKey: "enterprises.types.industrial" },
  { value: "mixed_use", labelKey: "enterprises.types.mixed_use" },
  { value: "land", labelKey: "enterpriseEdit.typeLabels.land" },
];

const ENTERPRISE_STATUS_OPTIONS = [
  { value: "planning", labelKey: "enterprises.status.planning" },
  { value: "under_construction", labelKey: "enterprises.status.under_construction" },
  { value: "active", labelKey: "buildings.status.active" },
  { value: "completed", labelKey: "enterprises.status.completed" },
  { value: "archived", labelKey: "buildings.status.archived" },
  { value: "deleted", labelKey: "buildings.status.deleted" },
];

const STATUS_COLORS: Record<string, string> = {
  planning: "#faad14",
  under_construction: "#1890ff",
  active: "#52c41a",
  completed: "#78716c",
  archived: "#8c8c8c",
  deleted: "#ff4d4f",
};

type Props = {
  data: EnterpriseFullResponseDTO;
  onSave: (newData: any) => void;
  onCancel: () => void;
};

const EditEnterpriseOverviewCard: React.FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  // Inicializar o formulário com os dados atuais
  useEffect(() => {
    if (data) {
      form.setFieldsValue({
        name: data.name ?? "",
        internalReference: data.internalReference ?? "",
        type: data.type ?? "residential",
        status: data.status ?? "planning",
        description: data.description ?? "",
      });
    }
  }, [data, form]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const enterpriseId = data.id;
      
      if (!enterpriseId) {
        message.error(t('enterpriseEdit.idNotFound'));
        return;
      }

      setSaving(true);

      // Preparar dados para enviar (apenas campos necessários)
      const payload = {
        name: values.name,
        internalReference: values.internalReference,
        type: values.type,
        status: values.status,
        description: values.description,
      };

      // Fazer a requisição de update
      const response = await api.patch(`/enterprise-relations/${enterpriseId}/overview`, payload);
      
      // Combinar dados antigos com novos para manter a integridade
      const updatedData = {
        ...data,
        ...response.data,
      };
      
      onSave(updatedData);
      message.success(t('enterprises.updated'));
    } catch (error) {
      console.error("Erro ao atualizar empreendimento:", error);
      message.error(t('enterprises.loadError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      className="rounded-2xl shadow-sm border-0"
      bodyStyle={{ padding: 0 }}
    >
      <Form form={form} layout="vertical">
      {/* Header com gradiente (igual ao OverviewCard) */}
      <div
        className="p-6 text-white rounded-t-2xl"
        style={{
          background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)',
          position: 'relative',
          overflow: 'hidden'
        }}
      >
        <div
          style={{
            position: 'absolute',
            top: '-50%',
            right: '-20%',
            width: '200px',
            height: '200px',
            background: 'rgba(255,255,255,0.1)',
            borderRadius: '50%',
            zIndex: 0
          }}
        />

        <div className="flex items-center justify-between" style={{ position: 'relative', zIndex: 1 }}>
          <div className="flex items-center">
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '56px',
              height: '56px',
              borderRadius: '16px',
              backgroundColor: 'rgba(255,255,255,0.2)',
              marginRight: '16px',
              backdropFilter: 'blur(10px)'
            }}>
              <BuildOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              {/* Título editável: Nome do Empreendimento */}
              <Form.Item 
                name="name" 
                style={{ margin: 0 }}
                rules={[{ required: true, message: t('enterpriseEdit.nameRequired') }]}
              >
                <Input
                  placeholder={t('enterpriseEdit.nameInputPlaceholder')}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    fontSize: '24px',
                    fontWeight: 'bold',
                    color: 'white',
                    padding: 0,
                    height: 'auto'
                  }}
                  className="text-white placeholder-white placeholder-opacity-70"
                />
              </Form.Item>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <NumberOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Form.Item name="internalReference" style={{ margin: 0 }}>
                  <Input
                    placeholder={t('enterpriseEdit.internalRefInputPlaceholder')}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      color: 'rgba(255,255,255,0.9)',
                      fontSize: '13px',
                      padding: 0,
                      height: 'auto',
                      width: '200px'
                    }}
                    className="placeholder-white placeholder-opacity-70"
                  />
                </Form.Item>
              </div>
            </div>
          </div>

          <div className="text-right">
            {/* Tag do tipo (editável) */}
           
          </div>
        </div>
      </div>

      {/* Conteúdo */}
      <div className="p-6">
        {/* Status destacado (editável) */}
        <div style={{
          padding: '16px 20px',
          background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
          borderRadius: '12px',
          border: '1px solid #e7e5e4',
          marginBottom: '20px'
        }}>
          <div className="flex items-center justify-between">
            <div className="flex items-center">
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                backgroundColor: STATUS_COLORS[data.status],
                marginRight: '12px'
              }}>
                <CheckCircleOutlined style={{ color: 'white', fontSize: '18px' }} />
              </div>
              <div>
                <Text strong style={{ fontSize: '16px', color: '#262626' }}>
                  {t('enterpriseEdit.projectStatus')}
                </Text>
                <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
                  {t('enterpriseEdit.projectStatusDesc')}
                </div>
              </div>
            </div>
            <Form.Item name="status" style={{ margin: 0 }}>
              <Select
                options={ENTERPRISE_STATUS_OPTIONS.map(o => ({ value: o.value, label: t(o.labelKey) }))}
                style={{
                  backgroundColor: STATUS_COLORS[form.getFieldValue('status')] || STATUS_COLORS[data.status],
                  color: 'white',
                  border: 'none',
                  borderRadius: '16px',
                  fontWeight: 600,
                  fontSize: '13px',
                  width: 180
                }}
              />
            </Form.Item>
          </div>
        </div>

        {/* Informações em cards organizados */}
        <Row gutter={[16, 16]} style={{ marginBottom: '20px' }}>
          <Col xs={24} sm={12}>
            <div style={{
              padding: '20px',
              backgroundColor: '#fafafa',
              borderRadius: '12px',
              border: '1px solid #e8e8e8',
              height: '100%',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  backgroundColor: '#f0f9ff',
                  marginRight: '12px'
                }}>
                  <HomeOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                </div>
                <Text strong style={{ color: '#595959', fontSize: '12px', textTransform: 'uppercase' }}>
                  {t('enterpriseEdit.projectType')}
                </Text>
              </div>
              <Form.Item name="type" style={{ margin: 0 }}>
                <Select
                  options={ENTERPRISE_TYPE_OPTIONS.map(o => ({ value: o.value, label: t(o.labelKey) }))}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </div>
          </Col>

          <Col xs={24} sm={12}>
            <div style={{
              padding: '20px',
              backgroundColor: '#fafafa',
              borderRadius: '12px',
              border: '1px solid #e8e8e8',
              height: '100%',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  backgroundColor: '#f6ffed',
                  marginRight: '12px'
                }}>
                  <NumberOutlined style={{ color: '#52c41a', fontSize: '16px' }} />
                </div>
                <Text strong style={{ color: '#595959', fontSize: '12px', textTransform: 'uppercase' }}>
                  {t('enterpriseEdit.internalRefLabel')}
                </Text>
              </div>
              <Form.Item name="internalReference" style={{ margin: 0 }}>
                <Input placeholder={t('enterpriseEdit.internalRefExPlaceholder')} />
              </Form.Item>
            </div>
          </Col>
        </Row>

        {/* Descrição (editável) */}
        {/* <div style={{
          padding: '20px',
          background: 'linear-gradient(135deg, #fff7e6 0%, #fef3e2 100%)',
          borderRadius: '12px',
          border: '1px solid #ffd591'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              backgroundColor: '#fa8c16',
              marginRight: '12px'
            }}>
              <FileTextOutlined style={{ color: 'white', fontSize: '16px' }} />
            </div>
            <Text strong style={{ color: '#d46b08', fontSize: '14px' }}>
              Descrição do Projeto
            </Text>
          </div>
          <Form.Item name="description" style={{ margin: 0 }}>
            <TextArea
              placeholder="Descreva o empreendimento..."
              rows={4}
              style={{ 
                background: 'transparent', 
                border: 'none', 
                color: '#8c4a00',
                resize: 'none'
              }}
            />
          </Form.Item>
        </div> */}

        {/* Botões de ação */}
        <div style={{
          paddingTop: '20px',
          borderTop: '1px solid #e8e8e8',
          display: 'flex',
          justifyContent: 'flex-end',
          gap: '8px'
        }}>
          <Button onClick={onCancel} disabled={saving} size="large">
            {t('common.cancel')}
          </Button>
          <Button type="primary" onClick={handleSave} loading={saving} size="large">
            {t('common.saveChanges')}
          </Button>
        </div>
      </div>
      </Form>
    </Card>
  );
};

export default EditEnterpriseOverviewCard;