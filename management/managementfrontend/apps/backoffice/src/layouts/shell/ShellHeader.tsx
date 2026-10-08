import type { CSSProperties, ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { SpaceLauncher } from "./SpaceLauncher";
import { UserMenu } from "./UserMenu";

interface ShellHeaderProps {
  /** O nome do espaço, ao lado do wordmark ("Worksite · Obras"). */
  spaceName: string;
  /** Para onde o wordmark leva: o início do espaço. */
  homePath: string;
  /** Header escuro do espaço Equipa; o de Obras é o claro de sempre. */
  dark?: boolean;
  children: ReactNode;
}

/**
 * O header comum aos espaços: três secções — lançador + wordmark à esquerda, a nav
 * do espaço ao centro, sino + utilizador à direita. Os dois lados têm `flex: 1 1 0`
 * iguais, o que mantém a nav no centro real. Só a nav e as cores mudam por espaço.
 */
export function ShellHeader({ spaceName, homePath, dark = false, children }: ShellHeaderProps) {
  const headerStyle: CSSProperties = {
    display: "flex",
    alignItems: "center",
    gap: "13.6px",
    padding: "10.2px 20.4px",
    borderBottom: `1px solid ${dark ? "var(--ind-team-header-border)" : "var(--ind-color-divider)"}`,
    background: dark ? "var(--ind-team-header-bg)" : "var(--ind-color-bg)",
    color: dark ? "var(--ind-team-header-text)" : "inherit",
    position: "sticky",
    top: 0,
    zIndex: 5,
  };

  return (
    <header className={dark ? "space-team-header" : undefined} style={headerStyle}>
      <div style={{ flex: "1 1 0", display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
        <SpaceLauncher />
        {/* O wordmark é o link para o início do espaço — evita um item "Início" à parte. */}
        <NavLink
          to={homePath}
          end
          style={{
            display: "flex",
            alignItems: "baseline",
            gap: 8,
            fontFamily: "var(--ind-font-heading)",
            fontWeight: 600,
            fontSize: 18,
            color: "inherit",
          }}
        >
          Worksite
          <span style={{ color: dark ? "var(--ind-team-header-muted)" : "var(--ind-neutral-500)", fontWeight: 400 }}>
            ·
          </span>
          <span style={{ color: dark ? "var(--ind-team-header-accent)" : "var(--ind-accent-700)" }}>
            {spaceName}
          </span>
        </NavLink>
      </div>

      <nav style={{ display: "flex", alignItems: "center", gap: 30 }}>{children}</nav>

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
        <div
          style={{
            width: 1,
            height: 22,
            background: dark ? "var(--ind-team-header-border)" : "var(--ind-color-divider)",
          }}
        />
        {/* Único ícone solto à direita, e de propósito: um contador que vive dentro
            de um menu não conta nada a ninguém. */}
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  );
}
