// Paleta Industry — steel-blue "blueprint" system (sistema visual atual).
// A fonte de verdade são as variáveis CSS em `index.css`/`colors.css` e o
// `theme.ts` do Ant Design — usa as classes `ind-*` e `var(--ind-*)` no JSX.
// Este objeto existe só para o caso pontual que precisa mesmo do valor em JS
// (ex.: a prop `stroke` de um SVG, que não aceita `var(...)`).
export const IND = {
  bg: "#f2f2f3",
  surface: "#e9e9ea",
  text: "#1d1f20",
  accent: "#5980a6",
  accent2: "#728fab",
  divider: "rgba(29,31,32,0.16)",
  neutral100: "#f5f5f8",
  neutral200: "#e7e7ea",
  neutral300: "#d4d4d7",
  neutral600: "#7a7a7d",
  neutral700: "#5d5d60",
  neutral900: "#2b2b2d",
  accent100: "#eef6ff",
  accent800: "#2c455d",
  accent900: "#1d2d3d",
};

// ─────────────────────────────────────────────────────────────────────────
// Removido em 2026-08-05: a paleta legacy `D` (terracotta/warmSand) e o
// `actionButtonBaseStyle` que a acompanhava. Eram usados só pelas três
// páginas de Construção, agora migradas para o sistema Industry. Os botões
// da coluna "Ações" passaram a vir de `components/common/ListActions.tsx`
// — usa esses componentes, não recries um objeto de estilo por ficheiro.
// Ver docs/skills/references/design/backoffice-tables-and-lists.md.
// ─────────────────────────────────────────────────────────────────────────
