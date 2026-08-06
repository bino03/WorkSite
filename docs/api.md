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
| GET | `/auth/me` | autenticado |

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
| PUT | `/employees/{id}/avatar` | `ADMIN` |

## Perfil (`ProfileController`, `/profile`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/profile/agents` | autenticado |
| GET | `/profile/myprofile` | autenticado |
| PUT | `/profile/updateNamePhone` | autenticado |
| PUT | `/profile/updateEmail` | autenticado |
| PUT | `/profile/updatePassword` | autenticado |
| POST | `/profile/{authUserId}/photo` | autenticado (multipart) |
| GET | `/profile/{authUserId}/photo` | autenticado |
| DELETE | `/profile/{authUserId}/photo` | autenticado |
| POST | `/profile/photo-url` | autenticado |

## Projetos / Empreendimentos (`EnterpriseController`, `/enterprises`)

O domínio "projeto" do Worksite — os nomes `enterprise`/`enterprises` foram mantidos do projeto de origem para minimizar risco de migração.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/enterprises` | autenticado |
| GET | `/enterprises/active` | autenticado |
| GET | `/enterprises/basic` | autenticado |
| GET | `/enterprises/search` | autenticado |
| GET | `/enterprises/{id}` | autenticado |
| GET | `/enterprises/{id}/basic` | autenticado |
| POST | `/enterprises` | autenticado (multipart) |
| PUT | `/enterprises/{id}` | autenticado |
| DELETE | `/enterprises/{id}` | autenticado |
| POST | `/enterprises/{id}/addPhotos` | autenticado |
| POST | `/enterprises/{id}/photos/banner` | autenticado |
| DELETE | `/enterprises/{id}/photos/banner` | autenticado |
| PATCH | `/enterprises/{id}/media/{mediaId}` | autenticado |
| DELETE | `/enterprises/{id}/media/{mediaId}` | autenticado |

### Relações do projeto (`EntrepriseRelationsController`, `/enterprise-relations/{id}`)

| Método | Rota | Acesso |
|---|---|---|
| POST | `/enterprise-relations/{id}/location/upsert` | autenticado |
| DELETE | `/enterprise-relations/{id}/location` | autenticado |
| PATCH | `/enterprise-relations/{id}/overview` | autenticado |
| PATCH | `/enterprise-relations/{id}/dates-areas` | autenticado |
| PATCH | `/enterprise-relations/{id}/finance` | autenticado |

## Orçamento de Construção (`ConstructionBudgetItemController`, `/construction-budget`)

Árvore de rubricas do orçamento de obra (profundidade livre via `parentId`) — ver
[[database.md]] para o modelo e o significado de `rowKind`.

| Método | Rota | Acesso |
|---|---|---|
| GET | `/construction-budget/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — árvore completa com agregados |
| GET | `/construction-budget/items/{id}` | `ADMIN` ou `EMPLOYEE` — um nó e a sua sub-árvore |
| POST | `/construction-budget/items` | `ADMIN` |
| PUT | `/construction-budget/items/{id}` | `ADMIN` |
| PATCH | `/construction-budget/items/{id}/move?parentId=&sortOrder=` | `ADMIN` — reordenar / mudar de rubrica-mãe |
| DELETE | `/construction-budget/items/{id}` | `ADMIN` — leva a sub-árvore e as despesas |
| POST | `/construction-budget/enterprise/{enterpriseId}/import?dryRun=&replace=` | `ADMIN` — multipart `file` (.xlsx) |

**Agregados por nó** (nos `GET`): `rolledUpBudget` soma o `totalPrice` das **folhas** da
sub-árvore — somar todos os nós duplicaria, porque o Excel guarda o total do capítulo na
própria linha *e* o detalhe por baixo. `budgetMismatch` fica a `true` quando o total escrito
na linha não bate certo com essa soma, e `budgetVariance` diz de quanto
(`totalPrice − rolledUpBudget`; `null` quando não há os dois lados para comparar). Junta-se
`spentTotal`, `remaining`, `percentSpent`, `overBudget`, `expenseCount`, `ownExpenseCount`,
`missingInvoiceCount`, `pendingAccountantCount` e `pendingAccountantTotal`.

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
exige `replace=true` se o projeto já tiver orçamento.

## Despesas de Construção (`ConstructionExpenseController`, `/construction-expenses`)

