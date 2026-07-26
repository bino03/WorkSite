import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Table,
  Input,
  Empty,
  message,
  Space,
  Button,
  Avatar,
  Popconfirm,
} from "antd";
import { Eye, Trash2, Plus, Home, Building, Factory, Box } from "lucide-react";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { SearchOutlined, EnvironmentOutlined, BankOutlined } from "@ant-design/icons";
import api from "@/api";
import { deleteEnterprise } from "@/services/enterpriseService";
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from "@/config/pagination";
import CreateEnterpriseDrawer from "@/components/enterprise/CreateEnterpriseDrawer";
import EnterpriseViewDrawer from "@/components/enterprise/EnterpriseViewDrawer";

const { Search } = Input;

// Tipos baseados na resposta da API
export type EnterpriseStatus = 'planning' | 'under_construction' | 'completed' | 'suspended' | 'cancelled';
export type EnterpriseType = 'residential' | 'commercial' | 'industrial' | 'mixed_use';

export interface Enterprise {
  id: string;
  name: string;
  internalReference?: string;
  type: EnterpriseType;
  status: EnterpriseStatus;
  startDate?: string;
  completionDate?: string;
  totalArea?: number;
  landArea?: number;
  totalUnits?: number;
  totalInvestment?: number;
  currentValue?: number;
  currency: string;
  constructionCompany?: string;
  architect?: string;
  managerId?: string;
  ownerId?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  isActive: boolean;
  banner?: string;
  location?: {
    id: string;
    locationId: string;
    city: string;
  }
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// Mapeamento de status para labels
const STATUS_LABEL: Record<EnterpriseStatus, string> = {
  planning: "enterprises.status.planning",
  under_construction: "enterprises.status.under_construction",
  completed: "enterprises.status.completed",
  suspended: "enterprises.status.suspended",
  cancelled: "enterprises.status.cancelled",
};

// Paleta Anthropic
const D = {
  parchment: "#f5f4ed",
  ivory: "#faf9f5",
  nearBlack: "#141413",
  terracotta: "#c96442",
  coral: "#d97757",
  oliveGray: "#5e5d59",
  stoneGray: "#87867f",
  warmSand: "#e8e6dc",
  charcoalWarm: "#4d4c48",
  borderCream: "#f0eee6",
  borderWarm: "#e8e6dc",
  ivory2: "#faf9f5",
  whisper: "rgba(0,0,0,0.05) 0px 4px 24px",
};

// Mapeamento de tipos com ícones e cores
const TYPE_CONFIG: Record<EnterpriseType, { labelKey: string; icon: React.ReactNode; color: string }> = {
  residential: {
    labelKey: "enterprises.types.residential",
    icon: <Home size={14} />,
    color: "#c96442"
  },
  commercial: {
    labelKey: "enterprises.types.commercial",
    icon: <Building size={14} />,
    color: "#5e5d59"
  },
  industrial: {
    labelKey: "enterprises.types.industrial",
    icon: <Factory size={14} />,
    color: "#87867f"
  },
  mixed_use: {
    labelKey: "enterprises.types.mixed_use",
    icon: <Box size={14} />,
    color: "#4d4c48"
  },
};

// Serviço para enterprises
export const fetchEnterprises = async (params?: {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  q?: string;
}): Promise<PageResponse<Enterprise>> => {
  const response = await api.get('/enterprises', { params });
  return response.data;
};

// Formatação de moeda
const formatCurrency = (value: number | undefined, currency: string = "EUR") => {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-PT", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(value);
};

export default function EnterprisesList() {
  const { t } = useTranslation();
  const [q, setQ] = useState("");
  const [data, setData] = useState<Enterprise[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState<TablePaginationConfig>({
    current: 1,
    pageSize: DEFAULT_PAGE_SIZE,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
  });

  const abortRef = useRef<AbortController | null>(null);

  // Estados para os drawers
  const [isCreateDrawerOpen, setIsCreateDrawerOpen] = useState(false);
  const [enterpriseDrawerOpen, setEnterpriseDrawerOpen] = useState(false);
  const [selectedEnterpriseId, setSelectedEnterpriseId] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleDrawerClose = () => {
    setIsCreateDrawerOpen(false);
  };

  const handleViewEnterprise = (id: string) => {
    setSelectedEnterpriseId(id);
    setEnterpriseDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setEnterpriseDrawerOpen(false);
    setSelectedEnterpriseId(null);
  };

  const handleUpdated = (id: string, updated: any) => {
    setData(prevData =>
      prevData.map(item => {
        if (item.id === id) {
          // O DTO completo usa array media; a lista usa banner como URL plana
          const bannerMedia = updated.media?.find((m: any) => m?.type === "banner");
          const bannerUrl = bannerMedia
            ? (bannerMedia.url || bannerMedia.downloadUrl || null)
            : null;

          return {
            ...item,
            ...updated,
            banner: bannerUrl,
            location: updated.location
              ? { ...item.location, ...updated.location }
              : item.location
          };
        }
        return item;
      })
    );
    message.success(t('enterprises.updated'));
  };

  const handleEnterpriseCreated = () => {
    message.success(t('enterprises.created'));
    setRefreshKey(prev => prev + 1);
    setIsCreateDrawerOpen(false);
  };

  const handleCreateEnterprise = () => {
    setIsCreateDrawerOpen(true);
  };

  const handleDeleteEnterprise = async (id: string) => {
    try {
      await deleteEnterprise(id);
      setData(prev => prev.filter(e => e.id !== id));
      message.success(t('enterprises.deleted'));
    } catch {
      message.error(t('enterprises.deleteError'));
    }
  };

  // Colunas da tabela - versão compacta com texto escuro e ícones coloridos
  const columns: ColumnsType<Enterprise> = useMemo(
    () => [
      {
        title: t('enterprises.title'),
        dataIndex: "banner",
        key: "banner",
        render: (bannerUrl: string | null, record: Enterprise) => {
          const typeConfig = record.type ? TYPE_CONFIG[record.type] : undefined;

          return (
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Avatar
                shape="square"
                size={60}
                src={bannerUrl || undefined}
                icon={!bannerUrl ? <BankOutlined /> : undefined}
                alt={record.name}
                style={{
                  boxShadow: D.whisper,
                  border: `1px solid ${D.borderCream}`,
                  backgroundColor: bannerUrl ? undefined : D.warmSand,
                  color: bannerUrl ? undefined : D.charcoalWarm,
                }}
              />
              <div>
                <div style={{
                  fontWeight: 500,
                  color: D.nearBlack,
                  fontSize: '14px',
                  marginBottom: '2px'
                }}>
                  {record.name}
                </div>
                {record.internalReference && (
                  <div style={{
                    fontSize: "12px",
                    color: D.stoneGray,
                    marginBottom: '4px'
                  }}>
                    {record.internalReference}
                  </div>
                )}
                {typeConfig && (
                  <div style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontSize: '11px',
                    fontWeight: 500,
                    color: typeConfig.color,
                    background: D.warmSand,
                    border: `1px solid ${D.borderWarm}`,
                    borderRadius: '6px',
                    padding: '2px 8px',
                  }}>
                    {React.cloneElement(typeConfig.icon as React.ReactElement, {
                      style: { color: typeConfig.color }
                    })}
                    {t(typeConfig.labelKey)}
                  </div>
                )}
              </div>
            </div>
          );
        },
        width: 300,
      },
      {
        title: t('enterprises.columns.status'),
        dataIndex: "status",
        key: "status",
        render: (status: EnterpriseStatus) => (
          <span style={{ color: D.nearBlack, fontSize: '13px', fontWeight: 600 }}>
            {status && STATUS_LABEL[status] ? t(STATUS_LABEL[status]) : '—'}
          </span>
        ),
        width: 120,
      },
      {
        title: t('enterprises.columns.location'),
        key: "location",
        render: (record: Enterprise) => {
          const city = record.location?.city;

          return (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <EnvironmentOutlined style={{ color: D.stoneGray, fontSize: '12px' }} />
              <span style={{ color: D.oliveGray, fontSize: '13px', fontWeight: 400 }}>
                {city || '—'}
              </span>
            </div>
          );
        },
        width: 130,
      },
      {
        title: t('enterprises.columns.investment'),
        dataIndex: "totalInvestment",
        key: "totalInvestment",
        align: "right",
        render: (investment: number | null, record: Enterprise) => (
          <span style={{ color: D.nearBlack, fontSize: '13px', fontWeight: 600 }}>
            {formatCurrency(investment, record.currency)}
          </span>
        ),
        width: 150,
      },
      {
        title: t('enterprises.columns.actions'),
        key: "actions",
        render: (_: unknown, record: Enterprise) => (
          <Space size={2} direction="vertical" style={{ width: '100%' }}>
            <Button
              type="text"
              icon={<Eye size={14} />}
              onClick={() => handleViewEnterprise(record.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-start',
                width: '100%',
                height: '32px',
                borderRadius: '8px',
                color: D.ivory,
                fontSize: '12px',
                fontWeight: 500,
                padding: '0 12px',
                background: D.terracotta,
                border: 'none',
                transition: 'all 0.2s ease',
                boxShadow: `0px 0px 0px 1px ${D.terracotta}`,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = D.coral;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.coral}`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = D.terracotta;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.terracotta}`;
              }}
            >
              {t('enterprises.view')}
            </Button>

            <Popconfirm
              title={t('enterprises.deleteConfirm')}
              description={t('common.cannotBeUndone')}
              okText={t('common.delete')}
              cancelText={t('common.cancel')}
              okButtonProps={{ danger: true }}
              onConfirm={() => handleDeleteEnterprise(record.id)}
            >
              <Button
                type="text"
                danger
                icon={<Trash2 size={14} />}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'flex-start',
                  width: '100%',
                  height: '32px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 500,
                  padding: '0 12px',
                  background: D.warmSand,
                  color: '#b53333',
                  border: `1px solid ${D.borderWarm}`,
                  transition: 'all 0.2s ease',
                  boxShadow: 'none',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = '#b53333';
                  e.currentTarget.style.background = '#fff5f5';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = D.borderWarm;
                  e.currentTarget.style.background = D.warmSand;
                }}
              >
                {t('common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ),
        width: 120,
      },
    ],
    []
  );

