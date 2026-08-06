import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Table, Input, Empty, message, Button } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { SearchOutlined } from "@ant-design/icons";
import { Plus } from "lucide-react";
import api from "@/api";
import { deleteEnterprise } from "@/services/enterpriseService";
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from "@/config/pagination";
import CreateEnterpriseDrawer from "@/components/enterprise/CreateEnterpriseDrawer";
import EnterpriseViewDrawer from "@/components/enterprise/EnterpriseViewDrawer";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { ListActions, ListActionPrimary, ListActionDanger } from "@/components/common/ListActions";

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

const STATUS_MAP: Record<EnterpriseStatus, { label: string; cls: string }> = {
  planning: { label: "Planeamento", cls: "ind-tag-outline" },
  under_construction: { label: "Em construção", cls: "ind-tag-accent" },
  completed: { label: "Concluído", cls: "ind-tag-neutral" },
  suspended: { label: "Suspenso", cls: "ind-tag-outline" },
  cancelled: { label: "Cancelado", cls: "ind-tag-neutral" },
};

const TYPE_MAP: Record<EnterpriseType, { label: string; cls: string }> = {
  residential: { label: "Residencial", cls: "ind-tag-accent" },
  commercial: { label: "Comercial", cls: "ind-tag-neutral" },
  industrial: { label: "Industrial", cls: "ind-tag-outline" },
  mixed_use: { label: "Uso misto", cls: "ind-tag-accent-2" },
};

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

const formatCurrency = (value: number | null | undefined, currency: string = "EUR") => {
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
  const confirm = useConfirm();
  const [q, setQ] = useState("");
  const [statusFilter, setStatusFilter] = useState<EnterpriseStatus | "">("");
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

  const [isCreateDrawerOpen, setIsCreateDrawerOpen] = useState(false);
  const [enterpriseDrawerOpen, setEnterpriseDrawerOpen] = useState(false);
  const [selectedEnterpriseId, setSelectedEnterpriseId] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

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

  const handleDeleteEnterprise = async (id: string) => {
    try {
      await deleteEnterprise(id);
      setData(prev => prev.filter(e => e.id !== id));
      message.success(t('enterprises.deleted'));
    } catch {
      message.error(t('enterprises.deleteError'));
    }
  };

  const confirmDelete = (enterprise: Enterprise) => {
    confirm({
      message: `Eliminar o empreendimento "${enterprise.name}"? Esta ação não pode ser desfeita.`,
      onConfirm: () => handleDeleteEnterprise(enterprise.id),
    });
  };

  const clearFilters = () => {
    setQ("");
    setStatusFilter("");
  };

  const columns: ColumnsType<Enterprise> = useMemo(
    () => [
      {
        title: "Empreendimento",
        dataIndex: "banner",
        key: "banner",
        render: (_bannerUrl: string | null, record: Enterprise) => {
          const typeInfo = TYPE_MAP[record.type];
          return (
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <div className="ind-hatch" style={{ width: 40, height: 40, flexShrink: 0 }} />
              <div>
                <div style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{record.name}</div>
                {record.internalReference && (
                  <div style={{ fontSize: 11, opacity: 0.6 }}>{record.internalReference}</div>
                )}
                {typeInfo && (
                  <span className={`ind-tag ${typeInfo.cls}`} style={{ marginTop: 3 }}>{typeInfo.label}</span>
                )}
              </div>
            </div>
          );
        },
        width: 300,
      },
      {
        title: "Estado",
        dataIndex: "status",
        key: "status",
        render: (status: EnterpriseStatus) => {
          const info = status ? STATUS_MAP[status] : undefined;
          return info ? <span className={`ind-tag ${info.cls}`}>{info.label}</span> : "—";
        },
        width: 140,
      },
      {
        title: "Localização",
        key: "location",
        render: (record: Enterprise) => record.location?.city || "—",
        width: 140,
      },
      {
        title: "Investimento",
        dataIndex: "totalInvestment",
        key: "totalInvestment",
        render: (investment: number | null, record: Enterprise) => formatCurrency(investment, record.currency),
        width: 150,
      },
      {
        title: "",
        key: "actions",
        render: (_: unknown, record: Enterprise) => (
          <ListActions>
            <ListActionPrimary onClick={() => handleViewEnterprise(record.id)}>Ver detalhes</ListActionPrimary>
            <ListActionDanger onClick={() => confirmDelete(record)}>Eliminar</ListActionDanger>
          </ListActions>
        ),
        width: 120,
      },
    ],
    []
  );

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

      const content = statusFilter ? res.content.filter((e) => e.status === statusFilter) : res.content;

      setData(content);
      setPagination((prev) => ({
        ...prev,
        current: (res.number ?? 0) + 1,
        pageSize: res.size ?? size,
        total: statusFilter ? content.length : (res.totalElements ?? 0),
      }));
    } catch (err) {
      console.error("Erro ao carregar Empreendimentos:", err);
      message.error(t('enterprises.loadError'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    fetchData(
      { current: pagination.current, pageSize: pagination.pageSize },
      controller
    );
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, statusFilter, pagination.current, pagination.pageSize, refreshKey]);

  function onTableChange(p: TablePaginationConfig) {
    setPagination((prev) => ({
      ...prev,
      current: p.current ?? prev.current,
      pageSize: p.pageSize ?? prev.pageSize,
    }));
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Projetos</h6>
          <h1 style={{ margin: 0 }}>Empreendimentos</h1>
        </div>
        <Button type="primary" icon={<Plus size={14} />} onClick={() => setIsCreateDrawerOpen(true)}>
          Novo Empreendimento
        </Button>
      </div>

      <div style={{ display: "flex", gap: "10.2px", alignItems: "center", marginBottom: "13.6px" }}>
        <Input
          allowClear
          placeholder="Pesquisar empreendimentos…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          style={{ maxWidth: 320 }}
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as EnterpriseStatus | "")}
          style={{
            maxWidth: 200,
            minHeight: 36,
            padding: "6px 10px",
            background: "var(--ind-color-surface)",
            border: "1px solid var(--ind-color-divider)",
            color: "var(--ind-color-text)",
          }}
        >
          <option value="">Todos os estados</option>
          {Object.entries(STATUS_MAP).map(([value, info]) => (
            <option key={value} value={value}>{info.label}</option>
          ))}
        </select>
        <Button onClick={clearFilters}>Limpar</Button>
      </div>

      <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
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
              <Empty description={t('enterprises.loadError')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )
          }}
        />
      </div>
      <p style={{ fontSize: 12, opacity: 0.6, marginTop: "10.2px" }}>{pagination.total ?? 0} resultado(s)</p>

      <CreateEnterpriseDrawer
        open={isCreateDrawerOpen}
        onClose={() => setIsCreateDrawerOpen(false)}
        onCreated={handleEnterpriseCreated}
      />
      <EnterpriseViewDrawer
        open={enterpriseDrawerOpen}
        enterpriseId={selectedEnterpriseId || undefined}
        onClose={handleCloseDrawer}
        onUpdated={handleUpdated}
      />
    </div>
  );
}
