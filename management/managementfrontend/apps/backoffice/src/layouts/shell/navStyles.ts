import type { CSSProperties } from "react";

const navBaseStyle: CSSProperties = {
  fontSize: 14,
  display: "inline-flex",
  alignItems: "center",
  gap: 5,
};

/** Estilo de um link da nav; `activeColor` é o destaque do espaço. */
export const navLinkStyle =
  (activeColor: string) =>
  ({ isActive }: { isActive: boolean }): CSSProperties => ({
    ...navBaseStyle,
    color: isActive ? activeColor : "inherit",
  });

/** Um botão de dropdown com o mesmo aspeto de um `NavLink` da nav. */
export const navButtonStyle = (active: boolean, activeColor: string): CSSProperties => ({
  ...navBaseStyle,
  color: active ? activeColor : "inherit",
  background: "none",
  border: "none",
  padding: 0,
  cursor: "pointer",
  fontFamily: "inherit",
});
