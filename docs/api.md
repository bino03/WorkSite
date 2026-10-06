# 📡 Referência da API

Base URL (dev): `http://localhost:8080`. Sem `context-path` global e sem `server.port` explícito — todas as rotas são relativas à raiz.

Ver [[security.md]] para as regras de acesso (público vs `ADMIN`/`EMPLOYEE`/autenticado) e [[architecture.md]] para quem consome cada grupo de endpoints.

> **Regra base**: `SecurityConfig` termina com `.anyRequest().authenticated()` — tudo o que não estiver explicitamente `permitAll()` exige autenticação. Onde a tabela diz "autenticado", é este default a atuar (o controller não tem `@PreAuthorize` próprio).

---

## Autenticação (`AuthController`, `/auth`)

| Método | Rota | Acesso |
|---|---|---|
| POST | `/auth/login` | público |
| POST | `/auth/refresh` | público |
| POST | `/auth/logout` | público |
| POST | `/auth/accept-invite` | público — token do email → conta + perfil |
| POST | `/auth/forgot-password` | público — pede o link de recuperação |
| POST | `/auth/reset-password` | público — define a password nova a partir do token |
| GET | `/auth/me` | autenticado |

`POST /auth/accept-invite` fecha o ciclo do `POST /auth/admin/invite`: valida o token contra
`settings.pending_invites`, cria o utilizador no Supabase Auth com a password escolhida, cria o
`worksite.profile` com o **role e telefone que vinham do convite** (não do que o cliente enviar) e
marca o convite como `ACCEPTED`. Devolve `204`, sem sessão — a página manda para o login.

Recusa com `USER_013` (token desconhecido, já usado ou cancelado) e `USER_012` (fora do prazo). O
convite fora do prazo é **marcado** `EXPIRED` ao ser recusado: enquanto ficasse `PENDING`,
continuava a bloquear um convite novo para o mesmo email (`existsByEmailAndStatus`). A mensagem
concreta viaja no campo `message` da resposta — o `AcceptInvitePage` mostra essa, não o mapa
genérico de `errorMessages.ts`.

O utilizador do Supabase é criado antes do perfil e essa chamada não é transacional: se o perfil
falhasse a gravar, ficaria uma conta de auth sem perfil (visível no dashboard do Supabase). A ordem
inversa era pior — um perfil sem conta aparece nas listas do Backoffice como se fosse gente.


### Recuperação de password

Fluxo **próprio**, não o `/auth/v1/recover` do Supabase: assim o email sai pelo SMTP de
`settings.email_providers` — o mesmo do convite — e não pelo do dashboard do Supabase, cujo
template não se controla daqui e cujo SMTP gratuito tem limites de rate apertados.

`POST /auth/forgot-password` resolve o email em `auth.users` (entidade `User`; o email **não** vive
em `worksite.profile`), exige que exista `profile` correspondente, queima os pedidos anteriores
ainda por usar e grava um token novo em `settings.password_reset_tokens` com **1 hora** de prazo.

**Responde `204` exista ou não a conta.** É público e sem autenticação: distinguir os dois casos
transformava-o num verificador de contas. O texto do ecrã já está escrito nesses termos ("se
existir uma conta associada a…"). **Uma falha a enviar o email (sem provedor configurado,
SMTP em baixo) também responde `204`** — fica só no log do servidor; até 2026-09-18 subia como
`400 EMAIL_002` e revelava a conta. Não há limite de tentativas — vale a pena quando isto estiver
exposto fora da rede interna.

`POST /auth/reset-password` valida o token (existe, por usar, dentro do prazo), define a password
via Supabase Admin API (`PUT /auth/v1/admin/users/{id}`), marca `used_at` e escreve
`profile.last_token_reset_at`. Este último passo **derruba as sessões abertas**: o
`AccountLockFilter` recusa qualquer JWT emitido antes desse instante. É o que se espera de uma
recuperação — quem a pediu pode estar a fazê-lo por a conta lhe ter fugido.

Recusa com `USER_013` (token desconhecido ou já usado) e `USER_012` (fora do prazo); a mensagem
concreta viaja no campo `message`. A password mínima são 8 caracteres, igual à do convite.

## Administração de contas (`AdminAuthController`, `/auth/admin`)

Toda a classe está anotada com `@PreAuthorize("hasRole('ADMIN')")`.

| Método | Rota | Acesso |
|---|---|---|
| POST | `/auth/admin/create` | `ADMIN` |
| POST | `/auth/admin/invite` | `ADMIN` |
| GET | `/auth/admin/invites` | `ADMIN` |

## Funcionários (`EmployeesController`, `/employees`)

CRUD sobre `worksite.profile` — não existe entidade `Employee` separada.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/employees` | `ADMIN` ou `EMPLOYEE` |
| GET | `/employees/{id}` | `ADMIN` ou `EMPLOYEE` |
| GET | `/employees/assignable` | `ADMIN` ou `EMPLOYEE` — candidatos a atribuição de tarefas |
| PUT | `/employees/{id}` | `ADMIN` |
| DELETE | `/employees/{id}` | `ADMIN` |
| PATCH | `/employees/{id}/block` | `ADMIN` |
| PATCH | `/employees/{id}/unblock` | `ADMIN` |
| PATCH | `/employees/{id}/role` | `ADMIN` |

Todas as respostas com `EmployeeResponseDTO` trazem `me: boolean` — verdadeiro na linha do
próprio utilizador autenticado. Existe para o frontend não ter de guardar o id da sessão só
para se comparar a cada linha da lista; no Backoffice é o que troca as ações da linha por um
único "Editar perfil".

`DELETE /employees/{id}` recusa o próprio com `USER_036` (`PROFILE_CANNOT_DELETE_SELF`): um
admin a eliminar-se ficava de fora sem ninguém que lhe repusesse a conta. `USER_033`
(`PROFILE_CANNOT_DELETE`) continua a ser o de já eliminado / inexistente.

## Perfil (`ProfileController`, `/profile`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/profile/agents` | autenticado |
| GET | `/profile/myprofile` | autenticado |
| PUT | `/profile/updateNamePhone` | autenticado |
| PUT | `/profile/updateEmail` | autenticado |
| PUT | `/profile/updatePassword` | autenticado |

> 🧹 **Fotos de perfil eliminadas por completo a 2026-09-16** (`V35`): saíram os 4 endpoints de
> foto daqui (`POST/GET/DELETE /{authUserId}/photo`, `POST /photo-url`) e o `PUT
> /employees/{id}/avatar` acima. O substituto são as iniciais do nome — já era o fallback nos 3
> sítios que mostravam a foto. `ProfileDTO`, `AuthResponse.UserData` e `EmployeeResponseDTO` (os
> dois, `dto/employee` e `dto/admin`) perderam o campo `photoUrl`; `Profile`/`profile` perderam as
> colunas `photo_url`/`photo_bucket`/`photo_key`. Os objetos já gravados em
> `private/profilephoto/**` no Supabase Storage não foram apagados — são poucos e apagam-se à mão.

## Projetos / Empreendimentos (`EnterpriseController`, `/enterprises`)

O domínio "projeto" do Worksite — os nomes `enterprise`/`enterprises` foram mantidos do projeto de origem para minimizar risco de migração.

