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

A fatura é o **documento**; a despesa é a sua afetação a uma rubrica. Estão separados porque
**registar e classificar são momentos diferentes**: quem chega da obra com quinze faturas
carrega-as todas sem decidir nada, e classifica depois. Uma fatura sem despesa associada
(`allocated: false`) é o que está por classificar — é essa a caixa de entrada.

| Método | Rota | Acesso |
|---|---|---|
| POST | `/construction-invoices?enterpriseId=&originalSizeBytes=` | `ADMIN` ou `EMPLOYEE` — multipart `file`; devolve `201` |
| GET | `/construction-invoices/enterprise/{enterpriseId}` | `ADMIN` ou `EMPLOYEE` — caixa de entrada, paginada |
| GET | `/construction-invoices/enterprise/{enterpriseId}/pending-count` | `ADMIN` ou `EMPLOYEE` — quantas por associar |
| GET | `/construction-invoices/enterprise/{enterpriseId}/suggestion?supplierNif=` | `ADMIN` ou `EMPLOYEE` — rubrica sugerida; `204` sem histórico |
| GET | `/construction-invoices/{id}` | `ADMIN` ou `EMPLOYEE` — única resposta com `fileUrl` |
| PUT | `/construction-invoices/{id}` | `ADMIN` ou `EMPLOYEE` — correção manual |
| POST | `/construction-invoices/{id}/file` | `ADMIN` ou `EMPLOYEE` — substitui o ficheiro, relê o QR |
| POST | `/construction-invoices/{id}/rescan` | `ADMIN` ou `EMPLOYEE` — relê o QR do ficheiro arquivado e repõe os campos fiscais |
| PATCH | `/construction-invoices/{id}/allocate?budgetItemId=` | `ADMIN` — liga a uma rubrica, cria o lançamento |
| DELETE | `/construction-invoices/{id}/allocate` | `ADMIN` — desfaz, devolve à caixa de entrada |
| PATCH | `/construction-invoices/{id}/accountant?sent=` | `ADMIN` — marca/desmarca enviada ao contabilista |
| DELETE | `/construction-invoices/{id}` | `ADMIN` — apaga fatura, ficheiro, miniatura e lançamento |

Não há endpoint de lote **de propósito**: o cliente chama o `POST` uma vez por ficheiro
largado, para que cada um tenha o seu resultado e um QR ilegível não estrague os restantes.
`originalSizeBytes` é o tamanho antes da compressão feita no browser e serve só para mostrar
a poupança.

O upload **nunca falha por dados em falta**. Sem QR legível a fatura entra na mesma, com
`needsReview: true`, e alguém completa os campos depois. A obrigatoriedade de data e total só
aparece no `allocate` (`INVOICE_006`), porque é a despesa que os exige.

`needsReview` e `allocated` são **derivados**, não colunas: com uma fatura por rubrica, um
estado guardado só arriscava ficar dessincronizado.

`thumbnailUrl` vem em todas as respostas; `fileUrl` só no detalhe (`GET /{id}`). Assinar o
documento completo de cada linha de uma lista de 20 seria trabalho deitado fora — quase
nenhum é aberto. Ambas são signed URLs geradas na leitura; a chave de storage nunca sai daqui.

Resposta do upload e do `PUT /{id}/file`:

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

### Duplicados — bloqueio, em dois momentos

Um duplicado é **recusado**, não avisado. `duplicates` vem vazio no upload e no
`POST /{id}/file` justamente por isso: se houvesse um, o pedido não chegava a passar.
Só o `POST /{id}/rescan` ainda o preenche.

A verificação corre com **duas chaves**, porque servem momentos diferentes:

| Chave | Quando apanha | Erro |
|---|---|---|
| `invoiceAtcud` | o QR foi lido — é o identificador que a AT atribui ao documento | `INVOICE_010` |
| (`supplierNif`, `invoiceNumber`) | o QR falhou e alguém completou os campos à mão | `INVOICE_011` |

A segunda existe porque a primeira só funciona quando já não é precisa: sem QR legível não
há ATCUD, e a fatura entrava sem passar por verificação nenhuma. Quem completa uma fatura
"por rever" escreve o NIF e o número, raramente o ATCUD — e o mesmo fornecedor não emite dois
documentos com o mesmo número.

Por isso a verificação corre **no `PUT /{id}` também**, e não só no carregamento: é na
correção manual que uma fatura sem QR ganha identidade pela primeira vez. Sem isso, completar
à mão duas fotografias da mesma fatura criava dois lançamentos iguais no orçamento. No `PUT`
só corre quando a **identidade muda** (ATCUD, NIF ou número) — de outro modo um duplicado já
existente na base bloqueava qualquer edição, incluindo mexer só nas notas.

O ATCUD tem ainda uma garantia ao nível da base: `uq_invoice_enterprise_atcud` (`V17`), único
e parcial sobre `(enterprise_id, invoice_atcud)`. A verificação no serviço é
SELECT-depois-INSERT em transações separadas, e o cliente carrega com três pedidos em
paralelo — dois ficheiros iguais em voo ao mesmo tempo passavam os dois. Aconteceu: duas
linhas com o mesmo ATCUD gravadas com 20 ms de diferença. O índice fecha essa janela; o
serviço continua a existir para dar a mensagem legível em vez de uma violação de constraint.

O par (NIF, número) **não** leva índice de propósito: só entra em jogo na correção manual, uma
pessoa de cada vez, onde não há corrida — e um índice ali criaria falsos positivos em gralhas.

Filtros da caixa de entrada, todos opcionais e cumuláveis. `Page` com 20 por omissão,
ordenada por `uploadedAt` descendente:

| Parâmetro | Efeito |
|---|---|
| `allocated` | `false` → o que está por classificar (o que o cliente abre por omissão) |
| `needsReview` | `true` → falta a data ou o total |
| `sentToAccountant` | `false` → o que falta enviar |
| `from` / `to` | intervalo de `invoiceDate` (ISO `AAAA-MM-DD`) |
| `q` | procura no nome e NIF do fornecedor, número, ATCUD, nome do ficheiro e notas |

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

A procura é uma escalada, do barato para o caro: imagem inteira primeiro (duas binarizações),
e só se essa não trouxer um QR da AT é que a imagem é recortada em quadros sobrepostos, cada
um ampliado. O segundo degrau existe para a **fotografia da fatura inteira tirada ao
telemóvel**, em que o QR ocupa uns 150 px de 1600 — na imagem toda o detetor não o localiza.
Para PDF a escalada é 200 DPI → 300 DPI → mosaico, por isso o PDF nascido de um ERP
resolve-se logo no primeiro. Medido contra 19 fotografias reais de faturas: 8 lidas só com a
imagem inteira, 14 com o mosaico.

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
| `INVOICE_010` | já existe uma fatura com este ATCUD no projeto (upload, `/file` ou `PUT`) |
| `INVOICE_011` | já existe uma fatura deste fornecedor com este número (sobretudo no `PUT`) |

Ficheiro: PDF, JPEG ou PNG, até 25 MB, bucket `documents`, chave
`construction-invoices/{enterpriseId}/…`. A miniatura é um extra — falhar a gerá-la não custa
a fatura, a lista cai num ícone de ficheiro.

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
