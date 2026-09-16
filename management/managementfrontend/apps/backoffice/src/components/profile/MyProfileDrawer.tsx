import { useEffect, useState } from "react";
import type { FC } from "react";
import { Alert, Avatar, Badge, Button, Drawer, Form, Input, Skeleton, Space, Tabs, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { LockOutlined, MailOutlined, UserOutlined } from "@ant-design/icons";

import {
  getMyProfile,
  updateNamePhone,
  updateEmail,
  updatePassword,
} from "@/services/profileService";
import type { MyProfile } from "@/services/profileService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { getInitials, roleToLabel, statusToBadge, statusToLabel, formatDateISOToPT } from "@/utils/profile";

const { Text } = Typography;

type Props = {
  onClose: () => void;
  onProfileUpdated?: () => void;
};

type TabKey = "general" | "email" | "password";

/**
 * Editar a própria conta — sempre o mesmo Drawer, quer se abra pelo avatar do
 * `AppLayout` quer pela linha "me" do `EmployeesList`.
 */
const MyProfileDrawer: FC<Props> = ({ onClose, onProfileUpdated }) => {
  const { t } = useTranslation();
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<TabKey>("general");

  const [generalForm] = Form.useForm();
  const [emailForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const [savingGeneral, setSavingGeneral] = useState(false);
  const [savingEmail, setSavingEmail] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const data = await getMyProfile();
        setProfile(data);
        generalForm.setFieldsValue({ name: data.name, phoneNumber: data.phoneNumber ?? "" });
        emailForm.setFieldsValue({ email: data.email });
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setLoading(false);
      }
    })();
  }, [generalForm, emailForm]);

  async function refetch() {
    const data = await getMyProfile();
    setProfile(data);
    generalForm.setFieldsValue({ name: data.name, phoneNumber: data.phoneNumber ?? "" });
    emailForm.setFieldsValue({ email: data.email });
  }

  async function onSaveGeneral() {
    const { name, phoneNumber } = await generalForm.validateFields();
    setSavingGeneral(true);
    try {
      await updateNamePhone({ name, phoneNumber: phoneNumber ?? "" });
      await refetch();
      onProfileUpdated?.();
      notificationService.success(t("myProfile.saveSuccess"));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingGeneral(false);
    }
  }

  async function onSaveEmail() {
    const { email } = await emailForm.validateFields();
    setSavingEmail(true);
    try {
      await updateEmail({ email });
      await refetch();
      notificationService.success(t("myProfile.emailUpdated"));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingEmail(false);
    }
  }

  async function onSavePassword() {
    const { currentPassword, newPassword, confirmPassword } = await passwordForm.validateFields();
    if (newPassword !== confirmPassword) {
      notificationService.warning(t("myProfile.passwordNoMatch"));
      return;
    }
    setSavingPassword(true);
    try {
      await updatePassword({ currentPassword, newPassword });
      passwordForm.resetFields();
      notificationService.success(t("myProfile.passwordUpdated"));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingPassword(false);
    }
  }

  const footerRow = (onSave: () => void, saving: boolean, label: string) => (
    <Space style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}>
      <Button onClick={onClose} disabled={saving}>
        {t("common.cancel")}
      </Button>
      <Button type="primary" onClick={onSave} loading={saving}>
        {label}
      </Button>
    </Space>
  );

  return (
    <Drawer
      open
      onClose={onClose}
      width={640}
      destroyOnClose
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>{t("profile.myAccount")}</h6>
          <h2 style={{ margin: 0 }}>{profile?.name ?? " "}</h2>
        </div>
      }
    >
      {loading ? (
        <Skeleton active avatar paragraph={{ rows: 4 }} />
      ) : profile ? (
        <>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              paddingBottom: 16,
              marginBottom: 16,
              borderBottom: "1px solid var(--ind-color-divider)",
            }}
          >
            <Avatar
              size={48}
              style={{
                background: "var(--ind-accent-100)",
                color: "var(--ind-accent-800)",
                fontFamily: "var(--ind-font-heading)",
                fontSize: 16,
              }}
            >
              {getInitials(profile.name)}
            </Avatar>
            <span className={`ind-tag ${profile.role === "ADMIN" ? "ind-tag-accent" : "ind-tag-neutral"}`}>
              {roleToLabel(profile.role)}
            </span>
            <div style={{ marginLeft: "auto", textAlign: "right" }}>
              <Badge {...statusToBadge(profile.accountStatus)} text={statusToLabel(profile.accountStatus)} />
              <div style={{ fontSize: 12, opacity: 0.6, marginTop: 4 }}>
                {t("myProfile.memberSince")} {formatDateISOToPT(profile.createdAt)}
              </div>
            </div>
          </div>

          <Tabs
            activeKey={tab}
            onChange={(key) => setTab(key as TabKey)}
            items={[
              {
                key: "general",
                label: (
                  <span>
                    <UserOutlined /> {t("myProfile.navGeneral")}
                  </span>
                ),
                children: (
                  <Form form={generalForm} layout="vertical">
                    <Text type="secondary" style={{ display: "block", marginBottom: 16 }}>
                      {t("myProfile.generalSubtitle")}
                    </Text>
                    <Form.Item
                      name="name"
                      label={t("common.name")}
                      rules={[{ required: true, message: t("myProfile.nameRequired") }]}
                    >
                      <Input placeholder={t("myProfile.namePlaceholder")} />
                    </Form.Item>
                    <Form.Item name="phoneNumber" label={t("common.phone")}>
                      <Input placeholder={t("myProfile.phonePlaceholder")} />
                    </Form.Item>
                    {footerRow(onSaveGeneral, savingGeneral, t("myProfile.saveChanges"))}
                  </Form>
                ),
              },
              {
                key: "email",
                label: (
                  <span>
                    <MailOutlined /> {t("myProfile.navEmail")}
                  </span>
                ),
                children: (
                  <Form form={emailForm} layout="vertical">
                    <Alert type="info" showIcon message={t("myProfile.emailWarning")} style={{ marginBottom: 16 }} />
                    <Form.Item
                      name="email"
                      label={t("myProfile.newEmail")}
                      rules={[
                        { type: "email", message: t("myProfile.emailInvalid") },
                        { required: true, message: t("myProfile.emailRequired") },
                      ]}
                    >
                      <Input placeholder="novo@email.com" />
                    </Form.Item>
                    {footerRow(onSaveEmail, savingEmail, t("myProfile.updateEmail"))}
                  </Form>
                ),
              },
              {
                key: "password",
                label: (
                  <span>
                    <LockOutlined /> {t("myProfile.navSecurity")}
                  </span>
                ),
                children: (
                  <Form form={passwordForm} layout="vertical">
                    <Alert type="info" showIcon message={t("myProfile.passwordHint")} style={{ marginBottom: 16 }} />
                    <Form.Item
                      name="currentPassword"
                      label={t("myProfile.currentPassword")}
                      rules={[{ required: true, message: t("myProfile.required") }]}
                    >
                      <Input.Password />
                    </Form.Item>
                    <Form.Item
                      name="newPassword"
                      label={t("myProfile.newPassword")}
                      rules={[
                        { required: true, message: t("myProfile.required") },
                        { min: 6, message: t("myProfile.passwordMinLength") },
                      ]}
                    >
                      <Input.Password />
                    </Form.Item>
                    <Form.Item
                      name="confirmPassword"
                      label={t("myProfile.confirmNewPassword")}
                      rules={[{ required: true, message: t("myProfile.required") }]}
                    >
                      <Input.Password />
                    </Form.Item>
                    {footerRow(onSavePassword, savingPassword, t("myProfile.updatePasswordBtn"))}
                  </Form>
                ),
              },
            ]}
          />
        </>
      ) : null}
    </Drawer>
  );
};

export default MyProfileDrawer;
