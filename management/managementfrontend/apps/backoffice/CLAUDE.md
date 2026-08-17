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
│   └── AppLayout.tsx         # Layout principal (nav: Home, Projetos, Tarefas, Equipa) + ⚙️ Definições → Fornecedores
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
│       ├── employee/EmployeeProfilePage.tsx
│       ├── enterprise/ConstructionBudgetPage.tsx
│       └── enterprise/EnterpriseInvoicesPage.tsx
│
├── components/               # Organizados por domínio
│   ├── enterprise/           # create/ (secções + schema Zod), edit/ (cards), Create/View drawers
│   ├── tasks/                # TasksList, TaskFormDrawer, TaskDetailDrawer
│   ├── budget/               # Árvore de orçamento: drawers, modal de importação, utils
│   ├── invoices/             # InvoicesList, InvoiceUploadDrawer (2 fases), InvoiceDetailDrawer, BudgetItemPickerModal
│   ├── suppliers/            # SuppliersDrawer (catálogo NIF → nome da empresa)
│   ├── construction/         # InvoicePreviewModal + regras da fatura (reaproveitados)
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
│   ├── enterpriseService.ts / budgetService.ts / locationService.ts / invoiceService.ts
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
│   ├── profile.ts
│   ├── budget.ts
│   └── invoice.ts
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
  /backoffice/empreendimentos/:enterpriseId/budget            → ConstructionBudgetPage
  /backoffice/empreendimentos/:enterpriseId/invoices           → EnterpriseInvoicesPage
  /backoffice/tasks                                          → TasksPage
```

`PrivateRoute` (`src/PrivateRoute.tsx`) verifica o `user` do `AuthContext` — sem sessão redireciona para `/login`.

### Orçamento de obra

`/backoffice/empreendimentos/:enterpriseId/budget` → `ConstructionBudgetPage`. Substituiu as
três páginas encadeadas de etapa → sub-etapa → despesa, que foram removidas com a migração
`V15` do backend para uma **árvore** de rubricas (`/construction-budget/**`, ver
[[../../../../docs/api.md]]).

- A árvore vem **toda numa chamada** — os agregados de cada nó são a soma da sua sub-árvore,
  por isso não há carregamento por nível. Ao abrir, só os capítulos ficam expandidos.
- Três tipos de linha (`rowKind`) distinguidos por **tipografia, não por cor**: `HEADING` em
  maiúsculas sem colunas numéricas, `NOTE` em itálico esbatido, `ITEM` normal. A tag
  "alternativa" marca as rubricas sem índice que têm preço — a cor fica reservada para
  problemas de dinheiro (`overBudget`, `budgetMismatch`).
- `components/budget/` — os drawers (despesas 900, detalhe 480, formulário 600, datas 480), o
  modal de importação, e `budgetTree.ts` com os utilitários da árvore.
- Reaproveitados do domínio anterior: `components/construction/InvoicePreviewModal.tsx` e o
  `validateInvoiceFile` em `constructionFormSchemas.ts` — as regras da fatura não mudaram
  (PDF/JPEG/PNG, 25 MB, signed URL).

### Faturas de obra

`/backoffice/empreendimentos/:enterpriseId/invoices` → `EnterpriseInvoicesPage`. A caixa de
entrada das faturas — o documento é registado aqui, a rubrica escolhe-se a seguir através do
`BudgetItemPickerModal` (ver [[../../../../docs/api.md]], secção "Faturas de obra").

- `components/invoices/InvoiceUploadDrawer.tsx` — carregamento em massa, em **duas fases**:
  "Enviar" chama `POST /preview` por ficheiro (lê o QR, verifica duplicados, não grava nada);
  só depois de rever o resultado é que "Guardar" grava a sério. Duplicadas — quer apanhadas
  pelo servidor, quer pelo checksum calculado no próprio browser contra o resto do lote — são
  excluídas do "Guardar" sozinhas, sem ação manual. Fecha-se sozinho quando tudo o que tentou
  guardar teve sucesso.
- `components/invoices/InvoiceDetailDrawer.tsx` — o detalhe e a correção manual. Quando falta a
  data ou o total (`needsReview`), a drawer alarga e mostra o documento **ao lado** dos campos,
  para preencher a olhar para ele sem abrir/fechar um modal a cada correção; caso contrário fica
  na largura normal e o documento só se vê a pedido (`components/construction/InvoicePreviewModal.tsx`,
  que tem os únicos botões de zoom — só para imagem, um PDF já traz o zoom próprio do
  visualizador do browser). Se a correção colidir com uma fatura já registada, oferece logo um
  confirm para apagar esta (repetida) — ficheiro, miniatura e linha.
- O preenchimento à mão está desenhado para o caso `needsReview`: a data e o total sobem para
  cima de tudo com um aviso a dizer o que falta (live region, muda para "Está preenchido"), e o
  **tipo do documento** ("FT", "FR", …) é uma lista já pré-selecionada com o mais usado no
  projeto (`FT` quando ainda não há faturas) em vez de texto a escrever —
  `components/invoices/invoiceNumber.ts` parte e junta `invoiceNumber` ("FT 2026/114" ↔ tipo +
  série/número) e calcula a sugestão a partir das faturas já carregadas na página. Escrever o número inteiro na caixa da série também funciona:
  o prefixo salta sozinho para a lista.
- Trocar de fatura sem fechar a drawer (clicar noutra linha da lista) não a desmonta — só muda
  o `invoiceId`. `fetchInvoice()` limpa o estado antes do pedido, de propósito: sem isso a
  fatura anterior ficava visível (e a decidir se a pré-visualização abre) até a resposta nova
  chegar.
- Seleção em bloco (`selectedIds`, `InvoicesList` com `rowSelection`) alimenta duas ações na
  barra de filtros: "Associar N à mesma rubrica" (abre o `BudgetItemPickerModal`) e "Marcar N
  como enviadas à contabilidade" (só `ADMIN`, só marca — nunca desmarca em bloco). As duas
  chamam a API sequencialmente, uma fatura de cada vez, para um erro a meio deixar as
  anteriores já gravadas em vez de um estado indefinido.

### Fornecedores

`components/suppliers/SuppliersDrawer.tsx`, aberta pelo ⚙️ **Definições** no cabeçalho
(`AppLayout`). O QR da AT traz o NIF do emitente mas nunca o nome da empresa.

Dois **separadores** com o número na etiqueta, não duas secções empilhadas — são duas listas sem
limite de tamanho, e quarenta NIFs por identificar empurravam o catálogo para fora do ecrã:
**"Por identificar"** (`GET /suppliers/unknown-nifs`, do mais frequente para o menos, já com o
nome pré-preenchido se alguém o escreveu nalguma fatura) e **"Empresas"** (catálogo, com
pesquisa; o botão "Guardar" de cada linha só aparece com alterações por gravar). Abre no
primeiro separador, ou no segundo quando não há nada por identificar — e não muda de separador
sozinho a meio do trabalho. Gravar um nome preenche as faturas desse NIF que estejam sem nome,
em todos os projetos, e o número aparece na notificação. Só `ADMIN` escreve; `EMPLOYEE` vê.

Como a drawer vive no cabeçalho e a lista de faturas noutra página, gravar dispara o evento de
janela `SUPPLIERS_CHANGED_EVENT` (`"suppliers:changed"`), que a `EnterpriseInvoicesPage` ouve
para recarregar — sem isso os nomes novos só apareciam ao recarregar a página.

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
