# Backoffice — Tokens & Colors

> Parte de [[../frontend-visual-consistency]]. Só Backoffice — é a única app frontend deste projeto. Auditoria 2026-08-05.

O sistema visual é o **Industry ("blueprint")** — steel-blue, tipografia Barlow. Substituiu a paleta terracotta/parchment herdada do Property-Management; a migração ficou **completa** a 2026-08-05 com as três páginas de Construção.

## Fonte de verdade

- **`src/index.css`** — as variáveis CSS `--ind-*`. É aqui que vive a paleta:
  - Base: `--ind-color-bg: #f2f2f3`, `--ind-color-surface: #e9e9ea`, `--ind-color-text: #1d1f20`, `--ind-color-divider`
  - Acento: `--ind-color-accent: #5980a6` (steel-blue), `--ind-color-accent-2: #728fab`, escalas `--ind-accent-100…900`
  - Neutros: `--ind-neutral-100…900`
  - Tipografia: `--ind-font-heading: "Barlow Condensed"`, `--ind-font-body: "Barlow"`
  - Sombras: `--ind-shadow-sm` / `-md` / `-lg`
- **`src/theme.ts`** — o tema Ant Design que espelha essas cores (`colorPrimary: "#5980a6"`, `colorError: "#b53333"`, `colorSuccess: "#3a7d44"`, `colorWarning: "#a0622b"`). Os componentes AntD herdam daqui — não voltes a definir cor de botão/input/tabela por ficheiro.
- **`src/colors.css`** — carregado a seguir ao `index.css`, para ajustes de cor complementares.

**Regra**: qualquer cor/raio/sombra nova vem destes ficheiros. Nunca um hex novo hardcoded num `style={{}}` — usa `var(--ind-*)`.

## Classes utilitárias disponíveis

`.ind-card`, `.ind-card-body`, `.ind-card-title`, `.ind-card-kicker`, `.ind-card-meta`, `.ind-tag` (+ `.ind-tag-accent`, `.ind-tag-accent-2`, `.ind-tag-neutral`, `.ind-tag-outline`), `.ind-elev-sm/md/lg`, `.ind-hatch`, `.ind-blueprint`, `.ind-corner`.

Antes de escrever estilo novo, verifica se uma destas já resolve — ex. o cartão de total em `ConstructionExpensesPage` usa `className="ind-card"` em vez de um `<Card>` do AntD com `bodyStyle` à mão.

## `config/entityColors.ts` — só `IND`

O ficheiro exporta **apenas** o objeto `IND`, um espelho em JS das variáveis CSS, para o caso pontual que precisa mesmo do valor literal (ex.: a prop `stroke` de um SVG, que não aceita `var(...)`).

**Removidos a 2026-08-05** (estavam a ser usados só pelas páginas de Construção, agora migradas):
- `D` — a paleta legacy terracotta/warmSand
- `actionButtonBaseStyle` — o estilo dos botões de ação, substituído pelo componente [[backoffice-tables-and-lists|`components/common/ListActions.tsx`]]
- `industryActionButtonStyle` — nunca chegou a ser usado

Se precisares de uma cor em JSX, usa `var(--ind-*)` numa string de `style`; `IND` é o último recurso.

## Drift que ainda existe — não repetir

### `#1890ff` (azul por omissão do AntD) hardcoded por cima do tema
16 ocorrências em 6 ficheiros: `ProfileView.tsx` (5), `InvitesDrawer.tsx` (4), `MapLocationPickerDrawer.tsx` (4), `EmployeeContextMenu.tsx`, `EditEnterpriseOverviewCard.tsx`, `MediaUploadsSection.tsx`. É quase sempre um bug visual — um componente que devia herdar `colorPrimary` mas tem o azul do AntD escrito à mão. Usa `var(--ind-color-accent)`, ou deixa o componente AntD herdar do tema.

### Paleta legacy ainda local em `MyProfileModal.tsx`
`MyProfileModal.tsx:26-35` mantém um objeto `D` local com os hex terracotta reais (`#c96442`, `#e8e6dc`…) e usa `fontFamily: "Georgia, serif"` — é o último componente por migrar para o sistema Industry. Migrar quando for tocado.

> Nota: `EmployeesList.tsx:51-64` também tem um objeto chamado `D`, mas os valores já apontam para `var(--ind-*)` — é só um alias local, não drift de cor.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tables-and-lists]] — onde estes tokens são aplicados em listas
- [[backoffice-buttons-and-icons]] — variantes de botão que herdam do tema
