import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Table,
  Input,
  Select,
  Badge,
  Avatar,
  Empty,
  message,
  Tooltip,
  Drawer,
  Space,
  Button,
  Typography,
} from "antd";
import {
  UserOutlined,
  PhoneOutlined,
  CrownOutlined,
  DeleteOutlined,
  UserAddOutlined,
  MailOutlined,
  SearchOutlined,
  FilterOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  StopOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import dayjs from "dayjs";
import api from "@/api";
import ProfileView from "@/components/profile/ProfileView";
import type { Employee } from "@/services/profileService";
import { markAccountDeleted } from "@/services/profileService";
import type { AccountStatus } from "@/services/profileService";
import CreateEmployeeDrawer from "@/components/employees/CreateEmployeeDrawer";
import InvitesDrawer from "@/components/invites/InvitesDrawer";
import EmployeeContextMenu from "@/components/employees/EmployeeContextMenu";
import { useConfirm } from "@/context/ConfirmDialogContext";

const { Text } = Typography;

// Paleta Industry
const D = {
  parchment: "var(--ind-color-bg)",
  ivory: "var(--ind-color-surface)",
  nearBlack: "var(--ind-color-text)",
  terracotta: "var(--ind-color-accent)",
  coral: "var(--ind-accent-600)",
  oliveGray: "var(--ind-neutral-700)",
  stoneGray: "var(--ind-neutral-600)",
  warmSand: "var(--ind-neutral-100)",
  charcoalWarm: "var(--ind-neutral-800)",
  borderCream: "var(--ind-color-divider)",
  borderWarm: "var(--ind-color-divider)",
  whisper: "var(--ind-shadow-sm)",
};

type Role = "ADMIN" | "EMPLOYEE";
type Status = "unlocked" | "blocked" | "deleted";

type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based
  size: number;
};

const { Search } = Input;
const { Option } = Select;

// helpers ---------------------------------------------------------
const toInt = (v: unknown, fallback: number) =>
  typeof v === "number" && Number.isFinite(v) ? v : fallback;

const safeApiNumber = (v: unknown, fallback: number) =>
  typeof v === "number" && Number.isFinite(v) ? v : fallback;

function useDebounced<T>(value: T, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

function initialsFromName(name: string) {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] ?? "" : "";
  return (first + last).toUpperCase();
}

function statusBadge(
  s: Status
): "success" | "default" | "processing" | "warning" | "error" {
  switch (s) {
    case "unlocked":
      return "success";
    case "blocked":
      return "error";
    case "deleted":
      return "default";
  }
  return "default";
}

// texto copiável --------------------------------------------------
function CopyableText({
  text,
  className,
  ariaLabel,
}: {
  text: string;
  className?: string;
  ariaLabel?: string;
}) {
  const { t } = useTranslation();
  const handleCopy = async (e: React.MouseEvent | React.KeyboardEvent) => {
    e.stopPropagation();
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement("textarea");
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand("copy");
        document.body.removeChild(ta);
      }
      message.success(t('common.copied'));
    } catch {
      message.warning(t('common.copyFailed'));
    }
  };

  return (
    <Tooltip title={t('common.clickToCopy')}>
      <span
        onClick={handleCopy}
        onKeyDown={(e) =>
          e.key === "Enter" || e.key === " " ? handleCopy(e) : null
        }
        className={`cursor-pointer underline-offset-2 hover:underline ${className ?? ""
          }`}
        aria-label={ariaLabel ?? t('common.clickToCopy')}
      >
        {text}
      </span>
    </Tooltip>
  );
}

