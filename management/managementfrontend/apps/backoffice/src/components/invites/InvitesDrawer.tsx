// src/components/employees/InvitesDrawer.tsx
import React, { useEffect, useState } from "react";
import { Drawer, Table, Select, Space, Button, Tag, Typography, message, Card } from "antd";
import { 
  MailOutlined,
  FilterOutlined,
  UserOutlined,
  CrownOutlined,
  TeamOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  StopOutlined,
  ClearOutlined
} from "@ant-design/icons";
import { listInvites, type InviteListItem, type InviteFilters } from "@/services/adminService";
import { useTranslation } from "react-i18next";
import type { Role } from "@/services/profileService";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/pt";

dayjs.extend(relativeTime);
dayjs.locale("pt");

type Props = {
  open: boolean;
  onClose: () => void;
};

const { Title, Text } = Typography;

const statusColors: Record<string, string> = {
  PENDING: "blue",
  ACCEPTED: "green",
  EXPIRED: "red",
  CANCELLED: "default",
};


const statusIcons: Record<string, React.ReactNode> = {
  PENDING: <ClockCircleOutlined />,
  ACCEPTED: <CheckCircleOutlined />,
  EXPIRED: <ExclamationCircleOutlined />,
  CANCELLED: <StopOutlined />,
};

export default function InvitesDrawer({ open, onClose }: Props) {
  const { t } = useTranslation();
  const [invites, setInvites] = useState<InviteListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<InviteFilters>({});

  async function fetchInvites() {
    setLoading(true);
    try {
      const data = await listInvites(filters);
      setInvites(data);
    } catch (err: any) {
      message.error(err?.response?.data?.message || "Erro ao carregar convites.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (open) {
      fetchInvites();
    }
  }, [open, filters]);

  function clearFilters() {
    setFilters({});
  }

  const columns: ColumnsType<InviteListItem> = [
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <MailOutlined style={{ color: '#1890ff' }} />
          <span>Email</span>
        </div>
      ),
      dataIndex: "email",
      key: "email",
      render: (email) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{
            padding: '6px',
            backgroundColor: '#f0f9ff',
            borderRadius: '6px',
            border: '1px solid #bae7ff'
          }}>
            <MailOutlined style={{ color: '#1890ff', fontSize: '12px' }} />
          </div>
          <Text style={{ fontWeight: 500 }}>{email}</Text>
        </div>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <UserOutlined style={{ color: '#722ed1' }} />
          <span>Role</span>
        </div>
      ),
      dataIndex: "role",
      key: "role",
      render: (role: Role) => (
        <Tag 
          icon={role === "ADMIN" ? <CrownOutlined /> : <TeamOutlined />}
          color={role === "ADMIN" ? "gold" : "blue"}
          style={{ 
            borderRadius: '16px',
            padding: '4px 12px',
            fontWeight: 500
          }}
        >
          {role === "ADMIN" ? t('profile.roles.admin') : t('profile.roles.employee')}
        </Tag>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <CheckCircleOutlined style={{ color: '#52c41a' }} />
          <span>{t('invites.statusFilter')}</span>
        </div>
      ),
      dataIndex: "status",
      key: "status",
      render: (status: string) => (
        <Tag 
          icon={statusIcons[status]}
          color={statusColors[status]}
          style={{ 
            borderRadius: '16px',
            padding: '4px 12px',
            fontWeight: 500
          }}
        >
          {t(`invites.status.${status.toLowerCase()}`, { defaultValue: status })}
        </Tag>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <UserOutlined style={{ color: '#8c8c8c' }} />
          <span>{t('common.by', { defaultValue: 'Por' })}</span>
        </div>
      ),
      dataIndex: "invitedByName",
      key: "invitedByName",
      render: (name) => (
        <Text style={{ color: '#595959', fontWeight: 500 }}>{name}</Text>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <CalendarOutlined style={{ color: '#fa8c16' }} />
          <span>{t('common.date')}</span>
        </div>
      ),
      dataIndex: "sentAt",
      key: "sentAt",
      render: (sentAt: string) => (
        <Text style={{ color: '#595959', fontSize: '13px' }}>
          {dayjs(sentAt).format("DD/MM/YYYY HH:mm")}
        </Text>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <CheckCircleOutlined style={{ color: '#52c41a' }} />
          <span>{t('invites.status.accepted')}</span>
        </div>
      ),
      dataIndex: "acceptedAt",
      key: "acceptedAt",
      render: (acceptedAt?: string) => (
        <Text style={{ color: '#595959', fontSize: '13px' }}>
          {acceptedAt ? dayjs(acceptedAt).format("DD/MM/YYYY HH:mm") : "—"}
        </Text>
      ),
    },
    {
      title: (
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <ClockCircleOutlined style={{ color: '#fa541c' }} />
          <span>{t('invites.status.expired')}</span>
        </div>
      ),
      dataIndex: "expiresAt",
      key: "expiresAt",
      render: (expiresAt: string, record) => {
        if (record.status === "ACCEPTED" || record.status === "CANCELLED") {
          return <Text style={{ color: '#bfbfbf' }}>—</Text>;
        }
        const isExpired = dayjs().isAfter(dayjs(expiresAt));
        return (
          <Text style={{ 
            color: isExpired ? '#ff4d4f' : '#595959',
            fontSize: '13px',
            fontWeight: isExpired ? 600 : 400
          }}>
            {isExpired ? t('invites.status.expired') : dayjs(expiresAt).fromNow()}
          </Text>
        );
      },
    },
  ];

  return (
    <Drawer
      title={
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '8px 0'
        }}>
          <div style={{
            padding: '8px',
            backgroundColor: '#f0f9ff',
            borderRadius: '8px',
            border: '1px solid #bae7ff'
          }}>
            <MailOutlined style={{ color: '#1890ff', fontSize: '18px' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0, color: '#262626' }}>
              {t('invites.title')}
            </Title>
            <Text style={{ color: '#8c8c8c', fontSize: '13px' }}>
              {t('invites.subtitle')}
            </Text>
          </div>
        </div>
      }
      placement="right"
      width={1200}
      open={open}
      onClose={onClose}
      destroyOnClose
      styles={{
        body: { 
          padding: '24px',
          backgroundColor: '#fafafa'
        }
      }}
    >
      {/* Filtros */}
      <Card 
        style={{ 
          marginBottom: '20px',
          borderRadius: '12px',
          border: '1px solid #e8e8e8'
        }}
        bodyStyle={{ padding: '20px' }}
      >
        <div style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <FilterOutlined style={{ color: '#1890ff', fontSize: '16px' }} />
            <Title level={5} style={{ margin: 0, color: '#262626' }}>
              {t('invites.filters')}
            </Title>
          </div>
        </div>

        <Space size="middle" wrap>
          <div>
            <Text style={{ 
              display: 'block', 
              marginBottom: '6px', 
              color: '#8c8c8c',
              fontSize: '13px',
              fontWeight: 500
            }}>
              {t('invites.statusFilter')}
            </Text>
            <Select
              placeholder="Filtrar por status"
              allowClear
              style={{ width: 180 }}
              size="large"
              value={filters.status}
              onChange={(status) => setFilters({ ...filters, status })}
              options={[
                { value: "PENDING", label: t('invites.status.pending') },
                { value: "ACCEPTED", label: t('invites.status.accepted') },
                { value: "EXPIRED", label: t('invites.status.expired') },
                { value: "CANCELLED", label: t('invites.status.cancelled') },
              ]}
            />
          </div>
          
          <div>
            <Text style={{ 
              display: 'block', 
              marginBottom: '6px', 
              color: '#8c8c8c',
              fontSize: '13px',
              fontWeight: 500
            }}>
              {t('employees.filterByRole')}
            </Text>
            <Select
              placeholder="Filtrar por função"
              allowClear
              style={{ width: 180 }}
              size="large"
              value={filters.role}
              onChange={(role) => setFilters({ ...filters, role })}
              options={[
                { value: "ADMIN", label: t('profile.roles.admin') },
                { value: "EMPLOYEE", label: t('profile.roles.employee') },
              ]}
            />
          </div>
          
          <div style={{ alignSelf: 'flex-end' }}>
            <Button 
              icon={<ClearOutlined />} 
              onClick={clearFilters}
              size="large"
              style={{
                borderRadius: '8px',
                border: '1px solid #e8e8e8',
                color: '#595959'
              }}
            >
              {t('common.clearFilters')}
            </Button>
          </div>
        </Space>
      </Card>

      {/* Tabela */}
      <Card
        style={{
          borderRadius: '12px',
          border: '1px solid #e8e8e8'
        }}
        bodyStyle={{ padding: 0 }}
      >
        <Table
          columns={columns}
          dataSource={invites}
          loading={loading}
          rowKey="id"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => (
              <Text style={{ color: '#8c8c8c' }}>
                {t('common.total')}: {total}
              </Text>
            ),
          }}
          style={{
            borderRadius: '12px',
            overflow: 'hidden'
          }}
          scroll={{ x: 1000 }}
        />
      </Card>
    </Drawer>
  );
}