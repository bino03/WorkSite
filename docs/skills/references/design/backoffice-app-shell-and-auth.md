# Backoffice — Rotas, Menu e Verificação de Role

> Parte de [[../frontend-visual-consistency]]. Só Backoffice (é a única app frontend do Worksite). Baseado em `main.tsx`, `PrivateRoute.tsx`, `context/AuthContext.tsx`, `hooks/useAuth.ts`, `layouts/AppLayout.tsx`, `pages/backoffice/BackofficeHome.tsx`. Auditoria 2026-08-05, secções 2 e 3 revistas a 2026-08-18, §2 revista a 2026-09-09 (2ª reorganização do header: dropdown "Gestão").

## 1. Superfície de rotas atual

`main.tsx:34-58` define tudo. Públicas: `/login`, `/loading`, `/forgot-password`, `/reset-password`, `/accept-invite`. Protegidas, todas debaixo de `/backoffice/*` com `<PrivateRoute><AppLayout /></PrivateRoute>` (`main.tsx:45`):

| Rota | Página |
|---|---|
| `/backoffice/` | `BackofficeHome` |
| `/backoffice/funcionarios` (`:id`) | redireciona para `/team/employees` (`:id`) — mudou de espaço a 2026-10-07 |
| `/backoffice/empreendimentos` | `EnterprisesList` |
| `/backoffice/empreendimentos/:enterpriseId/budget` | `ConstructionBudgetPage` |
| `/backoffice/empreendimentos/:enterpriseId/invoices` | `EnterpriseInvoicesPage` |
| `/backoffice/tasks` | `TasksPage` |
| `/team/` | `TeamTodayPage` — espaço **Equipa**, só `ADMIN` (ver §5) |
| `/team/employees` | `EmployeesList` |
| `/team/time-entries` | `TimeEntriesPage` — picagens de toda a equipa, por mês ou por dia |
| `/team/employees/:id` | `EmployeeProfilePage` — perfil + ficha de emprego + mês de assiduidade |
| `/team/settings/schedules` | `WorkSchedulesPage` — horários de trabalho |
| `/team/settings/holidays` | `HolidaysPage` — feriados, por ano |

> ⚠️ **Esta tabela esteve errada até 2026-08-18**: listava as três rotas `construction/`
> (`ConstructionStagesPage`, `ConstructionSubStagesPage`, `ConstructionExpensesPage`) que a `V15`
> apagou, e não listava `/budget` nem `/invoices`. Foi apanhado ao comparar com o
> `backoffice/CLAUDE.md`, que tinha a versão certa — o argumento concreto para não haver duas
> cópias da mesma tabela.

**Idioma dos segmentos**: os segmentos de topo herdados estão em português (`funcionarios`, `empreendimentos`), os criados depois em inglês (`tasks`, `construction`) — exatamente a exceção documentada em [[../../frontend/skill-frontend-design-system]] ("pai em português herdado + filhos novos em inglês"). **Segmento novo escreve-se sempre em inglês**, mesmo quando o pai está em português.

## 2. Menu de navegação — hoje cobre todas as rotas de topo

Reorganizado a 2026-09-09 para reduzir ao mínimo os títulos no header (era uma linha de sete
links achatados). Estrutura atual em `AppLayout.tsx`:

| Entrada | Visível a | Destino |
|---|---|---|
| Wordmark **Worksite** (`NavLink to="/backoffice" end`) | todos | `/backoffice/` — substitui o antigo link "Início" |
| **Empreendimentos** | todos | `/backoffice/empreendimentos` |
| **Tarefas** / **Minhas Tarefas** (rótulo por `isAdmin()`) | todos | `/backoffice/tasks` |
| ~~**Gerir Contas**~~ | — | saiu a 2026-10-07: é o **Funcionários** do espaço Equipa (§5) |
| **Faturas ▾** (`Dropdown`, `invoicesMenuItems`) | só `ADMIN` | Por identificar (`/invoices/unidentified`) · Despesas da empresa (`/invoices/company`) · Inconsistências (`/invoices/incidents`) |

O trigger "Faturas" acende (`--ind-color-accent`) quando `pathname` começa por uma das suas
rotas (`invoicesActive`); o item do dropdown fica `selected` por `selectedKeys: [pathname]`.
As restantes rotas (`funcionarios/:id`, `empreendimentos/:id/budget`, `.../invoices`) são de
**detalhe**, alcançadas por drill-down — é correto não terem entrada no nav.

