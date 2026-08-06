// src/components/employees/CreateEmployeeDrawer.tsx
import { useEffect, useState } from "react";
import { Drawer, Form, Input, Select, Button, Space, Divider, message, Typography, Segmented } from "antd";
import { useTranslation } from "react-i18next";
import { Save, X, UserPlus, Mail } from "lucide-react";
import type { Employee, Role } from "@/services/profileService";
import {
  createEmployee,
  sendEmployeeInvite,
} from "@/services/adminService";

type Props = {
  open: boolean;
  onClose: () => void;
  onCreated?: (emp: Employee) => void;
};

type CreationType = "manual" | "invite";

const { Text } = Typography;

export default function CreateEmployeeDrawer({ open, onClose, onCreated }: Props) {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [creationType, setCreationType] = useState<CreationType>("manual");

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setCreationType("manual");
    }
  }, [open, form]);

  async function onFinish(values: Record<string, string>) {
    setSubmitting(true);

    try {
      if (creationType === "manual") {
        // Lógica existente - Adicionar Manualmente
        const created = await createEmployee({
          email: values.email.trim(),
          password: values.password,
          name: values.name.trim(),
          phone: values.phone?.trim() || null,
          role: values.role as Role,
        });
        message.success(t('employees.created'));
        onCreated?.(created);
      } else {
        // Nova lógica - Enviar Convite
        await sendEmployeeInvite({
          email: values.email.trim(),
          phone: values.phone?.trim() || null,
          role: values.role as Role,
        });
        message.success(t('employees_drawer.inviteSent'));
      }
      onClose();
    } catch (err: unknown) {
      const e = err as Record<string, unknown>;
      const resp = (e.response as Record<string, unknown>)?.data as Record<string, unknown>;
      const apiMsg: string | undefined = (resp?.message || e.message) as string | undefined;
      message.error(
        apiMsg ?? 
        (creationType === "manual" ? t('employees_drawer.createError') : t('employees_drawer.inviteError'))
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer
      width={640}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Equipa</h6>
          <h3 style={{ margin: 0 }}>{t('employees_drawer.title')}</h3>
        </div>
      }
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <div className="mb-4">
        <Text className="muted">
          {creationType === "manual"
            ? t('employees_drawer.manualSubtitle')
            : t('employees_drawer.inviteSubtitle')}
        </Text>
      </div>

      {/* Seletor de tipo de criação */}
      <div className="mb-6">
        <Segmented
          value={creationType}
          onChange={(value) => {
            setCreationType(value as CreationType);
            form.resetFields();
          }}
          options={[
            {
              label: (
                <div className="flex items-center gap-2 px-2">
                  <UserPlus size={16} />
                  <span>{t('employees_drawer.addManually')}</span>
                </div>
              ),
              value: "manual",
            },
            {
              label: (
                <div className="flex items-center gap-2 px-2">
                  <Mail size={16} />
                  <span>{t('employees_drawer.sendInvite')}</span>
                </div>
              ),
              value: "invite",
            },
          ]}
          block
          disabled={submitting}
        />
      </div>

      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        disabled={submitting}
      >
        {/* Campos condicionais baseados no tipo */}
        {creationType === "manual" ? (
          <>
            <Form.Item
              name="name"
              label={t('common.name')}
              rules={[{ required: true, message: t('employees_drawer.nameRequired') }]}
            >
              <Input placeholder="Nome completo" aria-label="Nome" />
            </Form.Item>

            <Form.Item
              name="email"
              label={t('common.email')}
              rules={[
                { required: true, message: t('employees_drawer.emailRequired') },
                { type: "email", message: t('employees_drawer.emailInvalid') },
              ]}
            >
              <Input placeholder="email@exemplo.com" aria-label="Email" />
            </Form.Item>

            <Form.Item
              name="password"
              label={t('auth.password')}
              rules={[
                { required: true, message: t('employees_drawer.passwordRequired') },
                { min: 8, message: t('employees_drawer.passwordMinLength') },
              ]}
            >
              <Input.Password placeholder="********" aria-label="Password" />
            </Form.Item>

            <Form.Item name="phone" label={t('employees_drawer.phone')}>
              <Input placeholder="+351..." aria-label="Telefone" />
            </Form.Item>

            <Form.Item
              name="role"
              label={t('employees_drawer.permissions')}
              rules={[{ required: true, message: t('employees_drawer.selectProfile') }]}
            >
              <Select<Role>
                placeholder={t('employees_drawer.selectProfile')}
                options={[
                  { value: "ADMIN", label: t('profile.roles.admin') },
                  { value: "EMPLOYEE", label: t('profile.roles.employee') },
                ]}
                aria-label="Perfil"
              />
            </Form.Item>
          </>
        ) : (
          <>
            <Form.Item
              name="email"
              label={t('common.email')}
              rules={[
                { required: true, message: t('employees_drawer.emailRequired') },
                { type: "email", message: t('employees_drawer.emailInvalid') },
              ]}
            >
              <Input placeholder="email@exemplo.com" aria-label="Email" />
            </Form.Item>

            <Form.Item name="phone" label={t('employees_drawer.phone')}>
              <Input placeholder="+351..." aria-label="Telefone" />
            </Form.Item>

            <Form.Item
              name="role"
              label={t('employees_drawer.permissions')}
              rules={[{ required: true, message: t('employees_drawer.selectProfile') }]}
            >
              <Select<Role>
                placeholder={t('employees_drawer.selectProfile')}
                options={[
                  { value: "ADMIN", label: t('profile.roles.admin') },
                  { value: "EMPLOYEE", label: t('profile.roles.employee') },
                ]}
                aria-label="Perfil"
              />
            </Form.Item>
          </>
        )}

        {/* Rodapé sticky dentro do Drawer */}
        <Divider />
        <div
          className="w-full"
          style={{
            position: "sticky",
            left: 0,
            right: 0,
            bottom: 0,
            paddingTop: 8,
            paddingBottom: 8,
            background: "var(--ind-color-bg)",
          }}
        >
          <div className="flex items-center justify-between">
            <Space>
              <Button
                onClick={onClose}
                aria-label="Cancelar"
                icon={<X size={16} />}
                disabled={submitting}
              >
                {t('common.cancel')}
              </Button>
            </Space>

            <Button
              onClick={() => form.submit()}
              loading={submitting}
              aria-label={creationType === "manual" ? "Criar conta" : "Enviar convite"}
              icon={creationType === "manual" ? <Save size={16} /> : <Mail size={16} />}
              type="primary"
            >
              {creationType === "manual" ? t('common.create') : t('employees_drawer.sendInvite')}
            </Button>
          </div>
        </div>
      </Form>
    </Drawer>
  );
}