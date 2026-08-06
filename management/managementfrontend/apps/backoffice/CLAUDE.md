# CLAUDE.md - Backoffice Admin Dashboard

This file provides guidance to Claude Code when working with the **Backoffice** React application.

> 📦 Related documentation:
> - **Project root:** [`../../../../CLAUDE.md`](../../../../CLAUDE.md)
> - **Management folder:** [`../../../CLAUDE.md`](../../../CLAUDE.md)
> - **Backend API:** [`../../../managementapi/CLAUDE.md`](../../../managementapi/CLAUDE.md)
> - **Frontend root:** [`../CLAUDE.md`](../CLAUDE.md)

## Commands

```bash
npm run dev       # Vite dev server com HMR
npm run build     # tsc -b && vite build
npm run lint      # ESLint
npm run preview   # Preview do build de produção
```

Variáveis de ambiente (`.env`):
- `VITE_API_URL` — URL base do backend (ex: `http://localhost:8080`)
- `VITE_GOOGLE_MAPS_API_KEY` — opcional, só para o seletor de localização no mapa (`MapLocationPickerDrawer`)

---

## Stack

- React 18 + TypeScript 5.8 (modo strict)
- Vite 7, Tailwind CSS 4, Ant Design 5
- React Router v6 — rotas definidas em `src/main.tsx`
- Axios para API, Zod + React Hook Form para formulários
- **Sem SDK do Supabase no frontend** — a autenticação passa sempre pelo backend, nunca fala diretamente com o Supabase
- Path alias: `@/` → `src/`

---

## Estrutura de pastas

```
src/
├── main.tsx                  # Entry point — configuração de rotas
├── api.ts                    # Instância Axios principal (única — não há uma segunda)
├── theme.ts                  # Tema Ant Design centralizado
├── i18n.ts                   # i18next (pt/en)
├── index.css / colors.css    # Tailwind + variáveis CSS globais
│
├── layouts/
│   └── AppLayout.tsx         # Layout principal (nav: Home, Projetos, Tarefas, Equipa)
│
├── pages/
│   ├── Login.tsx / LoginLoadingPage.tsx / ForgotPassword.tsx / NotFoundPage.tsx
│   ├── AcceptInvitePage.tsx  # Rota pública para convites
│   ├── enterprises/
│   │   └── EnterprisesList.tsx
│   └── backoffice/
│       ├── BackofficeHome.tsx
│       ├── EmployeesList.tsx
│       ├── TasksPage.tsx
│       └── employee/EmployeeProfilePage.tsx
│
├── components/               # Organizados por domínio
│   ├── enterprise/           # create/ (secções + schema Zod), edit/ (cards), Create/View drawers
│   ├── tasks/                # TasksList, TaskFormDrawer, TaskDetailDrawer
│   ├── construction/         # InvoicePreviewModal + regras da fatura (ver nota abaixo)
│   ├── employees/            # CreateEmployeeDrawer, EmployeeContextMenu
│   ├── profile/              # MyProfileModal, ProfileView, ProfileDrawer, seeprofile/EmployeeMiniCard
│   ├── invites/               # InvitesDrawer
│   ├── location/             # MapLocationPickerDrawer
│   ├── image/                # AuthenticatedImage
│   ├── upload/                # MediaUploadsSection
│   └── common/                # ListActions (coluna de ações das listas), Label, ErrorBoundary, ErrorDisplay, CountryDistrictSelect, BlueprintCard
│
├── services/                 # Chamadas à API por domínio
│   ├── authService.ts / profileService.ts / adminService.ts
│   ├── enterpriseService.ts / locationService.ts
│   └── general/notificationService.tsx
│
├── hooks/
│   ├── useAuth.ts
│   └── useApiCall.ts / useErrorHandler.ts
│
├── errors/
│   ├── error.types.ts
│   ├── errorMessages.ts      # Mapeamento errorCode → mensagem
│   └── errorHandler.ts       # ErrorHandler.handle() centralizado
│
├── types/
│   └── profile.ts
│
├── config/
│   ├── entityColors.ts       # `IND` — espelho em JS das vars `--ind-*` (só para casos que precisam do hex, ex. `stroke` de SVG)
│   └── pagination.ts         # DEFAULT_PAGE_SIZE=10, PAGE_SIZE_OPTIONS
│
└── utils/
    ├── profile.ts
    ├── formatters.ts
    └── apiError.ts
```

This is a scoped copy of Property-Management's Backoffice — no property listings, no buildings, no portal-related code. See [[../../../../docs/architecture.md]] for what was kept.

---

## Rotas

```
/                      → redirect para /login
/login                 → Login (pública)
/loading               → LoginLoadingPage (troca credenciais → user info)
/forgot-password       → ForgotPassword (pública)
/accept-invite         → Aceitar convite (pública)
/backoffice/*          → Secção admin (PrivateRoute)
  /backoffice/                                              → BackofficeHome
  /backoffice/funcionarios                                  → EmployeesList
  /backoffice/funcionarios/:id                               → EmployeeProfilePage
  /backoffice/empreendimentos                                → EnterprisesList
  /backoffice/tasks                                          → TasksPage
```

