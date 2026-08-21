import { useCallback, useEffect, useState } from "react";
import type { CSSProperties, FC } from "react";
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
} from "antd";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { DeleteOutlined, EditOutlined, PlusOutlined, SendOutlined } from "@ant-design/icons";

import {
  createEmailProvider,
  deleteEmailProvider,
  listEmailProviders,
  setDefaultEmailProvider,
  setEmailProviderActive,
  testEmailProvider,
  updateEmailProvider,
} from "@/services/emailProviderService";
import {
  emailProviderDefaults,
  emailProviderFormSchema,
  type EmailProviderFormValues,
} from "@/components/settings/emailProviderFormSchema";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import type { EmailProvider } from "@/types/emailProvider";

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * Provedores de email (SMTP).
 *
 * A tabela `settings.email_providers` existe desde a V7 mas nunca teve interface:
 * a configuração entrava por `INSERT` à mão e, sem uma linha lá, o convite de
 * funcionário falhava com "Nenhum provedor de email configurado" e não havia
 * forma de o resolver sem acesso à base de dados.
 *
 * Duas decisões de ecrã que vale a pena não desfazer:
 *
 * - O aviso de "nenhum predefinido" está no topo e não junto a uma linha: é uma
 *   condição do conjunto ("não sai email nenhum"), não de um provedor.
 * - "Enviar teste" fica disponível em qualquer provedor, ativo ou não — serve
 *   precisamente para validar uma configuração *antes* de a promover.
 */
