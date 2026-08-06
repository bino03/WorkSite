import type { FC } from 'react';
import { Table, Spin, Empty, Pagination } from 'antd';
import { ListActions, ListActionPrimary, ListActionSecondary, ListActionDanger } from '@/components/common/ListActions';
import type { TaskResponse, TaskStatus } from '@/types/task';
import { taskService } from '@/services/taskService';
import { ErrorHandler } from '@/errors/errorHandler';
import { notificationService } from '@/services/general/notificationService';
import { formatDate } from '@/utils/formatters';
import { useAuth } from '@/hooks/useAuth';
import { useConfirm } from '@/context/ConfirmDialogContext';

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

const statusClassMap: Record<TaskStatus, string> = {
  PENDING: 'ind-tag-outline',
  IN_PROGRESS: 'ind-tag-accent',
  DONE: 'ind-tag-neutral',
};

const statusLabelMap: Record<TaskStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em curso',
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
  const confirm = useConfirm();

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

  const confirmDelete = (task: TaskResponse) => {
    confirm({
      message: `Eliminar a tarefa "${task.name}"? Esta ação não pode ser desfeita.`,
      onConfirm: () => handleDelete(task),
    });
  };

  const columns = [
    {
      title: 'Nome',
      dataIndex: 'name',
      key: 'name',
      width: '20%',
      render: (name: string) => <span style={{ fontFamily: 'var(--ind-font-heading)', fontWeight: 600 }}>{name}</span>,
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
        <span className={`ind-tag ${statusClassMap[status]}`}>{statusLabelMap[status]}</span>
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
        <div style={{ display: 'flex' }}>
          {record.assignees.map((a) => {
            const initials = a.name
              ?.split(' ')
              .map((n) => n[0])
              .join('')
              .toUpperCase() || '?';
            return (
              <div
                key={a.id}
                title={a.name}
                style={{
                  width: 24, height: 24, borderRadius: '50%',
                  background: 'var(--ind-accent-100)', color: 'var(--ind-accent-800)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 10, fontFamily: 'var(--ind-font-heading)',
                  border: '1.5px solid var(--ind-color-bg)', marginLeft: -6,
                }}
              >
                {initials}
              </div>
            );
          })}
        </div>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 170,
      render: (_: unknown, record: TaskResponse) => (
        <ListActions>
          <ListActionPrimary onClick={() => onView(record)}>Ver detalhes</ListActionPrimary>
          {canEdit(record) && (
            <ListActionSecondary onClick={() => onEdit(record)}>Editar</ListActionSecondary>
          )}
          {isAdmin() && (
            <ListActionDanger onClick={() => confirmDelete(record)}>Eliminar</ListActionDanger>
          )}
        </ListActions>
      ),
    },
  ];

  if (!tasks.length && !loading) {
    return <Empty description="Nenhuma tarefa encontrada" />;
  }

  return (
    <div>
      <div style={{ borderTop: '1px solid var(--ind-color-divider)' }}>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={tasks.map((task) => ({ ...task, key: task.id }))}
            pagination={false}
            scroll={{ x: 1100 }}
          />
        </Spin>
      </div>
      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: 12, opacity: 0.6, margin: 0 }}>{pagination.totalElements} resultado(s)</p>
        <Pagination
          current={pagination.currentPage + 1}
          total={pagination.totalElements}
          pageSize={pagination.pageSize}
          onChange={(page) => onPageChange(page - 1)}
        />
      </div>
    </div>
  );
};
