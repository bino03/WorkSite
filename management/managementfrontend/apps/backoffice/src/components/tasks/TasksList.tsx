import { FC } from 'react';
import { Table, Spin, Empty, Pagination, Tag, Button, Space, Popconfirm, Avatar, Tooltip } from 'antd';
import { Eye, Pencil, Trash2 } from 'lucide-react';
import type { TaskResponse, TaskStatus } from '@/types/task';
import { taskService } from '@/services/taskService';
import { ErrorHandler } from '@/errors/errorHandler';
import { notificationService } from '@/services/general/notificationService';
import { formatDate } from '@/utils/formatters';
import { useAuth } from '@/hooks/useAuth';
import { D, actionButtonBaseStyle } from '@/config/entityColors';

interface TasksListProps {
  tasks: TaskResponse[];
  loading: boolean;
  pagination: {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
  };
  onPageChange: (page: number) => void;
  onView: (task: TaskResponse) => void;
  onEdit: (task: TaskResponse) => void;
  onDeleted: () => void;
}

const statusColorMap: Record<TaskStatus, string> = {
  PENDING: 'default',
  IN_PROGRESS: 'blue',
  DONE: 'green',
};

const statusLabelMap: Record<TaskStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em Progresso',
  DONE: 'Concluída',
};

export const TasksList: FC<TasksListProps> = ({
  tasks,
  loading,
  pagination,
  onPageChange,
  onView,
  onEdit,
  onDeleted,
}) => {
  const { profileId, isAdmin } = useAuth();

  const canEdit = (task: TaskResponse) =>
    isAdmin() || task.assignees.some((a) => a.id === profileId);

  const handleDelete = async (task: TaskResponse) => {
    try {
      await taskService.remove(task.id);
      notificationService.success('Tarefa eliminada com sucesso');
      onDeleted();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const columns = [
    {
      title: 'Nome',
      dataIndex: 'name',
      key: 'name',
      width: '20%',
    },
    {
      title: 'Prazo',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: '13%',
      render: (dueDate: string) => formatDate(dueDate),
    },
    {
      title: 'Estado',
      dataIndex: 'status',
      key: 'status',
      width: '13%',
      render: (status: TaskStatus) => (
        <Tag color={statusColorMap[status]}>{statusLabelMap[status]}</Tag>
      ),
    },
    {
      title: 'Projeto',
      key: 'enterprise',
      width: '14%',
      render: (_: unknown, record: TaskResponse) => record.enterprise?.name ?? '—',
    },
    {
      title: 'Atribuídos',
      key: 'assignees',
      width: '20%',
      render: (_: unknown, record: TaskResponse) => (
        <Avatar.Group max={{ count: 4 }}>
          {record.assignees.map((a) => {
            const initials = a.name
              ?.split(' ')
              .map((n) => n[0])
              .join('')
              .toUpperCase() || '?';
            return (
              <Tooltip key={a.id} title={a.name}>
                <Avatar size={26} style={{ fontSize: 11 }}>{initials}</Avatar>
              </Tooltip>
            );
          })}
        </Avatar.Group>
      ),
    },
    {
      title: 'Ações',
      key: 'actions',
      width: 170,
      render: (_: unknown, record: TaskResponse) => (
        <Space size={2} direction="vertical" style={{ width: '100%' }}>
          <Button
            type="text"
            icon={<Eye size={14} />}
            onClick={() => onView(record)}
            style={{
              ...actionButtonBaseStyle,
              color: D.ivory,
              background: D.terracotta,
              border: 'none',
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
            Ver detalhes
          </Button>

          {canEdit(record) && (
            <Button
              type="text"
              icon={<Pencil size={14} />}
              onClick={() => onEdit(record)}
              style={{
                ...actionButtonBaseStyle,
                color: D.charcoalWarm,
                background: D.warmSand,
                border: `1px solid ${D.borderWarm}`,
                boxShadow: 'none',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = D.terracotta;
                e.currentTarget.style.color = D.terracotta;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = D.borderWarm;
                e.currentTarget.style.color = D.charcoalWarm;
              }}
            >
              Editar
            </Button>
          )}

          {isAdmin() && (
            <Popconfirm
              title="Eliminar esta tarefa?"
              description={`"${record.name}" será eliminada permanentemente.`}
              okText="Eliminar"
              okType="danger"
              cancelText="Cancelar"
              onConfirm={() => handleDelete(record)}
            >
              <Button
                type="text"
                icon={<Trash2 size={14} />}
                style={{
                  ...actionButtonBaseStyle,
                  color: '#b53333',
                  background: D.warmSand,
                  border: `1px solid ${D.borderWarm}`,
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
                Eliminar
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  if (!tasks.length && !loading) {
    return <Empty description="Nenhuma tarefa encontrada" />;
  }

  return (
    <Spin spinning={loading}>
      <Table
        columns={columns}
        dataSource={tasks.map((task) => ({ ...task, key: task.id }))}
        pagination={false}
        scroll={{ x: 1100 }}
      />
      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
        <Pagination
          current={pagination.currentPage + 1}
          total={pagination.totalElements}
          pageSize={pagination.pageSize}
          onChange={(page) => onPageChange(page - 1)}
        />
      </div>
    </Spin>
  );
};
