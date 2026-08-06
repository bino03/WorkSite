import { useEffect, useState } from 'react';
import type { FC } from 'react';
import { Button, Input } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useTasks } from '@/hooks/useTasks';
import { TasksList } from '@/components/tasks/TasksList';
import { TaskFormDrawer } from '@/components/tasks/TaskFormDrawer';
import { TaskDetailDrawer } from '@/components/tasks/TaskDetailDrawer';
import { useAuth } from '@/hooks/useAuth';
import type { TaskResponse, TaskStatus } from '@/types/task';

const statusOptions: { value: TaskStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pendente' },
  { value: 'IN_PROGRESS', label: 'Em curso' },
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
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Gestão</h6>
          <h1 style={{ margin: 0 }}>{isAdmin() ? "Tarefas" : "Minhas Tarefas"}</h1>
        </div>
        {isAdmin() && (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            Nova Tarefa
          </Button>
        )}
      </div>

      <div style={{ display: "flex", gap: "10.2px", alignItems: "center", marginBottom: "13.6px" }}>
        <Input
          placeholder="Pesquisar tarefas…"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={filters.q}
          onChange={(e) => handleFilterChange({ q: e.target.value })}
          allowClear
          style={{ maxWidth: 320 }}
        />
        <select
          value={filters.status ?? ""}
          onChange={(e) => handleFilterChange({ status: (e.target.value || null) as TaskStatus | null })}
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
          {statusOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
        <Button onClick={handleClearFilters}>Limpar</Button>
      </div>

      <TasksList
        tasks={tasks}
        loading={loading}
        pagination={pagination}
        onPageChange={handlePageChange}
        onView={handleView}
        onEdit={handleEdit}
        onDeleted={() => fetchTasks()}
      />

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
