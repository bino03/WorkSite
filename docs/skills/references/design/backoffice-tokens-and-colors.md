# Backoffice — Tokens & Colors

> Parte de [[../frontend-visual-consistency]]. Só Backoffice — o Portal tem o seu próprio ficheiro em [[portal-tokens-and-colors]].

## Fonte de verdade

- **`src/theme.ts`** — tema Ant Design ("Anthropic/Claude Design System"): `colorPrimary: "#c96442"` (terracota), texto `#141413`, texto secundário `#5e5d59`, fundo `colorBgLayout: "#f5f4ed"`, `colorBgContainer`/`colorBgElevated: "#faf9f5"`, `borderRadius: 8` (LG 12, SM 6, XS 4), `fontSize: 15`, sombra base `rgba(0,0,0,0.05) 0px 4px 24px`. Overrides por componente para Button, Input, Select, Table (`headerBg: "#e8e6dc"`), Card, Modal (`borderRadius: 12`), Drawer, Tag, Tabs, Menu, Pagination, Tooltip, Segmented.
- **`src/index.css`** — variáveis CSS complementares (`--terracotta`, `--parchment`, `--ivory`, etc.) + classes utilitárias `.card`, `.btn-primary`/`.btn-secondary`/`.btn-outline` + overrides diretos do AntD Table (`.ant-table-thead > tr > th { background: var(--warm-sand) !important; }`).

**Regra**: qualquer cor/raio/sombra nova vem destes dois ficheiros — nunca um hex novo hardcoded num `style={{}}`.

## Drift encontrado — não repetir

### `#1890ff` a vazar por cima do tema
84 ocorrências do azul por omissão do próprio Ant Design, mesmo com `colorPrimary` a apontar para terracota. É quase sempre um bug visual — um componente que devia herdar a cor primária do tema mas tem a cor por omissão do AntD hardcoded por cima. Se precisares da cor primária, usa o token do tema (herdado automaticamente pelo componente AntD, ou `var(--terracotta)`), nunca `#1890ff`.

### Paleta de "cor de entidade" copiada 3× nas listas
`PropertiesList.tsx:88-102`, `EmployeesList.tsx:51-64`, `BuildingsList.tsx:90-103` definem, cada um, o mesmo objeto local `const D = { terracotta: "#c96442", warmSand: "#e8e6dc", ... }` com os valores exatamente iguais — copiado, não importado. `ContactsList.tsx:212-260` nem usa esse objeto: tem os hex soltos diretamente em `style={{ color: '#141413' }}`.

**Convenção daqui em diante**: extrair este objeto para um módulo partilhado (ex. `src/config/entityColors.ts`, ou simplesmente re-exportar os tokens já existentes de `theme.ts`) e importar em todas as listas. Não voltar a copiar o objeto `D` para um ficheiro novo — se precisares dele, importa-o.

**Já existe**: `src/config/entityColors.ts` exporta `D` **e** `actionButtonBaseStyle` (o estilo base dos botões da coluna de ações — ver [[backoffice-buttons-and-icons]] e [[backoffice-tables-and-lists]]). `TasksList.tsx` é o primeiro consumidor; migrar `PropertiesList`/`LeadsList`/`BuildingsList`/`LicensesList` para importar daqui em vez das cópias locais, oportunisticamente.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tables-and-lists]] — onde a duplicação da paleta `D` mais aparece
