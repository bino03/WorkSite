import { FC, useEffect, useState } from 'react';
import { Card, Button, Input, Select, Space } from 'antd';
import { PlusOutlined, SearchOutlined, ClearOutlined } from '@ant-design/icons';
import { useTasks } from '@/hooks/useTasks';
import { TasksList } from '@/components/tasks/TasksList';
import { TaskFormDrawer } from '@/components/tasks/TaskFormDrawer';
import { TaskDetailDrawer } from '@/components/tasks/TaskDetailDrawer';
import { useAuth } from '@/hooks/useAuth';
import type { TaskResponse, TaskStatus } from '@/types/task';

const statusOptions: { value: TaskStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pendente' },
  { value: 'IN_PROGRESS', label: 'Em Progresso' },
  { value: 'DONE', label: 'Concluída' },
];

const TasksPage: FC = () => {
  const { tasks, loading, pagination, filters, fetchTasks, handleFilterChange, handlePageChange } =
    useTasks();
  const { isAdmin } = useAuth();

  const [selectedTask, setSelectedTask] = useState<TaskResponse | null>(null);
  const [formDrawerOpen, setFormDrawerOpen] = useState(false);
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);

  useEffect(() => {
    fetchTasks();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = () => {
    setSelectedTask(null);
    setFormDrawerOpen(true);
  };

  const handleEdit = (task: TaskResponse) => {
    setSelectedTask(task);
    setFormDrawerOpen(true);
  };

  const handleView = (task: TaskResponse) => {
    setSelectedTask(task);
    setDetailDrawerOpen(true);
  };

  const handleStatusUpdated = (updated: TaskResponse) => {
    setSelectedTask(updated);
    fetchTasks();
  };

  const handleClearFilters = () => {
    handleFilterChange({ q: '', status: null });
  };

  return (
    <div>
      <Card
        title="Gestão de Tarefas"
        extra={
          isAdmin() && (
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              Nova Tarefa
            </Button>
          )
        }
        style={{ marginBottom: 24 }}
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Input
            placeholder="Procurar por nome..."
            prefix={<SearchOutlined />}
            value={filters.q}
            onChange={(e) => handleFilterChange({ q: e.target.value })}
            allowClear
            style={{ width: 240 }}
          />
          <Select
            placeholder="Estado"
            value={filters.status}
            onChange={(value) => handleFilterChange({ status: value ?? null })}
            options={statusOptions}
            style={{ width: 160 }}
            allowClear
          />
          <Button icon={<ClearOutlined />} onClick={handleClearFilters}>
            Limpar
          </Button>
        </Space>

        <TasksList
          tasks={tasks}
          loading={loading}
          pagination={pagination}
          onPageChange={handlePageChange}
          onView={handleView}
          onEdit={handleEdit}
          onDeleted={() => fetchTasks()}
        />
      </Card>

      <TaskFormDrawer
        open={formDrawerOpen}
        task={selectedTask}
        onClose={() => setFormDrawerOpen(false)}
        onSaved={() => fetchTasks()}
      />

      <TaskDetailDrawer
        task={selectedTask}
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
        onStatusUpdated={handleStatusUpdated}
      />
    </div>
  );
};

export default TasksPage;