**Layout do header**: três secções — wordmark à esquerda, `<nav>` ao centro, ações (divisória,
`NotificationBell`, menu do avatar) à direita. Os dois lados têm `flex: 1 1 0` iguais, o que
mantém a `<nav>` no centro real do header independentemente da largura dos lados. Espaçamento
entre itens da nav: `gap: 30`.

**Convenção**: `AppLayout.tsx` é a única fonte de verdade para navegação persistente — uma rota
**de topo** nova precisa de um item aqui, não basta um card em `BackofficeHome.tsx`. Uma rota de
topo nova do domínio de **faturas fora de obra** entra no dropdown "Faturas"; as outras entram
como link direto (com o gate `isAdmin()` quando for o caso). Rotas de detalhe (`:id`,
sub-recursos) não entram no nav.

### 2.1 Menu de utilizador — um só ponto de entrada à direita (2026-08-18)

À direita do nav há **um único `Dropdown`** (`AppLayout.tsx:174`), cujo gatilho é o cartão de perfil (avatar + nome + tag de role). Os itens vivem em `userMenuItems` (`:85`):

| Item | O que faz |
|---|---|
| Minha Conta | abre o `MyProfileDrawer` |
| *Definições* (grupo) → Fornecedores | abre a `SuppliersDrawer` |
| *Definições* (grupo) → Provedores de email | abre a `EmailProvidersDrawer`; a entrada só é montada se `isAdmin()` — o endpoint por trás é `ADMIN` e mostrá-la a um `EMPLOYEE` só lhe dava um 403 |
| Idioma ▸ Português / English | `i18n.changeLanguage` + `localStorage`; o idioma activo fica `disabled` |
| Terminar sessão | `useConfirm()` → `logout` |

Antes desta revisão eram o cartão de perfil **mais três botões de ícone soltos** (engrenagem, globo, sair), cada um com `title` como única pista do que fazia, e o idioma era um alternador cujo `title` mostrava o idioma de *destino* — nunca se sabia em qual se estava.

**Convenções que saem daqui:**

- Ação transversal nova (pessoal ou de produto) entra em `userMenuItems`, **não** como mais um ícone no header. O grupo *Definições* existe precisamente para separar o que é do produto do que é pessoal — é onde uma segunda entrada transversal deve ir.
- O gatilho do menu é um `<button>` com `aria-label`, não um `<div onClick>`: o cartão de perfil tem de continuar alcançável por teclado.
- **O header não tem tratamento de ecrã pequeno** — foi decisão explícita a 2026-08-18 (ferramenta interna, usada em portátil). Não há breakpoint nem menu de hambúrguer, e o projeto continua sem convenção responsiva (a única media query de todo o CSS é `index.css:106`, para o painel de marca do login). Quem introduzir a primeira tem de a documentar aqui.

## 3. Verificação de role do utilizador atual

`hooks/useAuth.ts:17` expõe `isAdmin()` precisamente para centralizar este teste, e é o padrão dominante no código: `TasksList.tsx:51,145`, `TasksPage.tsx:60,62`, `BackofficeHome.tsx:28,99,110,114`, `ConstructionStagesPage.tsx:104,201`, `ConstructionSubStagesPage.tsx:125,222`, `ConstructionExpensesPage.tsx:106,202`.

> ✅ **Corrigido a 2026-08-18.** O `AppLayout` era a exceção: lia `user` direto de `useAuthContext()`, derivava `userRole` à mão e comparava em cru — `{userRole === "ADMIN" && ...}` para o gate de "Gerir Contas", e `userRole === "EMPLOYEE" ? "Minhas Tarefas" : "Tarefas"` para o rótulo, esta última invertida em relação ao `isAdmin()` usado em `TasksPage`/`BackofficeHome` (o que dava resultados diferentes se algum dia existisse uma terceira role). Passou a usar `useAuth().isAdmin()` nos dois sítios (`AppLayout.tsx:161,163`), com a mesma expressão dos outros dois ficheiros.

**Não há hoje nenhuma exceção conhecida a esta convenção.**

**Convenção**: qualquer gate de permissão ou variação de UI baseada na role do utilizador **autenticado** passa por `useAuth()` (`isAdmin()`/`isEmployee()`/`hasRole()`) — nunca ler `role`/`userRole` direto do contexto de auth para essa finalidade.