`PrivateRoute` (`src/PrivateRoute.tsx`) verifica o `user` do `AuthContext` — sem sessão redireciona para `/login`.

> ⚠️ **Orçamento de obra — frontend por construir.** O backend passou (migração `V15`) da
> hierarquia de dois níveis etapa → sub-etapa para uma **árvore** de rubricas
> (`/construction-budget/**`, ver [[../../../../docs/api.md]]). As três páginas encadeadas, os
> respetivos drawers, o `constructionService.ts` e o `types/construction.ts` foram removidos
> por terem ficado a apontar para endpoints que já não existem. Sobreviveram intactos, para
> reaproveitar: `components/construction/InvoicePreviewModal.tsx` e o `validateInvoiceFile`
> em `constructionFormSchemas.ts` — as regras da fatura não mudaram (PDF/JPEG/PNG, 25 MB,
> signed URL). O link "Gerir fases de construção" foi retirado do rodapé do
> `EnterpriseViewDrawer` e volta a entrar com a nova página.

---

## Autenticação

1. `POST /auth/login` no backend (que troca as credenciais com o Supabase) → o backend devolve os dados do utilizador e define cookies HttpOnly (`access_token`, `refresh_token`)
2. Estado do utilizador guardado em `sessionStorage` (`session_user`) via `src/services/authService.ts`
3. `GET /auth/me` — usado para refrescar a foto assinada após um refresh de token (evento `auth:refresh-success`)
4. Em 401, `src/api.ts` tenta um refresh automático (`POST /auth/refresh`) antes de redirecionar para `/login`

Funções em `src/services/authService.ts`: `login()`, `logout()`, `fetchMe()`, `loadUser()`, `saveUser()`, `clearUser()`.

---

## Cliente HTTP (`src/api.ts`)

Instância Axios única — todos os serviços usam esta (não existe uma segunda instância `axios.config.ts`, foi removida por ser código morto no projeto original).

Comportamento automático:
- `withCredentials: true` — envia sempre os cookies HttpOnly
- Remove `Content-Type` automaticamente em `FormData` (uploads multipart)
- Define `Accept` apropriado em respostas blob (downloads)
- Refresh automático em 401 (fila de pedidos enquanto o refresh está em curso)
- Notificações automáticas de erro via `notificationService`

---

## Padrões de componentes

### Drawers
Operações de criar/editar/ver usam `<Drawer>` do Ant Design em vez de páginas separadas (ex: `CreateEnterpriseDrawer`, `EnterpriseViewDrawer`, `TaskFormDrawer`).

### Formulários
- React Hook Form + Zod schemas (`enterpriseFormSchema.ts`)
- Formulário de criação de projeto dividido em secções (`BasicInfoSection`, `EnterpriseLocationSection`, `EnterpriseMediaSection`, `FinancialSection`, `TimelineMetricsSection`)

### Paginação com Spring
As respostas paginadas do backend seguem o formato Spring:
```ts
type WrappedPageResponse<T> = { content: T[]; page: SpringPageMeta };
type SpringPageMeta = { size: number; number: number; totalElements: number; totalPages: number };
```

### Imagens autenticadas
`AuthenticatedImage` (`src/components/image/`) — componente que carrega imagens de endpoints protegidos via Axios (blob → `URL.createObjectURL`). URLs assinadas do Supabase são carregadas diretamente sem token adicional.

### useApiCall
`src/hooks/useApiCall.ts` — hook genérico para executar chamadas API com gestão de `loading`/`error` e tratamento centralizado de erros.

---

## Tratamento de erros

`ErrorHandler.handle(error, config)` em `src/errors/errorHandler.ts`:
- Erros Axios: lê `errorCode` da resposta e mapeia para mensagem em `src/errors/errorMessages.ts`
- Erros de validação com campos múltiplos: lista todos os campos em notificação
- Exibe toast via `notificationService` (Ant Design `notification`)
- Config opcional: `showNotification`, `customMessage`, `logToConsole`

Ver [[../../../../docs/skills/frontend/skill-frontend-error-handling]].

---

## Design tokens

Sistema visual **Industry** (steel-blue "blueprint"): as variáveis `--ind-*` em `index.css` são a fonte de verdade, `theme.ts` espelha-as para os componentes Ant Design. `config/entityColors.ts` exporta só `IND`, para o caso pontual que precisa mesmo do hex em JS.

Antes de hardcodar uma cor nova, ver [[../../../../docs/skills/references/frontend-visual-consistency.md]].

### Listas — coluna de ações

Usa sempre `components/common/ListActions.tsx` (`ListActions` + `ListActionPrimary`/`ListActionSecondary`/`ListActionDanger`) em vez de estilizar botões por ficheiro. Confirmação de ações destrutivas via `useConfirm()` (`context/ConfirmDialogContext`), não `Popconfirm`. Ver [[../../../../docs/skills/references/design/backoffice-tables-and-lists.md]].
