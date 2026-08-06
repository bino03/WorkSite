# Backoffice — Rotas, Menu e Verificação de Role

> Parte de [[../frontend-visual-consistency]]. Só Backoffice (é a única app frontend do Worksite). Baseado em `main.tsx`, `PrivateRoute.tsx`, `context/AuthContext.tsx`, `hooks/useAuth.ts`, `layouts/AppLayout.tsx`, `pages/backoffice/BackofficeHome.tsx`. Auditoria 2026-08-05.

## 1. Superfície de rotas atual

`main.tsx:34-57` define tudo. Públicas: `/login`, `/loading`, `/forgot-password`, `/accept-invite`. Protegidas, todas debaixo de `/backoffice/*` com `<PrivateRoute><AppLayout /></PrivateRoute>` (`main.tsx:44`):

| Rota | Página |
|---|---|
| `/backoffice/` | `BackofficeHome` |
| `/backoffice/funcionarios` | `EmployeesList` |
| `/backoffice/funcionarios/:id` | `EmployeeProfilePage` |
| `/backoffice/empreendimentos` | `EnterprisesList` |
| `/backoffice/empreendimentos/:enterpriseId/construction` | `ConstructionStagesPage` |
| `.../construction/:stageId` | `ConstructionSubStagesPage` |
| `.../construction/:stageId/:subStageId` | `ConstructionExpensesPage` |
| `/backoffice/tasks` | `TasksPage` |

**Idioma dos segmentos**: os segmentos de topo herdados estão em português (`funcionarios`, `empreendimentos`), os criados depois em inglês (`tasks`, `construction`) — exatamente a exceção documentada em [[../../frontend/skill-frontend-design-system]] ("pai em português herdado + filhos novos em inglês"). **Segmento novo escreve-se sempre em inglês**, mesmo quando o pai está em português.

> ⚠️ O [[../../frontend/skill-frontend-design-system]] ainda lista segmentos que **não existem** neste projeto (`edificios`, `propriedades`, `contactos`, `localizacoes`, `certificados`) — sobras da cópia do Property-Management. Não os tomes como rotas reais do Worksite.

## 2. Menu de navegação — hoje cobre todas as rotas de topo

`AppLayout.tsx:98-111` lista: Home, Empreendimentos, Tarefas e — só para `ADMIN` — Gerir Contas (`funcionarios`). As restantes rotas (`funcionarios/:id`, as três de `construction`) são rotas de **detalhe**, alcançadas por drill-down a partir da lista respetiva; é correto não terem entrada própria no nav.

**Convenção**: `AppLayout.tsx` é a única fonte de verdade para navegação persistente — uma rota **de topo** nova precisa de um item aqui, não basta um card em `BackofficeHome.tsx`. Rotas de detalhe (`:id`, sub-recursos) não entram no nav.

## 3. Verificação de role do utilizador atual — drift a corrigir

`hooks/useAuth.ts:17` expõe `isAdmin()` precisamente para centralizar este teste, e é o padrão dominante no código: `TasksList.tsx:51,145`, `TasksPage.tsx:60,62`, `BackofficeHome.tsx:28,99,110,114`, `ConstructionStagesPage.tsx:104,201`, `ConstructionSubStagesPage.tsx:125,222`, `ConstructionExpensesPage.tsx:106,202`.

**`layouts/AppLayout.tsx` é a exceção** — o ficheiro que decide o que aparece no menu lê `user` direto de `useAuthContext()` (`:25`), deriva `userRole` à mão (`:32`) e faz a comparação em cru:

- `AppLayout.tsx:107` — `{userRole === "ADMIN" && (...)}` decide se mostra "Gerir Contas". **Isto é um gate de permissão** e devia ser `useAuth().isAdmin()`.
- `AppLayout.tsx:105` — `userRole === "EMPLOYEE" ? "Minhas Tarefas" : "Tarefas"` escolhe o rótulo do link. A **mesma decisão de rótulo** está feita com `isAdmin()` em `TasksPage.tsx:60` e `BackofficeHome.tsx:110`. Duas mecânicas para a mesma regra — e invertidas uma em relação à outra (`EMPLOYEE`? vs `isAdmin()`?), o que dá resultados diferentes se algum dia existir uma terceira role.

**Convenção**: qualquer gate de permissão ou variação de UI baseada na role do utilizador **autenticado** passa por `useAuth()` (`isAdmin()`/`isEmployee()`/`hasRole()`) — nunca ler `role`/`userRole` direto do contexto de auth para essa finalidade.

**Não confundir** com rotular a role de **outro** perfil num `<Tag>`/badge, que legitimamente lê o `role` do objeto em causa: `AppLayout.tsx:141` (badge do próprio perfil), `utils/profile.ts:13,18`, `InvitesDrawer.tsx:113-121`, `TaskFormDrawer.tsx:167-168`, `EmployeeMiniCard.tsx:136`, `ProfileDrawer.tsx:131,152,253-254`, `MyProfileModal.tsx:400`. Não são gates de permissão.

## 4. Guarda de rota

`PrivateRoute.tsx:4-12` só verifica se existe `user` no `AuthContext` e redireciona para `/login` caso contrário — **não verifica role**. Não há hoje nenhuma rota exclusiva de `ADMIN` ao nível do router; o gate de `ADMIN` é feito dentro das páginas/menu (ponto 3).

**Convenção**: se uma página passar a ser exclusiva de `ADMIN`, o gate tem de existir também no **backend** (`@PreAuthorize`, ver [[../../backend/skill-permissions-and-auth]]) — esconder o link no nav não é controlo de acesso.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]] — regra de idioma dos segmentos de rota
- [[backoffice-services-and-error-handling]] — camada de serviços e erros
- [[../../backend/skill-permissions-and-auth]] — autorização do lado do backend
