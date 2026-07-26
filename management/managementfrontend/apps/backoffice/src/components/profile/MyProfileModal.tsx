import React, { useEffect, useMemo, useRef, useState } from "react";
import { Modal, Button, Input, Form, message, Avatar } from "antd";
import { useTranslation } from "react-i18next";
import {
  CloseOutlined,
  CameraOutlined,
  DeleteOutlined,
  UserOutlined,
  MailOutlined,
  LockOutlined,
} from "@ant-design/icons";
import {
  getMyProfile,
  uploadProfilePhoto,
  deleteProfilePhoto,
  updateNamePhone,
  updateEmail,
  updatePassword,
  getAuthUserIdFromContext,
} from "@/services/profileService";
import { useAuthContext } from "@/context/AuthContext";
import type { MyProfile } from "@/services/profileService";

// ── Design tokens (DESIGN.md) ─────────────────────────────────
const D = {
  parchment:   "#f5f4ed",
  ivory:       "#faf9f5",
  nearBlack:   "#141413",
  terracotta:  "#c96442",
  oliveGray:   "#5e5d59",
  stoneGray:   "#87867f",
  warmSand:    "#e8e6dc",
  darkSurface: "#30302e",
  borderCream: "#f0eee6",
  borderWarm:  "#e8e6dc",
  warmSilver:  "#b0aea5",
  crimson:     "#b53333",
};

// ── Helper sub-components ─────────────────────────────────────
function FieldLabel({ children }: { children: React.ReactNode }) {
  return (
    <span style={{
      fontSize: 11, fontWeight: 600, color: D.stoneGray,
      textTransform: "uppercase", letterSpacing: "0.55px",
    }}>
      {children}
    </span>
  );
}

function InfoTile({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div style={{
      padding: "10px 14px",
      background: D.parchment,
      borderRadius: 8,
      border: `1px solid ${D.borderCream}`,
    }}>
      <div style={{ fontSize: 10, fontWeight: 600, color: D.stoneGray, textTransform: "uppercase", letterSpacing: "0.5px", marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 13, fontWeight: 500, color: highlight ? D.terracotta : D.nearBlack }}>
        {value}
      </div>
    </div>
  );
}

function PrimaryBtn({ onClick, loading, disabled, children }: {
  onClick?: () => void; loading?: boolean; disabled?: boolean; children: React.ReactNode;
}) {
  return (
    <Button
      onClick={onClick}
      loading={loading}
      disabled={disabled}
      style={{
        background: disabled ? D.borderWarm : D.terracotta,
        borderColor: disabled ? D.borderWarm : D.terracotta,
        color: disabled ? D.stoneGray : "#fff",
        fontWeight: 500,
        fontSize: 13,
        height: 38,
        paddingInline: 20,
        borderRadius: 8,
        boxShadow: "none",
      }}
    >
      {children}
    </Button>
  );
}

function SecondaryBtn({ onClick, children }: { onClick?: () => void; children: React.ReactNode }) {
  return (
    <Button
      onClick={onClick}
      style={{
        background: D.warmSand,
        borderColor: D.borderWarm,
        color: D.oliveGray,
        fontWeight: 500,
        fontSize: 13,
        height: 38,
        paddingInline: 18,
        borderRadius: 8,
        boxShadow: "none",
      }}
    >
      {children}
    </Button>
  );
}

function SectionHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div style={{ padding: "24px 28px 18px", borderBottom: `1px solid ${D.borderCream}` }}>
      <div style={{ fontSize: 17, fontWeight: 500, color: D.nearBlack, fontFamily: "Georgia, serif", lineHeight: 1.2, marginBottom: 4 }}>
        {title}
      </div>
      <div style={{ fontSize: 12, color: D.stoneGray, lineHeight: 1.5 }}>
        {subtitle}
      </div>
    </div>
  );
}

// ── Types & props ─────────────────────────────────────────────
type Props = {
  onClose: () => void;
  onProfileUpdated?: () => void;
};

type Tab = "general" | "email" | "password";

