import { useEffect } from "react";
import { Navigate, Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import { ConfigProvider, Dropdown } from "antd";
import type { MenuProps } from "antd";
import {
  CalendarOutlined,
  ClockCircleOutlined,
  DownOutlined,
  FieldTimeOutlined,
  HomeOutlined,
  SettingOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import { teamAntdTheme } from "@/theme";
import { useAuth } from "@/hooks/useAuth";
import { ShellHeader } from "./shell/ShellHeader";
import { navButtonStyle, navLinkStyle } from "./shell/navStyles";
import { rememberSpace } from "./shell/spaces";

const HEADER_ACCENT = "var(--ind-team-header-accent)";

/**
 * A shell do espaço **Equipa**: assiduidade, férias, relatórios e contas. Header
 * escuro e destaque verde-oliva, para se saber de relance em que espaço se está.
 *
 * A classe `space-team` vai para o `<body>` e não para um wrapper: drawers, modais
 * e dropdowns do AntD são renderizados em portal fora da árvore, e só assim herdam
 * os tokens `--ind-*` do espaço. O `ConfigProvider` cobre o tema do AntD.
 */
export default function TeamLayout() {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  useEffect(() => {
    document.body.classList.add("space-team");
    rememberSpace("team");
    return () => document.body.classList.remove("space-team");
  }, []);

  const base = "/team";

  // O espaço é só ADMIN porque os endpoints por trás são todos `hasRole('ADMIN')`;
  // um EMPLOYEE que chegue aqui por URL volta a Obras em vez de ver 403 em cada ecrã.
  if (!isAdmin()) {
    return <Navigate to="/backoffice" replace />;
  }

  const settingsMenuItems: MenuProps["items"] = [
    {
      key: `${base}/settings/schedules`,
      icon: <ClockCircleOutlined />,
      label: "Horários",
      onClick: () => navigate(`${base}/settings/schedules`),
    },
    {
      key: `${base}/settings/holidays`,
      icon: <CalendarOutlined />,
      label: "Feriados",
      onClick: () => navigate(`${base}/settings/holidays`),
    },
  ];

  /** O trigger "Configuração" acende quando a rota atual é uma das que ele contém. */
  const settingsActive = pathname.startsWith(`${base}/settings`);

  return (
    <ConfigProvider theme={teamAntdTheme}>
      <div style={{ minHeight: "100vh" }}>
        <ShellHeader spaceName="Equipa" homePath={base} dark>
          <NavLink to={base} end style={navLinkStyle(HEADER_ACCENT)}>
            <HomeOutlined />Hoje
          </NavLink>
          <NavLink to={`${base}/time-entries`} style={navLinkStyle(HEADER_ACCENT)}>
            <FieldTimeOutlined />Picagens
          </NavLink>
          <NavLink to={`${base}/employees`} style={navLinkStyle(HEADER_ACCENT)}>
            <TeamOutlined />Funcionários
          </NavLink>
          <Dropdown
            trigger={["click"]}
            menu={{ items: settingsMenuItems, selectable: true, selectedKeys: [pathname] }}
          >
            <button type="button" style={navButtonStyle(settingsActive, HEADER_ACCENT)}>
              <SettingOutlined />Configuração <DownOutlined style={{ fontSize: 10 }} />
            </button>
          </Dropdown>
        </ShellHeader>

        <main className="container-page" style={{ padding: "27.2px 20.4px" }}>
          <Outlet />
        </main>
      </div>
    </ConfigProvider>
  );
}
