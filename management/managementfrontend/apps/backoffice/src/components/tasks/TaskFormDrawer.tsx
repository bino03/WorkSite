import { FC, useEffect, useState } from 'react';
import { Drawer, Form, Input, DatePicker, Select, Button, Tag } from 'antd';
import dayjs from 'dayjs';
import { taskService } from '@/services/taskService';
import { notificationService } from '@/services/general/notificationService';
import { ErrorHandler } from '@/errors/errorHandler';
import { listAssignableUsers } from '@/services/profileService';
import type { AssignableEmployee } from '@/services/profileService';
import { searchEnterprises } from '@/services/enterpriseService';
import type { EnterpriseOption } from '@/services/enterpriseService';
import type { TaskResponse } from '@/types/task';

interface TaskFormValues {
  name: string;
  description?: string;
  dueDate: dayjs.Dayjs;
  enterpriseId?: string;
  assigneeIds: string[];
}

interface Props {
  open: boolean;
  task: TaskResponse | null; // null = criar, preenchido = editar
  onClose: () => void;
  onSaved: () => void;
}

export const TaskFormDrawer: FC<Props> = ({ open, task, onClose, onSaved }) => {
  const [form] = Form.useForm<TaskFormValues>();
  const [saving, setSaving] = useState(false);
  const [assignableUsers, setAssignableUsers] = useState<AssignableEmployee[]>([]);
  const [enterpriseOptions, setEnterpriseOptions] = useState<EnterpriseOption[]>([]);
  const [enterpriseSearchLoading, setEnterpriseSearchLoading] = useState(false);
  const isEdit = !!task;

  useEffect(() => {
    if (!open) return;
    listAssignableUsers().then(setAssignableUsers).catch(() => {});

    if (task) {
      form.setFieldsValue({
        name: task.name,
        description: task.description ?? undefined,
        dueDate: dayjs(task.dueDate),
        enterpriseId: task.enterprise?.id,
        assigneeIds: task.assignees.map((a) => a.id),
      });
      setEnterpriseOptions(task.enterprise ? [task.enterprise] : []);
    } else {
      form.resetFields();
      setEnterpriseOptions([]);
    }
  }, [open, task, form]);

  const handleEnterpriseSearch = async (q: string) => {
    setEnterpriseSearchLoading(true);
    try {
      const results = await searchEnterprises(q);
      setEnterpriseOptions(results);
    } catch {
      // silencioso — pesquisa de conveniência, não bloqueia o formulário
    } finally {
      setEnterpriseSearchLoading(false);
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();

    const payload = {
      name: values.name,
      description: values.description,
      dueDate: values.dueDate.toISOString(),
      enterpriseId: values.enterpriseId,
      assigneeIds: values.assigneeIds,
    };

    setSaving(true);
    try {
      if (isEdit && task) {
        await taskService.update(task.id, payload);
        notificationService.success('Tarefa atualizada com sucesso');
      } else {
        await taskService.create(payload);
        notificationService.success('Tarefa criada com sucesso');
      }
      onSaved();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? 'Editar Tarefa' : 'Nova Tarefa'}
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
      extra={
        <Button type="primary" loading={saving} onClick={handleSubmit}>
          Guardar
        </Button>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="Nome"
          rules={[
            { required: true, message: 'O nome é obrigatório' },
            { max: 200, message: 'Máximo de 200 caracteres' },
          ]}
        >
          <Input placeholder="Nome da tarefa" />
        </Form.Item>

        <Form.Item
          name="description"
          label="Descrição"
          rules={[{ max: 2000, message: 'Máximo de 2000 caracteres' }]}
        >
          <Input.TextArea rows={4} maxLength={2000} showCount />
        </Form.Item>

        <Form.Item
          name="dueDate"
          label="Prazo"
          rules={[{ required: true, message: 'O prazo é obrigatório' }]}
        >
          <DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="enterpriseId" label="Projeto (opcional)">
          <Select
            allowClear
            showSearch
            placeholder="Ligar a um projeto"
            filterOption={false}
            loading={enterpriseSearchLoading}
            onSearch={handleEnterpriseSearch}
            options={enterpriseOptions.map((e) => ({ value: e.id, label: e.name }))}
          />
        </Form.Item>

        <Form.Item
          name="assigneeIds"
          label="Atribuídos"
          rules={[{ required: true, message: 'Selecione pelo menos um utilizador', type: 'array', min: 1 }]}
        >
          <Select
            mode="multiple"
            placeholder="Selecionar utilizadores"
            filterOption={(input, option) =>
              (option?.searchText ?? '').toLowerCase().includes(input.toLowerCase())
            }
            options={assignableUsers.map((u) => ({
              value: u.id,
              searchText: `${u.name} ${u.email}`,
              label: (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                  <span>{u.name}</span>
                  <Tag color={u.role === 'ADMIN' ? 'gold' : 'blue'} style={{ marginInlineEnd: 0 }}>
                    {u.role === 'ADMIN' ? 'Admin' : 'Funcionário'}
                  </Tag>
                  <span style={{ color: '#87867f', fontSize: 12 }}>{u.email}</span>
                </span>
              ),
            }))}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
};
