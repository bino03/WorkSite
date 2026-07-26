// src/components/profile/ProfileView.tsx
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  message,
  Avatar,
  Badge,
  Button,
  Card,
  Form,
  Input,
  Popconfirm,
  Skeleton,
  Space,
  Tag,
  Typography,
  Select,
  Row,
  Col,
} from "antd";
import { getEmployee, patchAccountStatus, updateEmployee } from "@/services/profileService";
import type { Employee, AccountStatus } from "@/services/profileService";
import {
  getInitials,
  roleToLabel,
  roleToTagColor,
  statusToBadge,
  statusToLabel,
  formatDateISOToPT,
} from "@/utils/profile";
import {
  ArrowLeftOutlined,
  CopyOutlined,
  EditOutlined,
  LockOutlined,
  UnlockOutlined,
  SaveOutlined,
  CloseOutlined,
  PhoneOutlined,
  MailOutlined,
  UserOutlined,
  TeamOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons";
import defaultProfile from "@/assets/images/profile/profile.jpg";

type Mode = "page" | "drawer";

export type ProfileViewProps = {
  profileId: string;
  mode?: Mode;
  onClose?: () => void;
  onSaved?: (updated: Employee) => void;
  onProfileUpdated?: (updatedProfile: Employee) => void;  // ✅ Tornar opcional
};

const { Text, Title } = Typography;

export default function ProfileView({
  profileId,
  mode = "page",
  onClose,
  onSaved,
  onProfileUpdated,
}: ProfileViewProps) {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [editMode, setEditMode] = useState(false);

  const [form] = Form.useForm<Pick<Employee, "name" | "email" | "phoneNumber" | "role">>();

  const isPage = mode === "page";
  const isDeleted = employee?.status === "deleted";

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    getEmployee(profileId)
      .then((data) => {
        if (!mounted) return;
        setEmployee(data);
        form.setFieldsValue({
          name: data.name,
          email: data.email,
          phoneNumber: data.phoneNumber ?? "",
          role: data.role,
        });
      })
      .catch(() => {
        message.error(t('profileView.loadError'));
      })
      .finally(() => mounted && setLoading(false));

    return () => {
      mounted = false;
    };
  }, [profileId, form]);

  const nextStatus = useMemo<AccountStatus | null>(() => {
    if (!employee) return null;
    if (employee.status === "unlocked") return "blocked";
    if (employee.status === "blocked" || employee.status === "locked") return "unlocked";
    return null;
  }, [employee]);

  function handleClose() {
    if (onClose) return onClose();
    if (isPage) window.history.back();
  }

  async function handleToggleStatus() {
    if (!employee || !nextStatus) return;
    setToggling(true);
    try {
      await patchAccountStatus({ profileId: employee.id, status: nextStatus });
      const updated = { ...employee, status: nextStatus, updatedAt: new Date().toISOString() } as Employee;
      setEmployee(updated);
      message.success(nextStatus === "blocked" ? t('profileView.blocked') : t('profileView.unblocked'));
      onSaved?.(updated);
      onProfileUpdated?.(updated);
    } catch {
      message.error(t('profileView.statusError'));
    } finally {
      setToggling(false);
    }
  }

  function startEdit() {
    setEditMode(true);
    if (employee) {
      form.setFieldsValue({
        name: employee.name,
        email: employee.email,
        phoneNumber: employee.phoneNumber ?? "",
        role: employee.role,
      });
    }
  }

  function cancelEdit() {
    setEditMode(false);
    if (employee) {
      form.resetFields();
      form.setFieldsValue({
        name: employee.name,
        email: employee.email,
        phoneNumber: employee.phoneNumber ?? "",
        role: employee.role,
      });
    }
  }

  // ✅ CORRIGIDO: Adicionar callback onProfileUpdated
  async function saveEdit(values: Pick<Employee, "name" | "email" | "phoneNumber" | "role">) {
    if (!employee) return;
    
    console.log("💾 [ProfileView] Guardando perfil:", values);
    
    setSaving(true);
    try {
      const patch = await updateEmployee(employee.id, {
        name: values.name?.trim(),
        email: values.email?.trim(),
        phoneNumber: values.phoneNumber?.trim() || null,
        role: values.role,
      });

      console.log("✅ [ProfileView] Resposta da API:", patch);

      const updated: Employee = {
        ...employee,
        ...patch,
        updatedAt: new Date().toISOString(),
      } as Employee;

      setEmployee(updated);
      setEditMode(false);
      message.success(t('profileView.saved'));
      onSaved?.(updated);
      onProfileUpdated?.(updated);
    } catch (error) {
      console.error("❌ [ProfileView] Erro ao guardar:", error);
      message.error(t('profileView.saveError'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className={isPage ? "container-page" : undefined}>
      <Card className="card relative" bodyStyle={{ paddingBottom: 88 }}>
        {loading ? (
          <Skeleton active avatar paragraph={{ rows: 3 }} />
        ) : employee ? (
          <>
            {/* Cabeçalho do perfil com gradiente */}
            <Card 
              style={{ 
                marginBottom: "24px",
                background: "linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)",
                border: "1px solid #bae7ff"
              }}
            >
              <Row gutter={24} align="middle">
                <Col>
                  <div style={{ position: 'relative' }}>
                    <Avatar
                      size={80}
                      src={employee.photoUrl ?? defaultProfile}
                      alt={employee.name}
                      style={{
                        boxShadow: "0 8px 24px rgba(0,0,0,0.12)",
                        border: '4px solid white'
                      }}
                    >
                      {!employee.photoUrl && getInitials(employee.name)}
                    </Avatar>
                    
                    {/* Badge de status */}
                    <div style={{
                      position: 'absolute',
                      bottom: '5px',
                      right: '5px',
                      background: employee.status === "unlocked" ? '#52c41a' : '#f5222d',
                      borderRadius: '50%',
                      width: '20px',
                      height: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      border: '3px solid white'
                    }}>
                      {employee.status === "unlocked" ? (
                        <CheckCircleOutlined style={{ color: 'white', fontSize: '10px' }} />
                      ) : (
                        <ExclamationCircleOutlined style={{ color: 'white', fontSize: '10px' }} />
                      )}
                    </div>
                  </div>
                </Col>

                <Col flex="auto">
                  <div>
                    <Title level={3} style={{ margin: "0 0 8px 0", color: "#1890ff" }}>
                      {employee.name}
                    </Title>
                    
                    <Space size={12} wrap>
                      <Tag 
                        color={roleToTagColor(employee.role)} 
                        style={{ 
                          fontSize: '13px',
                          padding: '4px 12px',
                          borderRadius: '16px',
                          fontWeight: 500
                        }}
                      >
                        <TeamOutlined style={{ marginRight: '4px' }} />
                        {roleToLabel(employee.role)}
                      </Tag>
                      
                      <Badge 
                        {...statusToBadge(employee.status)} 
                        text={statusToLabel(employee.status)}
                        style={{ fontSize: '13px' }}
                      />
                    </Space>

                    <div style={{ marginTop: '12px' }}>
                      <Space direction="vertical" size={4}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <MailOutlined style={{ color: '#1890ff', fontSize: '14px' }} />
                          <Text
                            copyable={{ 
                              text: employee.email, 
                              tooltips: ["Copiar", "Copiado!"], 
                              icon: <CopyOutlined style={{ fontSize: '12px' }} /> 
                            }}
                            style={{ fontSize: '14px' }}
                          >
                            {employee.email}
                          </Text>
                        </div>

                        {employee.phoneNumber && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <PhoneOutlined style={{ color: '#52c41a', fontSize: '14px' }} />
                            <Text
                              copyable={{ 
                                text: employee.phoneNumber, 
                                tooltips: ["Copiar", "Copiado!"], 
                                icon: <CopyOutlined style={{ fontSize: '12px' }} /> 
                              }}
                              style={{ fontSize: '14px' }}
                            >
                              {employee.phoneNumber}
                            </Text>
                          </div>
                        )}
                      </Space>
                    </div>
                  </div>
                </Col>
              </Row>
            </Card>

            {/* Formulário de edição */}
            <Card 
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <UserOutlined style={{ color: '#1890ff' }} />
                  <span>{t('profileView.employeeInfo')}</span>
                </div>
              }
              style={{ marginBottom: '16px' }}
            >
              <Form
                form={form}
                layout="vertical"
                disabled={loading || saving || toggling || isDeleted || !editMode}
                onFinish={saveEdit}
              >
                <Row gutter={16}>
                  <Col xs={24} md={12}>
                    <Form.Item
                      name="name"
                      label={
                        <span>
                          <UserOutlined style={{ marginRight: '6px', color: '#1890ff' }} />
                          {t('profileView.fullName')}
                        </span>
                      }
                      rules={[{ required: true, message: t('profileView.nameRequired') }]}
                    >
                      <Input
                        placeholder={t('profileView.namePlaceholder')}
                        size="large"
                        style={{ borderRadius: '6px' }}
                      />
                    </Form.Item>
                  </Col>

                  <Col xs={24} md={12}>
                    <Form.Item
                      name="email"
                      label={
                        <span>
                          <MailOutlined style={{ marginRight: '6px', color: '#52c41a' }} />
                          Email
                        </span>
                      }
                      rules={[{ required: true, type: "email", message: t('profileView.emailInvalid') }]}
                    >
                      <Input
                        placeholder={t('profileView.emailPlaceholder')}
                        size="large"
                        style={{ borderRadius: '6px' }}
                      />
                    </Form.Item>
                  </Col>

                  <Col xs={24} md={12}>
                    <Form.Item
                      name="role"
                      label={
                        <span>
                          <TeamOutlined style={{ marginRight: '6px', color: '#722ed1' }} />
                          {t('profileView.profileField')}
                        </span>
                      }
                      rules={[{ required: true, message: t('profileView.profileRequired') }]}
                    >
                      <Select
                        placeholder={t('profileView.profilePlaceholder')}
                        size="large"
                        style={{ borderRadius: '6px' }}
                        options={[
                          {
                            value: "ADMIN",
                            label: (
                              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <TeamOutlined style={{ color: '#f5222d' }} />
                                <span>{t('profileView.admin')}</span>
                              </div>
                            )
                          },
                          {
                            value: "EMPLOYEE",
                            label: (
                              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                <UserOutlined style={{ color: '#1890ff' }} />
                                <span>{t('profileView.employee')}</span>
                              </div>
                            )
                          },
                        ]}
                      />
                    </Form.Item>
                  </Col>

                  <Col xs={24} md={12}>
                    <Form.Item 
                      name="phoneNumber" 
                      label={
                        <span>
                          <PhoneOutlined style={{ marginRight: '6px', color: '#fa8c16' }} />
                          Telefone
                        </span>
                      }
                    >
                      <Input
                        placeholder={t('profileView.phonePlaceholder')}
                        size="large"
                        style={{ borderRadius: '6px' }}
                      />
                    </Form.Item>
                  </Col>
                </Row>
              </Form>
            </Card>

            {/* Informações do sistema */}
            <Card 
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <CalendarOutlined style={{ color: '#8c8c8c' }} />
                  <span>{t('common.systemInfo')}</span>
                </div>
              }
              size="small"
              style={{ marginBottom: '16px' }}
            >
              <Row gutter={[16, 16]}>
                <Col xs={12}>
                  <div style={{
                    padding: "12px",
                    backgroundColor: "#fafafa",
                    borderRadius: "8px",
                    border: "1px solid #e8e8e8",
                  }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "8px" }}>
                      <CalendarOutlined style={{ color: "#595959", fontSize: '12px' }} />
                      <Text type="secondary" style={{
                        fontSize: "11px",
                        textTransform: "uppercase",
                        fontWeight: 500,
                      }}>
                        {t('common.createdAt')}
                      </Text>
                    </div>
                    <Text style={{
                      fontSize: "13px",
                      color: "#262626",
                      fontWeight: 500,
                    }}>
                      {formatDateISOToPT(employee.createdAt)}
                    </Text>
                  </div>
                </Col>

                <Col xs={12}>
                  <div style={{
                    padding: "12px",
                    backgroundColor: "#fafafa",
                    borderRadius: "8px",
                    border: "1px solid #e8e8e8",
                  }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "8px" }}>
                      <ClockCircleOutlined style={{ color: "#595959", fontSize: '12px' }} />
                      <Text type="secondary" style={{
                        fontSize: "11px",
                        textTransform: "uppercase",
                        fontWeight: 500,
                      }}>
                        {t('common.updatedAt')}
                      </Text>
                    </div>
                    <Text style={{
                      fontSize: "13px",
                      color: "#262626",
                      fontWeight: 500,
                    }}>
                      {formatDateISOToPT(employee.updatedAt)}
                    </Text>
                  </div>
                </Col>
              </Row>
            </Card>

            {/* Footer com ações */}
            <div
              style={{
                position: "absolute",
                left: 0,
                right: 0,
                bottom: 0,
                padding: "16px 24px",
                background: "#fafafa",
                borderTop: "1px solid #e8e8e8",
                borderRadius: "0 0 8px 8px"
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Space>
                  {!editMode ? (
                    <Button 
                      onClick={handleClose} 
                      icon={<ArrowLeftOutlined />}
                      size="large"
                    >
                      {isPage ? t('common.back') : t('common.close')}
                    </Button>
                  ) : (
                    <Button
                      onClick={cancelEdit}
                      icon={<CloseOutlined />}
                      size="large"
                    >
                      {t('common.cancel')}
                    </Button>
                  )}

                  {!editMode ? (
                    <Button
                      onClick={startEdit}
                      type="primary"
                      disabled={isDeleted}
                      icon={<EditOutlined />}
                      size="large"
                    >
                      {t('common.edit')}
                    </Button>
                  ) : (
                    <Button
                      onClick={() => form.submit()}
                      loading={saving}
                      type="primary"
                      disabled={isDeleted}
                      icon={<SaveOutlined />}
                      size="large"
                    >
                      {t('common.save')}
                    </Button>
                  )}
                </Space>

                <div>
                  {employee.status !== "deleted" && nextStatus && (
                    <Popconfirm
                      title={nextStatus === "blocked" ? t('profileView.blockConfirm') : t('profileView.unblockConfirm')}
                      okText={nextStatus === "blocked" ? t('profileView.block') : t('profileView.unblock')}
                      cancelText={t('common.cancel')}
                      onConfirm={handleToggleStatus}
                      disabled={toggling || saving || loading || editMode}
                    >
                      <Button
                        loading={toggling}
                        danger={nextStatus === "blocked"}
                        icon={nextStatus === "blocked" ? <LockOutlined /> : <UnlockOutlined />}
                        type={nextStatus === "blocked" ? "default" : "primary"}
                        disabled={editMode}
                        size="large"
                      >
                        {nextStatus === "blocked" ? t('profileView.block') : t('profileView.unblock')}
                      </Button>
                    </Popconfirm>
                  )}
                  {employee.status === "deleted" && (
                    <Button disabled size="large">
                      {t('profileView.deleted')}
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </>
        ) : (
          <div style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: "60px 20px",
            color: "#8c8c8c",
          }}>
            <UserOutlined style={{ fontSize: "64px", marginBottom: "20px", opacity: 0.3 }} />
            <Text style={{ fontSize: "18px", fontWeight: 500 }}>
              {t('profileView.notFound')}
            </Text>
          </div>
        )}
      </Card>
    </div>
  );
}