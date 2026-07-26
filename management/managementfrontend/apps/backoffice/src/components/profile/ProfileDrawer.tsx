// src/components/profile/ProfileDrawer.tsx
import React, { useEffect, useState } from "react";
import { Drawer, Avatar, Spin, message, Tag } from "antd";
import { useTranslation } from "react-i18next";
import {
  MailOutlined,
  PhoneOutlined,
  TeamOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  CheckOutlined,
  CopyOutlined,
  CloseOutlined,
} from "@ant-design/icons";
import api from "@/api";

interface EmployeeProfile {
  id: string;
  authUserId: string;
  name: string;
  email: string;
  phoneNumber: string | null;
  photoUrl: string | null;
  role: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface ProfileDrawerProps {
  open: boolean;
  employeeId: string | null;
  onClose: () => void;
}

function getInitials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .map((w) => w[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);
}

// translateRole moved into component to use t()

function formatDate(iso: string) {
  try {
    return new Intl.DateTimeFormat("pt-PT", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

function CopyRow({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div
      onClick={handleCopy}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "11px 14px",
        borderRadius: 10,
        cursor: "pointer",
        transition: "background 0.15s",
        background: copied ? "#f0fdf4" : "transparent",
      }}
      onMouseEnter={(e) => {
        if (!copied) (e.currentTarget as HTMLDivElement).style.background = "#f7f8fa";
      }}
      onMouseLeave={(e) => {
        if (!copied) (e.currentTarget as HTMLDivElement).style.background = "transparent";
      }}
    >
      <span style={{ color: "#9ca3af", fontSize: 15, width: 18, flexShrink: 0 }}>{icon}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 11, color: "#9ca3af", fontWeight: 500, marginBottom: 1 }}>{label}</div>
        <div
          style={{
            fontSize: 13,
            color: "#111827",
            fontWeight: 500,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {value}
        </div>
      </div>
      <span
        style={{
          fontSize: 13,
          color: copied ? "#16a34a" : "#d1d5db",
          flexShrink: 0,
          transition: "color 0.2s",
        }}
      >
        {copied ? <CheckOutlined /> : <CopyOutlined />}
      </span>
    </div>
  );
}

const ProfileDrawer: React.FC<ProfileDrawerProps> = ({ open, employeeId, onClose }) => {
  const { t } = useTranslation();
  const translateRole = (role: string) => {
    if (role === "ADMIN") return t('profileDrawer.admin');
    if (role === "EMPLOYEE") return t('profileDrawer.broker');
    return role;
  };
  const [profile, setProfile] = useState<EmployeeProfile | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open && employeeId) {
      setLoading(true);
      api
        .get<EmployeeProfile>(`/employees/${employeeId}`)
        .then((r) => setProfile(r.data))
        .catch(() => message.error(t('profileDrawer.loadError')))
        .finally(() => setLoading(false));
    } else {
      setProfile(null);
    }
  }, [open, employeeId]);

  const isActive = profile?.status === "unlocked";
  const avatarColor = profile?.role === "ADMIN" ? "#6366f1" : "#0ea5e9";

  return (
    <Drawer
      open={open}
      onClose={onClose}
      placement="right"
      width={360}
      maskClosable
      closable={false}
      bodyStyle={{ padding: 0, background: "#fff" }}
      headerStyle={{ display: "none" }}
    >
      {loading ? (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%" }}>
          <Spin />
        </div>
      ) : profile ? (
        <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>

          {/* ── Topo com avatar e nome ── */}
          <div style={{ padding: "28px 24px 20px", borderBottom: "1px solid #f3f4f6" }}>
            {/* Fechar */}
            <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 20 }}>
              <button
                onClick={onClose}
                style={{
                  background: "#f3f4f6",
                  border: "none",
                  borderRadius: 8,
                  width: 30,
                  height: 30,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  cursor: "pointer",
                  color: "#6b7280",
                  fontSize: 12,
                  transition: "background 0.15s",
                }}
                onMouseEnter={(e) => ((e.currentTarget as HTMLButtonElement).style.background = "#e5e7eb")}
                onMouseLeave={(e) => ((e.currentTarget as HTMLButtonElement).style.background = "#f3f4f6")}
              >
                <CloseOutlined />
              </button>
            </div>

            {/* Avatar + info */}
            <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
              <div style={{ position: "relative", flexShrink: 0 }}>
                <Avatar
                  size={72}
                  src={profile.photoUrl}
                  style={{
                    background: profile.photoUrl ? "transparent" : avatarColor,
                    fontSize: 26,
                    fontWeight: 700,
                    border: "3px solid #fff",
                    boxShadow: `0 0 0 2px ${avatarColor}33`,
                  }}
                >
                  {!profile.photoUrl && getInitials(profile.name)}
                </Avatar>
                {/* Status dot */}
                <span
                  style={{
                    position: "absolute",
                    bottom: 2,
                    right: 2,
                    width: 14,
                    height: 14,
                    borderRadius: "50%",
                    background: isActive ? "#22c55e" : "#f87171",
                    border: "2px solid #fff",
                    display: "block",
                  }}
                />
              </div>

              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    fontSize: 17,
                    fontWeight: 700,
                    color: "#111827",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {profile.name}
                </div>
                <div style={{ marginTop: 5, display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
                  <Tag
                    style={{
                      margin: 0,
                      fontSize: 11,
                      fontWeight: 600,
                      borderRadius: 6,
                      padding: "1px 8px",
                      border: "none",
                      background: profile.role === "ADMIN" ? "#ede9fe" : "#e0f2fe",
                      color: profile.role === "ADMIN" ? "#7c3aed" : "#0284c7",
                    }}
                  >
                    {translateRole(profile.role)}
                  </Tag>
                  <Tag
                    style={{
                      margin: 0,
                      fontSize: 11,
                      fontWeight: 600,
                      borderRadius: 6,
                      padding: "1px 8px",
                      border: "none",
                      background: isActive ? "#dcfce7" : "#fee2e2",
                      color: isActive ? "#16a34a" : "#dc2626",
                    }}
                  >
                    {isActive ? t('profileDrawer.active') : profile.status === "blocked" ? t('profileDrawer.blocked') : t('profileDrawer.inactive')}
                  </Tag>
                </div>
              </div>
            </div>
          </div>

          {/* ── Campos copiáveis ── */}
          <div style={{ flex: 1, overflowY: "auto", padding: "8px 10px" }}>

            <SectionLabel>{t('profileDrawer.contact')}</SectionLabel>
            <CopyRow icon={<MailOutlined />} label={t('common.email')} value={profile.email} />
            {profile.phoneNumber && (
              <CopyRow icon={<PhoneOutlined />} label={t('common.phone')} value={profile.phoneNumber} />
            )}

            <Divider />
            <SectionLabel>{t('profileDrawer.role')}</SectionLabel>
            <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "11px 14px" }}>
              <span style={{ color: "#9ca3af", fontSize: 15, width: 18, flexShrink: 0 }}><TeamOutlined /></span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11, color: "#9ca3af", fontWeight: 500, marginBottom: 1 }}>{t('profileDrawer.profileField')}</div>
                <div style={{ fontSize: 13, color: "#111827", fontWeight: 500 }}>{translateRole(profile.role)}</div>
              </div>
            </div>

            <Divider />
            <SectionLabel>{t('profileDrawer.dates')}</SectionLabel>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, padding: "4px 14px 8px" }}>
              <DateCard icon={<CalendarOutlined />} label={t('profileDrawer.created')} value={formatDate(profile.createdAt)} />
              <DateCard icon={<ClockCircleOutlined />} label={t('profileDrawer.updated')} value={formatDate(profile.updatedAt)} />
            </div>
          </div>

          {/* ── Rodapé: dica de copy ── */}
          <div
            style={{
              padding: "12px 24px",
              borderTop: "1px solid #f3f4f6",
              display: "flex",
              alignItems: "center",
              gap: 6,
            }}
          >
            <CopyOutlined style={{ fontSize: 12, color: "#9ca3af" }} />
            <span style={{ fontSize: 12, color: "#9ca3af" }}>
              {t('profileDrawer.copyHint')}
            </span>
          </div>
        </div>
      ) : (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            height: "100%",
            color: "#9ca3af",
            gap: 8,
          }}
        >
          <span style={{ fontSize: 40 }}>👤</span>
          <span style={{ fontSize: 14 }}>{t('profileDrawer.notFound')}</span>
        </div>
      )}
    </Drawer>
  );
};

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontSize: 10,
        fontWeight: 700,
        color: "#9ca3af",
        letterSpacing: "0.9px",
        textTransform: "uppercase",
        padding: "10px 14px 4px",
      }}
    >
      {children}
    </div>
  );
}

function Divider() {
  return <div style={{ height: 1, background: "#f3f4f6", margin: "6px 14px" }} />;
}

function DateCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div
      style={{
        padding: "10px 12px",
        borderRadius: 10,
        background: "#f9fafb",
        border: "1px solid #f3f4f6",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 4, color: "#9ca3af", fontSize: 11 }}>
        {icon}
        <span style={{ fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.5px" }}>{label}</span>
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "#374151" }}>{value}</div>
    </div>
  );
}

export default ProfileDrawer;