export const EmailProvidersDrawer: FC<Props> = ({ open, onClose }) => {
  const confirm = useConfirm();

  const [providers, setProviders] = useState<EmailProvider[]>([]);
  const [loading, setLoading] = useState(false);
  /** `null` = a criar; um id = a editar; `undefined` = formulário fechado. */
  const [editing, setEditing] = useState<EmailProvider | null | undefined>(undefined);
  const [submitting, setSubmitting] = useState(false);
  /** Id da linha com uma ação em curso — evita dois cliques em paralelo. */
  const [busyId, setBusyId] = useState<string | null>(null);
  const [testTarget, setTestTarget] = useState<EmailProvider | null>(null);
  const [testEmail, setTestEmail] = useState("");
  const [testing, setTesting] = useState(false);

  const isCreating = editing === null;
  const formOpen = editing !== undefined;

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EmailProviderFormValues>({
    resolver: zodResolver(emailProviderFormSchema(isCreating)),
    mode: "onBlur",
    defaultValues: emailProviderDefaults,
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setProviders(await listEmailProviders());
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open) return;
    setEditing(undefined);
    load();
  }, [open, load]);

  const openCreate = () => {
    reset(emailProviderDefaults);
    setEditing(null);
  };

  const openEdit = (provider: EmailProvider) => {
    reset({
      providerName: provider.providerName,
      host: provider.host,
      port: provider.port,
      username: provider.username,
      // Nunca vem do servidor. Vazia significa "mantém a que está".
      password: "",
      fromEmail: provider.fromEmail,
      fromName: provider.fromName ?? "",
      encryption: provider.encryption,
      isDefault: provider.isDefault,
      isActive: provider.isActive,
    });
    setEditing(provider);
  };

  const onSubmit = async (values: EmailProviderFormValues) => {
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        fromName: values.fromName?.trim() ? values.fromName.trim() : null,
        password: values.password?.trim() ? values.password : null,
      };

      if (isCreating) {
        await createEmailProvider(payload);
        notificationService.success("Email", "Provedor criado.");
      } else if (editing) {
        await updateEmailProvider(editing.id, payload);
        notificationService.success("Email", "Provedor atualizado.");
      }

      setEditing(undefined);
      await load();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSubmitting(false);
    }
  };

  const runOnRow = async (provider: EmailProvider, action: () => Promise<unknown>, done: string) => {
    setBusyId(provider.id);
    try {
      await action();
      notificationService.success("Email", done);
      await load();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setBusyId(null);
    }
  };

  const handleDelete = (provider: EmailProvider) => {
    confirm({
      title: "Eliminar provedor",
      message: provider.isDefault
        ? `"${provider.providerName}" é o provedor predefinido. Ao eliminá-lo, os convites e a recuperação de password deixam de conseguir enviar email até marcares outro.`
        : `Eliminar "${provider.providerName}"? As credenciais SMTP são apagadas.`,
      actionLabel: "Eliminar",
      onConfirm: async () => {
        try {
          await deleteEmailProvider(provider.id);
          notificationService.success("Email", "Provedor eliminado.");
          await load();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const handleSendTest = async () => {
    if (!testTarget || !testEmail.trim()) return;

    setTesting(true);
    try {
      await testEmailProvider(testTarget.id, testEmail.trim());
      notificationService.success("Email", `Email de teste enviado para ${testEmail.trim()}.`);
      setTestTarget(null);
      setTestEmail("");
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setTesting(false);
    }
  };

  const hasUsableDefault = providers.some((p) => p.isDefault && p.isActive);

  /* ── Formulário ──────────────────────────────────────────── */
  const form = (
    <Form layout="vertical" onFinish={handleSubmit(onSubmit)}>
      <Form.Item
        label="Nome do provedor"
        validateStatus={errors.providerName ? "error" : undefined}
        help={errors.providerName?.message}
      >
        <Controller
          name="providerName"
          control={control}
          render={({ field }) => <Input {...field} placeholder="Gmail, Sendgrid, servidor da empresa…" />}
        />
      </Form.Item>

      <Space.Compact style={{ display: "flex", gap: 8 }}>
        <Form.Item
          label="Host"
          style={{ flex: 2 }}
          validateStatus={errors.host ? "error" : undefined}
          help={errors.host?.message}
        >
          <Controller
            name="host"
            control={control}
            render={({ field }) => <Input {...field} placeholder="smtp.exemplo.com" />}
          />
        </Form.Item>

        <Form.Item
          label="Porta"
          style={{ flex: 1 }}
          validateStatus={errors.port ? "error" : undefined}
          help={errors.port?.message}
        >
          <Controller
            name="port"
            control={control}
            render={({ field }) => (
              <InputNumber
                {...field}
                style={{ width: "100%" }}
                min={1}
                max={65535}
                onChange={(value) => field.onChange(value ?? undefined)}
              />
            )}
          />
        </Form.Item>
      </Space.Compact>

      <Form.Item
        label="Utilizador"
        validateStatus={errors.username ? "error" : undefined}
        help={errors.username?.message}
      >
        <Controller
          name="username"
          control={control}
          render={({ field }) => <Input {...field} autoComplete="off" />}
        />
      </Form.Item>

      <Form.Item
        label="Password"
        validateStatus={errors.password ? "error" : undefined}
        help={
          errors.password?.message ??
          (isCreating
            ? undefined
            : "Deixa em branco para manter a password atual — ela nunca é devolvida pelo servidor.")
        }
      >
        <Controller
          name="password"
          control={control}
          render={({ field }) => (
            <Input.Password
              {...field}
              autoComplete="new-password"
              placeholder={isCreating || !editing?.hasPassword ? "" : "••••••••"}
            />
          )}
        />
      </Form.Item>

      <Space.Compact style={{ display: "flex", gap: 8 }}>
        <Form.Item
          label="Email de remetente"
          style={{ flex: 1 }}
          validateStatus={errors.fromEmail ? "error" : undefined}
          help={errors.fromEmail?.message}
        >
          <Controller
            name="fromEmail"
            control={control}
            render={({ field }) => <Input {...field} placeholder="obra@empresa.pt" />}
          />
        </Form.Item>

        <Form.Item
          label="Nome de remetente"
          style={{ flex: 1 }}
          validateStatus={errors.fromName ? "error" : undefined}
          help={errors.fromName?.message}
        >
          <Controller
            name="fromName"
            control={control}
            render={({ field }) => <Input {...field} placeholder="Worksite" />}
          />
        </Form.Item>
      </Space.Compact>

      <Form.Item label="Encriptação">
        <Controller
          name="encryption"
          control={control}
          render={({ field }) => (
            <Select
              {...field}
              options={[
                { value: "tls", label: "STARTTLS (porta 587)" },
                { value: "ssl", label: "SSL/TLS (porta 465)" },
                { value: "none", label: "Sem encriptação" },
              ]}
            />
          )}
        />
      </Form.Item>

      <Space size="large" style={{ marginBottom: 16 }}>
        <Controller
          name="isActive"
          control={control}
          render={({ field }) => (
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
              <Switch checked={field.value} onChange={field.onChange} /> Ativo
            </span>
          )}
        />
        <Controller
          name="isDefault"
          control={control}
          render={({ field }) => (
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
              <Switch checked={field.value} onChange={field.onChange} /> Predefinido
            </span>
          )}
        />
      </Space>

      <Space style={{ display: "flex", justifyContent: "flex-end" }}>
        <Button onClick={() => setEditing(undefined)}>Cancelar</Button>
        <Button type="primary" htmlType="submit" loading={submitting}>
          {isCreating ? "Criar" : "Guardar"}
        </Button>
      </Space>
    </Form>
  );

  /* ── Lista ───────────────────────────────────────────────── */
  const list =
    providers.length === 0 ? (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="Ainda não há nenhum provedor de email configurado."
      />
    ) : (
      <ul style={LIST}>
        {providers.map((provider, index) => (
          <li key={provider.id} style={rowStyle(index)}>
            <div style={ROW_HEAD}>
              <span style={{ fontFamily: "var(--ind-font-heading)", fontSize: 16 }}>
                {provider.providerName}
              </span>
              <Space size={4}>
                {provider.isDefault && <Tag color="var(--ind-accent-700)">Predefinido</Tag>}
                {!provider.isActive && <Tag>Inativo</Tag>}
              </Space>
            </div>

            <div style={{ fontSize: 12, opacity: 0.7 }}>
              {provider.host}:{provider.port} · {provider.encryption.toUpperCase()} ·{" "}
              {provider.fromName ? `${provider.fromName} <${provider.fromEmail}>` : provider.fromEmail}
            </div>

            <Space wrap size={4}>
              <Button
                size="small"
                icon={<EditOutlined />}
                onClick={() => openEdit(provider)}
                disabled={busyId === provider.id}
              >
                Editar
              </Button>
              <Button
                size="small"
                icon={<SendOutlined />}
                onClick={() => {
                  setTestTarget(provider);
                  setTestEmail("");
                }}
                disabled={busyId === provider.id}
              >
                Enviar teste
              </Button>
              {!provider.isDefault && (
                <Button
                  size="small"
                  loading={busyId === provider.id}
                  onClick={() =>
                    runOnRow(
                      provider,
                      () => setDefaultEmailProvider(provider.id),
                      `"${provider.providerName}" é agora o provedor predefinido.`
                    )
                  }
                >
                  Predefinir
                </Button>
              )}
              <Button
                size="small"
                loading={busyId === provider.id}
                onClick={() =>
                  runOnRow(
                    provider,
                    () => setEmailProviderActive(provider.id, !provider.isActive),
                    provider.isActive ? "Provedor desativado." : "Provedor ativado."
                  )
                }
              >
                {provider.isActive ? "Desativar" : "Ativar"}
              </Button>
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                aria-label={`Eliminar ${provider.providerName}`}
                onClick={() => handleDelete(provider)}
              />
            </Space>
          </li>
        ))}
      </ul>
    );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Definições</h6>
          <h2 style={{ margin: 0 }}>Provedores de email</h2>
        </div>
      }
      extra={
        !formOpen && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Novo provedor
          </Button>
        )
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose}>Fechar</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
          {!loading && !hasUsableDefault && (
            <Alert
              type="warning"
              showIcon
              message="Nenhum provedor predefinido ativo"
              description="Sem um provedor predefinido e ativo, os convites de funcionário e a recuperação de password não conseguem enviar email."
            />
          )}

          {formOpen ? (
            form
          ) : (
            <>
              <p style={{ fontSize: 13, opacity: 0.75, margin: 0, lineHeight: 1.5 }}>
                As credenciais SMTP que a plataforma usa para enviar convites e recuperações de
                password. Só o provedor predefinido é usado; os restantes ficam guardados como
                alternativa.
              </p>
              {list}
            </>
          )}
        </div>
      </Spin>

      <Modal
        open={testTarget !== null}
        title={`Enviar email de teste — ${testTarget?.providerName ?? ""}`}
        okText="Enviar"
        cancelText="Cancelar"
        confirmLoading={testing}
        okButtonProps={{ disabled: !testEmail.trim() }}
        onOk={handleSendTest}
        onCancel={() => setTestTarget(null)}
      >
        <p style={{ fontSize: 13, opacity: 0.75 }}>
          O teste usa as credenciais deste provedor, mesmo que não seja o predefinido.
        </p>
        <Input
          type="email"
          placeholder="para@exemplo.com"
          aria-label="Email de destino do teste"
          value={testEmail}
          onChange={(e) => setTestEmail(e.target.value)}
          onPressEnter={handleSendTest}
        />
      </Modal>
    </Drawer>
  );
};

/** Lista com divisórias — o mesmo padrão da drawer de fornecedores. */
const LIST: CSSProperties = { listStyle: "none", margin: 0, padding: 0 };

const ROW_HEAD: CSSProperties = {
  display: "flex",
  justifyContent: "space-between",
  gap: 10,
  alignItems: "baseline",
  flexWrap: "wrap",
};

const rowStyle = (index: number): CSSProperties => ({
  display: "flex",
  flexDirection: "column",
  gap: 6,
  padding: "10.2px 0",
  borderTop: index === 0 ? undefined : "1px solid var(--ind-color-divider)",
});