| Método | Rota | Acesso |
|---|---|---|
| GET | `/construction-expenses/budget-item/{budgetItemId}` | `ADMIN` ou `EMPLOYEE` — lista de uma rubrica |
| GET | `/construction-expenses/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — lista plana do projeto, paginada |
| GET | `/construction-expenses/{id}` | `ADMIN` ou `EMPLOYEE` |
| POST | `/construction-expenses/scan-invoice?enterpriseId=` | `ADMIN` ou `EMPLOYEE` — lê o QR da AT; multipart `invoiceFile` |
| POST | `/construction-expenses` | `ADMIN` — multipart: `expenseData` (JSON) + `invoiceFile` opcional |
| PUT | `/construction-expenses/{id}` | `ADMIN` — mesma forma multipart |
| PATCH | `/construction-expenses/{id}/accountant?sent=` | `ADMIN` — marca/desmarca fatura enviada ao contabilista |
| DELETE | `/construction-expenses/{id}` | `ADMIN` |

Só rubricas com `rowKind = ITEM` aceitam despesas (`EXPENSE_009` caso contrário). A resposta
traz `invoiceUrl` como signed URL gerada na leitura, mais quem/quando fez o upload e
quem/quando marcou o envio para a contabilidade — incluindo `sentToAccountantByRole`
(`ADMIN`/`EMPLOYEE` em cru, o cliente é que traduz o rótulo).

`hasInvoice` é separado de `invoiceUrl` de propósito: a geração da signed URL pode falhar e
nesse caso `invoiceUrl` vem `null` — sem o booleano, o cliente não distinguiria "não tem
fatura" de "tem, mas não foi possível gerar o link".

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

### Leitura da fatura pelo QR code da AT

`POST /construction-expenses/scan-invoice` evita transcrever a fatura à mão. Não grava nada:
devolve os campos para o formulário e o utilizador confirma antes de criar a despesa.

Escolheu-se o QR em vez de OCR por ser **determinístico** — obrigatório nas faturas
portuguesas desde 2022, traz os campos fiscais já estruturados, corre offline e a fatura não
sai do servidor. Implementado em `AtInvoiceQrService` com ZXing (descodificação) e PDFBox
(rasterização quando a fatura é PDF; varre até 5 páginas, o QR costuma estar na última).

```jsonc
{
  "read": true,
  "issuerNif": "509442013", "buyerNif": "999999990",
  "documentType": "FT", "documentStatus": "N",
  "documentNumber": "FT 2026/114", "atcud": "CSDF7T5H-0114",
  "invoiceDate": "2026-01-15",
  "taxableAmount": 12000.00, "taxAmount": 2760.00, "totalAmount": 14760.00,
  "alreadyRegistered": [
    { "expenseId": "…", "expenseName": "Betão fundações",
      "budgetItemCode": "4.2.1", "budgetItemName": "Sapatas Isoladas",
      "expenseDate": "2026-01-15", "totalPrice": 3381.38 }
  ],
  "warnings": []
}
```

`alreadyRegistered` lista despesas do projeto com o mesmo ATCUD. É **aviso, não bloqueio**:
repartir uma fatura por várias rubricas da obra é prática normal. Os campos `supplierNif`,
`invoiceNumber` e `invoiceAtcud` ficam guardados na despesa — é o que torna esta deteção
possível.

`warnings` assinala documento anulado na AT (`documentStatus = "A"`), nota de crédito
(`documentType = "NC"`, que abate em vez de somar) e campos ilegíveis.

**Sem QR legível** — fornecedor estrangeiro, documento anterior a 2022, digitalização má —
devolve `read: false` com o resto a `null` e o preenchimento segue manual. Nunca é erro.

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

## ⚠️ Regras de segurança sem controller correspondente

`SecurityConfig` ainda contém regras herdadas do projeto de origem que **não têm nenhum controller neste repo**:

- `GET /open/**` e `POST /open/leads` → `permitAll()` (não existe package `client/` no Worksite)
- `POST /assets` → `hasRole("ADMIN")` (não existe `AssetController`)
- `POST /banners` → `hasRole("ADMIN")` (não existe `BannerController`)
- `/auth/accept-invite` → `permitAll()` (verificar se o endpoint existe)

São regras inertes hoje (nenhuma rota corresponde), mas convém limpá-las para o ficheiro refletir a superfície real da API — está registado em [[../notes/refactoring.md]]. Ver [[security.md]].

---

## Relacionado

- [[security.md]] — Regras de acesso completas e roles
- [[database.md]] — Entidades por trás destes endpoints
- [[architecture.md]] — Quem consome cada grupo de endpoints
- [[../management/managementapi/CLAUDE.md]] — Guia do backend
- [[vault-sync-hooks]] — O hook que avisa quando um controller muda sem este ficheiro ser atualizado
