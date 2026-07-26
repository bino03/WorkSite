// src/components/enterprise/edit/EditEnterpriseLocationCard.tsx
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Card,
  Row,
  Col,
  Input,
  Select,
  Button,
  Space,
  message,
  Typography,
  InputNumber,
  Popconfirm,
  Empty,
} from "antd";
import {
  EnvironmentOutlined,
  PlusOutlined,
  GlobalOutlined,
  HomeOutlined,
  FileTextOutlined,
  CompassOutlined,
} from "@ant-design/icons";
import { MapPin } from "lucide-react";
import api from "@/api";
import { fetchLocations } from "@/services/locationService";
import CountryDistrictSelect, {
  type CountryDistrictValue,
} from "@/components/common/CountryDistrictSelect";

const { Title, Text } = Typography;

interface Props {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  data: any;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onSave: (updatedData: any) => void;
  onCancel: () => void;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const EditEnterpriseLocationCard: React.FC<Props> = ({ data, onSave }) => {
  const { t } = useTranslation();
  // Estado local para dados editados
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [edited, setEdited] = useState<any>(data ?? {});

  // Estados para localização
  const hasLocation = !!(edited?.location?.addressLine1 || edited?.location?.city);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [locDraft, setLocDraft] = useState<any>({});
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [locOptions, setLocOptions] = useState<any[]>([]);
  const [useExistingLocation, setUseExistingLocation] = useState(true);
  const [locLoading, setLocLoading] = useState(false);
  const [isEditingLocation, setIsEditingLocation] = useState(false);

  // Aplicar edição da localização
  const applyEditLocation = async () => {
    try {
      const enterpriseId = data?.id;
      if (!enterpriseId) throw new Error("ID do empreendimento não encontrado");

      let payload;

      if (useExistingLocation) {
        // PARA LOCALIZAÇÃO EXISTENTE - Enviar apenas o ID
        if (!locDraft.id) {
          message.error(t('enterpriseEdit.selectLocation'));
          return;
        }
        payload = { 
          id: locDraft.id
        };
      } else {
        // PARA NOVA LOCALIZAÇÃO - Enviar todos os dados
        payload = {
          addressLine1: locDraft.addressLine1,
          addressLine2: locDraft.addressLine2,
          postalCode: locDraft.postalCode,
          city: locDraft.city,
          country: locDraft.country,
          municipality: locDraft.municipality,
          parish: locDraft.parish,
          latitude: locDraft.latitude,
          longitude: locDraft.longitude,
          googlePlaceId: locDraft.googlePlaceId,
          notes: locDraft.notes
        };
      }

      const response = await api.post(
        `/enterprise-relations/${enterpriseId}/location/upsert`, 
        payload
      );

      message.success(t('locations.updated'));
      
      // Fechar o formulário de edição
      setIsEditingLocation(false);
      
      // O response.data agora contém EnterpriseLocationDTO com location completa
      const updatedData = {
        ...data,
        location: response.data.location // location vem dentro do DTO
      };
      
      setEdited(updatedData);
      onSave(updatedData);
      
    } catch (error) {
      console.error("Erro ao Guardar localização", error);
      message.error(t('enterpriseEdit.locationSaveError'));
    }
  };

  // Função para procurar localizações existentes
  const searchLocations = async (query: string) => {
    if (query.trim().length < 2) {
      setLocOptions([]);
      return;
    }
    setLocLoading(true);
    try {
      const results = await fetchLocations({ page: 1, size: 10, q: query });
      setLocOptions(results.content || []);
    } catch {
      message.error(t('enterpriseEdit.locationSearchError'));
      setLocOptions([]);
    } finally {
      setLocLoading(false);
    }
  };

  // Função para formatar o label da localização
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const formatLocLabel = (location: any) => {
    const left = `${location.country ?? "—"}, ${location.city ?? "—"}`;
    const right = [location.addressLine1, location.postalCode].filter(Boolean).join(" ; ");
    return right ? `${left}  -  ${right}` : left;
  };

  // Função para remover localização
  const handleRemoveLocation = async () => {
    const enterpriseId = edited?.id ?? data?.id;
    if (!enterpriseId) {
      message.error(t('enterpriseEdit.idNotFound'));
      return;
    }
    try {
      // Use o endpoint correto do enterprise-relations
      await api.delete(`/enterprise-relations/${enterpriseId}/location`);
      
      const updatedData = {
        ...data,
        location: null
      };

      setEdited(updatedData);
      onSave(updatedData);

      message.success(t('enterpriseEdit.locationRemoved'));
    } catch {
      message.error(t('enterpriseEdit.locationRemoveError'));
    }
  };

  // Abrir edição de localização
  const openEditLocation = () => {
    setLocDraft({});
    setUseExistingLocation(true);
    setIsEditingLocation(true);
  };

  // Renderização quando não há localização
  if (!hasLocation && !isEditingLocation) {
    return (
      <Card
        className="rounded-2xl shadow-sm border-0"
        bodyStyle={{ padding: 0 }}
      >
        {/* Header mesmo para estado vazio */}
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
              <EnvironmentOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
                {t('enterpriseLocationEdit.title')}
              </Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <MapPin style={{ width: '12px', height: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  {t('enterpriseLocationEdit.subtitle')}
                </Text>
              </div>
            </div>
          </div>
        </div>

        {/* Conteúdo vazio */}
        <div className="p-8">
          <Empty
            description={t('buildingEdit.location.noLocation')}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openEditLocation}
              size="large"
            >
              {t('buildingEdit.location.addLocation')}
            </Button>
          </div>
        </div>
      </Card>
    );
  }

  return (
    <Card
      className="rounded-2xl shadow-sm border-0"
      bodyStyle={{ padding: 0 }}
    >
      {/* Header com gradiente */}
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
              <EnvironmentOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
                {t('enterpriseLocationEdit.title')}
              </Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <MapPin style={{ width: '12px', height: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  {hasLocation && !isEditingLocation
                    ? (edited.location?.city ? t('enterpriseLocationEdit.locatedIn', { city: edited.location.city }) : t('enterpriseLocationEdit.subtitle'))
                    : t('enterpriseLocationEdit.configuringLocation')
                  }
                </Text>
              </div>
            </div>
          </div>

          {hasLocation && !isEditingLocation && (edited.location?.latitude || edited.location?.longitude) && (
            <Button
              type="primary"
              icon={<GlobalOutlined />}
              style={{
                backgroundColor: 'rgba(255,255,255,0.9)',
                color: '#78716c',
                border: 'none',
                borderRadius: '20px',
                fontWeight: 600,
              }}
              onClick={() => {
                if (edited.location.latitude && edited.location.longitude) {
                  const url = `https://www.google.com/maps?q=${edited.location.latitude},${edited.location.longitude}`;
                  window.open(url, '_blank');
                }
              }}
            >
              {t('enterpriseLocationEdit.viewOnMap')}
            </Button>
          )}
        </div>
      </div>

      {/* Conteúdo */}
      <div className="p-6">
        {!isEditingLocation ? (
          <>
            {/* Endereço principal destacado */}
            <div style={{
              padding: '20px',
              background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
              borderRadius: '12px',
              border: '1px solid #e7e5e4',
              marginBottom: '20px'
            }}>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                gap: '12px'
              }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px', flex: 1 }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '40px',
                    height: '40px',
                    borderRadius: '10px',
                    backgroundColor: '#78716c',
                    flexShrink: 0
                  }}>
                    <HomeOutlined style={{ color: 'white', fontSize: '18px' }} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <Text strong style={{ fontSize: '16px', color: '#262626', display: 'block', marginBottom: '4px' }}>
                      {edited.location?.addressLine1 || t('buildingView.sections.noAddress')}
                    </Text>
                    {edited.location?.addressLine2 && (
                      <Text style={{ color: '#595959', fontSize: '14px' }}>
                        {edited.location.addressLine2}
                      </Text>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Grid de informações */}
            <Row gutter={[16, 16]}>
              {/* Cidade */}
              <Col xs={24} sm={8}>
                <div style={{
                  padding: '20px',
                  backgroundColor: '#fafafa',
                  borderRadius: '12px',
                  border: '1px solid #e8e8e8',
                  height: '100%',
                  transition: 'all 0.3s ease'
                }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = 'none';
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
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
                      <EnvironmentOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                    </div>
                    <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>
                      {t('buildingEdit.location.city')}
                    </Text>
                  </div>
                  <Text style={{ fontSize: '16px', fontWeight: 600, color: '#262626' }}>
                    {edited.location?.city || "—"}
                  </Text>
                </div>
              </Col>

              {/* Código Postal */}
              <Col xs={24} sm={8}>
                <div style={{
                  padding: '20px',
                  backgroundColor: '#fafafa',
                  borderRadius: '12px',
                  border: '1px solid #e8e8e8',
                  height: '100%',
                  transition: 'all 0.3s ease'
                }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = 'none';
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
                      <FileTextOutlined style={{ color: '#52c41a', fontSize: '16px' }} />
                    </div>
                    <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>
                      {t('buildingEdit.location.postalCode')}
                    </Text>
                  </div>
                  <Text style={{ fontSize: '16px', fontWeight: 600, color: '#262626' }}>
                    {edited.location?.postalCode || "—"}
                  </Text>
                </div>
              </Col>

              {/* Município */}
              <Col xs={24} sm={8}>
                <div style={{
                  padding: '20px',
                  backgroundColor: '#fafafa',
                  borderRadius: '12px',
                  border: '1px solid #e8e8e8',
                  height: '100%',
                  transition: 'all 0.3s ease'
                }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = 'none';
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      backgroundColor: '#fff0f6',
                      marginRight: '12px'
                    }}>
                      <CompassOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                    </div>
                    <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>
                      {t('buildingEdit.location.municipality')}
                    </Text>
                  </div>
                  <Text style={{ fontSize: '16px', fontWeight: 600, color: '#262626' }}>
                    {edited.location?.municipality || "—"}
                  </Text>
                </div>
              </Col>
            </Row>

            {/* Informações adicionais */}
            <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
              {/* País */}
              <Col xs={24} sm={12}>
                <div style={{
                  padding: '16px',
                  backgroundColor: '#f9f9f9',
                  borderRadius: '8px',
                  border: '1px solid #f0f0f0'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                    <GlobalOutlined style={{ color: '#78716c', marginRight: '8px' }} />
                    <Text strong style={{ fontSize: '12px', color: '#595959', textTransform: 'uppercase' }}>
                      {t('buildingEdit.location.country')}
                    </Text>
                  </div>
                  <Text style={{ fontSize: '14px', color: '#262626', fontWeight: 500 }}>
                    {edited.location?.country || "—"}
                  </Text>
                </div>
              </Col>

              {/* Freguesia */}
              <Col xs={24} sm={12}>
                <div style={{
                  padding: '16px',
                  backgroundColor: '#f9f9f9',
                  borderRadius: '8px',
                  border: '1px solid #f0f0f0'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                    <HomeOutlined style={{ color: '#52c41a', marginRight: '8px' }} />
                    <Text strong style={{ fontSize: '12px', color: '#595959', textTransform: 'uppercase' }}>
                      {t('buildingEdit.location.parish')}
                    </Text>
                  </div>
                  <Text style={{ fontSize: '14px', color: '#262626', fontWeight: 500 }}>
                    {edited.location?.parish || "—"}
                  </Text>
                </div>
              </Col>
            </Row>

            {/* Coordenadas (se disponíveis) */}
            {(edited.location?.latitude || edited.location?.longitude) && (
              <div style={{
                marginTop: '20px',
                padding: '16px',
                background: 'linear-gradient(135deg, #fff7e6 0%, #fff2cc 100%)',
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
                    <CompassOutlined style={{ color: 'white', fontSize: '16px' }} />
                  </div>
                  <Text strong style={{ color: '#d46b08', fontSize: '14px' }}>
                    {t('enterpriseLocationEdit.gpsCoordinates')}
                  </Text>
                </div>
                <Row gutter={16}>
                  <Col xs={12}>
                    <Text style={{ fontSize: '12px', color: '#8c4a00', display: 'block' }}>{t('enterpriseLocationEdit.latitude')}</Text>
                    <Text strong style={{ fontSize: '13px', color: '#8c4a00', fontFamily: 'monospace' }}>
                      {edited.location?.latitude || "—"}
                    </Text>
                  </Col>
                  <Col xs={12}>
                    <Text style={{ fontSize: '12px', color: '#8c4a00', display: 'block' }}>{t('enterpriseLocationEdit.longitude')}</Text>
                    <Text strong style={{ fontSize: '13px', color: '#8c4a00', fontFamily: 'monospace' }}>
                      {edited.location?.longitude || "—"}
                    </Text>
                  </Col>
                </Row>
              </div>
            )}

            {/* Notas (se disponíveis) */}
            {edited.location?.notes && (
              <div style={{
                marginTop: '20px',
                padding: '16px',
                background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
                borderRadius: '12px',
                border: '1px solid #e7e5e4'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                  <FileTextOutlined style={{ color: '#78716c', marginRight: '8px' }} />
                  <Text strong style={{ color: '#78716c', fontSize: '14px' }}>
                    {t('enterpriseLocationEdit.notes')}
                  </Text>
                </div>
                <Text style={{ color: '#44403c', lineHeight: '1.5', fontSize: '14px' }}>
                  {edited.location.notes}
                </Text>
              </div>
            )}

            {/* Botões de ação */}
            <Space style={{ marginTop: 24 }}>
              <Button type="primary" onClick={openEditLocation} size="large">
                {t('buildingEdit.location.changeLocation')}
              </Button>
              <Popconfirm
                title={t('buildingEdit.location.removeLocation')}
                description={t('buildingEdit.location.removeConfirm')}
                okText={t('buildingEdit.location.removeYes')}
                cancelText={t('common.cancel')}
                okType="danger"
                onConfirm={handleRemoveLocation}
              >
                <Button danger size="large">
                  {t('buildingEdit.location.removeLocation')}
                </Button>
              </Popconfirm>
            </Space>
          </>
        ) : (
          /* FORMULÁRIO DE EDIÇÃO */
          <Card style={{ marginTop: 0, backgroundColor: '#fafafa', border: 'none' }}>
            {/* Cabeçalho do formulário */}
            <div style={{ marginBottom: 20 }}>
              <Text strong style={{ fontSize: '16px' }}>{t('enterpriseLocationEdit.configTitle')}</Text>
              <div style={{ height: 1, backgroundColor: '#e8e8e8', marginTop: 8 }}></div>
            </div>

            {/* Seleção do tipo de localização */}
            <div style={{ marginBottom: 24 }}>
              <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
                {t('buildingEdit.location.chooseOption')}
              </Text>
              <Row gutter={12}>
                <Col span={12}>
                  <Button
                    type={useExistingLocation ? "primary" : "default"}
                    onClick={() => setUseExistingLocation(true)}
                    style={{ 
                      width: "100%", 
                      height: 48,
                      border: useExistingLocation ? '2px solid #78716c' : '1px solid #d9d9d9'
                    }}
                  >
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontWeight: 500 }}>{t('enterpriseLocationEdit.useExisting')}</div>
                      <div style={{ fontSize: '12px', opacity: 0.7 }}>{t('enterpriseLocationEdit.selectLocation')}</div>
                    </div>
                  </Button>
                </Col>
                <Col span={12}>
                  <Button
                    type={!useExistingLocation ? "primary" : "default"}
                    onClick={() => setUseExistingLocation(false)}
                    style={{ 
                      width: "100%", 
                      height: 48,
                      border: !useExistingLocation ? '2px solid #78716c' : '1px solid #d9d9d9'
                    }}
                  >
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontWeight: 500 }}>{t('enterpriseLocationEdit.createNew')}</div>
                      <div style={{ fontSize: '12px', opacity: 0.7 }}>{t('enterpriseLocationEdit.insertManually')}</div>
                    </div>
                  </Button>
                </Col>
              </Row>
            </div>

            {/* Conteúdo baseado na seleção */}
            <div style={{ 
              padding: 20, 
              backgroundColor: 'white', 
              borderRadius: 8, 
              border: '1px solid #e8e8e8',
              marginBottom: 20
            }}>
              {useExistingLocation ? (
                <div>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>
                    {t('buildingEdit.location.selectExistingTitle')}
                  </Text>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: '13px' }}>
                    {t('buildingEdit.location.selectHint')}
                  </Text>
                  <Select
                    showSearch
                    placeholder={t('buildingEdit.location.searchPlaceholder')}
                    onSearch={searchLocations}
                    onFocus={() => {
                      if (locOptions.length === 0) {
                        searchLocations('');
                      }
                    }}
                    loading={locLoading}
                    filterOption={false}
                    notFoundContent={locLoading ? t('common.searching') : t('buildingEdit.location.searchTypePlaceholder')}
                    options={locOptions.map((loc) => ({ 
                      value: loc.id, 
                      label: formatLocLabel(loc)
                    }))}
                    onChange={(value) => {
                      // Encontrar a localização completa baseada no ID selecionado
                      const selectedLocation = locOptions.find(loc => loc.id === value);
                      setLocDraft(selectedLocation || { id: value });
                    }}
                    style={{ width: "100%" }}
                    size="large"
                  />
                </div>
              ) : (
                <div>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>
                    {t('buildingEdit.location.createNewTitle')}
                  </Text>
                  <Text type="secondary" style={{ display: 'block', marginBottom: 16, fontSize: '13px' }}>
                    {t('buildingEdit.location.createNewDesc2')}
                  </Text>
                  
                  {/* Seção: Endereço Principal */}
                  <div style={{ marginBottom: 20 }}>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
                      {t('buildingEdit.location.addressSection')}
                    </Text>
                    <Row gutter={[12, 12]}>
                      <Col span={24}>
                        <Input
                          placeholder={t('buildingEdit.location.addressLine1Placeholder')}
                          value={locDraft?.addressLine1}
                          onChange={(e) => setLocDraft({ ...locDraft, addressLine1: e.target.value })}
                          size="large"
                        />
                      </Col>
                      <Col span={12}>
                        <Input
                          placeholder={t('buildingEdit.location.addressLine2Placeholder')}
                          value={locDraft?.addressLine2}
                          onChange={(e) => setLocDraft({ ...locDraft, addressLine2: e.target.value })}
                        />
                      </Col>
                      <Col span={12}>
                        <Input
                          placeholder={t('buildingEdit.location.postalCodePlaceholder')}
                          value={locDraft?.postalCode}
                          onChange={(e) => setLocDraft({ ...locDraft, postalCode: e.target.value })}
                        />
                      </Col>
                    </Row>
                  </div>

                  {/* Seção: Localização Geográfica */}
                  <div style={{ marginBottom: 20 }}>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
                      {t('buildingEdit.location.geoSection')}
                    </Text>
                    <Row gutter={[12, 12]}>
                      <Col span={24}>
                        <CountryDistrictSelect
                          value={{
                            // eslint-disable-next-line @typescript-eslint/no-explicit-any
                            country: locDraft?.country as any,
                            district: locDraft?.city,
                          }}
                          onChange={(v: CountryDistrictValue) =>
                            setLocDraft({ ...locDraft, country: v.country, city: v.district })
                          }
                          countryLabel={t('common.country')}
                          districtLabel={t('common.city')}
                          size="large"
                        />
                      </Col>
                      <Col span={12}>
                        <Input
                          placeholder={t('common.parish')}
                          value={locDraft?.parish}
                          onChange={(e) => setLocDraft({ ...locDraft, parish: e.target.value })}
                        />
                      </Col>
                      <Col span={12}>
                        <Input
                          placeholder={t('common.municipality')}
                          value={locDraft?.municipality}
                          onChange={(e) => setLocDraft({ ...locDraft, municipality: e.target.value })}
                        />
                      </Col>
                    </Row>
                  </div>

                  {/* Seção: Coordenadas (Opcional) */}
                  <div>
                    <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
                      {t('buildingEdit.location.coordsSection')}
                    </Text>
                    <Row gutter={12}>
                      <Col span={12}>
                        <InputNumber
                          placeholder={t('buildingEdit.location.latPlaceholder')}
                          value={locDraft?.latitude}
                          onChange={(v) => setLocDraft({ ...locDraft, latitude: v })}
                          style={{ width: '100%' }}
                          step={0.000001}
                        />
                      </Col>
                      <Col span={12}>
                        <InputNumber
                          placeholder={t('buildingEdit.location.lngPlaceholder')}
                          value={locDraft?.longitude}
                          onChange={(v) => setLocDraft({ ...locDraft, longitude: v })}
                          style={{ width: '100%' }}
                          step={0.000001}
                        />
                      </Col>
                    </Row>
                  </div>
                </div>
              )}
            </div>

            {/* Botões de ação */}
            <div style={{ 
              display: 'flex', 
              justifyContent: 'flex-end', 
              gap: 8,
              paddingTop: 16,
              borderTop: '1px solid #e8e8e8'
            }}>
              <Button
                onClick={() => setIsEditingLocation(false)}
                size="large"
              >
                {t('common.cancel')}
              </Button>
              <Button
                type="primary"
                onClick={applyEditLocation}
                size="large"
                style={{ minWidth: 100 }}
              >
                {t('buildingEdit.location.saveButton')}
              </Button>
            </div>
          </Card>
        )}
      </div>
    </Card>
  );
};

export default EditEnterpriseLocationCard;