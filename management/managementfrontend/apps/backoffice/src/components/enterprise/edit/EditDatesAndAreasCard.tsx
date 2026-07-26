// src/components/enterprises/edit/EditDatesAndAreasCard.tsx
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Card,
  Row,
  Col,
  Button,
  message,
  Form,
  Typography,
  DatePicker,
  InputNumber,
} from "antd";
import {
  CalendarOutlined,
  FieldTimeOutlined,
  ScheduleOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ApartmentOutlined,
  BuildOutlined,
  EnvironmentOutlined,
} from "@ant-design/icons";
import api from "@/api";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";
import dayjs from "dayjs";

const { Title, Text } = Typography;

type Props = {
  data: EnterpriseFullResponseDTO;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onSave: (newData: any) => void;
  onCancel: () => void;
};

const EditDatesAndAreasCard: React.FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  // Inicializar o formulário com os dados atuais
  useEffect(() => {
    if (data) {
      form.setFieldsValue({
        landArea: data.landArea ?? undefined,
        totalArea: data.totalArea ?? undefined,
        startDate: data.startDate ? dayjs(data.startDate) : null,
        completionDate: data.completionDate ? dayjs(data.completionDate) : null,
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

      // Preparar dados para enviar
      const payload = {
        landArea: values.landArea,
        totalArea: values.totalArea,
        startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : null,
        completionDate: values.completionDate ? values.completionDate.format('YYYY-MM-DD') : null,
      };

      // Fazer a requisição de update
      const response = await api.patch(`/enterprise-relations/${enterpriseId}/dates-areas`, payload);
      
      // Combinar dados antigos com novos para manter a integridade
      const updatedData = {
        ...data,
        ...response.data,
      };
      
      onSave(updatedData);
      message.success(t('enterprises.updated'));
    } catch (error) {
      console.error("Erro ao atualizar datas e áreas:", error);
      message.error(t('enterpriseEdit.datesAreasError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      className="rounded-2xl shadow-sm border-0"
      bodyStyle={{ padding: 0 }}
    >
      {/* Header com o MESMO gradiente do OverviewCard */}
      <div
        className="p-6 text-white rounded-t-2xl"
        style={{
          background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)',
          position: 'relative',
          overflow: 'hidden'
        }}
      >
        {/* Padrão decorativo de fundo igual */}
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

        <div className="flex items-center" style={{ position: 'relative', zIndex: 1 }}>
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
            <CalendarOutlined style={{ fontSize: '24px', color: 'white' }} />
          </div>
          <div>
            <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
              {t('enterpriseDatesAndAreas.title')}
            </Title>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
              <FieldTimeOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
              <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                {t('enterpriseDatesAndAreas.subtitle')}
              </Text>
            </div>
          </div>
        </div>
      </div>

      {/* Conteúdo */}
      <div className="p-6">
        <Form form={form} layout="vertical">
          {/* Seção: Cronograma */}
          <div style={{
            padding: '20px',
            background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
            borderRadius: '12px',
            border: '1px solid #e7e5e4',
            marginBottom: '24px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '16px' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                backgroundColor: '#78716c',
                marginRight: '12px'
              }}>
                <ScheduleOutlined style={{ color: 'white', fontSize: '18px' }} />
              </div>
              <div>
                <Text strong style={{ fontSize: '16px', color: '#262626' }}>
                  {t('enterpriseDatesAndAreas.scheduleTitle')}
                </Text>
                <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
                  {t('enterpriseDatesAndAreas.schedulePeriod')}
                </div>
              </div>
            </div>

            <Row gutter={[16, 16]}>
              <Col xs={24} md={12}>
                <Form.Item
                  name="startDate"
                  label={t('enterpriseDatesAndAreas.startDateLabel')}
                  style={{ marginBottom: 0 }}
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    format="DD/MM/YYYY"
                    placeholder={t('enterpriseEdit.startDatePlaceholder')}
                    suffixIcon={<PlayCircleOutlined style={{ color: '#78716c' }} />}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item
                  name="completionDate"
                  label={t('enterpriseDatesAndAreas.endDateLabel')}
                  style={{ marginBottom: 0 }}
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    format="DD/MM/YYYY"
                    placeholder={t('enterpriseEdit.endDatePlaceholder')}
                    suffixIcon={<CheckCircleOutlined style={{ color: '#44403c' }} />}
                  />
                </Form.Item>
              </Col>
            </Row>
          </div>

          {/* Seção: Áreas */}
          <div style={{ marginBottom: '24px' }}>
            <Text type="secondary" style={{ display: 'block', marginBottom: '16px', fontWeight: 500 }}>
              {t('enterpriseDatesAndAreas.dimensionsTitle')}
            </Text>
            
            <div style={{ 
              padding: '20px', 
              backgroundColor: 'white', 
              borderRadius: '8px', 
              border: '1px solid #e8e8e8'
            }}>
              <Row gutter={[16, 16]}>
                {/* Área do Terreno */}
                <Col xs={24} md={8}>
                  <div style={{
                    padding: '20px',
                    backgroundColor: '#fafafa',
                    borderRadius: '12px',
                    border: '1px solid #e8e8e8',
                    height: '100%',
                    textAlign: 'center'
                  }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '48px',
                      height: '48px',
                      borderRadius: '12px',
                      backgroundColor: '#f6ffed',
                      margin: '0 auto 12px'
                    }}>
                      <EnvironmentOutlined style={{ color: '#52c41a', fontSize: '20px' }} />
                    </div>
                    <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '8px' }}>
                      {t('enterpriseDatesAndAreas.landArea')}
                    </Text>
                    <Form.Item name="landArea" style={{ margin: 0 }}>
                      <InputNumber
                        style={{ width: '100%' }}
                        min={0}
                        step={0.1}
                        placeholder="Ex: 1500.75"
                        addonAfter="m²"
                        size="large"
                      />
                    </Form.Item>
                  </div>
                </Col>

                {/* Área Total Construída (AGORA EDITÁVEL) */}
                <Col xs={24} md={8}>
                  <div style={{
                    padding: '20px',
                    backgroundColor: '#fafafa',
                    borderRadius: '12px',
                    border: '1px solid #e8e8e8',
                    height: '100%',
                    textAlign: 'center'
                  }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '48px',
                      height: '48px',
                      borderRadius: '12px',
                      backgroundColor: '#f5f5f4',
                      margin: '0 auto 12px'
                    }}>
                      <BuildOutlined style={{ color: '#78716c', fontSize: '20px' }} />
                    </div>
                    <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '8px' }}>
                      {t('enterpriseDatesAndAreas.builtArea')}
                    </Text>
                    <Form.Item name="totalArea" style={{ margin: 0 }}>
                      <InputNumber
                        style={{ width: '100%' }}
                        min={0}
                        step={0.1}
                        placeholder="Ex: 1200.50"
                        addonAfter="m²"
                        size="large"
                      />
                    </Form.Item>
                  </div>
                </Col>

                {/* Total de Unidades (apenas visual) */}
                <Col xs={24} md={8}>
                  <div style={{
                    padding: '20px',
                    backgroundColor: '#fafafa',
                    borderRadius: '12px',
                    border: '1px solid #e8e8e8',
                    height: '100%',
                    textAlign: 'center'
                  }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '48px',
                      height: '48px',
                      borderRadius: '12px',
                      backgroundColor: '#fff0f6',
                      margin: '0 auto 12px'
                    }}>
                      <ApartmentOutlined style={{ color: '#78716c', fontSize: '20px' }} />
                    </div>
                    <Text strong style={{ color: '#78716c', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '4px' }}>
                      {t('enterpriseDatesAndAreas.totalUnits')}
                    </Text>
                    <Text style={{ fontSize: '18px', fontWeight: 700, color: '#78716c', display: 'block' }}>
                      {data.totalUnits ?? "—"}
                    </Text>
                    <Text type="secondary" style={{ fontSize: '11px' }}>
                      {t('enterpriseDatesAndAreas.calculatedAuto')}
                    </Text>
                  </div>
                </Col>
              </Row>
            </div>
          </div>

          {/* Dica informativa */}
          <div style={{ 
            marginBottom: '20px',
            padding: '12px', 
            backgroundColor: '#f5f5f4', 
            borderRadius: '6px',
            border: '1px solid #e7e5e4'
          }}>
            <Text type="secondary" style={{ fontSize: '12px' }}>
              {t('enterpriseDatesAndAreas.unitsHint')}
            </Text>
          </div>

          {/* Botões de ação */}
          <div style={{ 
            paddingTop: '16px',
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
        </Form>
      </div>
    </Card>
  );
};

export default EditDatesAndAreasCard;