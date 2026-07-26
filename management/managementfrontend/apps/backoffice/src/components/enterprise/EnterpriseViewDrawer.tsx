// src/components/enterprises/EnterpriseViewDrawer.tsx
import React, { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import {
  Drawer,
  Button,
  Skeleton,
  Space,
  Layout,
  Steps,
  Empty,
  Typography,
  Card,
  Row,
  Col,
  Tag,
  Tooltip,
  message,
} from "antd";
import {
  HomeOutlined,
  CalendarOutlined,
  EuroCircleOutlined,
  EnvironmentOutlined,
  PictureOutlined,
  ApartmentOutlined,
  AuditOutlined,
  EditOutlined,
  CloseOutlined,
  BuildOutlined,
  NumberOutlined,
  TagOutlined,
  CheckCircleOutlined,
  FieldTimeOutlined,
  ScheduleOutlined,
  PlayCircleOutlined,
  DollarCircleOutlined,
  RiseOutlined,
  TeamOutlined,
  CalculatorOutlined,
  GlobalOutlined,
  CompassOutlined,
  FileTextOutlined,
  FileImageOutlined,
  StarOutlined,
  EyeOutlined,
  HistoryOutlined,
  PlusCircleOutlined,
  InfoCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";

import {
  MapPin,
  HardHat,
} from "lucide-react"
import type {
  EnterpriseFullResponseDTO,
  LocationResponseDTO,
  MediaResponseDTO,
} from "@/services/enterpriseService";
import { getEnterpriseById } from "@/services/enterpriseService";
import EditEnterpriseOverviewCard from "@/components/enterprise/edit/EditEnterpriseOverviewCard"
import EditDatesAndAreasCard from "@/components/enterprise/edit/EditDatesAndAreasCard"
import EditFinancialCard from "@/components/enterprise/edit/EditFinancialCard"
import EditEnterpriseLocationCard from "@/components/enterprise/edit/EditEnterpriseLocationCard" 
import EditEnterpriseGalleryCard from "@/components/enterprise/edit/EditEnterpriseGalleryCard" 
import AuthenticatedImage from '@/components/image/AuthenticatedImage';

import dayjs from "dayjs";

const { Title, Text } = Typography;
const { Sider, Content } = Layout;

/* ========= CONSTANTES ========= */

const TYPE_LABELS: Record<string, string> = {
  residential: "enterprises.types.residential",
  commercial: "enterprises.types.commercial",
  industrial: "enterprises.types.industrial",
  mixed_use: "enterprises.types.mixed_use",
};

const STATUS_LABELS: Record<string, string> = {
  planning: "enterprises.status.planning",
  under_construction: "enterprises.status.under_construction",
  completed: "enterprises.status.completed",
  active: "buildings.status.active",
  archived: "buildings.status.archived",
};

const STATUS_COLORS: Record<string, string> = {
  planning: "blue",
  under_construction: "orange",
  completed: "green",
  active: "cyan",
  archived: "red",
};

/* ========= HELPERS ========= */

const formatMoney = (amount?: number | null, currency?: string | null) => {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat("pt-PT", {
      style: "currency",
      currency: currency || "EUR",
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount} ${currency || ""}`.trim();
  }
};

const formatArea = (area?: number | null) => {
  if (area == null) return "—";
  return `${new Intl.NumberFormat("pt-PT", { maximumFractionDigits: 2 }).format(area)} m²`;
};

const formatDate = (dateStr?: string | null) => {
  if (!dateStr) return "—";
  return dayjs(dateStr).format("DD/MM/YYYY");
};

const formatDateTime = (dateStr?: string | null) => {
  if (!dateStr) return "—";
  return dayjs(dateStr).format("DD/MM/YYYY HH:mm");
};

// Versão corrigida - igual à do PropertyViewDrawer
// Função corrigida para resolver URLs de media no EnterpriseViewDrawer
// Substitua a função existente por esta versão

// Função CORRIGIDA para resolver URLs de media no EnterpriseViewDrawer
// Substitua a função existente (linha ~135) por esta versão

const resolveMediaUrl = (m: MediaResponseDTO & { url?: string | null }): string => {
  // Debug: mostrar o que foi recebido
  console.debug("[resolveMediaUrl] media object:", {
    url: m.url,
    downloadUrl: m.downloadUrl,
    bucket: m.bucket,
    storageKey: m.storageKey,
    type: m.type,
  });

  // 1. Se tiver URL completa, usa diretamente
  if (m.url) {
    console.debug("[resolveMediaUrl] ✅ usando m.url:", m.url);
    return m.url;
  }
  
  // 2. Se tiver downloadUrl
  if (m.downloadUrl) {
    console.debug("[resolveMediaUrl] downloadUrl recebido:", m.downloadUrl);
    
    // Se começar com http:// ou https://, já é URL completa
    if (m.downloadUrl.startsWith('http://') || m.downloadUrl.startsWith('https://')) {
      console.debug("[resolveMediaUrl] ✅ downloadUrl é URL completa");
      return m.downloadUrl;
    }
    
    // Se começar com /, é path relativo - CORRIGIDO para usar VITE_API_URL
    if (m.downloadUrl.startsWith('/')) {
      const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
      const fullUrl = `${apiUrl}${m.downloadUrl}`;
      console.debug("[resolveMediaUrl] ✅ construído URL completo:", fullUrl);
      return fullUrl;
    }
    
    // Caso contrário, retorna como está
    return m.downloadUrl;
  }
  
  // 3. Tenta construir URL pública do Supabase
  if (m.bucket && m.storageKey && import.meta.env.VITE_SUPABASE_URL) {
    const base = String(import.meta.env.VITE_SUPABASE_URL).replace(/\/+$/, "");
    const constructed = `${base}/storage/v1/object/public/${m.bucket}/${m.storageKey}`;
    console.debug("[resolveMediaUrl] ✅ construída URL Supabase:", constructed);
    return constructed;
  }
  
  console.debug("[resolveMediaUrl] ⚠️ nenhuma URL disponível, usando fallback");
  return "/src/assets/images/property/default.jpg"; // Fallback
};


/* ========= CARDS DE VISUALIZAÇÃO ========= */

// Card 1: Informações Gerais
const OverviewCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => {
  const { t } = useTranslation();
  return (
  <Card
    className="rounded-2xl shadow-sm border-0"
    bodyStyle={{ padding: 0 }}
  >
    {/* Header com gradiente melhorado */}
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
            <BuildOutlined style={{ fontSize: '24px', color: 'white' }} />
          </div>
          <div>
            <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
              {data.name}
            </Title>
            {data.internalReference && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <NumberOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  Ref: {data.internalReference}
                </Text>
              </div>
            )}
          </div>
        </div>

        <div className="text-right">
          <Tag
            style={{
              backgroundColor: 'rgba(255,255,255,0.9)',
              color: '#78716c',
              border: 'none',
              borderRadius: '20px',
              padding: '4px 16px',
              fontWeight: 600,
              fontSize: '12px'
            }}
          >
            <TagOutlined style={{ marginRight: '4px' }} />
            {t(TYPE_LABELS[data.type] || '') || data.type}
          </Tag>
        </div>
      </div>
    </div>

    {/* Conteúdo */}
    <div className="p-6">
      {/* Status destacado */}
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
              <Text strong style={{ fontSize: '16px', color: '#141413' }}>
                Status do Projeto
              </Text>
              <div style={{ fontSize: '12px', color: '#87867f' }}>
                Estado atual do empreendimento
              </div>
            </div>
          </div>
          <Tag
            style={{
              backgroundColor: STATUS_COLORS[data.status],
              color: 'white',
              border: 'none',
              borderRadius: '16px',
              padding: '6px 16px',
              fontWeight: 600,
              fontSize: '13px'
            }}
          >
            {t(STATUS_LABELS[data.status] || '') || data.status}
          </Tag>
        </div>
      </div>

      {/* Informações em cards organizados */}
      <Row gutter={[16, 16]} style={{ marginBottom: '20px' }}>
        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
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
                backgroundColor: '#faf9f5',
                marginRight: '12px'
              }}>
                <HomeOutlined style={{ color: '#78716c', fontSize: '16px' }} />
              </div>
              <Text strong style={{ color: '#5e5d59', fontSize: '12px', textTransform: 'uppercase' }}>
                Tipo de Projeto
              </Text>
            </div>
            <Text style={{ fontSize: '16px', fontWeight: 600, color: '#141413' }}>
              {t(TYPE_LABELS[data.type] || '') || data.type}
            </Text>
          </div>
        </Col>

        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
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
                <NumberOutlined style={{ color: '#5e5d59', fontSize: '16px' }} />
              </div>
              <Text strong style={{ color: '#5e5d59', fontSize: '12px', textTransform: 'uppercase' }}>
                Referência Interna
              </Text>
            </div>
            <Text style={{ fontSize: '16px', fontWeight: 600, color: '#141413' }}>
              {data.internalReference || t('common.noData')}
            </Text>
          </div>
        </Col>
      </Row>

      {/* Descrição (se existir) */}
      {data.description && (
        <div style={{
          padding: '20px',
          background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
          borderRadius: '12px',
          border: '1px solid #f0eee6'
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
          <Text style={{ color: '#8c4a00', lineHeight: '1.6', fontSize: '14px' }}>
            {data.description}
          </Text>
        </div>
      )}
    </div>
  </Card>
  );
};

/// Card 2: Datas e Áreas - Com a mesma paleta do OverviewCard
const DatesAndAreasCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
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
            Datas e Áreas
          </Title>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
            <FieldTimeOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
            <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
              Cronograma e dimensões do projeto
            </Text>
          </div>
        </div>
      </div>
    </div>

    {/* Conteúdo */}
    <div className="p-6">
      {/* Timeline visual das datas - com cores da paleta */}
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
            <Text strong style={{ fontSize: '16px', color: '#141413' }}>
              Cronograma do Projeto
            </Text>
            <div style={{ fontSize: '12px', color: '#87867f' }}>
              Período de desenvolvimento
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
          {/* Linha do tempo */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '0',
            right: '0',
            height: '2px',
            backgroundColor: '#e7e5e4',
            zIndex: 1
          }} />

          {/* Data de início */}
          <div style={{ position: 'relative', zIndex: 2, textAlign: 'center', flex: 1 }}>
            <div style={{
              width: '44px',
              height: '44px',
              borderRadius: '50%',
              backgroundColor: '#78716c',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 8px',
              border: '3px solid white',
              boxShadow: '0 2px 8px rgba(134, 179, 221, 0.3)'
            }}>
              <PlayCircleOutlined style={{ color: 'white', fontSize: '18px' }} />
            </div>
            <Text strong style={{ display: 'block', color: '#5e5d59', fontSize: '12px' }}>
              Início
            </Text>
            <Text style={{ color: '#141413', fontSize: '11px', fontWeight: 600 }}>
              {formatDate(data.startDate)}
            </Text>
          </div>

          {/* Data de conclusão */}
          <div style={{ position: 'relative', zIndex: 2, textAlign: 'center', flex: 1 }}>
            <div style={{
              width: '44px',
              height: '44px',
              borderRadius: '50%',
              backgroundColor: '#78716c',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 8px',
              border: '3px solid white',
              boxShadow: '0 2px 8px rgba(119, 56, 207, 0.3)'
            }}>
              <CheckCircleOutlined style={{ color: 'white', fontSize: '18px' }} />
            </div>
            <Text strong style={{ display: 'block', color: '#5e5d59', fontSize: '12px' }}>
              Conclusão
            </Text>
            <Text style={{ color: '#141413', fontSize: '11px', fontWeight: 600 }}>
              {formatDate(data.completionDate)}
            </Text>
          </div>
        </div>
      </div>

      {/* Grid de informações - Estilo IDÊNTICO ao OverviewCard */}
      <Row gutter={[16, 16]}>

        {/* Área do Terreno */}
        <Col xs={24} sm={8}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
            height: '100%',
            transition: 'all 0.3s ease',
            textAlign: 'center'
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = 'none';
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
              <EnvironmentOutlined style={{ color: '#5e5d59', fontSize: '20px' }} />
            </div>
            <Text strong style={{ color: '#5e5d59', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '4px' }}>
              Área do Terreno
            </Text>
            <Text style={{ fontSize: '18px', fontWeight: 700, color: '#5e5d59', display: 'block' }}>
              {formatArea(data.landArea)}
            </Text>
          </div>
        </Col>

        {/* Área Total Construída */}
        <Col xs={24} sm={8}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
            height: '100%',
            transition: 'all 0.3s ease',
            textAlign: 'center'
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = 'none';
            }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              backgroundColor: '#faf9f5',
              margin: '0 auto 12px'
            }}>
              <BuildOutlined style={{ color: '#78716c', fontSize: '20px' }} />
            </div>
            <Text strong style={{ color: '#5e5d59', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '4px' }}>
              Área Construída
            </Text>
            <Text style={{ fontSize: '18px', fontWeight: 700, color: '#78716c', display: 'block' }}>
              {formatArea(data.totalArea)}
            </Text>
          </div>
        </Col>

        

{/* Total de Unidades */}
        <Col xs={24} sm={8}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
            height: '100%',
            transition: 'all 0.3s ease',
            textAlign: 'center'
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = 'none';
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
              Total de Unidades
            </Text>
            <Text style={{ fontSize: '18px', fontWeight: 700, color: '#78716c', display: 'block' }}>
              {data.totalUnits ?? "—"}
            </Text>
          </div>
        </Col>

      </Row>

      {/* Informações adicionais - Estilo consistente */}
      {(data.startDate || data.completionDate) && (
        <div style={{
          marginTop: '20px',
          padding: '16px',
          backgroundColor: '#f9f9f9',
          borderRadius: '8px',
          border: '1px solid #f0f0f0'
        }}>
          <Row gutter={16}>
            {data.startDate && (
              <Col xs={12}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    backgroundColor: '#faf9f5',
                    marginRight: '12px'
                  }}>
                    <PlayCircleOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                  </div>
                  <div>
                    <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Data de Início</Text>
                    <Text strong style={{ fontSize: '13px', color: '#141413' }}>{formatDate(data.startDate)}</Text>
                  </div>
                </div>
              </Col>
            )}
            {data.completionDate && (
              <Col xs={12}>
                <div style={{ display: 'flex', alignItems: 'center' }}>
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
                    <CheckCircleOutlined style={{ color: '#78716c', fontSize: '16px' }} />
                  </div>
                  <div>
                    <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Data de Conclusão</Text>
                    <Text strong style={{ fontSize: '13px', color: '#141413' }}>{formatDate(data.completionDate)}</Text>
                  </div>
                </div>
              </Col>
            )}
          </Row>
        </div>
      )}
    </div>
  </Card>
);
// Card 3: Financeiro
// Card 3: Financeiro - Estilo consistente
const FinancialCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
  <Card
    className="rounded-2xl shadow-sm border-0"
    bodyStyle={{ padding: 0 }}
  >
    {/* Header com o MESMO gradiente */}
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
          <EuroCircleOutlined style={{ fontSize: '24px', color: 'white' }} />
        </div>
        <div>
          <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
            Informação Financeira
          </Title>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
            <DollarCircleOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
            <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
              Dados financeiros e investimento
            </Text>
          </div>
        </div>
      </div>
    </div>

    {/* Conteúdo */}
    <div className="p-6">
      {/* Cards principais de valores */}
      <Row gutter={[16, 16]} style={{ marginBottom: '20px' }}>
        {/* Investimento Total */}
        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
            height: '100%',
            transition: 'all 0.3s ease',
            textAlign: 'center'
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = 'none';
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
              <EuroCircleOutlined style={{ color: '#5e5d59', fontSize: '20px' }} />
            </div>
            <Text strong style={{ color: '#5e5d59', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '4px' }}>
              Investimento Total
            </Text>
            <Text style={{ fontSize: '18px', fontWeight: 700, color: '#5e5d59', display: 'block', lineHeight: '1.2' }}>
              {formatMoney(data.totalInvestment, data.currency)}
            </Text>
            {data.currency && (
              <Text style={{ fontSize: '12px', color: '#87867f', marginTop: '4px' }}>
                Moeda: {data.currency}
              </Text>
            )}
          </div>
        </Col>

        {/* Valor Atual */}
        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
            height: '100%',
            transition: 'all 0.3s ease',
            textAlign: 'center'
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = 'translateY(-2px)';
              e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = 'translateY(0)';
              e.currentTarget.style.boxShadow = 'none';
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
            <Text strong style={{ color: '#5e5d59', fontSize: '11px', textTransform: 'uppercase', display: 'block', marginBottom: '4px' }}>
              Valor Atual
            </Text>
            <Text style={{ fontSize: '18px', fontWeight: 700, color: '#78716c', display: 'block', lineHeight: '1.2' }}>
              {formatMoney(data.currentValue, data.currency)}
            </Text>
            <Text style={{ fontSize: '12px', color: '#87867f', marginTop: '4px' }}>
              Valor de mercado
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
            <Text strong style={{ fontSize: '16px', color: '#141413' }}>
              Equipa do Projeto
            </Text>
            <div style={{ fontSize: '12px', color: '#87867f' }}>
              Empresa e profissionais envolvidos
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
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                backgroundColor: '#faf9f5',
                marginRight: '12px'
              }}>
                <BuildOutlined style={{ color: '#78716c', fontSize: '16px' }} />
              </div>
              <div style={{ flex: 1 }}>
                <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>
                  Empresa de Construção
                </Text>
                <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                  {data.constructionCompany || "—"}
                </Text>
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
                backgroundColor: '#faf9f5',
                marginRight: '12px'
              }}>
                <EditOutlined style={{ color: '#fa8c16', fontSize: '16px' }} />
              </div>
              <div style={{ flex: 1 }}>
                <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>
                  Arquiteto
                </Text>
                <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                  {data.architect || "—"}
                </Text>
              </div>
            </div>
          </Col>
        </Row>
      </div>

      {/* Resumo financeiro */}
      {(data.totalInvestment || data.currentValue) && (
        <div style={{
          padding: '16px',
          backgroundColor: '#f9f9f9',
          borderRadius: '8px',
          border: '1px solid #f0f0f0'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
            <CalculatorOutlined style={{ color: '#78716c', marginRight: '8px' }} />
            <Text strong style={{ color: '#141413', fontSize: '14px' }}>
              Resumo Financeiro
            </Text>
          </div>

          <Row gutter={16}>
            {data.totalInvestment && (
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Investimento</Text>
                  <Text strong style={{ fontSize: '13px', color: '#5e5d59' }}>{formatMoney(data.totalInvestment, data.currency)}</Text>
                </div>
              </Col>
            )}
            {data.currentValue && (
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Valor Atual</Text>
                  <Text strong style={{ fontSize: '13px', color: '#78716c' }}>{formatMoney(data.currentValue, data.currency)}</Text>
                </div>
              </Col>
            )}
          </Row>
        </div>
      )}
    </div>
  </Card>
);

// Card 4: Localização
function loadGoogleMapsScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if ((window as any).google?.maps) { resolve(); return; }

    const existing = document.querySelector('script[data-google-maps="true"]');
    if (existing) {
      const prev = (window as any).__initMapView;
      (window as any).__initMapView = () => { if (prev) prev(); resolve(); };
      return;
    }

    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
    if (!apiKey) { reject(new Error("VITE_GOOGLE_MAPS_API_KEY não configurada")); return; }

    (window as any).__initMapView = () => resolve();

    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&callback=__initMapView`;
    script.async = true;
    script.defer = true;
    script.setAttribute("data-google-maps", "true");
    script.onerror = () => reject(new Error("Erro ao carregar Google Maps API"));
    document.head.appendChild(script);
  });
}

const LocationCard: React.FC<{ location?: LocationResponseDTO | null }> = ({ location }) => {
  const { t } = useTranslation();
  const hasLocation = location && (
    location.addressLine1 || location.city || location.country ||
    location.postalCode || location.parish || location.municipality
  );

  const [showMap, setShowMap] = useState(false);
  const [mapsLoaded, setMapsLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const markerRef = useRef<any>(null);

  const lat = (location as any)?.latitude ?? (location as any)?.lat;
  const lng = (location as any)?.longitude ?? (location as any)?.lng;
  const hasCoords = lat != null && lng != null;

  useEffect(() => {
    if (!showMap || !hasCoords) return;
    loadGoogleMapsScript()
      .then(() => setMapsLoaded(true))
      .catch((err) => setMapError(err.message));
  }, [showMap, hasCoords]);

  useEffect(() => {
    if (!showMap || !mapsLoaded || !mapRef.current || !hasCoords) return;
    if (mapInstanceRef.current) {
      mapInstanceRef.current.setCenter({ lat, lng });
      return;
    }
    const map = new (window as any).google.maps.Map(mapRef.current, {
      center: { lat, lng },
      zoom: 15,
      mapTypeControl: true,
      streetViewControl: false,
      fullscreenControl: false,
      gestureHandling: "greedy",
      zoomControl: true,
    });
    markerRef.current = new (window as any).google.maps.Marker({
      position: { lat, lng },
      map,
      title: location?.addressLine1 || t('common.location'),
      animation: (window as any).google.maps.Animation.DROP,
    });
    mapInstanceRef.current = map;
  }, [showMap, mapsLoaded, hasCoords, lat, lng]);

  useEffect(() => {
    if (!showMap && mapInstanceRef.current) {
      mapInstanceRef.current = null;
      markerRef.current = null;
    }
  }, [showMap]);

  if (!hasLocation) {
    return (
      <Card className="rounded-2xl shadow-sm border-0" bodyStyle={{ padding: 0 }}>
        <div className="p-6 text-white rounded-t-2xl" style={{ background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)', position: 'relative', overflow: 'hidden' }}>
          <div style={{ position: 'absolute', top: '-50%', right: '-20%', width: '200px', height: '200px', background: 'rgba(255,255,255,0.1)', borderRadius: '50%', zIndex: 0 }} />
          <div className="flex items-center" style={{ position: 'relative', zIndex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '56px', height: '56px', borderRadius: '16px', backgroundColor: 'rgba(255,255,255,0.2)', marginRight: '16px', backdropFilter: 'blur(10px)' }}>
              <EnvironmentOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>Localização</Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <MapPin style={{ width: '12px', height: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>Localização do empreendimento</Text>
              </div>
            </div>
          </div>
        </div>
        <div className="p-8">
          <Empty description={t('buildingEdit.location.noLocation')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </div>
      </Card>
    );
  }

  return (
    <Card className="rounded-2xl shadow-sm border-0" bodyStyle={{ padding: 0 }}>
      {/* Header com gradiente */}
      <div className="p-6 text-white rounded-t-2xl" style={{ background: 'linear-gradient(135deg, #78716c 0%, #44403c 100%)', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '-50%', right: '-20%', width: '200px', height: '200px', background: 'rgba(255,255,255,0.1)', borderRadius: '50%', zIndex: 0 }} />

        <div className="flex items-center justify-between" style={{ position: 'relative', zIndex: 1 }}>
          <div className="flex items-center">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '56px', height: '56px', borderRadius: '16px', backgroundColor: 'rgba(255,255,255,0.2)', marginRight: '16px', backdropFilter: 'blur(10px)' }}>
              <EnvironmentOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>Localização</Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <MapPin style={{ width: '12px', height: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  {location?.city ? `Localizado em ${location.city}` : 'Localização do empreendimento'}
                </Text>
              </div>
            </div>
          </div>

          {hasCoords && (
            <Button
              type="primary"
              icon={showMap ? <CloseOutlined /> : <GlobalOutlined />}
              style={{
                backgroundColor: 'rgba(255,255,255,0.9)',
                color: showMap ? '#ff4d4f' : '#78716c',
                border: 'none',
                borderRadius: '20px',
                fontWeight: 600,
              }}
              onClick={() => setShowMap((prev) => !prev)}
            >
              {showMap ? 'Fechar Mapa' : 'Ver no Mapa'}
            </Button>
          )}
        </div>
      </div>

      {/* Conteúdo */}
      <div className="p-6">
        {/* Vista do mapa */}
        {showMap && hasCoords && (
          <>
            {mapError && (
              <div style={{ padding: 40, textAlign: 'center', color: '#ff4d4f', borderRadius: 12, border: '1px solid #ffccc7', background: '#fff1f0' }}>
                <EnvironmentOutlined style={{ fontSize: 36, marginBottom: 8 }} />
                <p style={{ fontWeight: 600, margin: '8px 0 4px' }}>Erro ao carregar mapa</p>
                <p style={{ fontSize: 13, color: '#8c8c8c', margin: 0 }}>{mapError}</p>
              </div>
            )}
            {!mapError && (
              <div style={{ position: 'relative', borderRadius: 12, overflow: 'hidden', border: '1px solid #e8e8e8', marginBottom: 16 }}>
                <div ref={mapRef} style={{ width: '100%', height: 320 }} />
                {!mapsLoaded && (
                  <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#f5f5f5', zIndex: 2 }}>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontSize: 36, marginBottom: 8 }}>🗺️</div>
                      <p style={{ margin: 0, color: '#666', fontSize: 14 }}>A carregar mapa...</p>
                    </div>
                  </div>
                )}
              </div>
            )}
            {/* Barra resumo do endereço */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'linear-gradient(135deg, #f5f5f4 0%, #f5f5f4 100%)', borderRadius: 10, border: '1px solid #e7e5e4' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: 36, height: 36, borderRadius: 8, backgroundColor: '#78716c', flexShrink: 0 }}>
                <HomeOutlined style={{ color: 'white', fontSize: 16 }} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Text strong style={{ fontSize: 14, color: '#262626', display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {location?.addressLine1 || '—'}
                  {location?.addressLine2 ? ` — ${location.addressLine2}` : ''}
                </Text>
                <Text style={{ fontSize: 12, color: '#595959' }}>
                  {[location?.parish, location?.municipality, location?.city, location?.country].filter(Boolean).join(' · ')}
                  {location?.postalCode ? ` · ${location.postalCode}` : ''}
                </Text>
              </div>
              <Text style={{ fontSize: 11, color: '#8c8c8c', fontFamily: 'monospace', flexShrink: 0, textAlign: 'right', lineHeight: 1.6 }}>
                {Number(lat).toFixed(6)}<br />{Number(lng).toFixed(6)}
              </Text>
            </div>
          </>
        )}

        {/* Vista dos dados */}
        {!showMap && (
          <>
            {/* Endereço principal destacado */}
            <div style={{ padding: '20px', background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)', borderRadius: '12px', border: '1px solid #e7e5e4', marginBottom: '20px' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '40px', height: '40px', borderRadius: '10px', backgroundColor: '#78716c', flexShrink: 0 }}>
                  <HomeOutlined style={{ color: 'white', fontSize: '18px' }} />
                </div>
                <div style={{ flex: 1 }}>
                  <Text strong style={{ fontSize: '16px', color: '#141413', display: 'block', marginBottom: '4px' }}>
                    {location?.addressLine1 || t('buildingView.sections.noAddress')}
                  </Text>
                  {location?.addressLine2 && (
                    <Text style={{ color: '#595959', fontSize: '14px' }}>{location.addressLine2}</Text>
                  )}
                </div>
              </div>
            </div>

            {/* Grid de informações */}
            <Row gutter={[16, 16]}>
              {[
                { label: 'Cidade', value: location?.city, icon: <EnvironmentOutlined style={{ color: '#78716c', fontSize: '16px' }} />, bg: '#faf9f5' },
                { label: 'Código Postal', value: location?.postalCode, icon: <FileTextOutlined style={{ color: '#5e5d59', fontSize: '16px' }} />, bg: '#f6ffed' },
                { label: 'Município', value: location?.municipality, icon: <CompassOutlined style={{ color: '#78716c', fontSize: '16px' }} />, bg: '#fff0f6' },
              ].map((item) => (
                <Col xs={24} sm={8} key={item.label}>
                  <div
                    style={{ padding: '20px', backgroundColor: '#faf9f5', borderRadius: '12px', border: '1px solid #f0eee6', height: '100%', transition: 'all 0.3s ease' }}
                    onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = 'none'; }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '32px', height: '32px', borderRadius: '8px', backgroundColor: item.bg, marginRight: '12px' }}>
                        {item.icon}
                      </div>
                      <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>{item.label}</Text>
                    </div>
                    <Text style={{ fontSize: '16px', fontWeight: 600, color: '#141413' }}>{item.value || "—"}</Text>
                  </div>
                </Col>
              ))}
            </Row>

            {/* País + Freguesia */}
            <Row gutter={[16, 16]} style={{ marginTop: '16px' }}>
              <Col xs={24} sm={12}>
                <div style={{ padding: '16px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #f0f0f0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                    <GlobalOutlined style={{ color: '#78716c', marginRight: '8px' }} />
                    <Text strong style={{ fontSize: '12px', color: '#595959', textTransform: 'uppercase' }}>País</Text>
                  </div>
                  <Text style={{ fontSize: '14px', color: '#141413', fontWeight: 500 }}>{location?.country || "—"}</Text>
                </div>
              </Col>
              <Col xs={24} sm={12}>
                <div style={{ padding: '16px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #f0f0f0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                    <HomeOutlined style={{ color: '#5e5d59', marginRight: '8px' }} />
                    <Text strong style={{ fontSize: '12px', color: '#595959', textTransform: 'uppercase' }}>Freguesia</Text>
                  </div>
                  <Text style={{ fontSize: '14px', color: '#141413', fontWeight: 500 }}>{location?.parish || "—"}</Text>
                </div>
              </Col>
            </Row>

            {/* Coordenadas GPS */}
            {hasCoords && (
              <div style={{ marginTop: '20px', padding: '16px', background: 'linear-gradient(135deg, #fff7e6 0%, #fff2cc 100%)', borderRadius: '12px', border: '1px solid #ffd591' }}>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: '32px', height: '32px', borderRadius: '8px', backgroundColor: '#fa8c16', marginRight: '12px' }}>
                    <CompassOutlined style={{ color: 'white', fontSize: '16px' }} />
                  </div>
                  <Text strong style={{ color: '#d46b08', fontSize: '14px' }}>Coordenadas GPS</Text>
                </div>
                <Row gutter={16}>
                  <Col xs={12}>
                    <Text style={{ fontSize: '12px', color: '#8c4a00', display: 'block' }}>Latitude</Text>
                    <Text strong style={{ fontSize: '13px', color: '#8c4a00', fontFamily: 'monospace' }}>{lat || "—"}</Text>
                  </Col>
                  <Col xs={12}>
                    <Text style={{ fontSize: '12px', color: '#8c4a00', display: 'block' }}>Longitude</Text>
                    <Text strong style={{ fontSize: '13px', color: '#8c4a00', fontFamily: 'monospace' }}>{lng || "—"}</Text>
                  </Col>
                </Row>
              </div>
            )}

            {/* Notas */}
            {location?.notes && (
              <div style={{ marginTop: '20px', padding: '16px', background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)', borderRadius: '12px', border: '1px solid #d3adf7' }}>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '8px' }}>
                  <FileTextOutlined style={{ color: '#78716c', marginRight: '8px' }} />
                  <Text strong style={{ color: '#78716c', fontSize: '14px' }}>Observações</Text>
                </div>
                <Text style={{ color: '#391085', lineHeight: '1.5', fontSize: '14px' }}>{location.notes}</Text>
              </div>
            )}
          </>
        )}
      </div>
    </Card>
  );
}


// Card 5: Banner e Galeria
// Card 5: Banner e Galeria - Estilo consistente
const BannerAndGalleryCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => {
  const media = data.media || [];
  const banner = media.find((m) => m.type === "banner");
  const galleryImages = media.filter((m) => m.type === "image");

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
              <PictureOutlined style={{ fontSize: '24px', color: 'white' }} />
            </div>
            <div>
              <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
                Multimédia
              </Title>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                <FileImageOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
                <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                  {media.length} ficheiro{media.length !== 1 ? 's' : ''} multimédia
                </Text>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {banner && (
              <Tag
                style={{
                  backgroundColor: 'rgba(255,255,255,0.9)',
                  color: '#5e5d59',
                  border: 'none',
                  borderRadius: '12px',
                  padding: '4px 12px',
                  fontWeight: 600,
                  fontSize: '12px'
                }}
              >
                <StarOutlined style={{ marginRight: '4px' }} />
                Banner Disponível
              </Tag>
            )}
            <Tag
              style={{
                backgroundColor: 'rgba(255,255,255,0.9)',
                color: '#78716c',
                border: 'none',
                borderRadius: '12px',
                padding: '4px 12px',
                fontWeight: 600,
                fontSize: '12px'
              }}
            >
              {galleryImages.length} Imag{galleryImages.length !== 1 ? 'ens' : 'em'}
            </Tag>
          </div>
        </div>
      </div>

      {/* Conteúdo */}
      <div className="p-6">
        {/* Seção do Banner */}
        {banner ? (
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
                <StarOutlined style={{ color: 'white', fontSize: '18px' }} />
              </div>
              <div>
                <Text strong style={{ fontSize: '16px', color: '#141413' }}>
                  Imagem de Capa
                </Text>
                <div style={{ fontSize: '12px', color: '#87867f' }}>
                  Banner principal do empreendimento
                </div>
              </div>
            </div>

            <div style={{ textAlign: 'center' }}>
              <AuthenticatedImage
                src={resolveMediaUrl(banner)}
                alt={banner.altText || "Banner"}
                style={{
                  maxWidth: '100%',
                  maxHeight: 300,
                  objectFit: 'cover',
                  borderRadius: '12px',
                  border: '2px solid #e7e5e4'
                }}
                fallback="/src/assets/images/property/default.jpg"
                preview={{
                  mask: (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <EyeOutlined />
                      <span>Visualizar</span>
                    </div>
                  )
                }}
              />
              {banner.altText && (
                <Text style={{
                  display: 'block',
                  marginTop: '8px',
                  color: '#5e5d59',
                  fontSize: '12px',
                  fontStyle: 'italic'
                }}>
                  {banner.altText}
                </Text>
              )}
            </div>
          </div>
        ) : (
          <div style={{
            padding: '20px',
            background: '#faf9f5',
            borderRadius: '12px',
            border: '1px dashed #f0eee6',
            marginBottom: '24px',
            textAlign: 'center'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                backgroundColor: '#f0f0f0',
                marginRight: '12px'
              }}>
                <PictureOutlined style={{ color: '#87867f', fontSize: '18px' }} />
              </div>
              <Text strong style={{ color: '#87867f' }}>
                Sem Imagem de Capa
              </Text>
            </div>
            <Text style={{ color: '#87867f', fontSize: '14px' }}>
              Adicione uma imagem de banner para destacar este empreendimento
            </Text>
          </div>
        )}

        {/* Seção da Galeria */}
        <div style={{
          padding: '20px',
          background: 'linear-gradient(135deg, #fafaf9 0%, #f5f5f4 100%)',
          borderRadius: '12px',
          border: '1px solid #f0eee6'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: '16px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '40px',
              height: '40px',
              borderRadius: '10px',
              backgroundColor: '#fa8c16',
              marginRight: '12px'
            }}>
              <FileImageOutlined style={{ color: 'white', fontSize: '18px' }} />
            </div>
            <div>
              <Text strong style={{ fontSize: '16px', color: '#d46b08' }}>
                Galeria de Imagens
              </Text>
              <div style={{ fontSize: '12px', color: '#d48806' }}>
                {galleryImages.length} imagem{galleryImages.length !== 1 ? 'ens' : ''} disponível{galleryImages.length !== 1 ? 'eis' : ''}
              </div>
            </div>
          </div>

          {galleryImages.length > 0 ? (
            <Row gutter={[16, 16]}>
              {galleryImages.map((img, index) => (
                <Col key={img.id} xs={24} sm={12} md={8} lg={6}>
                  <div style={{
                    position: 'relative',
                    borderRadius: '8px',
                    overflow: 'hidden',
                    border: '1px solid #f0eee6',
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
                    <AuthenticatedImage
                      src={resolveMediaUrl(img)}
                      alt={img.altText || `Imagem ${index + 1}`}
                      style={{
                        width: '100%',
                        height: 150,
                        objectFit: 'cover',
                      }}
                      fallback="/src/assets/images/property/default.jpg"
                      preview={{
                        mask: (
                          <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            fontSize: '12px'
                          }}>
                            <EyeOutlined />
                            <span>Ver</span>
                          </div>
                        )
                      }}
                    />
                    {img.altText && (
                      <div style={{
                        position: 'absolute',
                        bottom: 0,
                        left: 0,
                        right: 0,
                        background: 'linear-gradient(transparent, rgba(0,0,0,0.7))',
                        padding: '8px',
                        color: 'white',
                        fontSize: '11px'
                      }}>
                        {img.altText}
                      </div>
                    )}
                  </div>
                </Col>
              ))}
            </Row>
          ) : (
            <div style={{
              padding: '40px 20px',
              textAlign: 'center',
              backgroundColor: 'rgba(255,255,255,0.5)',
              borderRadius: '8px',
              border: '1px dashed #f0eee6'
            }}>
              <FileImageOutlined style={{ fontSize: '32px', color: '#ffc069', marginBottom: '12px' }} />
              <Text style={{ display: 'block', color: '#d48806', fontSize: '14px', marginBottom: '4px' }}>
                Nenhuma imagem na galeria
              </Text>
              <Text style={{ color: '#d48806', fontSize: '12px' }}>
                Adicione imagens para mostrar diferentes aspetos do empreendimento
              </Text>
            </div>
          )}
        </div>

        {/* Resumo de ficheiros */}
        {media.length > 0 && (
          <div style={{
            marginTop: '20px',
            padding: '16px',
            backgroundColor: '#f9f9f9',
            borderRadius: '8px',
            border: '1px solid #f0f0f0'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
              <FileTextOutlined style={{ color: '#78716c', marginRight: '8px' }} />
              <Text strong style={{ color: '#141413', fontSize: '14px' }}>
                Resumo de Ficheiros
              </Text>
            </div>

            <Row gutter={16}>
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Total de Ficheiros</Text>
                  <Text strong style={{ fontSize: '16px', color: '#78716c' }}>{media.length}</Text>
                </div>
              </Col>
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Tamanho Total</Text>
                  <Text strong style={{ fontSize: '16px', color: '#5e5d59' }}>
                    {(() => {
                      const totalBytes = media.reduce((sum, m) => sum + (m.fileSizeBytes || 0), 0);
                      if (totalBytes < 1024) return `${totalBytes} B`;
                      if (totalBytes < 1024 * 1024) return `${(totalBytes / 1024).toFixed(1)} KB`;
                      return `${(totalBytes / (1024 * 1024)).toFixed(1)} MB`;
                    })()}
                  </Text>
                </div>
              </Col>
            </Row>
          </div>
        )}
      </div>
    </Card>
  );
};
// Card 7: Auditoria
// Card 7: Auditoria - Estilo consistente
const AuditCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
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
            <AuditOutlined style={{ fontSize: '24px', color: 'white' }} />
          </div>
          <div>
            <Title level={3} className="text-white mb-1" style={{ margin: 0 }}>
              Auditoria
            </Title>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
              <HistoryOutlined style={{ fontSize: '12px', color: 'rgba(255,255,255,0.8)' }} />
              <Text style={{ color: 'rgba(255,255,255,0.9)', fontSize: '13px' }}>
                Histórico de alterações do sistema
              </Text>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Tag
            style={{
              backgroundColor: 'rgba(255,255,255,0.9)',
              color: '#78716c',
              border: 'none',
              borderRadius: '12px',
              padding: '4px 12px',
              fontWeight: 600,
              fontSize: '12px'
            }}
          >
            <CalendarOutlined style={{ marginRight: '4px' }} />
            {formatDate(data.createdAt)}
          </Tag>
        </div>
      </div>
    </div>

    {/* Conteúdo */}
    <div className="p-6">
      {/* Timeline de Auditoria */}
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
            <HistoryOutlined style={{ color: 'white', fontSize: '18px' }} />
          </div>
          <div>
            <Text strong style={{ fontSize: '16px', color: '#141413' }}>
              Linha do Tempo
            </Text>
            <div style={{ fontSize: '12px', color: '#87867f' }}>
              Histórico de criação e atualizações
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
          {/* Linha do tempo */}
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '0',
            right: '0',
            height: '2px',
            backgroundColor: '#e7e5e4',
            zIndex: 1
          }} />

          {/* Criação */}
          <div style={{ position: 'relative', zIndex: 2, textAlign: 'center', flex: 1 }}>
            <div style={{
              width: '44px',
              height: '44px',
              borderRadius: '50%',
              backgroundColor: '#78716c',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 8px',
              border: '3px solid white',
              boxShadow: '0 2px 8px rgba(134, 179, 221, 0.3)'
            }}>
              <PlusCircleOutlined style={{ color: 'white', fontSize: '18px' }} />
            </div>
            <Text strong style={{ display: 'block', color: '#5e5d59', fontSize: '12px' }}>
              Criação
            </Text>
            <Text style={{ color: '#141413', fontSize: '11px', fontWeight: 600 }}>
              {formatDate(data.createdAt)}
            </Text>
          </div>

          {/* Atualização */}
          <div style={{ position: 'relative', zIndex: 2, textAlign: 'center', flex: 1 }}>
            <div style={{
              width: '44px',
              height: '44px',
              borderRadius: '50%',
              backgroundColor: '#78716c',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 8px',
              border: '3px solid white',
              boxShadow: '0 2px 8px rgba(119, 56, 207, 0.3)'
            }}>
              <EditOutlined style={{ color: 'white', fontSize: '18px' }} />
            </div>
            <Text strong style={{ display: 'block', color: '#5e5d59', fontSize: '12px' }}>
              Última Atualização
            </Text>
            <Text style={{ color: '#141413', fontSize: '11px', fontWeight: 600 }}>
              {formatDate(data.updatedAt)}
            </Text>
          </div>
        </div>
      </div>

      {/* Grid de informações de auditoria */}
      <Row gutter={[16, 16]}>
        {/* Criação */}
        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
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
                backgroundColor: '#faf9f5',
                marginRight: '12px'
              }}>
                <PlusCircleOutlined style={{ color: '#78716c', fontSize: '16px' }} />
              </div>
              <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>
                Criação
              </Text>
            </div>
            <div style={{ marginBottom: '8px' }}>
              <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Data e Hora</Text>
              <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                {formatDateTime(data.createdAt)}
              </Text>
            </div>
            <div>
              <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Criado por</Text>
              <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                {data.createdbyName || "—"}
              </Text>
            </div>
          </div>
        </Col>

        {/* Atualização */}
        <Col xs={24} sm={12}>
          <div style={{
            padding: '20px',
            backgroundColor: '#faf9f5',
            borderRadius: '12px',
            border: '1px solid #f0eee6',
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
                <EditOutlined style={{ color: '#78716c', fontSize: '16px' }} />
              </div>
              <Text strong style={{ color: '#595959', fontSize: '11px', textTransform: 'uppercase' }}>
                Última Atualização
              </Text>
            </div>
            <div style={{ marginBottom: '8px' }}>
              <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Data e Hora</Text>
              <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                {formatDateTime(data.updatedAt)}
              </Text>
            </div>
            <div>
              <Text style={{ fontSize: '12px', color: '#87867f', display: 'block' }}>Atualizado por</Text>
              <Text strong style={{ fontSize: '14px', color: '#141413' }}>
                {data.updatedBy || "—"}
              </Text>
            </div>
          </div>
        </Col>
      </Row>

      {/* Informações adicionais */}
      {(data.createdAt || data.updatedAt) && (
        <div style={{
          marginTop: '20px',
          padding: '16px',
          backgroundColor: '#f9f9f9',
          borderRadius: '8px',
          border: '1px solid #f0f0f0'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: '12px' }}>
            <InfoCircleOutlined style={{ color: '#78716c', marginRight: '8px' }} />
            <Text strong style={{ color: '#141413', fontSize: '14px' }}>
              Resumo de Auditoria
            </Text>
          </div>

          <Row gutter={16}>
            {data.createdAt && (
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Criado há</Text>
                  <Text strong style={{ fontSize: '13px', color: '#78716c' }}>
                    {(() => {
                      const created = dayjs(data.createdAt);
                      const now = dayjs();
                      const diffDays = now.diff(created, 'day');
                      if (diffDays === 0) return 'Hoje';
                      if (diffDays === 1) return 'Ontem';
                      if (diffDays < 30) return `${diffDays} dias`;
                      if (diffDays < 365) return `${Math.floor(diffDays / 30)} meses`;
                      return `${Math.floor(diffDays / 365)} anos`;
                    })()}
                  </Text>
                </div>
              </Col>
            )}
            {data.updatedAt && (
              <Col xs={12}>
                <div style={{ textAlign: 'center' }}>
                  <Text style={{ fontSize: '11px', color: '#87867f', display: 'block' }}>Atualizado há</Text>
                  <Text strong style={{ fontSize: '13px', color: '#78716c' }}>
                    {(() => {
                      const updated = dayjs(data.updatedAt);
                      const now = dayjs();
                      const diffDays = now.diff(updated, 'day');
                      if (diffDays === 0) return 'Hoje';
                      if (diffDays === 1) return 'Ontem';
                      if (diffDays < 30) return `${diffDays} dias`;
                      if (diffDays < 365) return `${Math.floor(diffDays / 30)} meses`;
                      return `${Math.floor(diffDays / 365)} anos`;
                    })()}
                  </Text>
                </div>
              </Col>
            )}
          </Row>
        </div>
      )}

      {/* Status de atividade */}
      <div style={{
        marginTop: '20px',
        padding: '16px',
        background: 'linear-gradient(135deg, #f6ffed 0%, #f0fff3 100%)',
        borderRadius: '12px',
        border: '1px solid #b7eb8f'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              backgroundColor: '#5e5d59',
              marginRight: '12px'
            }}>
              <CheckCircleOutlined style={{ color: 'white', fontSize: '16px' }} />
            </div>
            <div>
              <Text strong style={{ color: '#389e0d', fontSize: '14px' }}>
                Registro Ativo
              </Text>
              <Text style={{ color: '#73d13d', fontSize: '12px' }}>
                Todas as informações de auditoria estão disponíveis
              </Text>
            </div>
          </div>
          <Tag color="green" style={{ border: 'none', borderRadius: '12px', fontWeight: 600 }}>
            <SyncOutlined spin style={{ marginRight: '4px' }} />
            Atualizado
          </Tag>
        </div>
      </div>
    </div>
  </Card>
);

/* ========= COMPONENTE PRINCIPAL ========= */

interface EnterpriseViewDrawerProps {
  open: boolean;
  enterpriseId?: string;
  onClose: () => void;
  onUpdated?: (id: string, updated: any) => void;
}

const EnterpriseViewDrawer: React.FC<EnterpriseViewDrawerProps> = ({
  open,
  enterpriseId,
  onClose,
  onUpdated,
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [data, setData] = useState<EnterpriseFullResponseDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(0);
  const [editing, setEditing] = useState(false);

  const steps = [
    { title: t('buildingView.sections.statusTitle'), icon: <HomeOutlined /> },
    { title: t('buildingEdit.areasTeam.title'), icon: <CalendarOutlined /> },
    { title: t('enterprises.investment'), icon: <EuroCircleOutlined /> },
    { title: t('common.location'), icon: <EnvironmentOutlined /> },
    { title: t('buildingView.sections.media'), icon: <PictureOutlined /> },
    { title: t('buildingEdit.overview.statusLabel'), icon: <AuditOutlined /> },
  ];

  // Bloqueia scroll do body enquanto o drawer está aberto
  useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => { document.body.style.overflow = ''; };
  }, [open]);

  // Fetch data quando o drawer abre
  useEffect(() => {
    if (open && enterpriseId) {
      fetchData();
    }
  }, [open, enterpriseId]);

  const fetchData = async () => {
    if (!enterpriseId) return;

    setLoading(true);
    try {
      const result = await getEnterpriseById(enterpriseId);
      setData(result);
    } catch (error) {
      console.error("Erro ao carregar empreendimento:", error);
      message.error(t('enterprises.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const handleStepsChange = (value: number) => {
    setCurrent(value);
    setEditing(false);
  };

  const handleSave = (updatedData: EnterpriseFullResponseDTO) => {
    setData(updatedData);
    setEditing(false);
    message.success(t('enterprises.updated'));
    
    // Notifica o componente pai se existir callback
    if (onUpdated && enterpriseId) {
      onUpdated(enterpriseId, updatedData);
    }
  };

  const handleClose = () => {
    setCurrent(0);
    setEditing(false);
    onClose();
  };

  return (
    <Drawer
      title={
        <div>
          <Title level={4} style={{ margin: 0 }}>
            {data?.name || t('enterprises.title')}
          </Title>
          {data?.internalReference && (
            <Text type="secondary" style={{ fontSize: 14 }}>
              Ref: {data.internalReference}
            </Text>
          )}
        </div>
      }
      placement="right"
      width="80%"
      open={open}
      onClose={handleClose}
      destroyOnClose
      style={{ /* ← Adiciona este style object */
        padding: 0,
        margin: 0
      }}
      bodyStyle={{ /* ← Isto é crucial para remover o padding interno */
        padding: 0,
        margin: 0,
        overflow: 'hidden'
      }}
      extra={
        <Space>
          {!editing && enterpriseId && (
            <Tooltip title={t('construction.manageConstruction')}>
              <Button
                type="default"
                size="large"
                icon={<HardHat size={16} />}
                onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction`)}
              >
                {t('construction.manageConstruction')}
              </Button>
            </Tooltip>
          )}
          {!editing && current != 6 && current != 5 &&  (
            <Tooltip title={t('common.edit')}>
              <Button
                type="primary"
                size="large"
                icon={<EditOutlined />}
                onClick={() => setEditing(true)}
              >
                Editar
              </Button>
            </Tooltip>
          )}
          {editing && (
            <Tooltip title={t('common.cancel')}>
              <Button
                type="primary"
                size="large"
                icon={<CloseOutlined />}
                onClick={() => setEditing(false)}
              >
                Cancelar
              </Button>
            </Tooltip>
          )}
          <Button onClick={handleClose} size="large">
            Fechar
          </Button>
        </Space>
      }
      footer={
        <div className="flex items-center justify-between">
          <Space>
            <Button
              onClick={() => {
                setEditing(false);
                setCurrent((c) => Math.max(0, c - 1));
              }}
              disabled={current === 0}
            >
              Anterior
            </Button>
            <Button
              onClick={() => {
                setEditing(false);
                setCurrent((c) => Math.min(steps.length - 1, c + 1));
              }}
              disabled={current === steps.length - 1}
            >
              Seguinte
            </Button>
          </Space>
          <Text type="secondary">
            Secção {current + 1} de {steps.length}
          </Text>
        </div>
      }
    >
      {loading ? (
        <div style={{ padding: 24 }}>
          <Skeleton active paragraph={{ rows: 12 }} />
        </div>
      ) : data ? (
        <Layout hasSider style={{ minHeight: "calc(100vh - 180px)" }}>
          <Sider
            width={220}
            style={{
              background: "transparent",
              borderRight: "1px solid #f0f0f0",
              padding: "12px 8px",
            }}
          >
            <Steps
              direction="vertical"
              size="small"
              current={current}
              onChange={handleStepsChange}
              items={steps.map((s) => ({
                title: s.title,
                icon: s.icon,
                style: { marginBottom: 8 } // Adiciona espaçamento entre os steps
              }))}
            />
          </Sider>

          <Content
            style={{
              paddingTop: 8,     // ← Apenas o top reduzido
              paddingLeft: 16,
              paddingRight: 16,
              paddingBottom: 16,
              overflowY: "auto",
              maxHeight: "calc(100vh - 90px)",
            }}
          >
            {editing ? (
  <>
    {current === 0 && (
      <EditEnterpriseOverviewCard 
        data={data} 
        onSave={handleSave} 
        onCancel={() => setEditing(false)} 
      />
    )}
    {current === 1 && (
      <EditDatesAndAreasCard
        data={data} 
        onSave={handleSave} 
        onCancel={() => setEditing(false)} 
      />
    )}
    {current === 2 && (
      <EditFinancialCard
       data={data} 
        onSave={handleSave} 
        onCancel={() => setEditing(false)} 
      />
    )}
    {current === 3 && (
      <EditEnterpriseLocationCard
       data={data} 
        onSave={handleSave} 
        onCancel={() => setEditing(false)} 
      />
    )}
    {current === 4 && (
      <EditEnterpriseGalleryCard
       data={data} 
        onSave={handleSave} 
        onCancel={() => setEditing(false)} 
      />
    )}
    
    {/* Para as outras secções que ainda não têm componente de edição */}
    {current > 2 && (
      <Card>
        <Empty description={t('common.soon')} />
      </Card>
    )}
  </>
) : (
  <>
    {current === 0 && <OverviewCard data={data} />}
    {current === 1 && <DatesAndAreasCard data={data} />}
    {current === 2 && <FinancialCard data={data} />}
    {current === 3 && <LocationCard location={data.location} />}
    {current === 4 && <BannerAndGalleryCard data={data} />}
{current === 5 && <AuditCard data={data} />}
  </>
)}
          </Content>
        </Layout>
      ) : (
        <Empty description={t('common.noData')} />
      )}
    </Drawer>
  );
};

export default EnterpriseViewDrawer;