# Backoffice — Serviços e Tratamento de Erros

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Complementa [[../../frontend/skill-frontend-error-handling]] (o padrão prescrito) com o estado real do código — drift encontrado → convenção a seguir. Baseado em `api.ts`, `errors/*`, `services/*`, `hooks/useApiCall.ts`. Auditoria 2026-08-05.

## 1. `errorMessages.ts` vs `ErrorCode.java` — paridade quase completa, com 9 buracos reais

O skill de error handling exige espelho 1:1. Estado atual: **347** códigos no `ErrorCode.java`, **335** mapeados em `errorMessages.ts`.

**Códigos do backend sem entrada no frontend (caem na mensagem genérica `DEFAULT`)** — separados pelo que interessa mesmo:

- **Buracos reais** (features que existem no Worksite): `ACTIVITY_001`–`004` (registo de atividade, `ActivityLogController`) e `USER_006`, `USER_025`–`029`. Devem ser adicionados.
- **Inertes** (o código existe no enum do backend mas a feature não foi trazida para o Worksite — não há controller nenhum): `LEAD_001`–`003`, `BANNER_001`–`003`, `COMMISSION_001`/`002`. Não vale a pena mapeá-los no frontend; o que faz sentido é limpá-los do enum do backend.

**Entradas mortas no frontend**: `PAYMENT_006`/`007`/`008` — o enum do backend só vai até `PAYMENT_005`, e nem sequer existe domínio de pagamentos no Worksite. A remover.

> Nota metodológica: `errorMessages.ts:422-432` faz classificação por prefixo (`errorCode.startsWith('USER_02')`, `'MEDIA_0'`, `'DB_00'`). Isso **não** são entradas do mapa — se diffares os dois ficheiros com um grep de `[A-Z]+_[0-9]+`, estes prefixos aparecem como falsos positivos.

**Convenção**: feature nova segue [[../../frontend/skill-frontend-error-handling]] à risca desde o início — o mapeamento do código de erro faz parte do checklist, não é um passo a fazer "depois".

## 2. `ErrorHandler.handle()` ainda é minoria — mas está a crescer

Contagem por padrão de `catch` em chamadas à API (atualizada a 2026-08-05, após a migração das páginas de Construção):

- **`ErrorHandler.handle()`** (o prescrito): **7 ficheiros** — `TasksList.tsx`, `TaskFormDrawer.tsx`, `TaskDetailDrawer.tsx`, `hooks/useApiCall.ts`, e as três páginas de Construção (`ConstructionStagesPage`, `ConstructionSubStagesPage`, `ConstructionExpensesPage`).
- **`message.error('…')` direto do antd, string PT fixa, sem passar pelo mapa de códigos**: **20 ficheiros** — todo o domínio de empreendimentos (`CreateEnterpriseDrawer`, `EnterpriseViewDrawer`, os cinco `edit/Edit*Card`, `EnterprisesList`), os drawers de construção (`ConstructionExpenseUpsertDrawer`), funcionários (`CreateEmployeeDrawer`, `EmployeeContextMenu`, `EmployeesList`), perfil (`MyProfileModal`, `ProfileDrawer`, `ProfileView`), `InvitesDrawer`, `MapLocationPickerDrawer`, `MediaUploadsSection`, `AcceptInvitePage`, `Login`.
- **Silencioso — só `console.error`, o utilizador não vê nada**: **14 ficheiros**, com destaque para `EnterpriseLocationSection.tsx`, `AuthenticatedImage.tsx`, `EmployeeMiniCard.tsx` e vários `edit/Edit*Card`.

> 🐛 **Padrão pior que o silencioso, já corrigido nas páginas de Construção**: `try { … } finally { setLoading(false) }` **sem `catch` nenhum**. O `finally` faz o spinner parar, o que dá a ilusão de sucesso, mas o erro sobe como promise rejeitada não tratada — o utilizador vê uma lista vazia sem qualquer mensagem. Estava em `fetchStages`/`fetchSubStages`/`fetchExpenses` e nos três `handleDelete`. Se vires `try`/`finally` sem `catch` à volta de uma chamada à API, é bug, não estilo.

`api.ts` e `errors/errorHandler.ts` chamam `notificationService` diretamente, o que é correto — são a própria infraestrutura de notificação, não código de feature.

**Convenção**: `ErrorHandler.handle()` em todo o `catch` de chamada à API — não é opcional nem "só para casos complexos". Tarefas e Construção são os exemplos a copiar; `message.error` com string fixa continua a ser o padrão numericamente dominante mas **não** é o padrão a seguir em código novo, porque ignora o `errorCode` que o backend envia.

## 3. Camada de serviços — bem alinhada, com um desvio de forma

Estado real dos 7 serviços em `services/`:

- ✅ **Uma instância Axios partilhada**: todos importam `api` de `@/api` (`adminService`, `authService`, `constructionService`, `enterpriseService`, `locationService`, `profileService`, `taskService`). Não há segunda instância.
- ✅ **Um serviço por domínio** — não existem dois serviços a competir pelo mesmo recurso.
- ✅ **Sem `try/catch` a engolir erros da API**. Os dois `try/catch` do `authService.ts` são legítimos e não são o anti-padrão: `:31-34` (`loadUser`) apanha `JSON.parse` de `sessionStorage`, não uma chamada HTTP; `:103-105` (`logout`) engole deliberadamente a falha para a sessão local ser sempre limpa. O erro de API sobe intacto em todo o lado, por isso o `errorCode` chega ao `ErrorHandler`.
- ⚠️ **Forma de export inconsistente**: `taskService.ts:10` exporta um **objeto único** (`export const taskService = { list, … }`), enquanto os outros seis exportam funções nomeadas — e mesmo entre esses há mistura de `export const` (`constructionService.ts`) e `export async function` (`adminService`, `authService`, `enterpriseService`, `locationService`, `profileService`).

**Convenção**: funções nomeadas (`export async function getX(...)`), uma por operação, sobre a instância `api` partilhada, **sem `try/catch`** — deixar o erro subir intacto até ao `ErrorHandler` no componente. Migrar `taskService` oportunisticamente.

## 4. Toasts: `message.*` (antd) vs `notificationService`

`services/general/notificationService.tsx` é o wrapper próprio à volta do `notification` do antd, e é o que o `ErrorHandler` usa internamente. Fora da infraestrutura, é chamado pelo domínio de tarefas e pelas três páginas de Construção (que trocaram `message.success` por `notificationService.success` na migração de 2026-08-05). Os restantes 20 ficheiros usam `message.*` do antd diretamente para o mesmo tipo de evento ("guardado com sucesso", "erro ao guardar") — a escolha correlaciona com o domínio, não com o tipo de evento.

**Convenção**: `notificationService` como canal único de toasts, porque já centraliza o que o `ErrorHandler` precisa. Migrar oportunisticamente, em conjunto com o ponto 2 (são a mesma migração: quem passa a usar `ErrorHandler.handle()` deixa de precisar do `message.error`).

## Skills relacionadas
- [[../../frontend/skill-frontend-error-handling]] — o padrão prescrito, `ErrorHandler`, `errorMessages.ts`
- [[../code-best-practices]] — regras "sem try/catch nos services", "um ficheiro de serviço por domínio"
- [[backoffice-app-shell-and-auth]] — `api.ts` e o refresh automático em 401
- [[../../backend/skill-add-backend-feature]] — onde os `ErrorCode` do backend são criados