// ── Component ─────────────────────────────────────────────────
const MyProfileModal: React.FC<Props> = ({ onClose, onProfileUpdated }) => {
  const { t } = useTranslation();
  const { user } = useAuthContext();
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [, setShowLastUpdate] = useState(true);
  const [tab, setTab] = useState<Tab>("general");

  const [generalForm] = Form.useForm();
  const [emailForm]   = Form.useForm();
  const [passwordForm] = Form.useForm();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [pendingPhotoFile, setPendingPhotoFile]   = useState<File | null>(null);
  const [pendingPreviewUrl, setPendingPreviewUrl] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // ── Data loading ──────────────────────────────────────────
  useEffect(() => {
    (async () => {
      try {
        const data = await getMyProfile();
        setProfile(data);
        generalForm.setFieldsValue({ name: data.name, phoneNumber: data.phoneNumber ?? "" });
        emailForm.setFieldsValue({ email: data.email });
      } catch {
        message.error(t('myProfile.loadError'));
      }
    })();
  }, [generalForm, emailForm]);

  const authId = useMemo(() => getAuthUserIdFromContext(user?.authUserId), [user?.authUserId]);
  const effectiveProfileId = useMemo(() => profile?.id ?? authId ?? null, [profile?.id, authId]);
  // Use the URL already available from login context while the profile API call is in flight
  const photoSrc = pendingPreviewUrl || profile?.photoUrl || user?.photoUrl || "/src/assets/images/profile/profile1.jpg";
  const isActive = profile?.accountStatus === "unlocked";

  // ── Helpers ───────────────────────────────────────────────
  function fmt(dateStr: string) {
    const d = new Date(dateStr);
    const diff = Date.now() - d.getTime();
    const mins = Math.floor(diff / 60000);
    const hrs  = Math.floor(diff / 3600000);
    if (mins < 2)  return `há ${mins} minuto`;
    if (mins < 60) return `há ${mins} minutos`;
    if (hrs < 24)  return `há ${hrs} h`;
    return d.toLocaleDateString("pt-PT");
  }

  async function refetch() {
    const data = await getMyProfile();
    setProfile(data);
    generalForm.setFieldsValue({ name: data.name, phoneNumber: data.phoneNumber ?? "" });
    emailForm.setFieldsValue({ email: data.email });
  }

  async function refreshUserContext() {
    onProfileUpdated?.();
  }

  function triggerFilePicker() {
    if (!isEditing) { message.info(t('myProfile.clickToEditPhoto')); return; }
    fileInputRef.current?.click();
  }

  function handleFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
    setPendingPhotoFile(file);
    setPendingPreviewUrl(url);
    e.currentTarget.value = "";
  }

  function clearPendingSelection() {
    if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
    setPendingPhotoFile(null);
    setPendingPreviewUrl(null);
  }

  async function onDeleteServerPhoto() {
    if (!effectiveProfileId) { message.error(t('myProfile.profileIdError')); return; }
    const hide = message.loading(t('myProfile.removingPhoto'), 0);
    try {
      await deleteProfilePhoto(effectiveProfileId);
      await refetch();
      clearPendingSelection();
      await refreshUserContext();
      message.success(t('myProfile.photoRemoved'));
    } catch {
      message.error(t('myProfile.photoRemoveError'));
    } finally { hide(); }
  }

  function onEdit()       { setIsEditing(true);  setShowLastUpdate(false); }
  function onCancelEdit() {
    if (!profile) return;
    setIsEditing(false);
    setShowLastUpdate(true);
    generalForm.setFieldsValue({ name: profile.name, phoneNumber: profile.phoneNumber ?? "" });
    clearPendingSelection();
  }

  async function onSaveGeneral() {
    if (!profile) return;
    try {
      setBusy(true);
      const { name, phoneNumber } = await generalForm.validateFields();
      await updateNamePhone({ name, phoneNumber: phoneNumber ?? "" });
      if (pendingPhotoFile) {
        if (!effectiveProfileId) {
          message.error(t('myProfile.profileIdError'));
        } else {
          await uploadProfilePhoto(effectiveProfileId, pendingPhotoFile);
          clearPendingSelection();
        }
      }
      await refetch();
      await refreshUserContext();
      message.success(t('myProfile.saveSuccess'));
      setIsEditing(false);
      setShowLastUpdate(true);
    } finally { setBusy(false); }
  }

  async function onSaveEmail() {
    try {
      const { email } = await emailForm.validateFields();
      await updateEmail({ email });
      await refetch();
      message.success(t('myProfile.emailUpdated'));
    } catch {}
  }

  async function onSavePassword() {
    try {
      const { currentPassword, newPassword, confirmPassword } = await passwordForm.validateFields();
      if (newPassword !== confirmPassword) { message.warning(t('myProfile.passwordNoMatch')); return; }
      await updatePassword({ currentPassword, newPassword });
      passwordForm.resetFields();
      message.success(t('myProfile.passwordUpdated'));
    } catch {}
  }

  if (!profile) return null;

  // ── Nav config ────────────────────────────────────────────
  const navItems: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: "general",  label: t('myProfile.navGeneral'), icon: <UserOutlined /> },
    { key: "email",    label: t('myProfile.navEmail'),   icon: <MailOutlined /> },
    { key: "password", label: t('myProfile.navSecurity'),icon: <LockOutlined /> },
  ];

  const sectionMeta: Record<Tab, { title: string; subtitle: string }> = {
    general:  { title: t('myProfile.generalTitle'),  subtitle: t('myProfile.generalSubtitle') },
    email:    { title: t('myProfile.emailTitle'),    subtitle: t('myProfile.emailSubtitle') },
    password: { title: t('myProfile.passwordTitle'), subtitle: t('myProfile.passwordSubtitle') },
  };

  const inputStyle = (enabled: boolean): React.CSSProperties => ({
    borderColor: D.borderWarm,
    background:  enabled ? "#fff" : D.parchment,
    color:       D.nearBlack,
    height:      38,
    borderRadius: 7,
    fontSize:    13,
  });

  // ── Render ────────────────────────────────────────────────
  return (
    <Modal
      open={true}
      onCancel={onClose}
      footer={null}
      centered
      width={760}
      destroyOnClose
      closeIcon={null}
      styles={{
        content: { padding: 0, overflow: "hidden", borderRadius: 14 },
        body:    { padding: 0 },
      }}
    >
      <div style={{ display: "flex", height: 560 }}>

        {/* ══ Left sidebar ══════════════════════════════════════ */}
        <div style={{
          width: 220, flexShrink: 0,
          background: D.darkSurface,
          display: "flex", flexDirection: "column",
          padding: "28px 14px 18px",
        }}>

          {/* Avatar + photo controls */}
          <div style={{ textAlign: "center", marginBottom: 20 }}>
            <div
              style={{ position: "relative", display: "inline-block", cursor: isEditing ? "pointer" : "default" }}
              onClick={isEditing ? triggerFilePicker : undefined}
              title={isEditing ? "Clica para alterar a foto" : undefined}
            >
              <Avatar
                size={72}
                src={photoSrc}
                icon={<UserOutlined />}
                style={{
                  boxShadow: `0 0 0 2px ${D.terracotta}, 0 0 0 5px rgba(201,100,66,0.18)`,
                  fontSize: 28,
                }}
                onError={() => false}
              />
              {/* Status dot */}
              <span style={{
                position: "absolute", bottom: 2, right: 2,
                width: 13, height: 13, borderRadius: "50%",
                background: isActive ? "#4ade80" : "#f87171",
                border: `2.5px solid ${D.darkSurface}`,
                display: "block",
              }} />
              {/* Edit overlay */}
              {isEditing && (
                <div style={{
                  position: "absolute", inset: 0, borderRadius: "50%",
                  background: "rgba(0,0,0,0.38)", display: "flex",
                  alignItems: "center", justifyContent: "center",
                }}>
                  <CameraOutlined style={{ color: "#fff", fontSize: 18 }} />
                </div>
              )}
            </div>

            <input ref={fileInputRef} type="file" accept="image/*" style={{ display: "none" }} onChange={handleFileSelected} />

            {/* Photo action chips */}
            {tab === "general" && isEditing && (
              <div style={{ marginTop: 10, display: "flex", justifyContent: "center", gap: 6 }}>
                {pendingPhotoFile ? (
                  <button
                    onClick={clearPendingSelection}
                    style={{ background: "rgba(181,51,51,0.15)", border: `1px solid rgba(181,51,51,0.3)`, borderRadius: 6, color: "#f87171", fontSize: 11, padding: "3px 10px", cursor: "pointer" }}
                  >
                    <CloseOutlined style={{ marginRight: 4, fontSize: 9 }} />{t('myProfile.cancelPhoto')}
                  </button>
                ) : (
                  profile.photoBucket && profile.photoKey && (
                    <button
                      onClick={(e) => { e.stopPropagation(); onDeleteServerPhoto(); }}
                      style={{ background: "rgba(181,51,51,0.12)", border: `1px solid rgba(181,51,51,0.25)`, borderRadius: 6, color: "#f87171", fontSize: 11, padding: "3px 10px", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}
                    >
                      <DeleteOutlined style={{ fontSize: 10 }} /> {t('common.remove')}
                    </button>
                  )
                )}
              </div>
            )}

            {/* Name + role */}
            <div style={{ marginTop: 14 }}>
              <div style={{ fontSize: 15, fontWeight: 500, color: "#f5f4ed", fontFamily: "Georgia, serif", lineHeight: 1.3, marginBottom: 3 }}>
                {profile.name}
              </div>
              <div style={{ fontSize: 10, textTransform: "uppercase", letterSpacing: "0.6px", color: D.warmSilver }}>
                {profile.role === "ADMIN" ? t('myProfile.admin') : t('myProfile.employee')}
              </div>
            </div>
          </div>

          {/* Divider */}
          <div style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "0 0 14px" }} />

          {/* Nav items */}
          <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            {navItems.map(({ key, label, icon }) => {
              const active = tab === key;
              return (
                <button
                  key={key}
                  onClick={() => setTab(key)}
                  style={{
                    display: "flex", alignItems: "center", gap: 10,
                    padding: "9px 12px", borderRadius: 8,
                    background: active ? "rgba(201,100,66,0.13)" : "transparent",
                    border: "none",
                    borderLeft: `2px solid ${active ? D.terracotta : "transparent"}`,
                    cursor: "pointer",
                    color: active ? "#f5f4ed" : D.warmSilver,
                    fontSize: 13,
                    fontWeight: active ? 500 : 400,
                    transition: "all 0.14s ease",
                    textAlign: "left",
                    width: "100%",
                  }}
                  onMouseEnter={e => { if (!active) (e.currentTarget as HTMLButtonElement).style.color = "#f5f4ed"; }}
                  onMouseLeave={e => { if (!active) (e.currentTarget as HTMLButtonElement).style.color = D.warmSilver; }}
                >
                  <span style={{ fontSize: 13, opacity: 0.8 }}>{icon}</span>
                  {label}
                </button>
              );
            })}
          </nav>

          {/* Spacer */}
          <div style={{ flex: 1 }} />

          {/* Close */}
          <button
            onClick={onClose}
            style={{
              display: "flex", alignItems: "center", justifyContent: "center", gap: 6,
              width: "100%", padding: "9px 12px",
              background: "transparent", border: "1px solid rgba(255,255,255,0.09)",
              borderRadius: 8, cursor: "pointer",
              color: D.stoneGray, fontSize: 12,
              transition: "all 0.14s ease",
            }}
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.borderColor = "rgba(255,255,255,0.18)"; (e.currentTarget as HTMLButtonElement).style.color = D.warmSilver; }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.borderColor = "rgba(255,255,255,0.09)"; (e.currentTarget as HTMLButtonElement).style.color = D.stoneGray; }}
          >
            <CloseOutlined style={{ fontSize: 11 }} /> {t('common.close')}
          </button>
        </div>

        {/* ══ Right content ═════════════════════════════════════ */}
        <div style={{ flex: 1, background: D.ivory, display: "flex", flexDirection: "column", overflowY: "auto" }}>
          <SectionHeader title={sectionMeta[tab].title} subtitle={sectionMeta[tab].subtitle} />

          <div style={{ flex: 1, padding: "24px 28px", display: "flex", flexDirection: "column" }}>

            {/* ── Perfil ── */}
            {tab === "general" && (
              <Form form={generalForm} layout="vertical" component="div" onSubmitCapture={(e: React.FormEvent) => e.preventDefault()} style={{ display: "flex", flexDirection: "column", flex: 1 }}>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 20 }}>
                  <Form.Item
                    name="name"
                    label={<FieldLabel>{t('common.name')}</FieldLabel>}
                    rules={[{ required: true, message: t('myProfile.nameRequired') }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Input disabled={!isEditing} size="large" style={inputStyle(isEditing)} placeholder={t('myProfile.namePlaceholder')} />
                  </Form.Item>

                  <Form.Item
                    name="phoneNumber"
                    label={<FieldLabel>{t('common.phone')}</FieldLabel>}
                    style={{ marginBottom: 0 }}
                  >
                    <Input disabled={!isEditing} size="large" style={inputStyle(isEditing)} placeholder={t('myProfile.phonePlaceholder')} />
                  </Form.Item>
                </div>

                {/* Info tiles row */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 20 }}>
                  <InfoTile label={t('common.email')} value={profile.email} />
                  <InfoTile label={t('common.status')} value={isActive ? t('myProfile.statusActive') : t('myProfile.statusInactive')} highlight={isActive} />
                  <InfoTile label={t('myProfile.memberSince')} value={fmt(profile.createdAt)} />
                </div>

                {/* Actions */}
                <div style={{ marginTop: "auto", paddingTop: 16, borderTop: `1px solid ${D.borderCream}`, display: "flex", justifyContent: "flex-end", gap: 8 }}>
                  {isEditing ? (
                    <>
                      <SecondaryBtn onClick={onCancelEdit}>{t('common.cancel')}</SecondaryBtn>
                      <PrimaryBtn onClick={onSaveGeneral} loading={busy}>{t('myProfile.saveChanges')}</PrimaryBtn>
                    </>
                  ) : (
                    <PrimaryBtn onClick={onEdit}>{t('myProfile.editProfile')}</PrimaryBtn>
                  )}
                </div>
              </Form>
            )}

            {/* ── Email ── */}
            {tab === "email" && (
              <Form form={emailForm} layout="vertical" component="div" onSubmitCapture={(e: React.FormEvent) => e.preventDefault()} style={{ display: "flex", flexDirection: "column", flex: 1 }}>
                <div style={{ padding: "11px 14px", background: "#fdf6ef", borderRadius: 8, border: "1px solid #f0e0d0", marginBottom: 20 }}>
                  <span style={{ fontSize: 12, color: "#8a5a3a", lineHeight: 1.5 }}>
                    {t('myProfile.emailWarning')}
                  </span>
                </div>

                <Form.Item
                  name="email"
                  label={<FieldLabel>{t('myProfile.newEmail')}</FieldLabel>}
                  rules={[{ type: "email", message: t('myProfile.emailInvalid') }, { required: true, message: t('myProfile.emailRequired') }]}
                  style={{ marginBottom: 0 }}
                >
                  <Input size="large" style={inputStyle(true)} placeholder="novo@email.com" />
                </Form.Item>

                <div style={{ marginTop: "auto", paddingTop: 16, borderTop: `1px solid ${D.borderCream}`, display: "flex", justifyContent: "flex-end" }}>
                  <PrimaryBtn onClick={onSaveEmail}>{t('myProfile.updateEmail')}</PrimaryBtn>
                </div>
              </Form>
            )}

            {/* ── Password ── */}
            {tab === "password" && (
              <Form form={passwordForm} layout="vertical" component="div" onSubmitCapture={(e: React.FormEvent) => e.preventDefault()} style={{ display: "flex", flexDirection: "column", flex: 1 }}>
                <div style={{ padding: "11px 14px", background: D.parchment, borderRadius: 8, border: `1px solid ${D.borderWarm}`, marginBottom: 20 }}>
                  <span style={{ fontSize: 12, color: D.oliveGray, lineHeight: 1.5 }}>
                    {t('myProfile.passwordHint')}
                  </span>
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: 14, marginBottom: 20 }}>
                  <Form.Item name="currentPassword" label={<FieldLabel>{t('myProfile.currentPassword')}</FieldLabel>} rules={[{ required: true, message: t('myProfile.required') }]} style={{ marginBottom: 0 }}>
                    <Input.Password size="large" style={inputStyle(true)} />
                  </Form.Item>
                  <Form.Item name="newPassword" label={<FieldLabel>{t('myProfile.newPassword')}</FieldLabel>} rules={[{ required: true, message: t('myProfile.required') }, { min: 6, message: t('myProfile.passwordMinLength') }]} style={{ marginBottom: 0 }}>
                    <Input.Password size="large" style={inputStyle(true)} />
                  </Form.Item>
                  <Form.Item name="confirmPassword" label={<FieldLabel>{t('myProfile.confirmNewPassword')}</FieldLabel>} rules={[{ required: true, message: t('myProfile.required') }]} style={{ marginBottom: 0 }}>
                    <Input.Password size="large" style={inputStyle(true)} />
                  </Form.Item>
                </div>

                <div style={{ marginTop: "auto", paddingTop: 16, borderTop: `1px solid ${D.borderCream}`, display: "flex", justifyContent: "flex-end" }}>
                  <PrimaryBtn onClick={onSavePassword}>{t('myProfile.updatePasswordBtn')}</PrimaryBtn>
                </div>
              </Form>
            )}
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default MyProfileModal;