// component -------------------------------------------------------
export default function EmployeesList() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  // filtros/estado de UI
  const [q, setQ] = useState("");
  const debouncedQ = useDebounced(q, 300);
  const [role, setRole] = useState<"ALL" | Role>("ALL");
  const [sortBy, setSortBy] = useState("createdAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  // Drawer de criação
  const [createOpen, setCreateOpen] = useState(false);
  const closeCreate = () => setCreateOpen(false);

  // ABRIR DRAWER DE CONVITES
  const [invitesDrawerOpen, setInvitesDrawerOpen] = useState(false);

  // dados/paginação
  const [data, setData] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1, // AntD é 1-based
    pageSize: 20,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: [10, 20, 50, 100],
  });

  // Drawer global de perfil
  const [open, setOpen] = useState(false);
  const [selectedProfile, setSelectedProfile] = useState<Employee | null>(null);

  function openProfile(id: string) {
    const employee = data.find(emp => emp.id === id);
    setSelectedProfile(employee || null);
    setOpen(true);
  }

  function closeProfile() {
    setOpen(false);
    setSelectedProfile(null);
  }

  const handleProfileUpdated = (updatedProfile: Employee) => {
    console.log("🔄 [EmployeesList] Perfil atualizado recebido:", updatedProfile);
    
    // Atualizar o perfil na lista
    setData((prevData) =>
      prevData.map((emp) =>
        emp.id === updatedProfile.id ? updatedProfile : emp
      )
    );
    
    // Atualizar o perfil selecionado (para refletir no drawer)
    setSelectedProfile(updatedProfile);
    
    message.success(t('employees.profileUpdated'));
  };

  // apenas contas bloqueadas
  const [blockedOnly, setBlockedOnly] = useState(false);

  // estado para deletar
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // context menu
  const [ctxMenu, setCtxMenu] = useState<{ visible: boolean; x: number; y: number; record: Employee | null }>({
    visible: false, x: 0, y: 0, record: null,
  });

  const handleContextMenu = useCallback((e: React.MouseEvent, record: Employee) => {
    e.preventDefault();
    setCtxMenu({ visible: true, x: e.clientX, y: e.clientY, record });
  }, []);

  const closeCtxMenu = useCallback(() => {
    setCtxMenu(prev => ({ ...prev, visible: false }));
  }, []);

  const handleCtxStatusChanged = useCallback((id: string, newStatus: AccountStatus) => {
    setData(prev => prev.map(emp => emp.id === id ? { ...emp, status: newStatus } : emp));
    if (selectedProfile?.id === id) setSelectedProfile(prev => prev ? { ...prev, status: newStatus } : prev);
  }, [selectedProfile]);

  const handleCtxDeleted = useCallback((id: string) => {
    setData(prev => prev.map(emp => emp.id === id ? { ...emp, status: 'deleted' as AccountStatus } : emp));
    if (selectedProfile?.id === id) closeProfile();
  }, [selectedProfile]);

  // abort do pedido anterior
  const abortRef = useRef<AbortController | null>(null);

  // colunas -------------------------------------------------------
  const columns: ColumnsType<Employee> = useMemo(
    () => [
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <UserOutlined style={{ color: D.terracotta }} />
            <span>{t('employees.columns.employee')}</span>
          </div>
        ),
        key: "employee",
        dataIndex: "name",
        render: (_: unknown, record) => {
          const initials = initialsFromName(record.name);
          return (
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Avatar
                size={40}
                src={record.photoUrl}
                style={{
                  backgroundColor: record.photoUrl ? 'transparent' : D.warmSand,
                  color: D.charcoalWarm,
                  border: `1px solid ${D.borderCream}`,
                  boxShadow: D.whisper,
                }}
              >
                {!record.photoUrl && initials}
              </Avatar>
              <div>
                <div style={{
                  fontWeight: 600,
                  color: D.nearBlack,
                  fontSize: '14px'
                }}>
                  {record.name}
                </div>
                <CopyableText 
                  text={record.email} 
                  className="text-gray-500 text-sm"
                />
              </div>
            </div>
          );
        },
      },
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <PhoneOutlined style={{ color: D.terracotta }} />
            <span>{t('employees.columns.phone')}</span>
          </div>
        ),
        dataIndex: "phoneNumber",
        key: "phoneNumber",
        render: (val: string | null | undefined, _record: Employee) =>
          val ? (
            <CopyableText text={val} className="text-gray-700" />
          ) : (
            <Text style={{ color: D.stoneGray }}>—</Text>
          ),
      },
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <CrownOutlined style={{ color: D.terracotta }} />
            <span>{t('employees.columns.account')}</span>
          </div>
        ),
        dataIndex: "role",
        key: "role",
        render: (r: Role) => (
          <span className={`ind-tag ${r === "ADMIN" ? "ind-tag-accent" : "ind-tag-neutral"}`}>
            {t(`employees.roles.${r}`)}
          </span>
        ),
      },
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <CheckCircleOutlined style={{ color: D.terracotta }} />
            <span>{t('employees.columns.status')}</span>
          </div>
        ),
        dataIndex: "status",
        key: "status",
        render: (s: Status) => {
          const getStatusIcon = (status: Status) => {
            switch (status) {
              case "unlocked": return <CheckCircleOutlined />;
              case "blocked": return <StopOutlined />;
              case "deleted": return <ExclamationCircleOutlined />;
              default: return <CheckCircleOutlined />;
            }
          };

          return (
            <Badge 
              status={statusBadge(s)} 
              text={
                <span style={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '4px',
                  fontWeight: 500
                }}>
                  {getStatusIcon(s)}
                  {t(`employees.status.${s}`)}
                </span>
              } 
            />
          );
        },
      },
      {
        title: (
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <CalendarOutlined style={{ color: D.terracotta }} />
            <span>{t('employees.columns.createdAt')}</span>
          </div>
        ),
        dataIndex: "createdAt",
        key: "createdAt",
        sorter: true,
        sortOrder:
          sortBy === "createdAt"
            ? ((sortDir === "asc" ? "ascend" : "descend") as SortOrder)
            : null,
        render: (iso: string) => (
          <Text style={{ color: D.nearBlack, fontSize: '13px', fontWeight: 600 }}>
            {dayjs(iso).format("DD/MM/YYYY HH:mm")}
          </Text>
        ),
        width: 220,
      },
      {
        title: t('employees.columns.actions'),
        key: "actions",
        render: (_: unknown, record: Employee) => (
          <Space size="small">
            <Button
              size="small"
              onClick={() => openProfile(record.id)}
              disabled={record.status === "deleted"}
            >
              {t('employees.view')}
            </Button>

            <Tooltip title={t('employees.deleteAccount')}>
              <Button
                type="text"
                size="small"
                icon={<DeleteOutlined />}
                loading={deletingId === record.id}
                disabled={record.status === "deleted"}
                style={{ opacity: 0.75, color: "var(--ind-color-accent)" }}
                onClick={() => confirm({
                  message: `${t('employees.deleteConfirm')} (${record.name})`,
                  onConfirm: () => handleDelete(record.id),
                })}
              />
            </Tooltip>
          </Space>
        ),
      }
    ],
    [sortBy, sortDir, openProfile, deletingId, confirm, t]
  );

  // fetch ---------------------------------------------------------
  async function fetchData(
    p: { current?: number; pageSize?: number },
    controller: AbortController
  ) {
    abortRef.current = controller;

    try {
      setLoading(true);

      const curr = toInt(p.current, 1);
      const size = toInt(p.pageSize, 20);

      const params = new URLSearchParams();
      params.set("page", String(Math.max(0, curr - 1))); // 0-based
      params.set("size", String(size));
      params.set("sortDir", sortDir);
      if (sortBy) params.set("sortBy", String(sortBy));

      const trimmed = debouncedQ.trim();
      if (trimmed.length > 0) params.set("q", trimmed);
      if (role !== "ALL") params.set("role", role);

      if (blockedOnly) {
        params.set("status", "blocked");
      }

      const res = await api.get<PageResponse<Employee>>(
        `/employees?${params}`,
        { signal: controller.signal }
      );
      if (controller.signal.aborted) return;

      const page = res.data;
      const apiNumber = safeApiNumber(page.number, 0);
      const apiSize = safeApiNumber(page.size, size);
      const apiTotal = safeApiNumber(page.totalElements, 0);

      setData(Array.isArray(page.content) ? page.content : []);
      setPagination((prev) => ({
        ...prev,
        current: toInt(apiNumber + 1, 1),
        pageSize: toInt(apiSize, size),
        total: toInt(apiTotal, 0),
      }));
    } catch (err) {
      console.error("Erro ao carregar funcionários:", err);
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id);
    try {
      await markAccountDeleted(id);
      message.success(t('employees.accountDeleted'));
      if (selectedProfile?.id === id) closeProfile();

      const controller = new AbortController();
      await fetchData(
        { current: toInt(pagination.current, 1), pageSize: toInt(pagination.pageSize, 20) },
        controller
      );
    } catch (err) {
      console.error(err);
      message.error(t('employees.deleteError'));
    } finally {
      setDeletingId(null);
    }
  }

  // reset para página 1 quando muda a pesquisa (debounced)
  useEffect(() => {
    setPagination((prev) =>
      prev.current === 1 ? prev : { ...prev, current: 1 }
    );
  }, [debouncedQ]);

  // fetch quando muda paginação/filtros/sort
  useEffect(() => {
    const controller = new AbortController();
    fetchData(
      { current: toInt(pagination.current, 1), pageSize: toInt(pagination.pageSize, 20) },
      controller
    );
    return () => controller.abort();
  }, [pagination.current, pagination.pageSize, debouncedQ, role, sortBy, sortDir, blockedOnly]);

  function onTableChange(
    p: TablePaginationConfig,
    _filters: any,
    sorter: any
  ) {
    // sort
    if (!Array.isArray(sorter)) {
      const field = sorter?.field as keyof Employee | undefined;
      const order: SortOrder | undefined = sorter?.order;
      if (field && order) {
        setSortBy(field);
        setSortDir(order === "ascend" ? "asc" : "desc");
      } else {
        setSortBy("createdAt");
        setSortDir("desc");
      }
    }
    // paginação
    const nextCurrent = toInt(p.current, toInt(pagination.current, 1));
    const nextSize = toInt(p.pageSize, toInt(pagination.pageSize, 20));
    setPagination((prev) => ({ ...prev, current: nextCurrent, pageSize: nextSize }));
  }

  // UI ------------------------------------------------------------
  return (
    <div>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Organização</h6>
          <h1 style={{ margin: 0 }}>{t('employees.title')}</h1>
        </div>
        <Space size="middle">
          <Button
            icon={<StopOutlined />}
            type={blockedOnly ? "primary" : "default"}
            onClick={() => {
              setBlockedOnly(!blockedOnly);
              setPagination(prev => ({ ...prev, current: 1 }));
            }}
          >
            {t('employees.blockedOnly')}
          </Button>

          <Tooltip title={t('employees.viewInvites')}>
            <Button icon={<MailOutlined />} onClick={() => setInvitesDrawerOpen(true)} />
          </Tooltip>

          <Button type="primary" icon={<UserAddOutlined />} onClick={() => setCreateOpen(true)}>
            {t('employees.addAccount')}
          </Button>
        </Space>
      </div>

      {/* Filtros */}
      <div style={{ display: "flex", gap: "10.2px", alignItems: "center", marginBottom: "13.6px" }}>
        <Search
          placeholder={t('employees.searchPlaceholder')}
          allowClear
          onChange={(e) => setQ(e.target.value)}
          value={q}
          onSearch={(value) => setQ(value)}
          style={{ maxWidth: 320 }}
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
        />
        <Select
          placeholder={t('employees.filterByRole')}
          value={role}
          onChange={(v) => {
            setRole(v as any);
            setPagination((prev) =>
              prev.current === 1 ? prev : { ...prev, current: 1 }
            );
          }}
          style={{ minWidth: 180 }}
          suffixIcon={<FilterOutlined style={{ opacity: 0.5 }} />}
        >
          <Option value="ALL">{t('employees.roles.all')}</Option>
          <Option value="ADMIN">{t('employees.roles.admins')}</Option>
          <Option value="EMPLOYEE">{t('employees.roles.employees')}</Option>
        </Select>
      </div>

      {/* tabela */}
      <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
        <Table<Employee>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          onChange={onTableChange}
          onRow={(record) => ({
            onContextMenu: (e) => handleContextMenu(e, record),
            style: { cursor: 'context-menu' },
          })}
          pagination={{
            ...pagination,
            showTotal: (total, range) => (
              <Text style={{ color: D.stoneGray }}>
                {t('employees.rangeTotal', { from: range[0], to: range[1], total })}
              </Text>
            ),
          }}
          locale={{ 
            emptyText: (
              <Empty 
                description={t('employees.noEmployees')}
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )
          }}
        />
      </div>

      {/* Drawer global (fora da tabela) */}
      <Drawer
        title={t('employees.profileDrawerTitle')}
        placement="right"
        width={600}
        open={open}
        onClose={closeProfile}
        destroyOnClose
      >
        {selectedProfile && (
          <ProfileView
            profileId={selectedProfile.id}
            mode="drawer"
            onClose={closeProfile}
            onProfileUpdated={handleProfileUpdated}
          />
        )}
      </Drawer>

      {/* Drawer de CRIAÇÃO */}
      <CreateEmployeeDrawer
        open={createOpen}
        onClose={closeCreate}
        onCreated={() => {
          message.success(t('employees.created'));
          const controller = new AbortController();
          fetchData(
            { current: toInt(pagination.current, 1), pageSize: toInt(pagination.pageSize, 20) },
            controller
          );
        }}
      />
      
      <InvitesDrawer
        open={invitesDrawerOpen}
        onClose={() => setInvitesDrawerOpen(false)}
      />

      {/* Context menu botão direito */}
      <EmployeeContextMenu
        visible={ctxMenu.visible}
        x={ctxMenu.x}
        y={ctxMenu.y}
        record={ctxMenu.record}
        onClose={closeCtxMenu}
        onView={(id) => { closeCtxMenu(); openProfile(id); }}
        onStatusChanged={handleCtxStatusChanged}
        onDeleted={handleCtxDeleted}
      />
    </div>
  );
}