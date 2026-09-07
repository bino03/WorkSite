# 🗄️ Base de Dados

PostgreSQL, gerido por **Flyway** em `management/managementapi/src/main/resources/db/migration/` (`V1` a `V30`). Três schemas: **`worksite`** (core do domínio), **`settings`** (convites/config) e **`tasks`** (tarefas standalone).

Só o backend (`managementapi`) tem acesso direto à base de dados — ver [[architecture.md]].

## Hierarquia Projeto → Orçamento de obra

```
enterprises (projeto — nome de tabela/pacote mantido do Property-Management)
 ├── enterprises_location / enterprises_media (1:1 / 1:N)
 ├── construction_invoice (o registo da fatura — dados do QR da AT + scope, N:1 → enterprises, nullable)
 │    └── construction_invoice_document (o ficheiro, 0..N por fatura — V24)
 └── construction_budget_item (rubrica do orçamento, N:1 → enterprises)
      └── construction_budget_item (parent_id — árvore de profundidade livre)
           └── construction_expense (a afetação, N:1 → construction_budget_item)
                — mesmos campos de medição da rubrica + invoice_id (nullable) + envio ao contabilista
```

**`construction_invoice`** e **`construction_expense`** estão separados desde a `V16` porque
**registar e classificar são momentos diferentes**: quem chega da obra com quinze faturas
carrega-as todas sem decidir nada, e classifica depois. Uma fatura sem despesa associada
(`invoice_id` de nenhuma linha aponta para ela) é a caixa de entrada — "por associar". Ver
[[api.md]] → "Faturas de obra".

- **`construction_invoice`** — o **registo** da fatura, não o ficheiro. `scope` decide onde
  ela vive (`V26`): `PROJECT` (de uma obra, `enterprise_id` obrigatório), `COMPANY` (despesa
  da empresa) ou `UNIDENTIFIED` (a quarentena — chegou uma fatura e ainda não se sabe de
  quem é). O check `ck_invoice_scope_enterprise` garante os dois lados: `PROJECT` **exige**
  `enterprise_id`, os outros dois **proíbem-no** — e é por isso que `enterprise_id` deixou de
  ser `NOT NULL`. `possible_enterprises` e `ask_whom` são as duas notas em texto livre que a
  quarentena precisa ("é capaz de ser da Vila Aleu", "perguntar ao Sr. António").
  Guarda ainda os campos lidos do QR code da AT (obrigatório nas faturas portuguesas desde
  2022) — `supplier_nif`, `invoice_number`, `invoice_atcud`, `invoice_date`, `total_amount`,
  `taxable_amount`, `tax_amount`. Todos **nullable**: sem QR legível a fatura entra na mesma,
  marcada "por rever" (`AtInvoiceQrService`, ver [[api.md]] → "Leitura do QR code da AT").
  `sent_to_accountant` (+ `_by`/`_at`) também vive aqui, não na despesa.
  - `document_status` (`V27`, enum `invoice_document_status`) diz o que se passa com o
    papel: `ARCHIVED` (há ficheiro), `MISSING`, `TO_PRINT`, `TO_REQUEST`. **Segue o papel, não
    é editado à mão para valer**: passa a `ARCHIVED` quando se junta um documento e volta a
    `MISSING` quando se largam todos. Índice parcial `idx_invoice_document_status` só sobre o
    que **não** está arquivado — é essa a lista de trabalho.
  - `description` (`V27`) é a descrição da despesa **na fatura**, para a folha `Despesas` do
    Excel não depender de haver já um lançamento.
  - `document_type` (`V28`, enum `invoice_document_type`): `INVOICE` ou `CREDIT_NOTE`. O
    check `ck_invoice_credit_note_target` obriga uma nota de crédito a apontar
    (`related_invoice_id`) para a fatura que corrige, e proíbe uma fatura normal de o fazer.
    O auto-FK é `ON DELETE RESTRICT`: apagar uma fatura com nota de crédito pendurada tem de
    ser um ato deliberado. **As regras de negócio da NC são a fase 3** — a `V28` só abre a
    coluna.
  - **Unicidade global desde a `V29`.** Os três índices de duplicado deixaram de ser por
    projeto: `uq_invoice_atcud` (substitui `uq_invoice_enterprise_atcud` da `V17`),
    `uq_invoice_nif_number` (o par, que antes não tinha índice nenhum) e
    `uq_invoice_document_checksum` (`V24`, na tabela dos documentos). Todos parciais — nulos
    não colidem. O mesmo ficheiro carregado na obra A é agora recusado na obra B, na
    quarentena e nas despesas da empresa, e a mensagem de erro **nomeia onde está a
    primeira**. Ver [[excel-parity.md]] §5.
