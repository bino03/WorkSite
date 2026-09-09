import { Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import type { CSSProperties } from "react";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import MyProfileModal from "@/components/profile/MyProfileModal";
import { SuppliersDrawer } from "@/components/suppliers/SuppliersDrawer";
import { EmailProvidersDrawer } from "@/components/settings/EmailProvidersDrawer";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { useState, useEffect } from "react";
import api from "@/api";
import { useTranslation } from "react-i18next";
import { useAuthContext } from "@/context/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import {
  BankOutlined,
  QuestionCircleOutlined,
  WarningOutlined,
  BuildOutlined,
  CheckSquareOutlined,
  TeamOutlined,
  LogoutOutlined,
  GlobalOutlined,
  MailOutlined,
  ShopOutlined,
  UserOutlined,
  DownOutlined,
  FileTextOutlined,
} from "@ant-design/icons";

function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase() || "?";
}

export default function AppLayout() {
  const { user, logout } = useAuthContext();
  // Gates de permissão passam sempre por aqui — ver backoffice-app-shell-and-auth.md §3.
  const { isAdmin } = useAuth();
  const { t, i18n } = useTranslation();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [isProfileModalVisible, setIsProfileModalVisible] = useState(false);
  const [isSuppliersDrawerOpen, setIsSuppliersDrawerOpen] = useState(false);
  const [isEmailProvidersDrawerOpen, setIsEmailProvidersDrawerOpen] = useState(false);

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

  const isEnglish = i18n.language.startsWith("en");

  const setLanguage = (lang: "pt" | "en") => {
    i18n.changeLanguage(lang);
    localStorage.setItem("language", lang);
  };

  const getBasePath = () => "/backoffice";
  const base = getBasePath();

  /**
   * "Faturas" — o único dropdown de topo à esquerda. Junta as três listas de
   * faturas que não pertencem a obra nenhuma e que antes eram três links soltos
   * a encher o header. Só o ADMIN as vê; o gate real de cada rota é o
   * `@PreAuthorize` do backend — esconder aqui só evita que um EMPLOYEE bata
   * num 403 (ver backoffice-app-shell-and-auth.md §2). A gestão de contas fica
   * de fora, como link direto: é de outro domínio.
   */
  const invoicesMenuItems: MenuProps["items"] = [
    {
      key: `${base}/invoices/unidentified`,
      icon: <QuestionCircleOutlined />,
      label: "Por identificar",
      onClick: () => navigate(`${base}/invoices/unidentified`),
    },
    {
      key: `${base}/invoices/company`,
      icon: <BankOutlined />,
      label: "Despesas da empresa",
      onClick: () => navigate(`${base}/invoices/company`),
    },
    {
      key: `${base}/invoices/incidents`,
      icon: <WarningOutlined />,
      label: "Inconsistências",
      onClick: () => navigate(`${base}/invoices/incidents`),
    },
  ];

  /** O trigger "Faturas" acende quando a rota atual é uma das que ele contém. */
  const invoicesActive = [
    `${base}/invoices/unidentified`,
    `${base}/invoices/company`,
    `${base}/invoices/incidents`,
  ].some((p) => pathname.startsWith(p));

  /**
   * O menu único da direita. A conta e o idioma são pessoais; "Definições" fica num
   * grupo próprio porque é transversal ao produto e vai crescer — era o que o botão
   * de engrenagem separava antes, e sem o grupo isso perdia-se.
   */
  const userMenuItems: MenuProps["items"] = [
    {
      key: "profile",
      icon: <UserOutlined />,
      label: t("profile.myAccount"),
      onClick: () => setIsProfileModalVisible(true),
    },
    { type: "divider" },
    {
      type: "group",
      label: "Definições",
      children: [
        {
          key: "suppliers",
          icon: <ShopOutlined />,
          label: "Fornecedores",
          onClick: () => setIsSuppliersDrawerOpen(true),
        },
        // Só ADMIN: são credenciais SMTP, e o endpoint por trás recusa toda a gente
        // o resto — mostrar a entrada a um EMPLOYEE só lhe daria um 403.
        ...(isAdmin()
          ? [
              {
                key: "email-providers",
                icon: <MailOutlined />,
                label: "Provedores de email",
                onClick: () => setIsEmailProvidersDrawerOpen(true),
              },
            ]
          : []),
      ],
    },
    {
      // Submenu em vez do antigo botão-alternador, cujo `title` mostrava o idioma de
      // destino e não o activo — nunca se sabia em qual se estava.
      key: "language",
      icon: <GlobalOutlined />,
      label: t("profile.language"),
      children: [
        { key: "lang-pt", label: "Português", disabled: !isEnglish, onClick: () => setLanguage("pt") },
        { key: "lang-en", label: "English", disabled: isEnglish, onClick: () => setLanguage("en") },
      ],
    },
    { type: "divider" },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      label: t("profile.logout"),
      danger: true,
      onClick: handleLogoutClick,
    },
  ];

  const navBaseStyle: CSSProperties = {
    fontSize: 14,
    display: "inline-flex",
    alignItems: "center",
    gap: 5,
  };

  const linkStyle = ({ isActive }: { isActive: boolean }): CSSProperties => ({
    ...navBaseStyle,
    color: isActive ? "var(--ind-color-accent)" : "inherit",
  });

  /** Botão de dropdown com o mesmo aspeto de um `NavLink` do header. */
  const navButtonStyle = (active: boolean): CSSProperties => ({
    ...navBaseStyle,
    color: active ? "var(--ind-color-accent)" : "inherit",
    background: "none",
    border: "none",
    padding: 0,
    cursor: "pointer",
    fontFamily: "inherit",
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
        {/* Três secções: wordmark à esquerda, nav ao centro, ações à direita.
            Os dois lados têm `flex: 1` iguais, por isso a nav fica mesmo no
            meio do header. */}
        <div style={{ flex: "1 1 0", display: "flex", alignItems: "center", minWidth: 0 }}>
          {/* O wordmark é o link para o início — evita um item "Início" à parte. */}
          <NavLink
            to={base}
            end
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              fontFamily: "var(--ind-font-heading)",
              fontWeight: 600,
              fontSize: 18,
              color: "inherit",
            }}
          >
            <BuildOutlined style={{ color: "var(--ind-color-accent)" }} />
            Worksite
          </NavLink>
        </div>

        <nav style={{ display: "flex", alignItems: "center", gap: 30 }}>
          <NavLink to={`${base}/empreendimentos`} style={linkStyle}>
            <BuildOutlined />{t("nav.enterprises")}
          </NavLink>
          <NavLink to={`${base}/tasks`} style={linkStyle}>
            <CheckSquareOutlined />{isAdmin() ? "Tarefas" : "Minhas Tarefas"}
          </NavLink>
          {isAdmin() && (
            <NavLink to={`${base}/funcionarios`} style={linkStyle}>
              <TeamOutlined />{t("nav.manageAccounts")}
            </NavLink>
          )}

          {/* As três listas de faturas fora de obra, antes três links soltos a
              encher o header. Ver `invoicesMenuItems`. */}
          {isAdmin() && (
            <Dropdown
              trigger={["click"]}
              menu={{ items: invoicesMenuItems, selectable: true, selectedKeys: [pathname] }}
            >
              <button type="button" style={navButtonStyle(invoicesActive)}>
                <FileTextOutlined />Faturas <DownOutlined style={{ fontSize: 10 }} />
              </button>
            </Dropdown>
          )}
        </nav>

        <div
          style={{
            flex: "1 1 0",
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
            gap: "13.6px",
            minWidth: 0,
          }}
        >
        <div style={{ width: 1, height: 22, background: "var(--ind-color-divider)" }} />

        {/* Único ícone solto que sobrou à direita, e de propósito: um contador que
            vive dentro de um menu não conta nada a ninguém. */}
        <NotificationBell />

        {/* Um só ponto de entrada à direita. Antes eram o cartão de perfil mais três
            botões de ícone soltos (definições, idioma, sair), cada um com `title` como
            única pista do que fazia. */}
        <Dropdown trigger={["click"]} menu={{ items: userMenuItems }}>
          <button
            type="button"
            aria-label={t("profile.myAccount")}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              padding: 4,
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "inherit",
              fontFamily: "inherit",
              fontSize: 13,
            }}
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
          </button>
        </Dropdown>
        </div>
      </header>

      <main className="container-page" style={{ padding: "27.2px 20.4px" }}>
        <Outlet />
      </main>

      {isProfileModalVisible && (
        <MyProfileModal onClose={() => setIsProfileModalVisible(false)} />
      )}

      <EmailProvidersDrawer
        open={isEmailProvidersDrawerOpen}
        onClose={() => setIsEmailProvidersDrawerOpen(false)}
      />

      <SuppliersDrawer
        open={isSuppliersDrawerOpen}
        onClose={() => setIsSuppliersDrawerOpen(false)}
      />
    </div>
  );
}
