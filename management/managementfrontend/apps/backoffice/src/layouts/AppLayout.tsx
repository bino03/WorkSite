import { Outlet, NavLink } from "react-router-dom";
import type { CSSProperties } from "react";
import MyProfileModal from "@/components/profile/MyProfileModal";
import { useState, useEffect } from "react";
import api from "@/api";
import { useTranslation } from "react-i18next";
import { useAuthContext } from "@/context/AuthContext";
import { useConfirm } from "@/context/ConfirmDialogContext";
import {
  BuildOutlined,
  CheckSquareOutlined,
  TeamOutlined,
  LogoutOutlined,
  GlobalOutlined,
} from "@ant-design/icons";

function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase() || "?";
}

export default function AppLayout() {
  const { user, logout } = useAuthContext();
  const { t, i18n } = useTranslation();
  const confirm = useConfirm();
  const [isProfileModalVisible, setIsProfileModalVisible] = useState(false);

  const userPhoto = user?.photoUrl ?? null;
  const userName = user?.name ?? t("common.user");
  const userRole = user?.role ?? null;

  const [avatarBlobUrl, setAvatarBlobUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!userPhoto) { setAvatarBlobUrl(null); return; }

    if (userPhoto.startsWith("http://") || userPhoto.startsWith("https://")) {
      setAvatarBlobUrl(userPhoto);
      return;
    }

    let objectUrl: string;
    api.get(userPhoto, { responseType: "blob" })
      .then((res) => {
        objectUrl = URL.createObjectURL(res.data);
        setAvatarBlobUrl(objectUrl);
      })
      .catch(() => setAvatarBlobUrl(null));
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [userPhoto]);

  const handleLogoutClick = () => {
    confirm({
      title: "Terminar sessão",
      message: "Tem a certeza que quer terminar a sessão?",
      actionLabel: "Terminar sessão",
      onConfirm: logout,
    });
  };

  const toggleLanguage = () => {
    const lang = i18n.language === "en" ? "pt" : "en";
    i18n.changeLanguage(lang);
    localStorage.setItem("language", lang);
  };

  const getBasePath = () => "/backoffice";

  const linkStyle = ({ isActive }: { isActive: boolean }): CSSProperties => ({
    color: isActive ? "var(--ind-color-accent)" : "inherit",
    fontSize: 14,
    display: "inline-flex",
    alignItems: "center",
    gap: 5,
  });

  return (
    <div style={{ minHeight: "100vh" }}>
      <header
        style={{
          display: "flex",
          alignItems: "center",
          gap: "13.6px",
          padding: "10.2px 20.4px",
          borderBottom: "1px solid var(--ind-color-divider)",
          background: "var(--ind-color-bg)",
          position: "sticky",
          top: 0,
          zIndex: 5,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 18, marginRight: "auto" }}>
          <BuildOutlined style={{ color: "var(--ind-color-accent)" }} />
          Worksite
        </div>

        <NavLink to={getBasePath()} end style={linkStyle}>
          {t("nav.home")}
        </NavLink>
        <NavLink to={`${getBasePath()}/empreendimentos`} style={linkStyle}>
          <BuildOutlined />{t("nav.enterprises")}
        </NavLink>
        <NavLink to={`${getBasePath()}/tasks`} style={linkStyle}>
          <CheckSquareOutlined />{userRole === "EMPLOYEE" ? "Minhas Tarefas" : "Tarefas"}
        </NavLink>
        {userRole === "ADMIN" && (
          <NavLink to={`${getBasePath()}/funcionarios`} style={linkStyle}>
            <TeamOutlined />{t("nav.manageAccounts")}
          </NavLink>
        )}

        <div style={{ width: 1, height: 22, background: "var(--ind-color-divider)" }} />

        <div
          style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, cursor: "pointer" }}
          onClick={() => setIsProfileModalVisible(true)}
        >
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: "50%",
              background: avatarBlobUrl ? undefined : "var(--ind-accent-100)",
              color: "var(--ind-accent-800)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontFamily: "var(--ind-font-heading)",
              fontSize: 12,
              backgroundImage: avatarBlobUrl ? `url(${avatarBlobUrl})` : undefined,
              backgroundSize: "cover",
              backgroundPosition: "center",
              flexShrink: 0,
            }}
          >
            {!avatarBlobUrl && initialsOf(userName)}
          </div>
          <span>{userName}</span>
          <span className="ind-tag ind-tag-outline">
            {userRole === "ADMIN" ? t("profile.roles.admin") : t("profile.roles.employee")}
          </span>
        </div>

        <button
          title={i18n.language === "en" ? "Português" : "English"}
          onClick={toggleLanguage}
          style={{ background: "none", border: "none", cursor: "pointer", opacity: 0.7, display: "flex", padding: 4 }}
        >
          <GlobalOutlined style={{ fontSize: 16 }} />
        </button>

        <button
          title="Terminar sessão"
          onClick={handleLogoutClick}
          style={{ background: "none", border: "none", cursor: "pointer", opacity: 0.7, display: "flex", padding: 4 }}
        >
          <LogoutOutlined style={{ fontSize: 16 }} />
        </button>
      </header>

      <main className="container-page" style={{ padding: "27.2px 20.4px" }}>
        <Outlet />
      </main>

      {isProfileModalVisible && (
        <MyProfileModal onClose={() => setIsProfileModalVisible(false)} />
      )}
    </div>
  );
}