- **`construction_invoice_document`** (`V24`) — os ficheiros, 0..N por fatura. Foi esta
  tabela que tirou o ficheiro de dentro da fatura (`V25` largou as 11 colunas:
  `bucket`, `storage_key`, `original_filename`, `mime_type`, `size_bytes`,
  `original_size_bytes`, `thumbnail_key`, `thumbnail_mime`, `checksum_sha256`,
  `uploaded_by`, `uploaded_at`). Duas coisas ficavam impossíveis enquanto a fatura *era* um
  ficheiro, e as duas aparecem todos os dias no vault da Vilatro: a fatura que ainda **não
  tem** documento nenhum (por pedir, por imprimir) e a que tem **mais do que um** — a foto
  tirada na obra e o PDF que o fornecedor mandou depois, ou um PDF partido página a página.
  `kind` (enum `invoice_document_kind`: `ORIGINAL`/`PAGE`/`PHOTO`/`OTHER`) e `page_number`
  servem só à UI para agrupar; nenhuma regra de negócio decide com base neles. `qr_payload`
  guarda o texto bruto do QR **deste** ficheiro para auditoria — os campos já interpretados
  continuam na fatura. `ON DELETE CASCADE` a partir da fatura.
- **`construction_expense`** — a afetação: `invoice_id` (nullable — uma despesa lançada à mão,
  sem documento, continua possível), `expense_date` (a data da **fatura**, deliberadamente
  distinta do `created_at`/data de registo — sem esta separação, lançar faturas atrasadas em
  bloco atirava-as todas para o mês em que foram escritas na app) e `total_price`.
  `uq_expense_invoice` garante 1 fatura → no máximo 1 despesa.

A árvore substituiu (em `V15`) a hierarquia rígida de dois níveis
`construction_stage` → `construction_sub_stage`, que não comportava os orçamentos reais:
o da Villa Petrus tem numeração a 4 níveis (`17.1.5`) e sub-títulos sem numeração pelo meio.

Cada rubrica espelha uma linha do Excel de orçamento:

| Coluna Excel | Coluna |
|---|---|
| `Art` | `code` — `"4.2.1"` tal como no Excel; nulo em títulos, notas e alternativas |
| `Descrição` | `name` |
| `Un.` | `unit` |
| `Quant` | `quantity` |
| `Preço Un` | `unit_price` |
| `Preço total` | `total_price` |
| `Obs.` | `observations` |

`row_kind` (enum `worksite.budget_row_kind`) distingue o papel da linha:

- **`ITEM`** — rubrica normal. Nem sempre tem `code`: as linhas "Alternativa ..." não são
  numeradas mas são elas que trazem o preço efectivo quando a rubrica numerada acima ficou
  com o total vazio.
- **`HEADING`** — sub-título sem numeração (`Paredes`, `Pavimentos`, `Tectos`). Agrupa as
  rubricas seguintes até ao título seguinte, por isso na árvore é o **pai** delas.
- **`NOTE`** — nota de contexto entre parêntesis, filha da rubrica anterior.

Só rubricas `ITEM` aceitam despesas. `code` é único por projeto (índice parcial
`uq_budget_item_code`, que ignora os nulos).

Eliminação em cascata (`ON DELETE CASCADE`) em toda a cadeia, incluindo a FK
auto-referenciada — eliminar um projeto, ou uma rubrica, leva a sub-árvore e as despesas
atrás. O ficheiro de fatura é guardado apenas como `bucket`/`storage_key` **na sua linha de
`construction_invoice_document`** (bucket
`"documents"`), nunca a URL bruta — ver [[skill-add-file-upload]].

## Outras tabelas principais

