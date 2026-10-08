import { useEffect } from "react";
import { Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/hooks/useAuth";
import {
  BankOutlined,
  QuestionCircleOutlined,
  WarningOutlined,
  BuildOutlined,
  CheckSquareOutlined,
  DownOutlined,
  FileTextOutlined,
} from "@ant-design/icons";
import { ShellHeader } from "./shell/ShellHeader";
import { navButtonStyle, navLinkStyle } from "./shell/navStyles";
import { rememberSpace } from "./shell/spaces";

const ACCENT = "var(--ind-color-accent)";

/**
 * A shell do espaço **Obras**: empreendimentos, tarefas e faturas. A gestão de
 * contas saiu daqui a 2026-10-07 para o espaço Equipa (`TeamLayout`).
 */
export default function AppLayout() {
  // Gates de permissão passam sempre por aqui — ver backoffice-app-shell-and-auth.md §3.
  const { isAdmin } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const base = "/backoffice";

  useEffect(() => rememberSpace("works"), []);

  /**
   * "Faturas" — o único dropdown da nav. Junta as três listas de faturas que não
   * pertencem a obra nenhuma. Só o ADMIN as vê; o gate real de cada rota é o
   * `@PreAuthorize` do backend — esconder aqui só evita que um EMPLOYEE bata num 403.
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

  return (
    <div style={{ minHeight: "100vh" }}>
      <ShellHeader spaceName="Obras" homePath={base}>
        <NavLink to={`${base}/empreendimentos`} style={navLinkStyle(ACCENT)}>
          <BuildOutlined />{t("nav.enterprises")}
        </NavLink>
        <NavLink to={`${base}/tasks`} style={navLinkStyle(ACCENT)}>
          <CheckSquareOutlined />{isAdmin() ? "Tarefas" : "Minhas Tarefas"}
        </NavLink>
        {isAdmin() && (
          <Dropdown
            trigger={["click"]}
            menu={{ items: invoicesMenuItems, selectable: true, selectedKeys: [pathname] }}
          >
            <button type="button" style={navButtonStyle(invoicesActive, ACCENT)}>
              <FileTextOutlined />Faturas <DownOutlined style={{ fontSize: 10 }} />
            </button>
          </Dropdown>
        )}
      </ShellHeader>

      <main className="container-page" style={{ padding: "27.2px 20.4px" }}>
        <Outlet />
      </main>
    </div>
  );
}
