import { useEffect, useState } from "react";
import { Navigate, Outlet, NavLink, useLocation, useNavigate } from "react-router-dom";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/hooks/useAuth";
import {
  BankOutlined,
  ClockCircleOutlined,
  QuestionCircleOutlined,
  WarningOutlined,
  BuildOutlined,
  CheckSquareOutlined,
  DownOutlined,
  FileTextOutlined,
  SearchOutlined,
  CalendarOutlined,
  QrcodeOutlined,
} from "@ant-design/icons";
import { GlobalInvoiceSearchModal } from "@/components/invoices/GlobalInvoiceSearchModal";
import { ShellHeader } from "./shell/ShellHeader";
import { navButtonStyle, navLinkStyle } from "./shell/navStyles";
import { rememberSpace } from "./shell/spaces";

const ACCENT = "var(--ind-color-accent)";

/** As páginas do espaço Obras que só ADMIN e contabilidade abrem (desde 2026-11-20). */
const WORKS_ONLY = ["/backoffice/empreendimentos", "/backoffice/invoices"];

/**
 * A shell do espaço **Obras**: empreendimentos, tarefas e faturas. A gestão de
 * contas saiu daqui a 2026-10-07 para o espaço Equipa (`TeamLayout`).
 */
export default function AppLayout() {
  // Gates de permissão passam sempre por aqui — ver backoffice-app-shell-and-auth.md §3.
  const { isAdmin, isEmployee, canManageWorks } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const base = "/backoffice";

  useEffect(() => rememberSpace("works"), []);

  const [searchOpen, setSearchOpen] = useState(false);
  const managesWorks = canManageWorks();

  // Ctrl+K (⌘K no Mac) abre a pesquisa global de faturas de qualquer página do espaço.
  useEffect(() => {
    if (!managesWorks) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setSearchOpen(true);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [managesWorks]);

  /**
   * "Faturas" — o único dropdown da nav. Junta as listas de faturas que não são
   * de uma obra só — as sem obra e a "A pagar", que atravessa todas. Só o ADMIN e a contabilidade as veem; o gate real de cada rota é o
   * `@PreAuthorize` do backend — esconder aqui só evita que um EMPLOYEE bata num 403.
   */
  const invoicesMenuItems: MenuProps["items"] = [
    {
      key: `${base}/invoices/outstanding`,
      icon: <ClockCircleOutlined />,
      label: "A pagar",
      onClick: () => navigate(`${base}/invoices/outstanding`),
    },
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
    `${base}/invoices/outstanding`,
    `${base}/invoices/unidentified`,
    `${base}/invoices/company`,
    `${base}/invoices/incidents`,
  ].some((p) => pathname.startsWith(p));

  return (
    <div style={{ minHeight: "100vh" }}>
      <ShellHeader spaceName="Obras" homePath={base}>
        {/* Obras e faturas são de quem gere obras. O EMPLOYEE (a malta da obra) pica o
            ponto, vê as suas tarefas e as suas férias — desde 2026-11-20. */}
        {managesWorks && (
          <NavLink to={`${base}/empreendimentos`} style={navLinkStyle(ACCENT)}>
            <BuildOutlined />{t("nav.enterprises")}
          </NavLink>
        )}
        {isEmployee() && (
          <NavLink to="/picar?ler=1" style={navLinkStyle(ACCENT)}>
            <QrcodeOutlined />Picar ponto
          </NavLink>
        )}
        {isEmployee() && (
          <NavLink to={`${base}/ferias`} style={navLinkStyle(ACCENT)}>
            <CalendarOutlined />Férias e horas
          </NavLink>
        )}
        <NavLink to={`${base}/tasks`} style={navLinkStyle(ACCENT)}>
          <CheckSquareOutlined />{isAdmin() ? "Tarefas" : "Minhas Tarefas"}
        </NavLink>
        {managesWorks && (
          <Dropdown
            trigger={["click"]}
            menu={{ items: invoicesMenuItems, selectable: true, selectedKeys: [pathname] }}
          >
            <button type="button" style={navButtonStyle(invoicesActive, ACCENT)}>
              <FileTextOutlined />Faturas <DownOutlined style={{ fontSize: 10 }} />
            </button>
          </Dropdown>
        )}
        {managesWorks && (
          <button
            type="button"
            data-close-nav
            title="Pesquisar faturas em todas as obras (Ctrl+K)"
            onClick={() => setSearchOpen(true)}
            style={navButtonStyle(searchOpen, ACCENT)}
          >
            <SearchOutlined />Pesquisar
            <kbd style={{ fontSize: 10, opacity: 0.55, fontFamily: "inherit" }}>Ctrl K</kbd>
          </button>
        )}
      </ShellHeader>

      <main className="container-page shell-main" style={{ padding: "27.2px 20.4px" }}>
        {/* Um link antigo ou escrito à mão não abre as páginas de obras a um EMPLOYEE —
            o backend já recusa (403), isto só evita a página partida. */}
        {!managesWorks && WORKS_ONLY.some((path) => pathname.startsWith(path))
          ? <Navigate to={base} replace />
          : <Outlet />}
      </main>

      {managesWorks && <GlobalInvoiceSearchModal open={searchOpen} onClose={() => setSearchOpen(false)} />}
    </div>
  );
}