| Tabela | Schema | Propósito |
|---|---|---|
| `supplier` | `worksite` | Catálogo NIF → nome da empresa (`V19`). O QR da AT só traz o NIF do emitente; sem isto o nome era escrito à mão fatura a fatura. Global, sem `enterprise_id` — o mesmo NIF é a mesma empresa em todas as obras — e ligado às faturas **pelo NIF, não por FK**, para uma fatura poder existir com um fornecedor ainda desconhecido. `nif` único (`uq_supplier_nif`) |
| `profile` | `worksite` | Utilizador interno (liga a `auth_user_id` do Supabase); `role` = `ADMIN` ou `EMPLOYEE` |
| `location` | `worksite` | Localização standalone (endereço, cidade, coordenadas), reutilizada por `enterprises` |
| `activity_log` | `worksite` | Auditoria genérica (login/logout + CRUD em `enterprises`/`construction_budget_item`/`construction_expense`) |
| `revoked_token` | `worksite` | Lista negra de JWTs revogados (logout/invalidação) |
| `settings.pending_invites` | `settings` | Convites de acesso pendentes (email, role, token) |
| `settings.password_reset_tokens` | `settings` | Pedidos de recuperação de password (`V22`). Espelha `pending_invites`: token opaco + prazo (1 hora) + `used_at`. `auth_user_id` **sem FK** — o schema `auth` é do Supabase e não se referencia a partir das nossas migrações, tal como em `profile.auth_user_id`. Um pedido novo queima os anteriores por usar do mesmo utilizador |
| `settings.email_providers` | `settings` | Configuração SMTP (`V7`), gerível pelo Backoffice desde a `V21` (`EmailProviderController`). Índice único parcial `uq_email_provider_single_default` — só uma linha pode ter `is_default`. O trigger de `updated_at` da `V11` só percorre o schema `worksite`, por isso o desta tabela foi criado à mão na `V21`. `password` em **texto simples**; a API nunca a devolve |
| `tasks.task` | `tasks` | Tarefa standalone (nome, descrição, prazo, estado), sem ligação a nenhum ativo/imóvel |
| `tasks.task_assignee` | `tasks` | Junção many-to-many entre `tasks.task` e `worksite.profile` — utilizadores atribuídos |
| `notification` | `worksite` | Notificações in-app dirigidas a um `profile` (`V20`). `title`/`body` guardados **já escritos**, não tipo + parâmetros: torna a leitura um `select` simples, ao custo de o histórico ficar na língua em que nasceu. `entity_id` **sem FK** de propósito — aponta para tabelas diferentes conforme o `type`, e o aviso deve sobreviver ao desaparecimento da origem. `read_at` nulo = por ler |

## Convenções

- Todas as PKs são `UUID DEFAULT gen_random_uuid()`, exceto `revoked_token` (BIGSERIAL).
- Trigger genérico `worksite.tg_set_updated_at()` (definido em `V1`) mantém `updated_at` automaticamente — aplicado a todas as tabelas `worksite` com essa coluna via loop dinâmico em `V11`, e explicitamente às tabelas de construção em `V15`.
- Enums nativos do Postgres: `role_enum` (`ADMIN`/`EMPLOYEE`), `account_status_enum` (`unlocked`/`blocked`/`deleted`), `media_type_enum`, `visibility_enum`, `activity_type`, `entity_type` (`V2`) e `budget_row_kind` (`ITEM`/`HEADING`/`NOTE`, `V15`). A fase 1 da paridade com o Excel acrescentou quatro: `invoice_document_kind` (`V24`), `invoice_scope` (`V26`), `invoice_document_status` (`V27`) e `invoice_document_type` (`V28`).
- `notification.type` é **texto e não enum** (`V20`): um tipo novo não vale uma migração, e nada no backend decide nada com base no valor — serve ao frontend para escolher o ícone.
- `entity_type` ganhou `budget_item` em `V15`, `construction_invoice` em `V16`, `supplier` em `V19` e `email_provider` em `V21`. Os valores `construction_stage` e `construction_sub_stage` **mantêm-se de propósito**: há linhas históricas em `activity_log` que ainda os referenciam, e um valor não se remove de um enum do Postgres.
- `enterprises` tem `slug` e `is_test` desde a `V23`: o `slug` é o nome da pasta desta obra no vault Excel da Vilatro (`Vila Petrus`), único quando preenchido (`ENT_032`), e é ele que faz a ponte entre os dois sistemas — ver [[excel-parity.md]] §2; `is_test` (NOT NULL, `false`) marca as obras que existem só para experimentar, para os relatórios as poderem excluir.
- `V8` concede permissões explícitas aos roles do Supabase (`anon`, `authenticated`, `service_role`) — necessário porque a validação de JWT é feita localmente pelo backend, mas o Supabase continua a gerir os utilizadores de autenticação (`auth.users`). A `V30` acrescenta o role de leitura `worksite_expenses_ro` (criado à mão fora do Flyway; a migração cria-o `NOLOGIN` se faltar) e dá-lhe `SELECT` também na `construction_invoice_document`. **Está lá por inércia**: servia um frontend de consulta que foi descartado a 2026-09-06.
- `V9` cria a FK condicional `profile.auth_user_id → auth.users(id)` (só se o schema `auth` existir — é o caso quando a app corre contra um projeto Supabase real).

## Deixado de fora (deliberadamente)

Não copiado do Property-Management: `property_asset`, `buildings`, `agency`, `contact`, `license`, `characteristic_*`, `lead`, `banner`, o schema `payments`. Nenhuma destas tabelas foi pedida para este projeto — são candidatas a funcionalidades futuras, não uma lacuna.

`task`/`task_assignee` **foram** copiadas (`V14`), mas isoladas no seu próprio schema `tasks` em vez de `worksite` — e sem o campo opcional `asset_id` que existia no original (não há conceito de imóvel/ativo aqui).

## Relacionado

- [[architecture.md]] — Como o backend acede à base de dados
- [[security.md]] — `profile.role` e como é usado na autorização
- [[../management/managementapi/CLAUDE.md]] — Guia do backend
