import React, { useState, useEffect } from 'react';
import { useTranslation } from "react-i18next";
import { 
  Card, 
  Input, 
  InputNumber, 
  Select, 
  Button, 
  Row, 
  Col, 
  Typography,
  message,
  Form
} from 'antd';
import { 
  EuroCircleOutlined, 
  DollarCircleOutlined, 
  RiseOutlined,
  TeamOutlined,
  BuildOutlined,
  EditOutlined,
  CalculatorOutlined,
  SaveOutlined,
  CloseOutlined
} from '@ant-design/icons';
import api from "@/api";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";

const { Title, Text } = Typography;
const { Option } = Select;

// Tipos
export type CurrencyType = 'EUR' | 'USD' | 'GBP' | 'BRL';

interface EditFinancialCardProps {
  data: EnterpriseFullResponseDTO;
  onSave: (updatedData: EnterpriseFullResponseDTO) => void;
  onCancel: () => void;
}

// Formatação de moeda para preview
const formatMoney = (value: number | undefined, currency: string = "EUR") => {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-PT", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value);
};

const EditFinancialCard: React.FC<EditFinancialCardProps> = ({
  data,
  onSave,
  onCancel
}) => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    totalInvestment: data.totalInvestment ?? undefined,
    currentValue: data.currentValue ?? undefined,
    currency: data.currency ?? 'EUR',
    constructionCompany: data.constructionCompany ?? '',
    architect: data.architect ?? ''
  });

  // Inicializa o form quando data muda
  useEffect(() => {
    if (data) {
      const initialValues = {
        totalInvestment: data.totalInvestment ?? undefined,
        currentValue: data.currentValue ?? undefined,
        currency: data.currency ?? 'EUR',
        constructionCompany: data.constructionCompany ?? '',
        architect: data.architect ?? ''
      };
      setFormData(initialValues);
      form.setFieldsValue(initialValues);
    }
  }, [data, form]);

  const handleSave = async () => {
    try {
      // Validação
      const values = await form.validateFields();
      
      if (!data.id) {
        message.error(t('enterpriseEdit.idNotFound'));
        return;
      }

      setSaving(true);

      // Prepara payload conforme especificado
      const payload = {
        totalInvestment: values.totalInvestment ?? null,
        currentValue: values.currentValue ?? null,
        currency: values.currency,
        constructionCompany: values.constructionCompany || null,
        architect: values.architect || null
      };

      console.log('📤 Enviando para API:', payload);

      // Faz PATCH request
      const response = await api.patch(
        `/enterprise-relations/${data.id}/finance`, 
        payload
      );

      console.log('📥 Resposta da API:', response.data);

      // Combina dados antigos com novos para manter integridade
      const updatedData: EnterpriseFullResponseDTO = {
        ...data,
        ...response.data,
      };

      console.log('✅ Dados atualizados completos:', updatedData);

      onSave(updatedData);
      message.success(t('enterpriseEdit.financialUpdated'));
    } catch (error: any) {
      console.error("❌ Erro ao atualizar informação financeira:", error);
      if (error.response?.data?.message) {
        message.error(error.response.data.message);
      } else {
        message.error(t('enterpriseEdit.financialError'));
      }
    } finally {
      setSaving(false);
    }
  };

  const handleChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
    form.setFieldValue(field, value);
  };

  const currencyOptions = [
    { value: 'EUR', label: t('enterpriseEdit.currencyEUR') },
    { value: 'USD', label: t('enterpriseEdit.currencyUSD') },
    { value: 'GBP', label: t('enterpriseEdit.currencyGBP') },
    { value: 'BRL', label: t('enterpriseEdit.currencyBRL') },
  ];

  return (
    <Card
      className="rounded-2xl shadow-sm border-0"
      bodyStyle={{ padding: 0 }}
    >
      <Form form={form} layout="vertical">
        {/* Header com o MESMO gradiente */}
        <div
          className="p-6 text-white rounded-t-2xl"
          style={{
            background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)',
            position: 'relative',
            overflow: 'hidden'
          }}
        >
          {/* Padrão decorativo de fundo */}
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
              <EuroCircleOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
                {t('enterpriseEdit.financialTitle')}
              </Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <DollarCircleOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  {t('enterpriseEdit.financialSubtitle')}
                </Text>
              </div>
            </div>
          </div>
        </div>

        {/* Conteúdo Editável */}
        <div className="p-6">
          {/* Cards principais de valores */}
          <Row gutter={[16, 16]} style={{ marginBottom: '20px' }}>
            {/* Investimento Total */}
            <Col xs={24} sm={12}>
              <div style={{
                padding: '20px',
                backgroundColor: '#fafafa',
                borderRadius: '12px',
                border: '1px solid #e8e8e8',
                height: '100%',
                transition: 'all 0.3s ease',
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
                  <EuroCircleOutlined style={{ color: '#52c41a', fontSize: '20px' }} />
                </div>
                <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '8px' }}>
                  {t('enterpriseEdit.totalInvestment')}
                </Text>

                <Form.Item
                  name="totalInvestment"
                  style={{ margin: 0 }}
                  rules={[
                    {
                      type: 'number',
                      min: 0,
                      message: t('enterpriseEdit.totalInvestmentNegError')
                    }
                  ]}
                >
                  <InputNumber
                    style={{ width: '100%', marginBottom: '4px' }}
                    size="large"
                    onChange={(value) => handleChange('totalInvestment', value)}
                    formatter={value =>
                      value ? formatMoney(Number(value), formData.currency).replace('€', '').replace('$', '').replace('£', '').replace('R$', '').trim() : ''
                    }
                    parser={value => Number(value ? value.replace(/\$\s?|(,*)/g, '') : '0') as any}
                    min={0}
                    step={1000}
                    placeholder="0"
                  />
                </Form.Item>

                <div style={{ marginTop: '8px' }}>
                  <Text style={{ fontSize: '12px', color: '#8c8c8c' }}>
                    {t('enterpriseEdit.currencyLabel')}
                    <Form.Item
                      name="currency" 
                      style={{ display: 'inline-block', margin: 0 }}
                    >
                      <Select
                        onChange={(value) => handleChange('currency', value)}
                        size="small"
                        bordered={false}
                        style={{ 
                          marginLeft: '4px', 
                          fontWeight: 'bold',
                          color: '#52c41a',
                          minWidth: 100
                        }}
                      >
                        {currencyOptions.map(option => (
                          <Option key={option.value} value={option.value}>
                            {option.label}
                          </Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Text>
                </div>
              </div>
            </Col>

            {/* Valor Atual */}
            <Col xs={24} sm={12}>
              <div style={{
                padding: '20px',
                backgroundColor: '#fafafa',
                borderRadius: '12px',
                border: '1px solid #e8e8e8',
                height: '100%',
                transition: 'all 0.3s ease',
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
                  <RiseOutlined style={{ color: '#78716c', fontSize: '20px' }} />
                </div>
                <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '8px' }}>
                  {t('enterpriseEdit.currentValueLabel')}
                </Text>

                <Form.Item
                  name="currentValue"
                  style={{ margin: 0 }}
                  rules={[
                    {
                      type: 'number',
                      min: 0,
                      message: t('enterpriseEdit.currentValueNegError')
                    }
                  ]}
                >
                  <InputNumber
                    style={{ width: '100%', marginBottom: '4px' }}
                    size="large"
                    onChange={(value) => handleChange('currentValue', value)}
                    formatter={value =>
                      value ? formatMoney(Number(value), formData.currency).replace('€', '').replace('$', '').replace('£', '').replace('R$', '').trim() : ''
                    }
                    parser={value => Number(value ? value.replace(/\$\s?|(,*)/g, '') : '0') as any}
                    min={0}
                    step={1000}
                    placeholder="0"
                  />
                </Form.Item>

                <Text style={{ fontSize: '12px', color: '#8c8c8c', marginTop: '8px', display: 'block' }}>
                  {t('enterpriseEdit.currentValueMarket')}
                </Text>
              </div>
            </Col>
          </Row>

          {/* Informações adicionais */}
          <div style={{
            padding: '20px',
            background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
            borderRadius: '12px',
            border: '1px solid #e7e5e4',
            marginBottom: '20px'
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
                <TeamOutlined style={{ color: 'white', fontSize: '18px' }} />
              </div>
              <div>
                <Text strong style={{ fontSize: '16px', color: '#262626' }}>
                  {t('enterpriseEdit.projectTeam')}
                </Text>
                <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
                  {t('enterpriseEdit.projectTeamDesc')}
                </div>
              </div>
            </div>

            <Row gutter={[16, 12]}>
              {/* Empresa de Construção */}
              <Col xs={24} md={12}>
                <div style={{ display: 'flex', alignItems: 'center', padding: '12px 0' }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    backgroundColor: '#f5f5f4',
                    marginRight: '12px'
                  }}>
                    <BuildOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <Text style={{ fontSize: '12px', color: '#8c8c8c', display: 'block', marginBottom: '4px' }}>
                      {t('enterpriseEdit.constructionCompanyLabel')}
                    </Text>
                    <Form.Item name="constructionCompany" style={{ margin: 0 }}>
                      <Input
                        onChange={(e) => handleChange('constructionCompany', e.target.value)}
                        placeholder={t('enterpriseEdit.constructionCompanyPlaceholder')}
                        size="small"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                  </div>
                </div>
              </Col>

              {/* Arquiteto */}
              <Col xs={24} md={12}>
                <div style={{ display: 'flex', alignItems: 'center', padding: '12px 0' }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    backgroundColor: '#fff7e6',
                    marginRight: '12px'
                  }}>
                    <EditOutlined style={{ color: '#fa8c16', fontSize: '16px' }} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <Text style={{ fontSize: '12px', color: '#8c8c8c', display: 'block', marginBottom: '4px' }}>
                      {t('enterpriseEdit.architectLabel')}
                    </Text>
                    <Form.Item name="architect" style={{ margin: 0 }}>
                      <Input
                        onChange={(e) => handleChange('architect', e.target.value)}
                        placeholder={t('enterpriseEdit.architectPlaceholder')}
                        size="small"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                  </div>
                </div>
              </Col>
            </Row>
          </div>

          {/* Preview dos valores */}
          {(formData.totalInvestment || formData.currentValue) && (
            <div style={{
              padding: '16px',
              backgroundColor: '#f9f9f9',
              borderRadius: '8px',
              border: '1px solid #f0f0f0',
              marginBottom: '20px'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                <CalculatorOutlined style={{ color: '#44403c', marginRight: '8px' }} />
                <Text strong style={{ color: '#262626', fontSize: '14px' }}>
                  {t('enterpriseEdit.financialPreview')}
                </Text>
              </div>

              <Row gutter={16}>
                {formData.totalInvestment && (
                  <Col xs={12}>
                    <div style={{ textAlign: 'center' }}>
                      <Text style={{ fontSize: '11px', color: '#8c8c8c', display: 'block' }}>{t('enterpriseEdit.investmentLabel')}</Text>
                      <Text strong style={{ fontSize: '13px', color: '#52c41a' }}>
                        {formatMoney(formData.totalInvestment, formData.currency)}
                      </Text>
                    </div>
                  </Col>
                )}
                {formData.currentValue && (
                  <Col xs={12}>
                    <div style={{ textAlign: 'center' }}>
                      <Text style={{ fontSize: '11px', color: '#8c8c8c', display: 'block' }}>{t('enterpriseEdit.currentValueLabel')}</Text>
                      <Text strong style={{ fontSize: '13px', color: '#78716c' }}>
                        {formatMoney(formData.currentValue, formData.currency)}
                      </Text>
                    </div>
                  </Col>
                )}
              </Row>

              {/* Cálculo do ROI se ambos os valores existirem */}
              {formData.totalInvestment && formData.currentValue && formData.totalInvestment > 0 && (
                <div style={{ 
                  marginTop: '12px', 
                  paddingTop: '12px', 
                  borderTop: '1px solid #f0f0f0',
                  textAlign: 'center' 
                }}>
                  <Text style={{ fontSize: '11px', color: '#8c8c8c', display: 'block' }}>
                    {t('enterpriseEdit.variationLabel')}
                  </Text>
                  <Text 
                    strong 
                    style={{ 
                      fontSize: '14px', 
                      color: formData.currentValue >= formData.totalInvestment ? '#52c41a' : '#ff4d4f' 
                    }}
                  >
                    {((formData.currentValue - formData.totalInvestment) / formData.totalInvestment * 100).toFixed(1)}%
                  </Text>
                </div>
              )}
            </div>
          )}

          {/* Botões de ação */}
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end', marginTop: '16px' }}>
            <Button
              icon={<CloseOutlined />}
              onClick={onCancel}
              disabled={saving}
              size="large"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={saving}
              size="large"
              style={{
                background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)',
                border: 'none'
              }}
            >
              {t('common.saveChanges')}
            </Button>
          </div>
        </div>
      </Form>
    </Card>
  );
};

export default EditFinancialCard;