**Não confundir** com rotular a role de **outro** perfil num `<Tag>`/badge, que legitimamente lê o `role` do objeto em causa: `AppLayout.tsx:213` (badge do próprio perfil), `utils/profile.ts:13,18`, `InvitesDrawer.tsx:113-121`, `TaskFormDrawer.tsx:167-168`, `MyProfileDrawer.tsx` (tag de role na tira de identidade). Não são gates de permissão.

## 4. Guarda de rota

`PrivateRoute.tsx:4-12` só verifica se existe `user` no `AuthContext` e redireciona para `/login` caso contrário — **não verifica role**. Não há hoje nenhuma rota exclusiva de `ADMIN` ao nível do router; o gate de `ADMIN` é feito dentro das páginas/menu (ponto 3).

**Convenção**: se uma página passar a ser exclusiva de `ADMIN`, o gate tem de existir também no **backend** (`@PreAuthorize`, ver [[../../backend/skill-permissions-and-auth]]) — esconder o link no nav não é controlo de acesso.

## 5. Espaços de trabalho — Obras e Equipa (2026-10-07)

O Backoffice tem **dois espaços**, cada um com a sua shell inteira (header, nav, página inicial, cor de
destaque). Mudar de espaço muda tudo menos o **sino e o bloco do utilizador**. Maquete aprovada:
https://claude.ai/artifact/Pc44vRLcHtYd5qVLFqUEnB.

| Espaço | Rotas | Layout | Visível a | Identidade |
|---|---|---|---|---|
| **Obras** | `/backoffice/*` | `AppLayout` | todos | header claro, azul-aço (`--ind-color-accent`) |
| **Equipa** | `/team/*` | `TeamLayout` | só `ADMIN` | header escuro (`--ind-team-header-*`), verde-oliva |

Peças em `layouts/shell/`:

- **`ShellHeader`** — o header comum (lançador + wordmark "Worksite · <espaço>" à esquerda, a nav do
  espaço ao centro como `children`, sino + `UserMenu` à direita). Prop `dark` para o de Equipa.
- **`SpaceLauncher`** — o botão de grelha e o painel de mosaicos (um `Modal` do AntD com fundo
  transparente, para ter foco preso e `Esc` de graça). **Quem só tem um espaço não vê o botão.**
- **`UserMenu`** — o menu do avatar (§2.1), tirado do `AppLayout` para os dois espaços o partilharem.
- **`spaces.ts`** — a lista de espaços, `visibleSpaces(isAdmin)`, e o último espaço usado
  (`localStorage`, chave `worksite.lastSpace`): o login aterra nele (`landingPath`, em `LoginLoadingPage`).
- **`navStyles.ts`** — `navLinkStyle(cor)` / `navButtonStyle(ativo, cor)`, a cor é o destaque do espaço.

**Como o verde chega aos componentes sem os tocar:** o `TeamLayout` põe a classe `space-team` no
`<body>` (não num wrapper — drawers, modais e dropdowns do AntD vivem em portal fora da árvore) e
envolve tudo num `ConfigProvider` com `teamAntdTheme` (`theme.ts`). A classe redefine
`--ind-color-accent` e a escala `--ind-accent-*` (`index.css`); o tema troca o `colorPrimary` e os
componentes que o tinham escrito à mão. Um componente que use os tokens fica verde em Equipa sozinho.

**Nav de Equipa** (`TeamLayout`), pela ordem do maquete aprovado: Hoje (`/team`) · Picagens
(`/team/time-entries`) · Funcionários (`/team/employees`) · **Configuração ▾**
(`Dropdown`, 2026-10-08) → Horários · Feriados. O dropdown é o mesmo molde do "Faturas ▾" do
`AppLayout` — `trigger={["click"]}`, `selectedKeys: [pathname]`, o gatilho é um `<button>` com
`navButtonStyle(ativo, cor)` e acende quando `pathname` começa por `/team/settings`. Rotas de
**configuração** do espaço entram aqui; rotas de trabalho diário entram como link direto.

**Gate**: o espaço Equipa é só `ADMIN` porque os endpoints de assiduidade são `hasRole('ADMIN')`. O
`TeamLayout` devolve um `EMPLOYEE` a `/backoffice` (o self-service é a fase 5 da assiduidade, por
decidir). **Rota nova de topo**: entra na nav do layout do seu espaço — Obras no `AppLayout`, Equipa no
`TeamLayout`. Segmentos novos em inglês (`/team/...`), como manda a convenção.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]] — regra de idioma dos segmentos de rota
- [[backoffice-services-and-error-handling]] — camada de serviços e erros
- [[../../backend/skill-permissions-and-auth]] — autorização do lado do backend