  // Função para buscar os dados
  async function fetchData(
    p: { current?: number; pageSize?: number },
    controller: AbortController
  ) {
    abortRef.current = controller;
    setLoading(true);
    try {
      const page0 = Math.max(0, (p.current ?? 1) - 1);
      const size = p.pageSize ?? DEFAULT_PAGE_SIZE;
      const res = await fetchEnterprises({
        page: page0,
        size,
        q: q.trim(),
      });
      if (controller.signal.aborted) return;

      setData(res.content ?? []);
      setPagination((prev) => ({
        ...prev,
        current: (res.number ?? 0) + 1,
        pageSize: res.size ?? size,
        total: res.totalElements ?? 0,
      }));
    } catch (err) {
      console.error("Erro ao carregar Empreendimentos:", err);
      message.error(t('enterprises.loadError'));
    } finally {
      setLoading(false);
    }
  }

  // Efeito para buscar dados
  useEffect(() => {
    const controller = new AbortController();
    fetchData(
      { current: pagination.current, pageSize: pagination.pageSize },
      controller
    );
    return () => controller.abort();
  }, [q, pagination.current, pagination.pageSize, refreshKey]);

  // Manipulador de mudança da tabela
  function onTableChange(p: TablePaginationConfig) {
    setPagination((prev) => ({
      ...prev,
      current: p.current ?? prev.current,
      pageSize: p.pageSize ?? prev.pageSize,
    }));
  }

