import { useState } from "react";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useTranslation } from "react-i18next";
import {
  GlobalOutlined,
  LogoutOutlined,
  MailOutlined,
  ShopOutlined,
  UserOutlined,
} from "@ant-design/icons";
import MyProfileDrawer from "@/components/profile/MyProfileDrawer";
import { SuppliersDrawer } from "@/components/suppliers/SuppliersDrawer";
import { EmailProvidersDrawer } from "@/components/settings/EmailProvidersDrawer";
import { useAuthContext } from "@/context/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";

function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase() || "?";
}

/**
 * O bloco do utilizador — o único pedaço do header que não muda entre espaços.
 * Um só ponto de entrada à direita: a conta e o idioma são pessoais; "Definições"
 * fica num grupo próprio porque é transversal ao produto.
 */
export function UserMenu() {
  const { user, logout } = useAuthContext();
  const { isAdmin } = useAuth();
  const { t, i18n } = useTranslation();
  const confirm = useConfirm();
  const [isProfileDrawerOpen, setIsProfileDrawerOpen] = useState(false);
  const [isSuppliersDrawerOpen, setIsSuppliersDrawerOpen] = useState(false);
  const [isEmailProvidersDrawerOpen, setIsEmailProvidersDrawerOpen] = useState(false);

  const userName = user?.name ?? t("common.user");
  const userRole = user?.role ?? null;
  const isEnglish = i18n.language.startsWith("en");

  const setLanguage = (lang: "pt" | "en") => {
    i18n.changeLanguage(lang);
    localStorage.setItem("language", lang);
  };

  const handleLogoutClick = () => {
    confirm({
      title: "Terminar sessão",
      message: "Tem a certeza que quer terminar a sessão?",
      actionLabel: "Terminar sessão",
      onConfirm: logout,
    });
  };

  const items: MenuProps["items"] = [
    {
      key: "profile",
      icon: <UserOutlined />,
      label: t("profile.myAccount"),
      onClick: () => setIsProfileDrawerOpen(true),
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

  return (
    <>
      <Dropdown trigger={["click"]} menu={{ items }}>
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
              background: "var(--ind-accent-100)",
              color: "var(--ind-accent-800)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontFamily: "var(--ind-font-heading)",
              fontSize: 12,
              flexShrink: 0,
            }}
          >
            {initialsOf(userName)}
          </div>
          <span>{userName}</span>
          <span className="ind-tag ind-tag-outline">
            {userRole === "ADMIN" ? t("profile.roles.admin") : t("profile.roles.employee")}
          </span>
        </button>
      </Dropdown>

      {isProfileDrawerOpen && <MyProfileDrawer onClose={() => setIsProfileDrawerOpen(false)} />}

      <EmailProvidersDrawer
        open={isEmailProvidersDrawerOpen}
        onClose={() => setIsEmailProvidersDrawerOpen(false)}
      />

      <SuppliersDrawer open={isSuppliersDrawerOpen} onClose={() => setIsSuppliersDrawerOpen(false)} />
    </>
  );
}