Desde a `V23`, o projeto tem dois campos que existem **só para a paridade com o Excel da
Vilatro**: `slug` (o nome exato da pasta `Empreendimentos\<Obra>\` no vault, com espaços e
acentos — `"Vila Petrus"`) e `isTest` (`false` por omissão; marca as obras que existem só para
experimentar). Entram no `POST`/`PUT` e no `PATCH /enterprise-relations/{id}/overview`
(desde 2026-09-07 — antes o overview só editava nome/referência/tipo/estado), e saem em todas
as respostas, incluindo a do próprio overview. No `PATCH`, enviar `slug: ""` limpa a coluna; um
`slug` ausente do corpo não lhe toca. O `slug` é **único quando preenchido** — o segundo
projeto com o mesmo slug é recusado com `ENT_032`, porque dois projetos a apontar para a mesma
pasta tornariam ambígua qualquer importação ou exportação. Ver [[excel-parity.md]] §2.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/enterprises` | autenticado |
| GET | `/enterprises/active` | autenticado |
| GET | `/enterprises/basic` | autenticado |
| GET | `/enterprises/search` | autenticado |
| GET | `/enterprises/{id}` | autenticado |
| GET | `/enterprises/{id}/basic` | autenticado |
| POST | `/enterprises` | `ADMIN` (multipart) |
| PUT | `/enterprises/{id}` | `ADMIN` |
| DELETE | `/enterprises/{id}` | `ADMIN` |
| POST | `/enterprises/{id}/addPhotos` | `ADMIN` |
| POST | `/enterprises/{id}/photos/banner` | `ADMIN` |
| DELETE | `/enterprises/{id}/photos/banner` | `ADMIN` |
| PATCH | `/enterprises/{id}/media/{mediaId}` | `ADMIN` |
| DELETE | `/enterprises/{id}/media/{mediaId}` | `ADMIN` |

> 🧹 **Escrita restringida a `ADMIN` a 2026-09-22** — até aí, `EnterpriseController` e
> `EntrepriseRelationsController` eram os únicos controllers sensíveis da app sem nenhum
> `@PreAuthorize`: qualquer `EMPLOYEE` podia criar, editar e **apagar** um projeto inteiro. A
> leitura (`GET`) continua aberta a `ADMIN`/`EMPLOYEE`. Ver `notes/roadmap/pre-deploy-security.md`.

> O `GET /enterprises` (`EnterpriseListDTO`) traz `budgetTotal` além do `totalInvestment`: é o
> mesmo número que o cabeçalho da página do orçamento (`BudgetTreeDTO.budgetTotal`, mesma regra
> de rollup — `ConstructionBudgetItemService.budgetTotalsByEnterprise`), e é o que a lista de
> projetos mostra como "Investimento" desde 2026-09-18. O `totalInvestment` escrito à mão na obra
> divergia do orçamento importado; fica só no cartão Financeiro da edição. `null` numa obra sem
> rubricas.
>
> Desde 2026-09-24, o mesmo DTO traz também `lots` (`BudgetLotOptionDTO[]`, só `id`+`name`, sem os
> números do orçamento) — vazio (nunca `null`) numa obra sem lotes. É o que alimenta o seletor de
> lote no `TransferInvoiceDrawer`: depois de escolher a obra de destino, se ela tiver lotes, escolhe-se
> também um (opcional). Depois de a transferência correr, o lote grava-se com `PATCH /construction-invoices/{id}/budget`
> (ver a tabela de rotas de faturas); sem lote escolhido, a fatura fica sem lote até ser classificada.

### Relações do projeto (`EntrepriseRelationsController`, `/enterprise-relations/{id}`)

| Método | Rota | Acesso |
|---|---|---|
| POST | `/enterprise-relations/{id}/location/upsert` | `ADMIN` |
| DELETE | `/enterprise-relations/{id}/location` | `ADMIN` |
| PATCH | `/enterprise-relations/{id}/overview` | `ADMIN` |
| PATCH | `/enterprise-relations/{id}/dates-areas` | `ADMIN` |
| PATCH | `/enterprise-relations/{id}/finance` | `ADMIN` |

> 🧹 **`constructionCompany`/`architect` mudaram do `finance` para o `overview` a 2026-09-16**
> (`EditOverViewCardDTO`/`FinanceCardDTO`) — o Financeiro ficou só com valores e moeda; construtora e
> arquiteto entram junto do resto dos dados descritivos do projeto.

## Orçamento de Construção (`ConstructionBudgetItemController`, `/construction-budget`)

Árvore de rubricas do orçamento de obra (profundidade livre via `parentId`) — ver
[[database.md]] para o modelo e o significado de `rowKind`. Desde a `V39` um projeto tem **vários orçamentos, um por lote** (edifício): a árvore, a importação e a zona de recuperação são por lote (`budgetId`); a árvore do projeto (`/enterprise/{id}`) e a pesquisa (`/enterprise/{id}/search`) continuam a cobrir a vila inteira.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/construction-budget/enterprise/{enterpriseId}/budgets` | `ADMIN` ou `EMPLOYEE` — os lotes, com `budgetTotal`/`spentTotal`/`itemCount` de cada |
| POST | `/construction-budget/enterprise/{enterpriseId}/budgets` | `ADMIN` — criar lote `{name}` (`BUDGET_016` se o nome se repetir no projeto) |
| PATCH | `/construction-budget/budgets/{budgetId}` | `ADMIN` — renomear/reordenar `{name, sortOrder?}` |
| DELETE | `/construction-budget/budgets/{budgetId}` | `ADMIN` — apaga o lote e as rubricas (hard delete); `BUDGET_017` se alguma tiver despesas |
| GET | `/construction-budget/budgets/{budgetId}/tree` | `ADMIN` ou `EMPLOYEE` — a árvore de um lote com agregados (a página do orçamento) |
| GET | `/construction-budget/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — a vila inteira: as árvores de todos os lotes lado a lado (a exportação e o seletor de rubricas) |
| GET | `/construction-budget/items/{id}` | `ADMIN` ou `EMPLOYEE` — um nó e a sua sub-árvore |
| POST | `/construction-budget/items` | `ADMIN` |
| PUT | `/construction-budget/items/{id}` | `ADMIN` |
| PATCH | `/construction-budget/items/{id}/move?parentId=&sortOrder=` | `ADMIN` — reordenar / mudar de rubrica-mãe. `sortOrder` é a **posição final** entre os irmãos vivos (0 = primeira; omitido = último) — desde 2026-09-17, antes era "antes de quem tiver esse sortOrder" e o "Descer" não mexia |
| DELETE | `/construction-budget/items/{id}` | `ADMIN` — soft delete da sub-árvore (ver abaixo) |
| GET | `/construction-budget/budgets/{budgetId}/deleted` | `ADMIN` — a zona de recuperação do lote |
| PATCH | `/construction-budget/items/{id}/recover` | `ADMIN` — repõe a rubrica e a sub-árvore eliminada junto |
| POST | `/construction-budget/budgets/{budgetId}/import?dryRun=&replace=` | `ADMIN` — multipart `file` (.xlsx), lê a 1.ª folha; `replace` só apaga **este** lote |
| GET | `/construction-budget/enterprise/{enterpriseId}/export/summary` | `ADMIN` ou `EMPLOYEE` — o que a exportação vai escrever (contagens, avisos, nome do ficheiro) |
| GET | `/construction-budget/enterprise/{enterpriseId}/export?sheets=BUDGET,EXPENSES,COMPARISON` | `ADMIN` ou `EMPLOYEE` — o `.xlsx` (binário, `Content-Disposition: attachment`) |
| GET | `/construction-budget/enterprise/{enterpriseId}/export/zip?sheets=…[&budgetId=&docs=&docsBudgetId=&invoiceIds=]` | `ADMIN` ou `EMPLOYEE` — a pasta da obra: `<slug>.zip` com o `.xlsx` e `Faturas/Lançadas/*` (documentos com o nome do vault, §7), em streaming. Com os parâmetros opcionais, o livro de um lote e/ou um recorte dos documentos — e **outro nome** |
| GET | `/construction-budget/budgets/{budgetId}/export?sheets=BUDGET,EXPENSES,COMPARISON` | `ADMIN` ou `EMPLOYEE` — **um lote** em ficheiro próprio: `[TESTE - ]Orçamento - <Obra> - <Lote>.xlsx`, com as mesmas folhas do export da obra recortadas a esse lote |

> 🧹 **`DELETE` passou a soft delete a 2026-09-16** (`V36`, coluna `deleted_at`). Bloqueado com
> `BUDGET_013` se houver despesas em **qualquer** nó da sub-árvore (mover ou apagar as despesas
> primeiro); sem elas, marca `deleted_at` na rubrica e em toda a sub-árvore — nunca a linha em si.
> Uma rubrica eliminada deixa de aparecer em `GET` da árvore/pesquisa, deixa de aceitar despesas
> novas e o seu `code` fica livre para reutilização. A purga real (hard delete, cascata na BD) só
> corre **30 dias depois**, por job agendado (`ConstructionBudgetItemPurgeConfig`, 3h da manhã) — a
> janela é a zona de recuperação. `GET .../deleted` devolve `{id, code, name, rowKind, deletedAt,
> purgeAt}`, mais recente primeiro; `purgeAt = deletedAt + 30 dias`, para o cliente não embutir a
> regra. `PATCH .../recover` volta à mãe original se ela ainda existir e não estiver eliminada, ao
> topo (sem mãe) caso contrário; se o `code` entretanto foi reutilizado por outra rubrica → `BUDGET_014`
> se a rubrica não estava eliminada, `BUDGET_010` (`BUDGET_DUPLICATE_CODE`) se o código colidir.

**Agregados por nó** (nos `GET`): `rolledUpBudget` soma o `totalPrice` das **folhas** da
sub-árvore — somar todos os nós duplicaria, porque o Excel guarda o total do capítulo na
própria linha *e* o detalhe por baixo. `budgetMismatch` fica a `true` quando o total escrito
na linha não bate certo com essa soma, e `budgetVariance` diz de quanto
(`totalPrice − rolledUpBudget`; `null` quando não há os dois lados para comparar). Junta-se
`spentTotal`, `remaining`, `percentSpent`, `overBudget`, `expenseCount`, `ownExpenseCount`,
`missingInvoiceCount`, `pendingAccountantCount` e `pendingAccountantTotal`.

O `spentTotal` desce a árvore ao contrário do orçamento (desde 2026-09-21): uma despesa lançada
numa rubrica **com sub-rubricas** aparece repartida em **partes iguais** por elas — 15 € na `4.3`
mostram 5 € em cada uma de `4.3.1`, `4.3.2` e `4.3.3`, e assim sucessivamente até às folhas. A
despesa fica gravada na `4.3` (`construction_expense` não muda, e o Excel continua a vê-la só na
`4.3`); é só a leitura que reparte, e o `spentTotal` da `4.3` volta a ser a soma das filhas, por
isso nada conta duas vezes. Só rubricas e títulos com rubricas lá dentro recebem parte — as notas
não. A divisão é a 2 casas e a diferença de arredondamento vai toda para a **última** filha
(10 € por 3 → 3,33 / 3,33 / 3,34), para a soma ser exatamente o valor lançado. `expenseCount` e
`ownExpenseCount` **não** se repartem — continuam a contar onde a despesa está. O `GET` de um nó
isolado constrói a árvore inteira por causa disto: a parte que herda dos pais só se conhece a partir
da raiz. (`BudgetSpentDistributionTest`.)

O `BudgetTreeDTO` traz ainda `enterpriseName` — o cabeçalho da página precisa dele e sem isso
seria uma segunda chamada a `/enterprises/{id}` só para o título. Repete os totais do projeto
inteiro e acrescenta `overBudgetCount` / `overBudgetAmount`. Estes contam apenas as rubricas **mais acima** de cada ramo em
derrapagem: se um capítulo passou do orçamento, conta o capítulo e não também cada rubrica
lá dentro — somar os dois contaria a mesma derrapagem duas vezes.

**Datas**: gravar uma rubrica com `startDate`/`endDate` cujos ascendentes não a tenham devolve
`datePropagationHints` em vez de propagar em silêncio; o cliente reenvia com
`propagateStartDate`/`propagateEndDate` a `true` para aplicar.

**Importação**: `dryRun=true` (omissão) devolve a árvore que *seria* criada, com avisos
(índices repetidos, células de texto em colunas numéricas, rubricas sem descrição) e a
reconciliação contra a linha `TOTAL` do Excel — sem gravar nada. Com `dryRun=false` grava, e
exige `replace=true` se o projeto já tiver orçamento. O cabeçalho aceita `Art` **ou** `Rubrica`
na coluna A (desde 2026-09-17 — é o nome que o vault usa e que a exportação escreve), e **as restantes
colunas resolvem-se pelo nome do cabeçalho** (`Descrição`, `Un.`, `Quant`, `Preço Un`, `Preço total`,
`Obs.`), com a posição do orçamento do empreiteiro como fallback só se o cabeçalho não tiver nomes conhecidos (as colunas ausentes num cabeçalho reconhecido ficam vazias) — a "Orçamento inicial" do vault tem só
`Rubrica | Descrição | Preço total`. Ver [[excel-parity.md]] §6.

**Exportação** (2026-09-17, fase 6 lado app → Excel — o contrato é [[excel-parity.md]] §9):
`sheets` é um conjunto de `BUDGET` ("Orçamento inicial"), `EXPENSES` ("Despesas") e
`COMPARISON` ("Orçamento vs Gasto" **+** "Rubricas", gerada sempre). `COMPARISON` arrasta
`EXPENSES` — o painel é todo fórmulas `SUMIF` sobre a `TabelaDespesas`/`TabelaRubricas` e sem
elas dava `#NAME?`. Regras: `BUDGET_026` sem folhas; `BUDGET_027` se a obra não tiver rubricas
vivas e se pedir `BUDGET` ou `COMPARISON` (`EXPENSES` sozinha exporta sempre, mesmo sem faturas);
Uma obra **sem slug fica com um** ao passar por qualquer dos dois endpoints (nome limpo, sufixo numérico
em colisão — [[excel-parity.md]] §2); por isso nenhum deles é `readOnly`. `BUDGET_028` se o POI falhar a escrever. Uma obra `is_test` **exporta** (para se poder testar o
próprio exportador), mas o ficheiro leva o prefixo `TESTE - `. O nome vai em
`filename*=UTF-8''…` porque os slugs têm acentos e espaços — o cliente tem de descodificar esse
parâmetro, não o `filename=` cru. `GET …/export/summary` devolve o `BudgetExportSummaryDTO`
(`fileName`, `hasBudget`, contagens de rubricas/faturas/linhas, `unclassifiedInvoiceCount`,
`manualExpenseCount`, `creditNoteCount`, `partialPaymentCount`, `missingNumberCount`,
`needsReviewCount`, `warnings`, `documents`) — é o passo 2 do modal, antes do download.

**Rate limiting** (`/export`, `/export/zip` e `/budgets/{id}/export`, desde 2026-09-22): por utilizador
autenticado, não por IP — `RATE_LIMIT_EXPORT_USER` pedidos (default 10) por
`RATE_LIMIT_EXPORT_WINDOW_MIN` minutos (default 10); acima disso, `429` com `errorCode: "ERR_011"`.
`/export/summary` e a árvore normal ficam de fora (não fazem streaming pesado nenhum). Ver
`ExportRateLimitFilter`.

**Um lote em ficheiro próprio** (`GET /construction-budget/budgets/{budgetId}/export?sheets=`, desde
2026-10-01): `[TESTE - ]Orçamento - <Obra> - <Lote>.xlsx`. Aceita o **mesmo `sheets`** do export da
obra e as mesmas regras (`COMPARISON` arrasta `EXPENSES`; `BUDGET_EXPORT_NO_SHEETS` sem nenhuma;
`BUDGET_EXPORT_NO_BUDGET` se o lote não tiver rubricas). Não há variante `.zip`: o zip é a pasta da obra.

O ficheiro é uma **fatia** do da obra, não um formato à parte — o modelo carrega-se inteiro e
recorta-se ao lote (`narrowToLot`), por isso o escritor é o mesmo e as folhas não podem divergir
entre os dois exports:

- a folha do orçamento é a do lote, com o nome `Orçamento - <Lote>` (logo reimportá-la para o mesmo
  lote volta a encontrá-la pelo nome);
- na "Despesas" e no painel entram **só as linhas das rubricas desse lote**, mais as faturas **por
  classificar cujo lote (`construction_invoice.budget_id`) é este**. As dos outros lotes e as faturas
  por classificar sem lote ficam de fora, com um aviso que diz quantas linhas caíram, porque o total da
  "Despesas" deixa de ser o da obra. Numa obra de um só lote as por classificar continuam fora, como antes;
- a coluna `Rubrica` **mantém** o prefixo `<Lote> · `, apesar de haver um só lote no ficheiro: é o que
  mantém as linhas inequívocas se o ficheiro for reimportado numa obra com vários lotes (o
  `DespesasExcelImportService` recusa-se a adivinhar o lote) e o que faz as `SUMIFS` do painel bater.
  Por isso o modo "multi-lote" do escritor fica **ligado** (coluna `Lote` na `TabelaRubricas`).

> Exceção: numa obra de **um só lote**, o `load` sai como antes da `V39` — não marca o lote em nada e
> as etiquetas não levam prefixo. Aí o ficheiro do lote mantém-se no modo de um lote (folha
> `Orçamento inicial`, sem coluna `Lote`), coerente com as etiquetas; só o **nome do ficheiro** leva o
> lote. Filtrar pelo nome do lote nesse caso apagaria tudo — ver o ramo em `narrowToLot`.

> ⚠️ Este endpoint **não substitui** o `/enterprise/{id}/export`. Esse é o livro da obra que alimenta o
> vault Excel — um ficheiro por obra, uma folha `Orçamento - <Lote>` por lote ([[excel-parity.md]] §9) —
> e partí-lo em ficheiros obrigaria a mudar o lado do Excel, decisão que se escreve em
> `Vilatro\Decisões.md` e não aqui. O endpoint do lote existe **a par** dele, para o caso de levar um
> lote só (é como os orçamentos circulam com os empreiteiros).

> **Levar o Excel e os PDFs de uma vez** (desde 2026-10-02): o `/export/zip` aceita quatro
> parâmetros **opcionais** — `budgetId` (o livro de **um lote** em vez do da obra), e `docs` +
> `docsBudgetId` + `invoiceIds` (que documentos levar, com os mesmos âmbitos do
> `/construction-invoices/.../documents/zip`). São duas escolhas independentes: o livro e os
> documentos.
>
> ⚠️ **Sem nenhum deles — ou com `docs=ALL` — nada muda**: o pedido segue o caminho de sempre e
> devolve a pasta da obra do vault, `<slug>.zip`, descrita a seguir. **Qualquer outra combinação
> leva sufixo no nome** (`<slug> - Lote 3.zip`, `- Por classificar`, `- N selecionadas`,
> `- Associadas`), porque já não é a pasta do vault e extrair duas coisas diferentes com o mesmo
> nome por cima uma da outra é a maneira de perder a boa. Há teste a fixar as duas vias.
>
> **Onde ficam os documentos**: um zip que leva livro é uma pasta → `Faturas/Lançadas/`; um download
> **só** de documentos é plano → raiz. É a única regra a decorar entre os dois endpoints.

**Zip da pasta da obra** (`GET …/export/zip?sheets=`, desde 2026-09-20): o mesmo `sheets` e as
mesmas regras do `/export`, mas devolve `application/zip` com o nome `[TESTE - ]<slug>.zip` e, na
raiz, `Despesas - <slug>.xlsx` + `Faturas/Lançadas/<ficheiro>` — extrai-se em `Empreendimentos\<slug>\`
e fica a estrutura exata do vault ([[excel-parity.md]] §7). Os nomes dos ficheiros decidem-se em
`InvoiceDocumentsExportService.plan` (dentro da transação): um `original_filename` que **já obedeça**
a `^\d{8}(-\d{8})?_<nº>_<resto>.<ext>` sem espaços (os 206 migrados do vault) mantém-se tal e qual;
os outros (uploads feitos na app) recebem `<aaaammdd>_<nº sanitizado>_<FornecedorCamelCase>[_pN].<ext>`
— sem nº é `SEM-N` + descrição, sem data é a data do upload com aviso, `_pN` vem de `kind=PAGE` +
`page_number`, e um nome repetido fica `_2`, `_3` (nunca sobrepõe; listado nos avisos). Miniaturas e
comprovativos de pagamento (`payment.proof_*`) ficam de fora. Os bytes vêm do Storage **um a um,
enquanto a resposta se escreve** (`StreamingResponseBody`; `spring.mvc.async.request-timeout=10m`) —
um documento que o Storage não devolva não aborta o download: fica de fora e listado em
`Faturas/Lançadas/_EM-FALTA.txt` dentro do zip. `documents` no summary: `{ documentCount,
invoicesWithoutDocument, renamedCount, warnings[] }`.

## Despesas de Construção (`ConstructionExpenseController`, `/construction-expenses`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/construction-expenses/budget-item/{budgetItemId}` | `ADMIN` ou `EMPLOYEE` — lista de uma rubrica |
| GET | `/construction-expenses/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — lista plana do projeto, paginada |
| GET | `/construction-expenses/{id}` | `ADMIN` ou `EMPLOYEE` |
| POST | `/construction-expenses` | `ADMIN` — JSON, sem documento |
| PUT | `/construction-expenses/{id}` | `ADMIN` |
| DELETE | `/construction-expenses/{id}` | `ADMIN` |

**Deixou de haver upload aqui.** O ficheiro, a leitura do QR e o envio para a contabilidade
passaram para `/construction-invoices` (secção seguinte), porque a fatura existe antes de se
saber a que rubrica pertence. O que resta neste controlador são os lançamentos feitos à mão,
sem documento — o corpo é JSON, já não multipart, e os antigos `POST /scan-invoice` e
`PATCH /{id}/accountant` não existem.

Só rubricas com `rowKind = ITEM` aceitam despesas (`EXPENSE_009` caso contrário).

A despesa que nasceu de uma fatura traz o objeto `invoice` (`ExpenseInvoiceRefDTO`) com
fornecedor, número, ATCUD, data, miniatura assinada e estado na contabilidade — incluindo
`sentToAccountantByRole` (`ADMIN`/`EMPLOYEE` em cru, o cliente é que traduz o rótulo). Vem a
`null` quando o lançamento foi feito à mão: é assim que o cliente distingue "não tem fatura"
sem ter de adivinhar por campos vazios. O documento completo pede-se a
`GET /construction-invoices/{id}`, e só quando alguém o abre.

**`expenseDate` é a data da fatura, não a de registo** (`createdAt`). É obrigatória, e é
sobre ela que assentam os filtros e qualquer mapa mensal — sem esta separação, lançar
faturas atrasadas em bloco atirava-as todas para o mês em que foram escritas na app.

A lista plana (`/enterprise/{id}`) é a vista de quem trata da contabilidade e aceita filtros
opcionais e cumuláveis, `Page` com 20 por omissão ordenada por `expenseDate` descendente:

| Parâmetro | Efeito |
|---|---|
| `from` / `to` | intervalo de `expenseDate` (ISO `AAAA-MM-DD`) |
| `sentToAccountant` | `false` → o que falta enviar |
| `hasInvoice` | `false` → lançado sem documento anexado |
| `q` | procura no nome e descrição da despesa e no índice e nome da rubrica |

## Faturas de obra (`ConstructionInvoiceController`, `/construction-invoices`)

A fatura é o **registo**; os ficheiros são 0..N documentos seus e a despesa é a sua afetação a
uma rubrica. Registo e classificação estão separados porque **são momentos diferentes**: quem
chega da obra com quinze faturas carrega-as todas sem decidir nada, e classifica depois. Uma
fatura sem despesa associada (`allocated: false`) é o que está por classificar — é essa a caixa
de entrada.

| Método | Rota | Acesso |
|---|---|---|
| POST | `/construction-invoices/preview?enterpriseId=` | `ADMIN` ou `EMPLOYEE` — lê o QR e verifica duplicados, não grava nada |
| POST | `/construction-invoices?enterpriseId=` | `ADMIN` ou `EMPLOYEE` — multipart `file`; devolve `201` |
| POST | `/construction-invoices/register` | `ADMIN` — regista uma fatura **sem ficheiro** (JSON, não multipart); devolve `201` |
| POST | `/construction-invoices/import-excel?scope=&enterpriseId=&dryRun=` | `ADMIN` — multipart `file` (o `Despesas - <Obra>.xlsx` do vault) + parte `answers` (JSON, opcional). Importa a folha "Despesas"; ver [[#Importar a folha "Despesas" do Excel (fase 6)]] |
| POST | `/construction-invoices/{id}/documents` | `ADMIN` ou `EMPLOYEE` — multipart `file`, **junta** mais um documento; devolve `201` |
| DELETE | `/construction-invoices/{id}/documents/{documentId}` | `ADMIN` — remove **um** documento; devolve `204` |
| GET | `/construction-invoices/unidentified` | `ADMIN` — a quarentena, paginada, mais antigas primeiro |
| GET | `/construction-invoices/company` | `ADMIN` — despesas da empresa, paginadas, mais recentes primeiro |
| GET | `/construction-invoices/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — caixa de entrada, paginada |
| GET | `/construction-invoices/enterprise/{enterpriseId}/documents/summary?scope=&budgetId=` | `ADMIN` ou `EMPLOYEE` — quantos documentos tem o âmbito (`DocumentsExportSummaryDTO`), sem tocar no Storage |
| GET | `/construction-invoices/enterprise/{enterpriseId}/documents/zip?scope=&budgetId=` | `ADMIN` ou `EMPLOYEE` — os PDFs/fotos num `.zip`, em streaming: `[TESTE - ]Faturas - <Obra>[ - <Lote> \| - Por classificar].zip` |
| GET | `/construction-invoices/enterprise/{enterpriseId}/pending-summary[?budgetId=]` | `ADMIN` ou `EMPLOYEE` — `{ count, total }` das faturas por associar (NC não entram); com `budgetId`, só as desse lote: o contador do botão "Faturas" e o cartão "Por classificar" ao lado do "Gasto" no orçamento. Era `pending-count` (só o número) até 2026-09-18 |
| GET | `/construction-invoices/enterprise/{enterpriseId}/outstanding-summary` | `ADMIN` ou `EMPLOYEE` — `{ count, total, withoutTotalCount }` do que **falta pagar** nas faturas por liquidar, com os mesmos filtros da lista (todos os da tabela "Filtros da caixa de entrada", incluindo os da pesquisa avançada; `outstanding` é sempre true). `total` = Σ (total − NC − pago) sobre **todas** as faturas do filtro, não só a página; as sem total contam em `count`/`withoutTotalCount` e valem 0. É o "Falta pagar X" ao lado do filtro "Por liquidar" (2026-09-21) |
| GET | `/construction-invoices/unidentified/outstanding-summary?q=` · `/company/outstanding-summary?q=` | `ADMIN` — o mesmo para a quarentena e as despesas da empresa |
| GET | `/construction-invoices/enterprise/{enterpriseId}/suggestion?supplierNif=` | `ADMIN` ou `EMPLOYEE` — rubrica sugerida por NIF, sem o porquê (o `rubric-suggestion` por fatura veio substituí-lo); `204` sem histórico |
| GET | `/construction-invoices/{id}` | `ADMIN` ou `EMPLOYEE` — única resposta com `fileUrl` |
| PUT | `/construction-invoices/{id}` | `ADMIN` ou `EMPLOYEE` — correção manual |
| POST | `/construction-invoices/{id}/file` | `ADMIN` ou `EMPLOYEE` — substitui o ficheiro, relê o QR |
| POST | `/construction-invoices/{id}/rescan` | `ADMIN` ou `EMPLOYEE` — relê o QR do ficheiro arquivado e repõe os campos fiscais |
| GET | `/construction-invoices/{id}/rubric-suggestion` | `ADMIN` ou `EMPLOYEE` — rubrica sugerida **com o porquê**; `204` sem histórico |
| PATCH | `/construction-invoices/{id}/budget?budgetId=` | `ADMIN` — define o lote da fatura antes de a classificar; sem `budgetId` limpa-o. Recusa com `INVOICE_052` se já tiver despesas (exceto para fixar o lote que elas já têm) |
| PATCH | `/construction-invoices/{id}/allocate?budgetItemId=` | `ADMIN` — liga a **uma** rubrica, cria o lançamento. Obra de vários lotes sem lote na fatura → `INVOICE_051`; rubrica de outro lote → `INVOICE_050` |
| POST | `/construction-invoices/{id}/expenses/split` | `ADMIN` — reparte por N rubricas, substituindo a repartição atual |
| POST | `/construction-invoices/batch-allocate` | `ADMIN` — N faturas → 1 rubrica, melhor esforço (resultado por fatura) |
| DELETE | `/construction-invoices/{id}/allocate` | `ADMIN` — desfaz **todas** as linhas, devolve à caixa de entrada |
| PATCH | `/construction-invoices/{id}/accountant?sent=` | `ADMIN` — marca/desmarca enviada ao contabilista |
| DELETE | `/construction-invoices/{id}` | `ADMIN` — apaga fatura, ficheiro, miniatura e lançamento |
| POST | `/construction-invoices/{id}/payments` | `ADMIN` — marca **uma** fatura como paga (multipart: `payment` JSON + `proof` opcional); `201` |
| POST | `/construction-invoices/payments` | `ADMIN` — pagamento **agregado** de N faturas (multipart: `payment` JSON com `invoiceIds[]` + `proof` opcional); `201` se bater, `200` (nada gravado) se o valor não bater |
| DELETE | `/construction-invoices/payments/{paymentId}` | `ADMIN` — anula um pagamento, repõe as faturas; `204` |
| GET | `/construction-invoices/{id}/credit-notes/split-preview?amount=` | `ADMIN` — proposta de repartição negativa de uma NC sobre esta fatura, na proporção das despesas dela; nada gravado |
| POST | `/construction-invoices/{id}/credit-notes` | `ADMIN` — regista uma **nota de crédito** a partir desta fatura (JSON: valor, nº, data, NIF, `expenses[]` confirmadas); `201` |

As três listas (`/unidentified`, `/company`, `/enterprise/{id}`) aceitam `?outstanding=true`
para filtrar só as **por liquidar** — o filtro é aplicado por subquery no servidor, para a
paginação continuar certa. Ver "Pagamentos".

Não há endpoint de lote **de propósito**: o cliente chama o `POST` uma vez por ficheiro
largado, para que cada um tenha o seu resultado e um QR ilegível não estrague os restantes.

O carregamento em massa do Backoffice é em **duas fases**, e usa dois endpoints diferentes de
propósito: "Enviar" chama `POST /preview` para cada ficheiro — lê o QR e verifica duplicados,
mas não toca no Storage nem na base de dados — e só depois de rever o resultado é que
"Guardar" chama o `POST /` de sempre, um por ficheiro. `POST /preview` corre em transação só de
leitura e pode chamar-se quantas vezes for preciso sem custar nada; quem decide gravar é sempre
o `POST /` (que relê e revalida tudo de novo — nunca confia cegamente no que o preview mostrou,
porque outra fatura pode ter entrado entretanto).

O ficheiro sobe sempre por comprimir. O servidor lê o QR da AT a partir do original e só
comprime depois — e só quando a leitura teve sucesso — para guardar em Storage; ver
`InvoiceCompressionService`. Sem QR legível o original fica intacto, para a melhor hipótese
possível numa revisão manual ou num `/rescan` mais tarde.

O upload **nunca falha por dados em falta**. Sem QR legível a fatura entra na mesma, com
`needsReview: true`, e alguém completa os campos depois. A obrigatoriedade de data e total só
aparece no `allocate` (`INVOICE_006`), porque é a despesa que os exige.

`needsReview` e `allocated` são **derivados**, não colunas: com uma fatura por rubrica, um
estado guardado só arriscava ficar dessincronizado.

`thumbnailUrl` vem em todas as respostas; `fileUrl` e o `documents[]` completo só no detalhe (`GET /{id}`). Os campos soltos de ficheiro (`fileUrl`, `thumbnailUrl`, `originalFilename`, `mimeType`, `sizeBytes`, `uploadedBy/Name/At`) **continuam a existir** na resposta e descrevem o **primeiro** documento — é o que mantém o Backoffice a funcionar sem reescrita. Assinar o
documento completo de cada linha de uma lista de 20 seria trabalho deitado fora — quase
nenhum é aberto. Ambas são signed URLs geradas na leitura; a chave de storage nunca sai daqui.

Resposta do upload e do `POST /{id}/file`:

```jsonc
{
  "invoice": { /* ConstructionInvoiceResponseDTO — existe sempre, mesmo sem QR */ },
  "qrRead": true,
  "duplicates": [
    { "invoiceId": "…", "supplierName": "Betão Liz", "invoiceNumber": "FT 2026/114",
      "invoiceDate": "2026-01-15", "totalAmount": 14760.00,
      "budgetItemCode": "4.2.1", "budgetItemName": "Sapatas Isoladas" }
  ],
  "warnings": []
}
```

### A fatura é o registo, não o ficheiro

Desde a fase 1 da paridade com o Excel (`V23`–`V29`), a fatura **é** o registo e os ficheiros
são 0..N documentos seus (`documents[]` na resposta). Isto desbloqueia os dois casos que o
vault da Vilatro tem todos os dias e que eram impossíveis enquanto a fatura *era* um ficheiro:
a fatura que ainda não tem documento nenhum, e a que tem mais do que um (a foto tirada na obra
**e** o PDF do fornecedor). Ver [[database.md]] e [[faturas-modelo-alvo.md]] §2.2.

Três campos novos comandam o comportamento:

| Campo | Valores | O que decide |
|---|---|---|
| `scope` | `PROJECT` · `COMPANY` · `UNIDENTIFIED` | Onde a fatura vive. `PROJECT` **exige** `enterpriseId`; os outros dois **proíbem-no** (daí `enterpriseId` ser agora nullable). Uma fatura fora de `PROJECT` não pode ser associada a uma rubrica (`INVOICE_013`) |
| `documentStatus` | `ARCHIVED` · `MISSING` · `TO_PRINT` · `TO_REQUEST` | O que se passa com o papel. **Segue o papel**: passa a `ARCHIVED` ao juntar um documento, volta a `MISSING` ao largar todos — por isso `POST /register` recusa `ARCHIVED` (`INVOICE_017`) |
| `documentType` | `INVOICE` · `CREDIT_NOTE` | Uma nota de crédito tem de apontar (`relatedInvoiceId`) para a fatura que corrige. A lógica entrou na fase 3: NC ligada a uma fatura lançada, líquido = total − Σ NC, despesas negativas (ver "Notas de crédito") |

`POST /register` é a única entrada de fatura que **não é multipart**: recebe JSON
(`InvoiceRegisterDTO` — `scope` obrigatório, `enterpriseId`, campos fiscais, `description`,
`documentStatus`, `possibleEnterprises`, `askWhom`, `notes`) e devolve o
`ConstructionInvoiceResponseDTO`. O par `scope`/`enterpriseId` é validado **no serviço** e não
só pelo check da base de dados, para o erro sair com mensagem legível (`INVOICE_014`,
`INVOICE_015`, `INVOICE_016`) em vez de uma violação de constraint.

`POST /{id}/documents` acrescenta e `DELETE /{id}/documents/{documentId}` (só `ADMIN`) tira **um** —
apaga o ficheiro e a miniatura do Storage, e se era o último documento a fatura volta a
`documentStatus = MISSING`. `POST /{id}/file` **substitui** (larga todos os documentos e
põe um só no lugar — comportamento antigo, mantido). No `documents`, se o QR do ficheiro novo
trouxer dados que divergem dos que a fatura já tem, o resultado traz `qrDivergences` e **nada é
sobreposto**: só os campos vazios são preenchidos.

`GET /unidentified` e `GET /company` são **só `ADMIN`** (o `NavLink` do Backoffice também tem
gate, mas o gate real é este). A quarentena ordena por `createdAt` **ascendente** por omissão —
quanto mais tempo lá está, mais urgente é — e mostra `possibleEnterprises` e `askWhom`, as duas
notas em texto livre de quem recebeu a fatura sem saber de quem era.

### Duplicados — bloqueio, em três chaves

Um duplicado é **recusado**, não avisado. `duplicates` vem vazio no upload e no
`POST /{id}/file` justamente por isso: se houvesse um, o pedido não chegava a passar.
Só o `POST /{id}/rescan` ainda o preenche.

A verificação corre com **três chaves**, por ordem de certeza — a mais forte primeiro —
porque servem momentos e falhas diferentes:

| Chave | Quando apanha | Erro |
|---|---|---|
| `checksumSha256` | o ficheiro é byte-a-byte igual a um já carregado — não depende de nada ter sido lido | `INVOICE_012` |
| `invoiceAtcud` | o QR foi lido — é o identificador que a AT atribui ao documento | `INVOICE_010` |
| (`supplierNif`, `invoiceNumber`) | o QR falhou e alguém completou os campos à mão | `INVOICE_011` |

**A unicidade é global desde a `V29`** — não por projeto, como era até à fase 1. Os índices
`uq_invoice_atcud`, `uq_invoice_nif_number` e `uq_invoice_document_checksum` cobrem toda a base
de dados, por isso um documento já carregado numa obra é recusado noutra, na quarentena e nas
despesas da empresa. A mensagem de erro **nomeia onde está a primeira** ("já existe na obra
Vila Petrus", "nas despesas da empresa", "nas faturas por identificar") — sem isso, o bloqueio
seria um beco sem saída para quem não tem acesso à obra onde a fatura ficou. Ver
[[excel-parity.md]] §5.

O checksum existe para o caso em que **nenhuma das outras duas serve**: a mesma foto
carregada duas vezes, sem QR legível em nenhuma das cópias — sem ATCUD nem NIF/número por
onde comparar, o ficheiro entrava sempre. Calculado (SHA-256, hex) a partir dos bytes já em
memória no upload e no `POST /{id}/file`, não custa uma leitura extra.

A segunda chave existe porque a primeira só funciona quando já não é precisa: sem QR legível
não há ATCUD. A terceira existe porque a segunda só funciona quando o ficheiro já mudou de
bytes entre uma cópia e outra (duas fotos diferentes do mesmo papel) — quem completa uma
fatura "por rever" escreve o NIF e o número, raramente o ATCUD, e o mesmo fornecedor não
emite dois documentos com o mesmo número.

O número **não** é comparado por igualdade exata: cada software de faturação formata-o à sua
maneira ("FT 2024/123", "FT2024-123", "ft.2024.123", …), e é escrito à mão dos dois lados da
comparação. `ConstructionInvoiceService.normalizeDocumentNumber` reduz a maiúsculas e só
letras/dígitos antes de comparar — `findByEnterpriseAndSupplierNif` filtra só por fornecedor,
e é o serviço que normaliza e compara em memória. Não se aplica ao ATCUD nem ao checksum:
nenhum dos dois é escrito à mão.

Por isso a verificação por ATCUD e por (NIF, número) corre **no `PUT /{id}` também**, e não só
no carregamento: é na correção manual que uma fatura sem QR ganha identidade pela primeira
vez. Sem isso, completar à mão duas fotografias da mesma fatura criava dois lançamentos
iguais no orçamento. No `PUT` só corre quando a **identidade muda** (ATCUD, NIF ou número) —
de outro modo um duplicado já existente na base bloqueava qualquer edição, incluindo mexer só
nas notas. O checksum não entra no `PUT`: a edição manual não troca o ficheiro, só
`POST /{id}/file` o faz.

Quando o `PUT` recusa por duplicado, o Backoffice não mostra só o toast genérico: oferece logo
um confirm para apagar **esta** fatura (ficheiro e miniatura do Storage, e a linha) — é o caso
normal de quem está a completar uma fatura "por rever" à mão e só aí percebe que já a tinha
carregado antes.

O ATCUD, o par (NIF, número) e o checksum têm ainda uma garantia ao nível da base — os índices
únicos parciais `uq_invoice_atcud`, `uq_invoice_nif_number` (`V29`, globais) e
`uq_invoice_document_checksum` (`V24`, na tabela do documento). A verificação no serviço é
SELECT-depois-INSERT em transações separadas, e o cliente carrega com três pedidos em paralelo —
dois ficheiros iguais em voo ao mesmo tempo passavam os dois. Aconteceu com o ATCUD: duas linhas
gravadas com 20 ms de diferença. Os índices fecham essa janela; o serviço continua a existir para
dar a mensagem legível em vez de uma violação de constraint.

Quando a janela fecha mesmo assim (dois pedidos em voo, nenhum vê o `INSERT` do outro), o
Postgres recusa com um `unique_violation` — e é o `GlobalExceptionHandler` que traduz isso de
volta para a mesma mensagem que o serviço teria dado no caminho normal: reconhece os três índices
pelo nome na exceção do driver (`uq_invoice_atcud` → `INVOICE_010`, `uq_invoice_nif_number` →
`INVOICE_011`, `uq_invoice_document_checksum` → `INVOICE_012`). Sem essa tradução por índice, cai
no genérico `DATABASE_CONSTRAINT_VIOLATION` (`DB_003`) — e foi o que aconteceu, em silêncio, entre
a `V25`/`V29` e 2026-09-18: o handler ainda procurava os nomes antigos por projeto
(`uq_invoice_enterprise_atcud`, `uq_invoice_enterprise_checksum`), já largados. O Backoffice ainda
reduz a corrida do lado do cliente: calcula o SHA-256 de cada ficheiro no browser antes do "Enviar"
e recusa localmente um que já esteja reivindicado por outro do mesmo lote, sem gastar pedido nenhum
ao servidor — mas isso só apanha duplicados dentro do próprio lote, nunca substitui a garantia da
base.

Em `POST /{id}/documents` a procura por checksum é global e **não exclui a própria fatura**: juntar
outra vez um ficheiro que essa fatura já tem dá `INVOICE_012` com a mensagem "Este ficheiro já está
anexado a esta fatura", antes de qualquer escrita no Storage. Até 2026-09-18 excluía-a, o ficheiro
subia ao bucket e só o índice da `V24` o recusava — como `DB_003`, com o ficheiro já órfão.

Filtros da caixa de entrada, todos opcionais e cumuláveis. `Page` com 20 por omissão,
ordenada por `uploadedAt` descendente:

| Parâmetro | Efeito |
|---|---|
| `allocated` | `false` → o que está por classificar (o que o cliente abre por omissão) |
| `needsReview` | `true` → falta a data ou o total |
| `sentToAccountant` | `false` → o que falta enviar |
| `atChapter` | `true` → pelo menos uma linha de repartição aponta para uma rubrica que ainda tem filhas `ITEM` (a mesma regra do `chapter` do ecrã "Classificar" — ver `BudgetItemSearchResultDTO`). Só existe aqui: as outras duas listas não têm rubrica. **O Backoffice deixou de o usar** a 2026-09-21 (o botão "Ao capítulo" saiu: com o gasto de uma rubrica-pai repartido pelas filhas, lançar ao capítulo deixou de esconder dinheiro); o parâmetro fica na API, a tag "capítulo" na coluna Rubrica também |
| `from` / `to` | intervalo de `invoiceDate` (ISO `AAAA-MM-DD`) |
| `q` | procura no nome e NIF do fornecedor, número, ATCUD, nome do ficheiro e notas |
| `supplierNif` | NIF exato do fornecedor (a pesquisa avançada escolhe-o do catálogo `/suppliers`) |
| `documentType` | `INVOICE` / `CREDIT_NOTE` |
| `documentStatus` | `ARCHIVED` / `MISSING` / `TO_PRINT` / `TO_REQUEST` |
| `paymentStatus` | `UNPAID` (nada pago) / `PARTIAL` / `PAID` — mais fino do que `outstanding`, que junta os dois primeiros; a mesma conta (pago + NC vs. total) |
| `allocationStatus` | `NONE` / `PROVISIONAL` (com linhas, sem total) / `PARTIAL` (Σ linhas ≠ total) / `COMPLETE` — a regra do `allocationStatus` da resposta, em SQL |
| `minAmount` / `maxAmount` | intervalo de `totalAmount`, inclusive |
| `budgetItemId` | rubrica **e toda a sub-árvore dela** (o serviço resolve os ids das descendentes vivas antes da query; uma rubrica de outra obra ou apagada filtra tudo, não devolve a lista inteira) |
| `budgetId` | lote da fatura (`construction_invoice.budget_id`). Apanha também as faturas **sem rubrica** — é o que deixa ver as "por classificar" de um lote. Vale também no `outstanding-summary` |

Os últimos oito são a **pesquisa avançada** do Backoffice (2026-09-21, `InvoiceFiltersModal`) e vão
também no `GET …/outstanding-summary`, que aceita exatamente o mesmo conjunto (menos `outstanding`).
No backend chegam todos num `InvoiceSearchFilter`. Os enums vão como texto e a query compara
`cast(coluna as string)`: um parâmetro enum a `null` contra uma coluna `NAMED_ENUM` não tem tipo que o
Postgres consiga inferir. O filtro de rubrica usa um flag `budgetFilter` + lista de ids, porque
"`:lista is null`" não é fiável com coleções em JPQL (`InvoiceSearchFilterTest`).

A sugestão de rubrica é a que as faturas deste fornecedor costumam levar **neste projeto**. É
o que transforma a associação num clique a partir da segunda fatura do mesmo fornecedor.

### Leitura do QR code da AT

Escolheu-se o QR em vez de OCR por ser **determinístico** — obrigatório nas faturas
portuguesas desde 2022, traz os campos fiscais já estruturados, corre offline e a fatura não
sai do servidor. Implementado em `AtInvoiceQrService` com ZXing (descodificação) e PDFBox
(rasterização quando a fatura é PDF; varre até 5 páginas, o QR costuma estar na última).

Do QR saem `supplierNif`, `invoiceNumber`, `invoiceAtcud`, `invoiceDate`, `totalAmount`,
`taxableAmount` e `taxAmount`. O preenchimento **só toca em campos vazios** — correções feitas
à mão não são deitadas fora por se ter substituído a digitalização. O `supplierName` nunca vem
do QR: a AT só declara o NIF do emitente.

A procura é uma escalada, do barato para o caro, em três degraus:

1. **Imagem inteira** (duas binarizações do ZXing).
2. **Mosaico** — só se o primeiro não trouxer um QR da AT: a imagem é recortada em quadros
   sobrepostos, cada um ampliado. Existe para a **fotografia da fatura inteira tirada ao
   telemóvel**, em que o QR ocupa uns 150 px de 1600 — na imagem toda o detetor não o localiza.
3. **Detetor da WeChat** (`WeChatQrCodeService`, `org.bytedeco:opencv`) — só se o mosaico
   também falhar: uma CNN com super-resolução, treinada para QR pequeno/desfocado, em vez de
   só binarizar. Continua em processo, nada sai do servidor. Corre por último por ser o mais
   caro (a primeira chamada por arranque do servidor carrega o modelo, ~15-20 s; feito em
   segundo plano no arranque para não atrasar a primeira fatura real).

Para PDF a escalada é 200 DPI → 300 DPI → mosaico → WeChat, por isso o PDF nascido de um ERP
resolve-se logo no primeiro degrau. Medido contra 19 fotografias reais de faturas: 8 lidas só
com a imagem inteira, 14 com o mosaico, 15 com o detetor da WeChat.

`warnings` assinala documento anulado na AT (campo de estado `"A"`), nota de crédito
(tipo `"NC"`, que abate em vez de somar) e campos ilegíveis. São frases já em português,
prontas a mostrar.

**Sem QR legível** — fornecedor estrangeiro, documento anterior a 2022, impressão gasta —
devolve `qrRead: false`, a fatura entra como `needsReview` e o preenchimento segue manual.
Nunca é erro.

### Correção manual (`PUT /{id}`)

Todos os campos são opcionais: a fatura entra a partir do ficheiro, não deste formulário.

`taxableAmount` e `taxAmount` **não fazem parte do corpo**. São o que o QR da AT declarou, não
campos de edição — enquanto lá estiveram, um `PUT` que os omitisse (como o do próprio
Backoffice, que nunca os mostrou como editáveis) apagava-os à primeira correção de qualquer
outro campo. Corrigir o total à mão não os recalcula: o que lá está é o que a AT recebeu.

Apagar a data ou o total de uma fatura **já associada** é recusado com `INVOICE_006` — o
lançamento ficaria sem os campos que a despesa exige. A despesa que dela nasceu acompanha a
correção, senão o orçamento continuava a somar o valor errado.

### Repor os dados do QR (`POST /{id}/rescan`)

O desfazer de uma correção manual feita por engano. Vai buscar ao Storage o ficheiro que já lá
está, relê o QR e **sobrepõe** os campos fiscais — ao contrário do upload e do
`POST /{id}/file`, que só preenchem o que está vazio. É a única operação em que o QR ganha ao
que foi escrito à mão, porque é exatamente isso que se está a pedir.

Não recebe corpo nem ficheiro: para trocar a digitalização é `POST /{id}/file`. Não toca no
documento nem na miniatura, e **`supplierName` e `notes` ficam intactos** — não vêm do QR, são
escritos por gente.

Devolve o mesmo `InvoiceUploadResultDTO` do upload (com `qrRead: true`, `duplicates` e os
`warnings` do QR). Falha com `INVOICE_008` quando o documento não tem QR da AT legível — nada
é apagado, a fatura fica como estava. Se a fatura já estiver associada e o QR trouxer a data
ou o total ilegíveis, é recusado com `INVOICE_006`, pela mesma razão do `PUT /{id}`. A despesa
associada acompanha os novos valores.

### Códigos de erro

| Código | Quando |
|---|---|
| `INVOICE_001` | Fatura não encontrada |
| `INVOICE_002` | Projeto da fatura não encontrado |
| `INVOICE_003` | Erro ao carregar o ficheiro |
| `INVOICE_004` | Já associada a uma rubrica — desassociar primeiro |
| `INVOICE_005` | Não está associada a nenhuma rubrica |
| `INVOICE_006` | Falta a data ou o total para associar |
| `INVOICE_007` | A rubrica indicada pertence a outro projeto |
| `INVOICE_008` | `rescan` não encontrou QR da AT legível no documento |
| `INVOICE_009` | `rescan` não conseguiu obter o ficheiro original do Storage |
| `INVOICE_010` | já existe uma fatura com este ATCUD (upload, `/file` ou `PUT`) — global desde a `V29`, a mensagem diz onde |
| `INVOICE_011` | já existe uma fatura deste fornecedor com este número (sobretudo no `PUT`) |
| `INVOICE_012` | este ficheiro já foi carregado — igual, byte a byte (upload, `/file` ou `/documents`); global desde a `V24` |
| `INVOICE_013` | fatura da empresa ou por identificar não pode ser associada a uma rubrica |
| `INVOICE_014` | `scope = PROJECT` sem `enterpriseId` |
| `INVOICE_015` | `scope = COMPANY` ou `UNIDENTIFIED` com `enterpriseId` |
| `INVOICE_016` | `scope` desconhecido |
| `INVOICE_017` | `POST /register` com `documentStatus = ARCHIVED` — uma fatura sem ficheiro não está arquivada |
| `INVOICE_018` | o documento indicado não pertence a esta fatura (`DELETE /{id}/documents/{documentId}`) |
| `INVOICE_019` | a fatura já está totalmente paga (`POST /{id}/payments`) |
| `INVOICE_020` | o valor a pagar é superior ao que falta liquidar nesta fatura |
| `INVOICE_021` | pagamento agregado — o valor do movimento é **superior** à soma do que falta pagar. (O caso **inferior** não é erro: resposta `200`, `created=false`, com a lista das faturas a tirar da seleção.) |
| `INVOICE_022` | pagamento agregado com faturas de obras diferentes |
| `INVOICE_023` | pagamento não encontrado (`DELETE /payments/{paymentId}`) |
| `INVOICE_024` | pagamento agregado sem faturas |
| `INVOICE_025` | a prova de pagamento tem de ser PDF ou imagem |
| `INVOICE_026` | uma nota de crédito tem de apontar para uma **fatura**, não para outra NC (sem NC de NC) |
| `INVOICE_027` | as notas de crédito **não se pagam** — reduzem a fatura a que pertencem |
| `INVOICE_028` | a repartição por rubricas não soma o total da fatura (era "NC por várias rubricas fica para a fase 4"; a `V32` tornou-a possível e o código passou a significar o que sobrou) |
| `INVOICE_029` | repartição sem nenhuma rubrica |
| `INVOICE_030` | a mesma rubrica repetida na repartição |
| `INVOICE_031` | transferência para uma obra de teste (`is_test`) |
| `INVOICE_032` | transferência cujo destino é o âmbito/obra onde a fatura já está |
| `INVOICE_033` | `POST /{id}/transfer` sobre uma nota de crédito — a NC segue a fatura de origem, não se transfere sozinha |
| `INVOICE_050` | `PATCH /{id}/allocate` / `expenses/split` / `batch-allocate` — a rubrica é de outro lote do que o da fatura |
| `INVOICE_051` | `PATCH /{id}/allocate` / `expenses/split` / `batch-allocate` — obra de vários lotes e a fatura ainda não tem lote |
| `INVOICE_052` | `PATCH /{id}/budget` — a fatura já tem despesas e o pedido mudaria o lote |
| `INVOICE_034` | inconsistência não encontrada (`GET/POST /invoice-incidents/{id}`) |
| `INVOICE_035` | `POST /invoice-incidents` com uma fatura que não existe |
| `INVOICE_036` | `POST /import-excel` com ficheiro vazio |
| `INVOICE_037` | `POST /import-excel` com ficheiro que não é `.xlsx` |
| `INVOICE_038` | `POST /import-excel` — o POI não conseguiu ler o livro |
| `INVOICE_039` | `POST /import-excel` — o livro não tem a folha esperada: `Despesas` em `PROJECT`/`COMPANY`, `Por identificar` em `UNIDENTIFIED` |
| `INVOICE_040` | `POST /import-excel` — sem linha de cabeçalho (procura-se a célula "Nº Fatura" nas primeiras 20 linhas) |
| `INVOICE_041` | `POST /import-excel` — falta uma coluna obrigatória; a mensagem diz quais |
| `INVOICE_042` | `POST /import-excel` — a folha não tem linhas |
| `INVOICE_043` | `POST /import-excel?dryRun=false` com `errors` por corrigir — nada gravado |
| `INVOICE_044` | `POST /import-excel?dryRun=false` com `questions` por responder — nada gravado |
| `INVOICE_045` | `POST /import-excel?dryRun=false` numa obra `is_test` com um ficheiro que não começa por `TESTE - ` |
| `INVOICE_046` | `POST /import-excel?dryRun=false` — o que ficou gravado não bate com a folha (bug nosso, não erro do Excel); transação anulada |

(`ENT_032` — slug de projeto duplicado — sai do `EnterpriseController`, não daqui; ver [[excel-parity.md]] §2.)

Ficheiro: PDF, JPEG ou PNG, até 25 MB, bucket `documents`, chave
`construction-invoices/{enterpriseId}/…`. A miniatura é um extra — falhar a gerá-la não custa
a fatura, a lista cai num ícone de ficheiro.

### Download dos documentos por âmbito (desde 2026-10-02)

`GET …/documents/zip?scope=ALL|LOT|UNCLASSIFIED[&budgetId=]` devolve **só** os PDFs e fotos, com os
ficheiros na **raiz** do zip e o nome a dizer o âmbito
(`[TESTE - ]Faturas - <Obra>[ - <Lote> | - Por classificar].zip`). Os nomes de cada ficheiro são os
do vault (§7), os mesmos que o zip da pasta da obra usa.

Os âmbitos seguem **dois eixos**, não uma lista plana — e foi achatá-los numa lista que confundiu o
utilizador a 2026-10-02 (escolhia "um lote", vinha vazio, e nada dizia porquê):

1. **está associada a uma rubrica?** — `ASSOCIATED` ou `UNCLASSIFIED`, `ALL` para os dois juntos;
2. **de que lote?** — **só se aplica às associadas**, porque o lote de uma fatura vem das rubricas
   onde está classificada (`expense → budget_item → budget`) e não de um campo dela. Por isso o lote
   é um parâmetro **opcional** do `ASSOCIATED` e não um âmbito à parte.

O `SELECTED` está fora desses eixos de propósito: a lista de faturas já filtra por fornecedor, datas
e estado de pagamento, por isso deixar escolher "estas" dá todos os outros cortes sem inventar aqui
um âmbito por cada um. O `InvoiceDocumentsDownloadModal` desenha exatamente esta forma — o selector
de lote vive **dentro** da opção "já associadas".

| `scope` | O que leva |
|---|---|
| `ALL` | todas as faturas da obra |
| `ASSOCIATED` | as classificadas em rubricas. **Com** `budgetId`, só as desse lote; **sem** ele, as de qualquer lote |
| `UNCLASSIFIED` | as que não estão em rubrica nenhuma |
| `SELECTED` | as de `invoiceIds` (lista separada por vírgulas). Vazia → `INVOICE_048`; mais de 300 → `INVOICE_049` |

> ⚠️ **O `UNCLASSIFIED` não é conveniência, é cobertura.** Uma fatura sem rubrica não pertence a lote
> nenhum, por isso sem esse âmbito os seus documentos não saíam em zip nenhum — e isso não é um caso
> de canto: na Vila Aleu, a 2026-10-02, **as 44 faturas estavam todas por classificar**, ou seja os 43
> documentos dela eram todos inalcançáveis por lote. `ASSOCIATED` (sem lote) e `UNCLASSIFIED` não se
> sobrepõem e, somados, dão o `ALL`. Desde a `V41` uma fatura por classificar **com lote** (`budget_id`)
> entra também no zip desse lote (`LOT`), sem rubrica — por isso essa fatura conta em `LOT` e em
> `UNCLASSIFIED`. Uma fatura classificada não se reparte entre lotes.

**`SELECTED` e segurança**: os ids vêm no query string, mas o ponto de partida do filtro é sempre
`findAllByEnterpriseIdForExport(enterpriseId)` — um id de outra obra posto à mão no URL não traz
nada, porque a interseção é com as faturas desta obra. O teto de **300** ids existe para o URL não
bater no limite do Tomcat e falhar com um erro que não diz nada; acima disso, `INVOICE_049`.

**Não confundir com `/construction-budget/enterprise/{id}/export/zip`**: esse é a **pasta da obra do
vault** — leva o livro `Despesas - <Obra>.xlsx` na raiz e os documentos em `Faturas/Lançadas/` (§7 de
[[excel-parity.md]]), para extrair em `Empreendimentos\<slug>\`. Este é um download de conveniência:
sem livro, sem pasta. O `_EM-FALTA.txt` que lista o que o Storage não devolveu existe nos dois, ao
lado dos documentos (na raiz aqui, em `Faturas/Lançadas/` lá).

`GET …/documents/summary?scope=&budgetId=` dá a contagem do âmbito sem tocar no Storage — é o que o
modal mostra antes de alguém pedir dezenas de MB. O `/documents/zip` **entra no rate limiting** dos
exports (é o mais pesado de todos); o `/documents/summary` fica de fora.

**Âmbito sem documentos** → `INVOICE_047`, e não um zip vazio. Até 2026-10-02 devolvia `200` com 22
bytes (um zip sem entradas): o browser "descarregava" e ninguém percebia que não havia nada. A
verificação é **antes** de o streaming começar — depois disso os cabeçalhos já seguiram e não há como
dizer porquê. O caso que o expôs: pedir o `LOT` de uma obra onde **nenhuma** fatura está classificada
(a Vila Aleu, com as 44 por classificar) — o lote de uma fatura vem da rubrica, por isso sem
classificação nenhum lote tem documentos. Quem chamar isto deve ler primeiro o `/documents/summary` e
explicar a causa ao utilizador, que é o que o `InvoiceDocumentsDownloadModal` faz.

## Pagamentos (`PaymentController`)

Fase 2 da paridade com o Excel. "Dar como pago" e "método de pagamento" são **um só gesto**,
e regista-se quem o fez. Um pagamento é uma entidade própria (`payment`) ligada às faturas
por uma junção (`invoice_payment`) — é o que faz o caso normal (1 fatura, 1 movimento) e o
caso raro (N faturas, 1 transferência) serem o **mesmo modelo**. Tudo `ADMIN`. Ver
[[database.md]] → `payment` e [[faturas-modelo-alvo.md]] §2.3.

| Método | Rota | Notas |
|---|---|---|
| POST | `/construction-invoices/{id}/payments` | multipart `payment` (JSON) + `proof` opcional. `paidOn`, `method`, `amount?` (por omissão o que falta liquidar), `reference?`, `notes?`. `201` → `PaymentResponseDTO` |
| POST | `/construction-invoices/payments` | agregado. `payment` JSON com `invoiceIds[]` + `amount` do movimento + `proof` opcional. `201` `AggregatePaymentResultDTO` com `created=true` se o valor bater com a soma; `200` `created=false` + `leftOut[]` se for **menor** |
| DELETE | `/construction-invoices/payments/{paymentId}` | anula: apaga as ligações e o movimento, as faturas voltam a `UNPAID`/`PARTIAL`. Fica em `activity_log`. `204` |

**Estado de pagamento da fatura** sai em todos os DTOs de fatura, derivado (nunca coluna):
`paymentStatus` (`UNPAID` / `PARTIAL` / `PAID`), `paidAmount`, `netAmount` (= `totalAmount`
até à fase 3) e `payments[]` (cada movimento já do ponto de vista da fatura: quanto lhe
tocou, e "junto com" que outras faturas). O selo de estado e o filtro `?outstanding=true` nas
listas vêm daqui.

| `method` | Valor do Excel `Metodo Pagamento` |
|---|---|
| `NUMERARIO` | `Numerário` |
| `MULTIBANCO` | `Pagamento MB`, `MB`, `TPA` |
| `TRANSFERENCIA` | `Transferência` |
| `OUTRO` | outro texto (o original vai para `notes`) |

Regras: uma fatura fora de `PROJECT` também pode ser paga (a quarentena e as despesas da
empresa têm pagamentos); o agregado **bloqueia** faturas de obras diferentes (`INVOICE_022`);
um valor abaixo do líquido numa fatura deixa-a `PARTIAL` (a única via de parcial na fase 2 —
não há repartição fina por linha); a prova de pagamento **não é obrigatória**.

## Notas de crédito

Fase 3 da paridade com o Excel. Uma NC só existe **agarrada a uma fatura já lançada** — o seu
único significado é "esta fatura vale menos X". Vive na mesma tabela
(`document_type = CREDIT_NOTE`, `related_invoice_id` = a origem). Tudo `ADMIN`. Ver
[[faturas-modelo-alvo.md]] §6 e [[database.md]] → `construction_invoice`.

- `GET /construction-invoices/{id}/credit-notes/split-preview?amount=100` → proposta de
  repartição negativa (`{ originAllocated, total, lines: [{budgetItemId, budgetItemCode,
  budgetItemName, amount<0}] }`), na proporção das despesas da fatura. Nada gravado. Origem
  sem despesa → `lines` vazio (a NC também não gera despesas).
- `POST /construction-invoices/{id}/credit-notes` → cria a NC. Corpo: `{ totalAmount (positivo),
  invoiceNumber?, invoiceAtcud?, invoiceDate?, supplierNif?, description?, notes?,
  documentStatus?, expenses?: [{budgetItemId, amount}] }`. A NC **herda** `scope`/obra da
  origem e o NIF por omissão (NIF diferente → grava com aviso). `expenses` são as linhas
  confirmadas (N desde a `V32`); o serviço grava-as sempre com `total_price` negativo. A soma
  das linhas **não** é imposta aqui — ao contrário do `split` de uma fatura: a NC corrige uma
  fatura que já pode estar torta, e prender quem a regista não ajudava.

**Líquido da fatura** = `totalAmount − Σ (totalAmount das suas NC)`, exposto em todos os DTOs
de fatura como `netAmount`, com `creditNoteTotal` e `creditNotes[]` (refs). É o `netAmount`
que os pagamentos cobrem e o filtro `?outstanding=` usa; as linhas `CREDIT_NOTE` nunca contam
como "por liquidar" nem entram no `pending-summary`. `POST .../{id}/payments` recusa uma NC
(`INVOICE_027`). O `preview` do upload passa a devolver `documentType` (campo `D` do QR): um
`"NC"` encaminha o utilizador para este fluxo em vez do registo normal.

## Classificar faturas em rubricas

Fase 4 da paridade com o Excel. Ver [[faturas-modelo-alvo.md]] §7 e
[[database.md]] → `construction_expense`.

**Repartir por N rubricas** — `POST /construction-invoices/{id}/expenses/split` (`ADMIN`).
Corpo `{ lines: [{budgetItemId, amount}] }`. **Substitui** a repartição atual por inteiro, nunca
acrescenta: editar uma repartição é redesenhá-la, e um "acrescenta esta linha" deixaria a soma a
divergir do total sem ninguém dar por isso. A soma **tem** de esgotar o `totalAmount`
(`INVOICE_028`); rubricas repetidas → `INVOICE_030`; lista vazia → `INVOICE_029`. Uma fatura
ainda **sem total** classifica-se na mesma e as linhas nascem a zero (§7). O
`PATCH .../allocate` continua a servir o caso normal de uma rubrica.

**A fatura na resposta** ganhou:

| Campo | O que diz |
|---|---|
| `allocations[]` | `{expenseId, budgetItemId, budgetItemCode, budgetItemName, amount, chapter}` — a verdade completa. `chapter` é a mesma regra do ecrã "Classificar": a rubrica ainda tem filhas `ITEM` |
| `allocationStatus` | `NONE` · `COMPLETE` · `PARTIAL` · `PROVISIONAL` (fatura sem total, linhas a zero) |
| `unallocatedAmount` | `total − Σ despesas`; null quando a fatura não tem total |
| `expenseId`, `budgetItemId`, `budgetItemCode`, `budgetItemName` | **só preenchidos com exatamente uma** afetação; null quando repartida — apontar para a primeira mentiria sobre as outras |
| `allocated` | continua a ser "tem ≥1 despesa" (semântica inalterada) |

**Sugestão com o porquê** — `GET /construction-invoices/{id}/rubric-suggestion` (`ADMIN` ou
`EMPLOYEE`) → `{ budgetItemId, code, name, source, explanation, referenceEnterprise }`, ou `204`.
Dois níveis, o primeiro que existir ganha:

1. `HISTORY_PROJECT` — a rubrica onde as faturas deste NIF **costumam** ser lançadas nesta obra.
   É "a mais usada", não "a última", de propósito: uma classificação errada isolada não passa a
   mandar na sugestão. `explanation`: "4 das 5 faturas de Casa Dolores nesta obra foram para 5.1.2."
2. `HISTORY_GLOBAL` — sem histórico aqui, procura noutras obras e atravessa pelo **código**
   (`4.2.1` é o mesmo em orçamentos do mesmo empreiteiro). Só vale se esta obra tiver esse código.

Não há nível de **regras** declaradas (`supplier_rubric_rule`): ficaram de fora por decisão de
2026-09-08 — o histórico já cobre os casos que interessam, as regras envelhecem, e no vault da
Vilatro nunca existiram. A sugestão **nunca grava sozinha**. O nome do fornecedor na `explanation` — e o
`supplierName` de qualquer resposta de fatura — cai para o **catálogo** (`supplier` por NIF) quando a
fatura não o tem (2026-09-17): só leitura, não grava nada na fatura; antes aparecia o NIF cru mesmo
com a empresa registada ao lado.

**Lote** — `POST /construction-invoices/batch-allocate` (`ADMIN`), corpo
`{ invoiceIds: [], budgetItemId }` → `{ succeeded, failures: [{invoiceId, invoiceNumber,
errorCode, message}] }`. **Melhor esforço**, à imagem do upload que já é por-ficheiro: uma
fatura que outro separador entretanto classificou não faz perder as outras quatro.

**Procurar rubrica** — `GET /construction-budget/enterprise/{enterpriseId}/search?q=&limit=20`
(`ADMIN` ou `EMPLOYEE`) → `[{budgetId, budgetName, id, code, name, path, depth, chapter, rolledUpBudget, spentTotal,
remaining, overBudget}]`. Aceita código (`4.2`) ou texto (`betão`); o `path` completo
(`4. Estrutura › 4.2 Lajes › 4.2.1 Betão`) é o que distingue os três "Betão" de um orçamento
real. Rubricas que não aceitam despesas não aparecem; `chapter: true` assinala que ainda tem
sub-rubricas por baixo — classificar ao capítulo é legítimo, mas fica assinalado. **`q` vazio ou
omitido devolve os capítulos** (2026-09-17) — é o estado inicial do campo de pesquisa do ecrã
"Classificar", que antes ficava em branco até se escrever alguma coisa. **Um `q` só de dígitos e
pontos é pesquisa por código: prefixo no mesmo nível** (2026-09-21) — `2` dá a `2` e a `20` (não a
`2.1`, a `20.1` nem a `12`), `2.` dá as filhas diretas da `2`, `2.1` dá a `2.1` e a `2.10`; é o que
"ver sub-rubricas ›" assume ao escrever `2.`. Antes era `contains` e `2` trazia a árvore inteira.
Texto continua a procurar em toda a árvore (`BudgetItemSearchTest`). Procura em **todos os lotes** da vila; com mais do que um, o `path` começa pelo nome do lote (`Lote A › 4. Estrutura › …`), porque o `4.2.1` existe em todos (`BudgetLotsTest`).

## Transferir faturas (`ConstructionInvoiceController`)

Fase 5 da paridade com o Excel. Mudar uma fatura de obra ou de âmbito é uma **transferência com
razão obrigatória**, nunca edição direta do `scope`/`enterprise` (o `PUT /{id}` não lhes toca).
Ver [[faturas-modelo-alvo.md]] §4.

`POST /construction-invoices/{id}/transfer` (`ADMIN`), corpo
`{ targetScope, targetEnterpriseId?, reason }`:

- `targetScope` `PROJECT` exige `targetEnterpriseId`; `COMPANY`/`UNIDENTIFIED` **não** o aceitam e
  a obra é limpa (check `ck_invoice_scope_enterprise`).
- `reason` obrigatória (`@NotBlank` → `VALIDATION_ERROR`).
- **Apaga as despesas** da fatura — sem obra não há rubrica a que pertençam — depois de guardar a
  repartição antiga (a da fatura **e a de cada nota de crédito ligada**) no `activity_log`
  (`activity_type = transfer`, snapshot em `metadata`). A escrita do log é **síncrona**, na mesma
  transação: se a transferência falhar a seguir, o log desaparece no rollback.
- As **notas de crédito** (`related_invoice_id`) seguem a fatura: mesmo âmbito, mesma obra,
  despesas apagadas.
- **Documentos e pagamentos ficam** — presos pela FK `invoice_id`, nada a mover. Um pagamento
  agregado que passe a atravessar obras é aceite: o invariante "mesma obra" do `registerAggregate`
  só vale à criação, e as somas por obra usam o valor por fatura (`invoice_payment.amount`).
- Recusa: destino = obra `is_test` (`INVOICE_031`); destino = âmbito/obra atual (`INVOICE_032`);
  chamada sobre uma NC (`INVOICE_033`).

Resposta: `{ invoice, suggestIncident }` — `suggestIncident` fica `true` quando a fatura trazia
repartição, pagamentos ou NC (os casos em que vale a pena propor registar uma inconsistência).

**Em "Por identificar", preencher a obra É transferir** — o Backoffice abre o mesmo fluxo com
`targetScope` fixo em `PROJECT`.

**"Outra rubrica desta obra" no drawer de transferir NÃO é uma transferência** (desde 2026-09-21): o
`TransferInvoiceDrawer` mostra esse quarto destino só numa fatura de obra, e ao confirmar chama
`POST /{id}/expenses/split` com **uma linha** (`amount = totalAmount`) — substitui a repartição inteira e
serve a fatura já associada (o `allocate` recusava com `INVOICE_004`) e a que ainda não está. Não pede
razão, não escreve no `activity_log`, não gera `suggestIncident` e **não toca nas NC ligadas** (ficam com
a repartição delas — só a transferência de obra as arrasta). A rubrica escolhe-se com o
`RubricSearchField` do ecrã de classificação.

O **detalhe da fatura** (`GET /construction-invoices/{id}`) ganhou `transfers[]`:
`{ transferredAt, fromScope, fromEnterpriseId, fromEnterpriseName, toScope, toEnterpriseId,
toEnterpriseName, reason, byName }`, da mais recente para a mais antiga. É uma projeção leve das
entradas `transfer` do `activity_log` — **só vem no detalhe**, nas listas vem vazio.

## Importar a folha "Despesas" do Excel (fase 6)

`POST /construction-invoices/import-excel` — o sentido Excel → app do contrato de
[[excel-parity.md]] §9; o inverso é `GET /construction-budget/enterprise/{id}/export`.
Serviço: `DespesasExcelImportService`. Só `ADMIN`.

| Parâmetro | Notas |
|---|---|
| `scope` | `PROJECT` (exige `enterpriseId`), `COMPANY` ou `UNIDENTIFIED` (proíbem-no). Em `UNIDENTIFIED` lê o `Faturas por identificar.xlsx` — ver abaixo |
| `file` | multipart, o `Despesas - <Obra>.xlsx` (ou `Despesas da empresa.xlsx`; na quarentena o `Faturas por identificar.xlsx`). Lê **só a folha `Despesas`** (`Por identificar` na quarentena), pelo nome; as colunas pelo cabeçalho normalizado (`\s+`→espaço, sem acentos, sem caixa — o "Metodo\nPagamento" real passa), nunca pela letra. `Rubrica`, `Fornecedor` e `NIF` são opcionais |
| `dryRun` | `true` por omissão: devolve o relatório, nada gravado. `false` grava tudo numa transação |
| `answers` | parte multipart JSON `{ answers: [{ questionId, value }] }` — as respostas às `questions` do `dryRun` |

**O que a leitura faz** (detalhe e decisões em [[excel-parity.md]] §9): agrupa as linhas por
`Nº Fatura` (as sem nº só quando contíguas e iguais em tudo menos rubrica/valor); `Imprimir`/`Pedir`
→ `documentStatus`; `-` conta como sem nº; linha negativa = nota de crédito; `Liquidada` (`x`/`X`/`Sim`)
+ `Metodo Pagamento` (mapa §4, sem acentos/caixa) + `Observações` ("Pago por … em dd-mm-aaaa (ref)",
"Pago parcialmente …", "… junto com …", "Nota de crédito da fatura …" — as frases que o exportador
gera são lidas de volta; o resto fica em `notes`); `Rubrica` `<Art> — …` → `construction_expense`
por linha (inexistente ou título = erro); `Bizdocs` → `sentToAccountant`; a linha
"Despesa registada à mão na app, sem fatura." volta a ser uma despesa solta.

**Resposta** `ExpensesImportResultDTO`: `{ dryRun, scope, sheetName, rowCount, invoiceCount,
creditNoteCount, manualExpenseCount, transferredCount, paidCount, partiallyPaidCount, unpaidCount, parsedTotal,
sheetTotal, totalDifference, errors[{excelRow, message}], warnings[], questions[], invoices[] }`.
`invoices[]` é a pré-visualização (uma por fatura, com `key`, `excelRows`, `lines[{excelRow,
rubricCode, rubricLabel, amount}]`, `paymentStatus`, `creditNoteOrigin`, `duplicate`, `possibleEnterprises`,
`askWhom`, `transferTo`, …).

**Bloqueia a gravação** (`errors`, → `INVOICE_043`): rubrica inexistente/título, nº que já existe
na app (comparado sem NIF, decisão 18 do Vilatro; obras `is_test` não contam), data ilegível, fatura liquidada
sem data nem "Pago … em", e **a soma das linhas ≠ linha TOTAL da `TabelaDespesas`** acima de 1
cêntimo — ao contrário do orçamento, aqui a diferença é erro (passo 9 do §9).

**Pergunta** (`questions`, → `INVOICE_044` se por responder): `CREDIT_NOTE_ORIGIN` (opções
`file:<key>` para uma fatura do ficheiro, `db:<uuid>` para uma já na app, `SKIP`) e
`AGGREGATE_PAYMENT` (faturas pagas com a mesma observação com data — `ONE_PAYMENT` ou `SEPARATE`).
Os `id` das perguntas derivam da linha do Excel, por isso são estáveis entre o `dryRun` e a gravação.

**Gravação**: `register` → `split` (rubricas) → NC (`createCreditNote`, com as despesas negativas
da linha) → pagamentos (`registerAggregate` / `markAsPaid`; uma fatura anulada por inteiro por NC do
ficheiro entra **sem** pagamento, com aviso — líquido zero). Uma linha sem valor entra com total nulo
(por rever), com aviso. No fim confere contra a folha: nº de
faturas, Σ e nº de liquidadas — diferença → `INVOICE_046` e rollback. Uma obra `is_test` só aceita
um ficheiro `TESTE - …` (o que o exportador gera para obras de teste) → senão `INVOICE_045`.
Os documentos de `Faturas\Lançadas\` **não** entram por aqui — juntam-se depois por `POST /{id}/documents`,
um a um (a migração de 2026-09-18 fê-lo com a correspondência pelo nº no nome do ficheiro; regras em
[[excel-parity.md]] §9, passo 7).

**Quarentena** (`scope=UNIDENTIFIED`, desde 2026-09-20): o mesmo endpoint lê a folha
`Por identificar` (tabela `TabelaPorIdentificar`) do `Faturas por identificar.xlsx` — as 8 colunas
da `Despesas` (sem `Rubrica`: rubrica aqui é erro) mais quatro, todas opcionais:
`Fornecedor` → `supplierName`, `Obras possíveis` → `possibleEnterprises`, `Perguntar a` → `askWhom`,
`Aqui desde` → acrescentado a `notes` como "Em quarentena desde dd-mm-aaaa." (não há coluna própria;
`created_at` é a data da importação). `Empreendimento` é o valor da dropdown do vault: o **slug** de
uma obra (= nome da pasta) ou `Despesas da empresa`. Preenchido, a fatura entra em `UNIDENTIFIED` e é
**transferida na mesma transação** (`ConstructionInvoiceService.transfer`, razão automática que cita
a folha e a célula → `activity_log` como qualquer transferência) para essa obra ou para `COMPANY`,
antes das NC e dos pagamentos. Slug sem obra na app, ou obra `is_test` → erro por linha (→ `INVOICE_043`
na gravação); a obra cria-se primeiro, com o nome da pasta. O relatório traz `transferredCount` e, por
fatura, `transferTo` (nome da obra ou "Despesas da empresa"). Os PDFs de `Faturas por identificar\Lançadas\`
juntam-se depois, como nas obras.

## Inconsistências (`InvoiceIncidentController`, `/invoice-incidents`)

Fase 5. Notas livres (markdown) sobre uma ou mais faturas que ficaram por conciliar —
tipicamente depois de uma transferência. A transferência **sugere** criar uma (`suggestIncident`);
a criação é sempre à mão. Tudo `ADMIN`. Ver [[database.md]] → `invoice_incident`.

| Método | Rota | Notas |
|---|---|---|
| GET | `/invoice-incidents` | lista; por resolver primeiro, depois as mais recentes |
| GET | `/invoice-incidents/{id}` | uma; `INVOICE_034` se não existir |
| POST | `/invoice-incidents` | `{ title, body, invoiceIds[] }` — `body` markdown, `invoiceIds` não vazio; `201`. Fatura inexistente → `INVOICE_035` |
| POST | `/invoice-incidents/{id}/resolve` | marca `resolvedAt`/`resolvedBy`; idempotente (não mexe na data se já resolvida) |

`InvoiceIncidentResponseDTO`: `{ id, title, body, resolvedAt, resolvedBy, resolvedByName,
createdBy, createdByName, createdAt, updatedAt, invoices: [{id, invoiceNumber, supplierName,
scope, enterpriseId}] }`.

## Fornecedores (`SupplierController`, `/suppliers`)

Catálogo **NIF → nome da empresa**. Existe porque o QR da AT identifica o emitente só pelo NIF
(campo `A`) — **não há campo para o nome** na especificação. Sem catálogo, o nome era escrito à
mão uma vez por fatura, para sempre.

É **global, não por projeto**: o mesmo NIF é a mesma empresa em todas as obras. A ligação às
faturas faz-se pelo NIF e não por chave estrangeira, para uma fatura poder existir com um
fornecedor que ainda não está no catálogo — que é o estado normal de uma fatura acabada de
carregar.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/suppliers?q=` | `ADMIN` ou `EMPLOYEE` — catálogo por ordem alfabética, com `invoiceCount` |
| GET | `/suppliers/unknown-nifs` | `ADMIN` ou `EMPLOYEE` — NIFs vistos nas faturas ainda sem empresa, do mais frequente para o menos |
| POST | `/suppliers` | `ADMIN` — dá nome a um NIF; devolve `201` |
| PUT | `/suppliers/{id}` | `ADMIN` — renomeia |
| DELETE | `/suppliers/{id}` | `ADMIN` — tira do catálogo; devolve `204` |

**Gravar propaga-se às faturas.** `POST` e `PUT` escrevem o nome nas faturas desse NIF que
estejam **sem nome** — de todos os projetos — e devolvem quantas foram (`invoicesUpdated`). Só
as vazias: um nome escrito à mão é uma correção deliberada de quem tinha o papel à frente, e o
catálogo não tem autoridade para a deitar fora. No sentido inverso, o `upload`/`preview` de uma
fatura nova consulta o catálogo e nasce já com o nome (`applyKnownSupplierName`).

`DELETE` **não** limpa o nome das faturas: o nome já lá está escrito e é um dado do documento,
não uma referência viva à tabela. Apagar só significa "deixa de preencher sozinho a partir de
agora".

`unknown-nifs` traz `suggestedName` — um nome que alguém já escreveu à mão nalguma fatura desse
NIF, se existir — para o ecrã o mostrar pré-preenchido em vez de o pedir outra vez.

Códigos de erro: `SUPPLIER_001` (não encontrado), `SUPPLIER_002` (NIF já no catálogo).

## Tarefas (`TaskController`, `/tasks`)

Tarefas isoladas no seu próprio schema `tasks`, atribuíveis a um ou mais utilizadores `worksite.profile`. **Não estão ligadas a nenhum imóvel/ativo** — esse conceito não existe no Worksite.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/tasks` | `ADMIN` ou `EMPLOYEE` (empregado só vê as tarefas onde está atribuído) |
| GET | `/tasks/{id}` | `ADMIN` ou `EMPLOYEE` |
| POST | `/tasks` | `ADMIN` |
| PUT | `/tasks/{id}` | `ADMIN` ou utilizador atribuído (validado no service) |
| PATCH | `/tasks/{id}/status` | `ADMIN` ou utilizador atribuído (validado no service) |
| DELETE | `/tasks/{id}` | `ADMIN` |

## Horários de trabalho (`WorkScheduleController`, `/attendance/work-schedules`)

Catálogo de horários do módulo de assiduidade (`V42`, schema `attendance`). Um horário é **atribuível**:
cria-se uma vez e dá-se a quem se quiser, porque cada trabalhador pode ter o seu. Tudo `ADMIN` — o
self-service do funcionário é a fase 5 de [[../notes/roadmap/assiduidade]].

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/work-schedules` | `ADMIN` (paginado, ordenado por nome) |
| GET | `/attendance/work-schedules/deleted` | `ADMIN` — zona de recuperação |
| GET | `/attendance/work-schedules/{id}` | `ADMIN` |
| POST | `/attendance/work-schedules` | `ADMIN` → `201` |
| PUT | `/attendance/work-schedules/{id}` | `ADMIN` |
| DELETE | `/attendance/work-schedules/{id}` | `ADMIN` → `204`, **soft-delete** |
| POST | `/attendance/work-schedules/{id}/restore` | `ADMIN` |

O corpo de `POST`/`PUT` traz **sempre a lista completa de dias** — gravar substitui a lista, não a
acumula: "deixar de trabalhar à sexta" é apagar o dia, não editá-lo. `weekday` é o nome do dia
(`"MONDAY"`…`"SUNDAY"`), e `startTime`/`endTime` vêm como `"HH:mm"`:

```json
{
  "name": "08–17 c/ 1h almoço",
  "notes": null,
  "days": [
    { "weekday": "MONDAY", "startTime": "08:00", "endTime": "17:00", "breakMinutes": 60 }
  ]
}
```

A resposta acrescenta `expectedMinutes` por dia e `weeklyMinutes` no horário, ambos já com a pausa
descontada — são derivados, não colunas.

### Códigos de erro

| Código | Quando |
|---|---|
| `SCHED_001` | Horário não encontrado (ou apagado, nas rotas que só veem os vivos) |
| `SCHED_002` | Já existe um horário com este nome entre os vivos |
| `SCHED_004` | O mesmo dia da semana aparece duas vezes |
| `SCHED_005` | Hora de saída não é depois da entrada — **um dia de trabalho não atravessa a meia-noite** |
| `SCHED_006` | A pausa é igual ou maior que o período de trabalho do dia |
| `SCHED_008` | Restaurar um horário que não está apagado |

`SCHED_003` não existe: "horário sem dias" é apanhado pelo `@NotEmpty` do DTO e volta como erro de
campo. `SCHED_007` está reservado para "horário já atribuído, logo imutável", que entra com a tabela
`employment`.

## Emprego (`EmploymentController`, `/attendance/employments`)

Os dados de emprego de cada funcionário (`V43`). **As condições têm histórico**: mudar o horário ou os
dias de férias não reescreve o passado — fecha o período em vigor no dia anterior e abre um novo, para
que um relatório de janeiro continue correto depois de uma mudança em março.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/employments` | `ADMIN` |
| GET | `/attendance/employments/{profileId}` | `ADMIN` |
| POST | `/attendance/employments` | `ADMIN` → `201` (cria o vínculo + o primeiro período) |
| PUT | `/attendance/employments/{profileId}/dates` | `ADMIN` — só `hiredAt`/`endedAt`, por query param |
| POST | `/attendance/employments/{profileId}/terms` | `ADMIN` — **muda as condições a partir de uma data** |

O `POST .../terms` é o único caminho para mudar horário ou dias de férias. Não há `PUT` de um período:
alterar um período já decorrido é exactamente o que o histórico existe para impedir.

| Código | Quando |
|---|---|
| `ATT_001` | O funcionário não tem dados de emprego |
| `ATT_002` | Já tem — um funcionário só tem um registo de emprego |
| `ATT_003` | Um período (ou a data de fim) começa antes da admissão |
| `ATT_004` | O período novo não começa depois do que está em vigor |
| `ATT_005` | Não há período em vigor, logo não há horário atribuído |

## Picagens (`TimeEntryController`, `/attendance/time-entries`)

As picagens (`V43`). `source` é sempre `MANUAL` nesta fase e **não vem do cliente** — o método de
registo é um facto de como o registo entrou, não uma escolha de quem o grava; quando houver QR, é o
endpoint do QR que o define.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/time-entries?profileId=` | `ADMIN` (paginado) |
| GET | `/attendance/time-entries/day?profileId=&day=` | `ADMIN` — o dia local, 00:00 a 00:00 em `Europe/Lisbon` |
| GET | `/attendance/time-entries/deleted?profileId=` | `ADMIN` — zona de recuperação |
| GET | `/attendance/time-entries/{id}` | `ADMIN` |
| GET | `/attendance/time-entries/{id}/revisions` | `ADMIN` — **o rasto de auditoria** |
| POST | `/attendance/time-entries` | `ADMIN` → `201` |
| PUT | `/attendance/time-entries/{id}` | `ADMIN` — corrigir (grava revisão) |
| DELETE | `/attendance/time-entries/{id}?reason=` | `ADMIN` → `204`, **soft-delete** |
| POST | `/attendance/time-entries/{id}/restore?reason=` | `ADMIN` |

Não existe endpoint de apagar a sério. A resposta traz `localDate` — o dia a que a picagem pertence em
`Europe/Lisbon`, que **não** é o dia do `happenedAt` em UTC.

### Códigos de erro

| Código | Quando |
|---|---|
| `ATT_010` | Picagem não encontrada |
| `ATT_011` | Fora de sequência — uma entrada tem de ser seguida de uma saída, e a primeira picagem do dia é uma entrada. Validado também ao inserir no meio de um dia já preenchido |
| `ATT_012` | Já existe uma picagem deste funcionário neste instante |
| `ATT_013` | Restaurar uma picagem que não está anulada |
| `ATT_014` | Picagem antes da data de admissão. Sem dados de emprego não se valida — registar antes de criar a ficha não é erro |

A sequência é avaliada **dentro do dia local**: um dia de trabalho não atravessa a meia-noite (decisão
de 2026-10-06), por isso cada dia começa a zero e um dia mal preenchido não contamina o seguinte.

## Resumos de assiduidade (`AttendanceSummaryController`, `/attendance/summary`)

Horas, extras, atrasos e faltas. **Só leitura, porque nada disto está guardado**: é tudo derivado das
picagens e do horário que estava em vigor em cada dia. Um total gravado ficaria a mentir na primeira
correção a uma picagem, e corrigir picagens é uma funcionalidade.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/summary/day?profileId=&day=` | `ADMIN` — devolve um dia |
| GET | `/attendance/summary/week?profileId=&day=` | `ADMIN` — a semana ISO (segunda a domingo) desse dia |
| GET | `/attendance/summary/month?profileId=&month=YYYY-MM` | `ADMIN` |
| GET | `/attendance/summary/range?profileId=&from=&to=` | `ADMIN` |

Cada dia traz `workedMinutes`, `expectedMinutes`, `overtimeMinutes`, `latenessMinutes`, `firstIn`,
`lastOut`, `incomplete`, `needsAttention` e um `status`:

| `status` | Significado |
|---|---|
| `WORKED` | Há picagens no dia |
| `MISSING` | O horário previa trabalho e não há picagem nenhuma — **falta por justificar** |
| `NOT_SCHEDULED` | O horário não previa trabalho (fim de semana, horário parcial). **Não é falta** |
| `NO_SCHEDULE` | Não há horário atribuído a cobrir o dia (antes da admissão, ou sem ficha de emprego). Nem falta nem previsão |

Como os números saem:
- **horas trabalhadas** = soma dos pares entrada→saída, menos a pausa que o *horário* declara (ninguém
  pica o almoço). A pausa não se desconta a quem não trabalhou, para o total nunca ser negativo.
- **atraso** = primeira entrada depois da `start_time` do horário. Chegar adiantado dá 0, não negativo.
- **extra** = o que passa das horas previstas. Trabalhar num dia não previsto conta tudo como extra.
- **`incomplete`** = número ímpar de picagens (alguém esqueceu-se de picar a saída). O par fechado
  continua a contar; a picagem sem par é ignorada em vez de o dia ser descartado.
- **`scheduleChanges`** no resumo = quantas mudanças de horário o período atravessou. Mais de zero quer
  dizer que os totais somam dias calculados com horários diferentes — e isso diz-se, não se esconde.

O cálculo lê o horário **por dia** (`EmploymentTerm.termOn`), não uma vez para o período: é o que faz um
mês que atravessa uma mudança de condições ser calculado com o horário certo em cada metade.

## Feriados (`HolidayController`, `/attendance/holidays`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/holidays` (opcional `?from=&to=`) | `ADMIN` |
| GET | `/attendance/holidays/{id}` | `ADMIN` |
| POST | `/attendance/holidays` | `ADMIN` → `201` |
| PUT | `/attendance/holidays/{id}` | `ADMIN` |
| DELETE | `/attendance/holidays/{id}` | `ADMIN` → `204` (apagar a sério: um feriado errado não tem de ficar guardado) |

`scope` é `NATIONAL` ou `MUNICIPAL`. O concelho é **obrigatório** nos municipais (`ABS_003`) e
**recusado** nos nacionais (`ABS_004`) — um municipal sem concelho não se sabe onde se aplica, e um
nacional com concelho é contraditório. Mudar um municipal para nacional limpa o concelho.

## Ausências e férias (`AbsenceController`, `/attendance/absences`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/attendance/absences?profileId=&from=&to=` | `ADMIN` |
| GET | `/attendance/absences/team?from=&to=[&status=]` | `ADMIN` — **os dados do mapa de equipa** (o mapa é frontend) |
| GET | `/attendance/absences/pending` | `ADMIN` — o que está à espera de decisão |
| GET | `/attendance/absences/{id}` | `ADMIN` — detalhe, **com os justificativos assinados** |
| GET | `/attendance/absences/balance?profileId=[&year=]` | `ADMIN` — saldo de férias em dias úteis |
| POST | `/attendance/absences` | `ADMIN` → `201`, nasce `PENDING` |
| PUT | `/attendance/absences/{id}` | `ADMIN` — só enquanto `PENDING` |
| POST | `/attendance/absences/{id}/approve` | `ADMIN` |
| POST | `/attendance/absences/{id}/reject` | `ADMIN` |
| POST | `/attendance/absences/justify?profileId=&day=&type=[&note=]` | `ADMIN` → `201` — **justificar uma falta** |
| DELETE | `/attendance/absences/{id}` | `ADMIN` → `204`, soft-delete |
| POST | `/attendance/absences/{id}/restore` | `ADMIN` |
| POST | `/attendance/absences/{id}/documents` (multipart `file`) | `ADMIN` → `201` |
| DELETE | `/attendance/absences/{id}/documents/{documentId}` | `ADMIN` → `204` (apaga também no Storage) |

Decisões que explicam a forma destas rotas:

- **Nasce sempre `PENDING`, e aprovar é um passo à parte.** É o que faz o ato de decidir ficar
  registado com quem e quando. Só `APPROVED` faz o dia deixar de ser falta.
- **Justificar uma falta é criar uma ausência aprovada que cobre o dia** — não há um estado "falta
  justificada" em separado, porque a ausência *é* a justificação. Duas fontes de verdade para o mesmo
  dia divergiriam.
- **Uma falta `UNJUSTIFIED` continua a contar como falta.** Registá-la serve para ficar documentada,
  não para a esconder.
- **Sobreposições são recusadas na marcação** (`ABS_013`): duas ausências sobrepostas não têm
  significado — qual delas explicaria o dia? Uma ausência `REJECTED` não bloqueia.
- **Na lista os justificativos não vêm; no detalhe vêm assinados.** Assinar os documentos de um ano de
  ausências que ninguém vai abrir é trabalho deitado fora, como nas faturas.
- Justificativo: PDF ou imagem (`ABS_021`), 25 MB, bucket `documents`, chave
  `attendance/absence/<id>/<8 chars>_<nome>`. **Validado antes de subir** — subir primeiro deixa órfãos
  no bucket, que foi o que aconteceu com as faturas a 2026-09-18.

### Saldo de férias

Conta **dias úteis**, não dias de calendário: 22 é o mínimo legal em dias úteis. Por isso o saldo
precisa do horário (que dias da semana a pessoa trabalha) e dos feriados — 5 dias de férias que
atravessam um feriado descem **4** ao saldo, e uns que atravessam o fim de semana não gastam sábado nem
domingo. `PENDING` desconta do `available` (senão marcava-se o dobro dos dias que se tem e só se
descobria ao aprovar); `APPROVED` conta em `taken`. Meio dia conta 0,5. Umas férias que atravessam o
ano só gastam, em cada ano, os dias que nele caem.

### Códigos de erro

| Código | Quando |
|---|---|
| `ABS_001`–`ABS_004` | Feriado: não encontrado, duplicado na data+âmbito, concelho em falta, concelho a mais |
| `ABS_010` | Ausência não encontrada |
| `ABS_011` | Data de fim antes da de início |
| `ABS_012` | Meio dia num intervalo de vários dias |
| `ABS_013` | Sobreposição com outra ausência não recusada |
| `ABS_014` | Já foi aprovada ou recusada (editar e decidir só valem enquanto `PENDING`) |
| `ABS_015` | Restaurar uma ausência que não está anulada |
| `ABS_016` | Ausência antes da data de admissão |
| `ABS_020`/`ABS_021` | Justificativo não encontrado / tipo não permitido |

`ABS_017` está **reservado** para "saldo de férias insuficiente": bloquear a marcação por saldo é uma
decisão de negócio que ainda não foi tomada. Hoje o saldo pode ficar negativo e mostra-se como está, em
vez de recusar algo que o utilizador pode querer fazer de propósito.

## Notificações (`NotificationController`, `/notifications`)

Avisos in-app para utilizadores **internos**. O item original do backlog dizia "notificações
para o cliente", mas não existe cliente no modelo nem papel externo (`role_enum` é
`ADMIN`/`EMPLOYEE`) — ficou esclarecido a 2026-08-18 que o destinatário é sempre um `profile`.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/notifications` | `ADMIN` ou `EMPLOYEE` — só as próprias, mais recentes primeiro (`page`/`size`) |
| GET | `/notifications/unread-count` | `ADMIN` ou `EMPLOYEE` — `{ "count": n }`, é o contador do sino |
| PATCH | `/notifications/{id}/read` | `ADMIN` ou `EMPLOYEE` — só as próprias |
| PATCH | `/notifications/read-all` | `ADMIN` ou `EMPLOYEE` — `{ "updated": n }` |

**O destinatário vem sempre do token, nunca do URL** — não há rota para ver as notificações de
outra pessoa, nem sequer para `ADMIN`. Marcar como lida uma notificação alheia devolve
`NOTIF_001` (*não encontrada*) e **não** um 403: distinguir "não é tua" de "não existe"
confirmaria a existência a quem não devia sequer saber disso.

### Quem gera notificações

| Tipo | Quando | Para quem |
|---|---|---|
| `task_assigned` | `POST /tasks` e `PUT /tasks/{id}` | Os atribuídos, **menos** os que já estavam atribuídos antes e **menos** quem fez a atribuição |
| `invoice_pending` | `POST /construction-invoices` (upload) | **Só quem carregou** |
| `budget_item_deadline` | Job agendado (`BudgetItemDeadlineNotifierConfig`, 07:00 diário **e no arranque**) — rubricas `ITEM` vivas, de obras que não estejam `completed`/`archived`/`deleted`, com `end_date` entre hoje e hoje+N (`APP_BUDGET_DEADLINE_DAYS_AHEAD`, 7 por omissão) | **Todos os `ADMIN` desbloqueados**, uma vez por rubrica e destinatário (dedupe `existsByRecipientIdAndTypeAndEntityId`); adiar a data de uma rubrica já avisada **não** gera aviso novo |

> O `budget_item_deadline` corre também no arranque porque a app não está ligada 24h por dia —
> um cron às 7h numa app que só arranca às 9h nunca dispararia. O link é a página do orçamento da
> obra (`/backoffice/empreendimentos/{id}/budget`), sem realçar a rubrica. Decisões de 2026-09-16.

> ⚠️ Consequência conhecida do `invoice_pending`: alocar uma fatura a uma rubrica é
> `hasRole('ADMIN')`, mas carregar é `ADMIN` **ou** `EMPLOYEE`. Quando um empregado carrega uma
> fatura, o aviso vai para ele e **nenhum admin fica a saber** que há trabalho por fazer — o
> contador de pendentes na página do orçamento continua a ser o único sinal. Foi decisão
> explícita a 2026-08-18; mudar é trocar o destinatário para os ADMIN.

A escrita acontece **na mesma transação** da operação que a originou: se a tarefa não for
gravada, a notificação também não existe.

## Localizações (`LocationController`, `/locations`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/locations` | autenticado |
| POST | `/locations` | autenticado |
| GET | `/locations/{id}` | autenticado |
| PATCH | `/locations/{id}` | autenticado |
| GET | `/locations/countries` | autenticado |
| GET | `/locations/cities` | autenticado |
| GET | `/locations/municipalities` | autenticado |
| GET | `/locations/parishes` | autenticado |

## Registo de atividade (`ActivityLogController`, `/activities`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/activities/recent` | `ADMIN` ou `EMPLOYEE` |
| GET | `/activities/user/{userId}` | `ADMIN` ou `EMPLOYEE` |
| GET | `/activities/entity/{entityType}/{entityId}` | `ADMIN` ou `EMPLOYEE` |
| POST | `/activities/filter` | `ADMIN` ou `EMPLOYEE` |

---

## Provedores de email (`EmailProviderController`, `/settings/email-providers`)

A configuração SMTP em `settings.email_providers`. A tabela existe desde a `V7`, mas até à `V21`
**só era lida**: entrava por `INSERT` à mão e, sem uma linha lá, o convite de funcionário falhava
com `EMAIL_002` sem forma de o resolver sem acesso à base de dados.

Tudo `ADMIN` — são credenciais de envio, e quem as controla controla os emails que saem em nome
da plataforma. Reforçado em dois sítios: `@PreAuthorize` na classe e `.requestMatchers("/settings/**").hasRole("ADMIN")`
no `SecurityConfig`.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/settings/email-providers` | `ADMIN` — todos, o predefinido primeiro |
| GET | `/settings/email-providers/{id}` | `ADMIN` |
| POST | `/settings/email-providers` | `ADMIN` — devolve `201` |
| PUT | `/settings/email-providers/{id}` | `ADMIN` |
| PATCH | `/settings/email-providers/{id}/default` | `ADMIN` — passa a ser o predefinido; desmarca o anterior |
| PATCH | `/settings/email-providers/{id}/activate` | `ADMIN` |
| PATCH | `/settings/email-providers/{id}/deactivate` | `ADMIN` |
| POST | `/settings/email-providers/{id}/test` | `ADMIN` — envia email de teste; devolve `204` |
| DELETE | `/settings/email-providers/{id}` | `ADMIN` — devolve `204` |

**A password nunca sai.** O `EmailProviderResponseDTO` não a tem; traz `hasPassword: boolean` para
o formulário saber que já existe uma. Na escrita, o campo é obrigatório ao criar e opcional ao
editar — vir a `null`/vazio significa **manter a atual**, não apagar. Continua em texto simples na
coluna; encriptar em repouso ficou por fazer (ver [[notes/ToDo]]).

**Só um predefinido.** `EmailProviderService` desmarca os outros antes de marcar o novo; o índice
único parcial `uq_email_provider_single_default` (`V21`) é a rede por baixo. O **primeiro** provedor
criado nasce predefinido — sem isso configurava-se o SMTP e os emails continuavam a não sair, sem
nada a dizer porquê.

**O teste usa o provedor pedido, não o predefinido** — nem exige que esteja ativo. É o que permite
validar credenciais antes de as promover.

Códigos de erro: `EMAIL_001` (não encontrado), `EMAIL_002` (nenhum configurado), `EMAIL_003`
(predefinido desativado), `EMAIL_004` (falha no envio), `EMAIL_005` (falha no teste).

`EMAIL_002` e `EMAIL_003` são levantados **fora** do `try/catch` do envio, de propósito: falta de
configuração não é falha de envio, e antes saíam ambos como `ERR_001` ("erro interno do servidor"),
que não diz a ninguém que falta configurar o SMTP.

---

## Relacionado

- [[security.md]] — Regras de acesso completas e roles
- [[database.md]] — Entidades por trás destes endpoints
- [[architecture.md]] — Quem consome cada grupo de endpoints
- [[backend-conventions]] — Convenções e armadilhas do backend
- [[vault-sync-hooks]] — O hook que avisa quando um controller muda sem este ficheiro ser atualizado
