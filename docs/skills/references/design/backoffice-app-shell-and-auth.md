# Backoffice — Rotas, Menu e Verificação de Role

> Parte de [[../frontend-visual-consistency]]. Só Backoffice (é a única app frontend do Worksite). Baseado em `main.tsx`, `PrivateRoute.tsx`, `context/AuthContext.tsx`, `hooks/useAuth.ts`, `layouts/AppLayout.tsx`, `pages/backoffice/BackofficeHome.tsx`. Auditoria 2026-08-05, secções 2 e 3 revistas a 2026-08-18 (reorganização do header).

## 1. Superfície de rotas atual

`main.tsx:34-58` define tudo. Públicas: `/login`, `/loading`, `/forgot-password`, `/reset-password`, `/accept-invite`. Protegidas, todas debaixo de `/backoffice/*` com `<PrivateRoute><AppLayout /></PrivateRoute>` (`main.tsx:45`):

| Rota | Página |
|---|---|
| `/backoffice/` | `BackofficeHome` |
| `/backoffice/funcionarios` | `EmployeesList` |
| `/backoffice/funcionarios/:id` | `EmployeeProfilePage` |
| `/backoffice/empreendimentos` | `EnterprisesList` |
| `/backoffice/empreendimentos/:enterpriseId/budget` | `ConstructionBudgetPage` |
| `/backoffice/empreendimentos/:enterpriseId/invoices` | `EnterpriseInvoicesPage` |
| `/backoffice/tasks` | `TasksPage` |

> ⚠️ **Esta tabela esteve errada até 2026-08-18**: listava as três rotas `construction/`
> (`ConstructionStagesPage`, `ConstructionSubStagesPage`, `ConstructionExpensesPage`) que a `V15`
> apagou, e não listava `/budget` nem `/invoices`. Foi apanhado ao comparar com o
> `backoffice/CLAUDE.md`, que tinha a versão certa — o argumento concreto para não haver duas
> cópias da mesma tabela.

**Idioma dos segmentos**: os segmentos de topo herdados estão em português (`funcionarios`, `empreendimentos`), os criados depois em inglês (`tasks`, `construction`) — exatamente a exceção documentada em [[../../frontend/skill-frontend-design-system]] ("pai em português herdado + filhos novos em inglês"). **Segmento novo escreve-se sempre em inglês**, mesmo quando o pai está em português.

> ⚠️ O [[../../frontend/skill-frontend-design-system]] ainda lista segmentos que **não existem** neste projeto (`edificios`, `propriedades`, `contactos`, `localizacoes`, `certificados`) — sobras da cópia do Property-Management. Não os tomes como rotas reais do Worksite.

## 2. Menu de navegação — hoje cobre todas as rotas de topo

`AppLayout.tsx:154-167` lista: Home, Empreendimentos, Tarefas e — só para `ADMIN` — Gerir Contas (`funcionarios`). As restantes rotas (`funcionarios/:id`, as três de `construction`) são rotas de **detalhe**, alcançadas por drill-down a partir da lista respetiva; é correto não terem entrada própria no nav.

**Convenção**: `AppLayout.tsx` é a única fonte de verdade para navegação persistente — uma rota **de topo** nova precisa de um item aqui, não basta um card em `BackofficeHome.tsx`. Rotas de detalhe (`:id`, sub-recursos) não entram no nav.

### 2.1 Menu de utilizador — um só ponto de entrada à direita (2026-08-18)

À direita do nav há **um único `Dropdown`** (`AppLayout.tsx:174`), cujo gatilho é o cartão de perfil (avatar + nome + tag de role). Os itens vivem em `userMenuItems` (`:85`):

| Item | O que faz |
|---|---|
| Minha Conta | abre o `MyProfileModal` |
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

**Não confundir** com rotular a role de **outro** perfil num `<Tag>`/badge, que legitimamente lê o `role` do objeto em causa: `AppLayout.tsx:213` (badge do próprio perfil), `utils/profile.ts:13,18`, `InvitesDrawer.tsx:113-121`, `TaskFormDrawer.tsx:167-168`, `EmployeeMiniCard.tsx:136`, `ProfileDrawer.tsx:131,152,253-254`, `MyProfileModal.tsx:400`. Não são gates de permissão.

## 4. Guarda de rota

`PrivateRoute.tsx:4-12` só verifica se existe `user` no `AuthContext` e redireciona para `/login` caso contrário — **não verifica role**. Não há hoje nenhuma rota exclusiva de `ADMIN` ao nível do router; o gate de `ADMIN` é feito dentro das páginas/menu (ponto 3).

**Convenção**: se uma página passar a ser exclusiva de `ADMIN`, o gate tem de existir também no **backend** (`@PreAuthorize`, ver [[../../backend/skill-permissions-and-auth]]) — esconder o link no nav não é controlo de acesso.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]] — regra de idioma dos segmentos de rota
- [[backoffice-services-and-error-handling]] — camada de serviços e erros
- [[../../backend/skill-permissions-and-auth]] — autorização do lado do backend
