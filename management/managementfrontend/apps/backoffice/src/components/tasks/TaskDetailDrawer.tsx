import { FC, useState } from 'react';
import { Drawer, Descriptions, Tag, Form, Select, Button, Space, Spin } from 'antd';
import { taskService } from '@/services/taskService';
import { notificationService } from '@/services/general/notificationService';
import { ErrorHandler } from '@/errors/errorHandler';
import { formatDate } from '@/utils/formatters';
import type { TaskResponse, TaskStatus } from '@/types/task';

interface Props {
  task: TaskResponse | null;
  open: boolean;
  onClose: () => void;
  onStatusUpdated: (task: TaskResponse) => void;
}

const statusOptions: { value: TaskStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pendente' },
  { value: 'IN_PROGRESS', label: 'Em Progresso' },
  { value: 'DONE', label: 'Concluída' },
];

export const TaskDetailDrawer: FC<Props> = ({ task, open, onClose, onStatusUpdated }) => {
  const [form] = Form.useForm<{ status: TaskStatus }>();
  const [loading, setLoading] = useState(false);

  const handleStatusUpdate = async (values: { status: TaskStatus }) => {
    if (!task) return;

    setLoading(true);
    try {
      const updated = await taskService.updateStatus(task.id, { status: values.status });
      notificationService.success('Estado atualizado com sucesso');
      onStatusUpdated(updated);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Drawer title={task ? `Detalhes da Tarefa - ${task.name}` : 'Detalhes da Tarefa'} open={open} onClose={onClose} width={500}>
      <Spin spinning={loading}>
        {task && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Nome">{task.name}</Descriptions.Item>
              <Descriptions.Item label="Descrição">{task.description || '—'}</Descriptions.Item>
              <Descriptions.Item label="Prazo">{formatDate(task.dueDate)}</Descriptions.Item>
              <Descriptions.Item label="Criado por">{task.createdBy?.name ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Atribuídos">
                {task.assignees.length
                  ? task.assignees.map((a) => <Tag key={a.id}>{a.name}</Tag>)
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Criado em">{formatDate(task.createdAt)}</Descriptions.Item>
            </Descriptions>

            <Form
              form={form}
              layout="vertical"
              initialValues={{ status: task.status }}
              onFinish={handleStatusUpdate}
            >
              <Form.Item
                label="Estado"
                name="status"
                rules={[{ required: true, message: 'Selecione um estado' }]}
              >
                <Select options={statusOptions} />
              </Form.Item>

              <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                <Button onClick={onClose}>Fechar</Button>
                <Button type="primary" htmlType="submit" loading={loading}>
                  Atualizar Estado
                </Button>
              </Space>
            </Form>
          </div>
        )}
      </Spin>
    </Drawer>
  );
};