  return (
    <div className="container-page">
      <header className="mb-6">
        <h1 style={{ color: D.nearBlack, fontSize: "22px", fontWeight: 500, fontFamily: "Georgia, serif", margin: "0 0 4px 0", lineHeight: 1.2 }}>
          {t('enterprises.title')}
        </h1>
        <p style={{ color: D.stoneGray, fontSize: "13px", margin: 0, lineHeight: 1.6 }}>
          {t('enterprises.subtitle')}
        </p>
      </header>

      <section className="card">
        <div className="flex flex-col gap-3 px-6 py-4 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-1 gap-3">
            <Search
              allowClear
              placeholder={t('enterprises.searchPlaceholder')}
              value={q}
              onChange={(e) => setQ(e.target.value)}
              prefix={<SearchOutlined />}
              className="w-full max-w-xl"
            />
          </div>

          <div className="flex gap-3">
            <Button
              type="primary"
              onClick={handleCreateEnterprise}
              icon={<Plus size={16} />}
              style={{
                background: D.terracotta,
                border: 'none',
                borderRadius: '8px',
                fontWeight: 500,
                fontSize: '14px',
                padding: '8px 24px',
                height: '40px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                color: D.ivory,
                boxShadow: `0px 0px 0px 1px ${D.terracotta}`,
                transition: 'all 0.2s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = D.coral;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.coral}`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = D.terracotta;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.terracotta}`;
              }}
            >
              {t('enterprises.addEnterprise')}
            </Button>
          </div>
        </div>

        <div className="mt-5 px-3 pb-6">
          <Table<Enterprise>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={loading}
            onChange={onTableChange}
            pagination={pagination}
            scroll={{ x: 800 }}
            locale={{
              emptyText: (
                <Empty
                  description={t('enterprises.loadError')}
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              )
            }}
          />

          {/* Drawers */}
          <CreateEnterpriseDrawer
            open={isCreateDrawerOpen}
            onClose={handleDrawerClose}
            onCreated={handleEnterpriseCreated}
          />
          <EnterpriseViewDrawer
            open={enterpriseDrawerOpen}
            enterpriseId={selectedEnterpriseId || undefined}
            onClose={handleCloseDrawer}
            onUpdated={handleUpdated}
          />
        </div>
      </section>
    </div>
  );
}
