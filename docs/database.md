# 🗄️ Base de Dados

PostgreSQL, gerido por **Flyway** em `management/managementapi/src/main/resources/db/migration/` (`V1` a `V44`). Quatro schemas: **`worksite`** (core do domínio), **`settings`** (convites/config), **`tasks`** (tarefas standalone) e **`attendance`** (assiduidade, horas e férias).

> ⚠️ Um schema novo tem de entrar em `spring.flyway.schemas` no `application.yml` (`create-schemas: true` cria-o), senão a migração falha. E o trigger de `updated_at` **não** é automático fora de `worksite`: o `DO $$` da `V11` só percorre `table_schema = 'worksite'`, por isso cada tabela de `settings`, `tasks` ou `attendance` declara o seu `CREATE TRIGGER` à mão.

Só o backend (`managementapi`) tem acesso direto à base de dados — ver [[architecture.md]].

## Hierarquia Projeto → Orçamento de obra

```
enterprises (projeto — nome de tabela/pacote mantido do Property-Management)
 ├── enterprises_location / enterprises_media (1:1 / 1:N)
 ├── construction_invoice (o registo da fatura — dados do QR da AT + scope, N:1 → enterprises, nullable)
 │    ├── construction_invoice_document (o ficheiro, 0..N por fatura — V24)
 │    └── invoice_payment (junção N:N → payment — V31; estado UNPAID/PARTIAL/PAID é derivado)
 └── construction_budget (o lote/edifício — um orçamento cada, V39)
      └── construction_budget_item (rubrica do orçamento, N:1 → construction_budget; enterprise_id mantido)
           └── construction_budget_item (parent_id — árvore de profundidade livre, sempre no mesmo lote)
                └── construction_expense (a afetação, N:1 → construction_budget_item)
                     — mesmos campos de medição da rubrica + invoice_id (nullable) + envio ao contabilista
```

**`construction_budget`** (`V39`) — o **lote**: um edifício do empreendimento com o seu próprio
orçamento (`name`, único por projeto entre os lotes vivos; `sort_order`). Uma vila pode ter vários,
cada um com a sua numeração. A migração criou um lote `"Orçamento"` em cada projeto que já tinha
rubricas; um projeto novo não tem nenhum até se criar o primeiro. A rubrica guarda `budget_id` **e**
`enterprise_id` (desnormalizado, para todas as verificações "rubrica da obra da fatura" e as queries
por projeto continuarem iguais); a FK composta `(budget_id, enterprise_id) → construction_budget(id, enterprise_id)`
impede que divirjam. Soft delete (`deleted_at`, `V40`) em vez do hard delete original da `V39` — apagar
um lote arrasta as rubricas que ainda lhe restarem vivas para a mesma marca de tempo (a filtragem por
`deleted_at` que já existe em toda a árvore/pesquisa/totais esconde-as sem precisar de saber nada sobre
lotes); sem zona de recuperação dedicada, reverte-se na BD. Bloqueado se alguma rubrica do lote tiver
despesas (`BUDGET_017`). Ao apagar um lote, as faturas que lhe pertenciam ficam sem lote (ver `construction_invoice.budget_id` abaixo).

**`construction_invoice`** e **`construction_expense`** estão separados desde a `V16` porque
**registar e classificar são momentos diferentes**: quem chega da obra com quinze faturas
carrega-as todas sem decidir nada, e classifica depois. Uma fatura sem despesa associada
(`invoice_id` de nenhuma linha aponta para ela) é a caixa de entrada — "por associar". Ver
[[api.md]] → "Faturas de obra".

- **`construction_invoice`** — o **registo** da fatura, não o ficheiro. `budget_id` (`V41`, nullable) é o lote da
  fatura: FK composta `(budget_id, enterprise_id)` → `construction_budget`, `ON DELETE SET NULL (budget_id)`, e
  `ck_invoice_budget_scope` impede lote fora de `PROJECT`. Numa obra de vários lotes, classificar exige o lote
  primeiro (`INVOICE_051`); as rubricas da fatura são todas desse lote (`INVOICE_050`). O backfill da `V41` só
  preenche obras de um só lote. `scope` decide onde
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
    ser um ato deliberado. **A lógica da NC entrou na fase 3** (sem migração): a NC vive nesta
    mesma linha (`document_type = CREDIT_NOTE`, `related_invoice_id` = a origem, que tem de ser
    um `INVOICE` — "sem NC de NC", validado no serviço), `total_amount` **positivo** (o valor da
    NC), herda `scope`/`enterprise`/NIF da origem. **Líquido de uma fatura = `total_amount − Σ
    total_amount das suas NC`** — não é coluna, calcula-se no serviço; é o que os pagamentos
    cobrem e o que o filtro "por liquidar" usa. A NC gera as suas próprias `construction_expense`
    com `total_price` **negativo**, na proporção da origem — desde a `V32` são N, portanto uma NC
    sobre uma fatura repartida 70/30 propõe −70/−30. A soma da repartição de uma NC **não** é
    imposta (avisa e grava): a NC corrige uma fatura que já pode estar torta. Ver [[api.md]] →
    "Notas de crédito".
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
  - **Uma fatura pode ser repartida por N rubricas desde a `V32`**, que largou o
    `uq_expense_invoice` (a `V16` já escrevia que bastaria largá-lo) e o substituiu pelo índice
    não-único `idx_expense_invoice`. O que forçava a folha do armazém — cimento e ferragens no
    mesmo papel — a ir toda para uma rubrica era esse índice, e era o principal motivo para o
    gasto por rubrica não bater certo com o Excel da Vilatro.
  - **`name` é nullable desde a `V32`**: uma linha de repartição não tem nome próprio, herda o da
    fatura. Continua obrigatório numa despesa lançada à mão, onde não há de onde herdar — isso
    valida-se no `ConstructionExpenseUpsertDTO`, não na coluna.
  - Repartir **substitui** a repartição inteira, nunca acrescenta a ela, e a soma tem de esgotar
    o `total_amount` da fatura (`INVOICE_028`). A exceção é a fatura ainda sem total: classifica-se
    na mesma e as linhas nascem a zero — é o `allocationStatus = PROVISIONAL` da API.
- **`payment`** (`V31`) — um **movimento** de dinheiro: `paid_on`, `method` (enum
  `payment_method`: `NUMERARIO`/`MULTIBANCO`/`TRANSFERENCIA`/`OUTRO` — mapa dos valores do
  Excel em [[excel-parity.md]] §4), `amount`, `reference`, `notes`, `proof_*` (recibo ou
  página do extrato — um ficheiro, opcional, `bucket`+`key` no bucket `documents`) e
  `registered_by`/`registered_at` (quem deu como pago; FK → `profile` `ON DELETE SET NULL`).
- **`invoice_payment`** (`V31`) — junção `payment` ↔ `construction_invoice`, PK composta
  `(payment_id, invoice_id)`, `amount` = quanto **deste** movimento cobre **esta** fatura.
  Caso normal: uma linha com `amount = payment.amount`. O caso raro (uma transferência paga N
  faturas — decisão 23 do Vilatro) é o mesmo modelo com N linhas. `ON DELETE CASCADE` dos dois
  lados. As invariantes (`Σ ligações = payment.amount`; por fatura `Σ ≤ líquido`) são do
  serviço (`PaymentService`), não constraints. **O estado de pagamento da fatura — `UNPAID` /
  `PARTIAL` / `PAID` — é derivado, nunca coluna**: `PaymentService.deriveStatus` compara
  `Σ(invoice_payment.amount)` com o líquido (`total_amount` na fase 2; `total_amount − Σ notas
  de crédito` a partir da fase 3). As listas de faturas aceitam o filtro `outstanding` (por
  liquidar), aplicado por subquery no JPQL de `search`/`searchByScope`. Ver
  [[api.md]] → "Pagamentos".

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
| — | `deleted_at` — soft delete (`V36`). Não nulo = eliminada; nunca aparece nas leituras normais |

`row_kind` (enum `worksite.budget_row_kind`) distingue o papel da linha:

- **`ITEM`** — rubrica normal. Nem sempre tem `code`: as linhas "Alternativa ..." não são
  numeradas mas são elas que trazem o preço efectivo quando a rubrica numerada acima ficou
  com o total vazio.
- **`HEADING`** — sub-título sem numeração (`Paredes`, `Pavimentos`, `Tectos`). Agrupa as
  rubricas seguintes até ao título seguinte, por isso na árvore é o **pai** delas.
- **`NOTE`** — nota de contexto entre parêntesis, filha da rubrica anterior.

Só rubricas `ITEM` aceitam despesas. `code` é único **por lote** desde a `V39` (antes, por projeto) —
índice parcial `uq_budget_item_code (budget_id, code)`, que ignora os nulos **e** as eliminadas desde a `V36`.

Eliminação em cascata (`ON DELETE CASCADE`) em toda a cadeia, incluindo a FK
auto-referenciada — eliminar um **projeto** continua a levar a sub-árvore e as despesas atrás,
de imediato. Eliminar uma **rubrica** (o `DELETE` do ecrã) já não: desde a `V36` é soft delete —
marca `deleted_at` na rubrica e em toda a sub-árvore (bloqueado com `BUDGET_013` se houver
despesas nalgum nó), e só a purga automática 30 dias depois (job agendado,
`ConstructionBudgetItemPurgeConfig`) é que apaga a linha a sério, e aí sim com a cascata da FK.
Ver [[api.md]] → "Orçamento de Construção". O ficheiro de fatura é guardado apenas como
`bucket`/`storage_key` **na sua linha de `construction_invoice_document`** (bucket
`"documents"`), nunca a URL bruta — ver [[skill-add-file-upload]].

## Outras tabelas principais

| Tabela | Schema | Propósito |
|---|---|---|
| `supplier` | `worksite` | Catálogo NIF → nome da empresa (`V19`). O QR da AT só traz o NIF do emitente; sem isto o nome era escrito à mão fatura a fatura. Global, sem `enterprise_id` — o mesmo NIF é a mesma empresa em todas as obras — e ligado às faturas **pelo NIF, não por FK**, para uma fatura poder existir com um fornecedor ainda desconhecido. `nif` único (`uq_supplier_nif`) |
| `profile` | `worksite` | Utilizador interno (liga a `auth_user_id` do Supabase); `role` = `ADMIN` ou `EMPLOYEE` |
| `location` | `worksite` | Localização standalone (endereço, cidade, coordenadas), reutilizada por `enterprises` |
| `activity_log` | `worksite` | Auditoria genérica (login/logout + CRUD em `enterprises`/`construction_budget_item`/`construction_expense`). Desde a `V33`, uma **transferência de fatura** grava aqui uma linha `activity_type = transfer` com a repartição antiga (da fatura e das NC ligadas) em `metadata` JSONB — é a fonte do `transfers[]` do detalhe da fatura |
| `invoice_incident` | `worksite` | Inconsistências (`V33`, fase 5): nota livre em **markdown** (`title`, `body`) sobre faturas por conciliar, tipicamente pós-transferência. `resolved_at`/`resolved_by` nulos = por resolver. Sem soft-delete. `created_by`/`resolved_by` → `profile` `on delete set null` |
| `invoice_incident_invoice` | `worksite` | Junção N:N `invoice_incident` ↔ `construction_invoice` (`V33`), PK composta, `ON DELETE CASCADE` dos dois lados |
| `revoked_token` | `worksite` | Lista negra de JWTs revogados (logout/invalidação) |
| `settings.pending_invites` | `settings` | Convites de acesso pendentes (email, role, token) |
| `settings.password_reset_tokens` | `settings` | Pedidos de recuperação de password (`V22`). Espelha `pending_invites`: token opaco + prazo (1 hora) + `used_at`. `auth_user_id` **sem FK** — o schema `auth` é do Supabase e não se referencia a partir das nossas migrações, tal como em `profile.auth_user_id`. Um pedido novo queima os anteriores por usar do mesmo utilizador |
| `settings.email_providers` | `settings` | Configuração SMTP (`V7`), gerível pelo Backoffice desde a `V21` (`EmailProviderController`). Índice único parcial `uq_email_provider_single_default` — só uma linha pode ter `is_default`. O trigger de `updated_at` da `V11` só percorre o schema `worksite`, por isso o desta tabela foi criado à mão na `V21`. **`password` cifrada em repouso** (AES-256-GCM) desde a `V34`: coluna passou a `text`, valor no formato `gcm:<base64 iv>:<base64 ct+tag>`, cifra/decifra transparente pelo `EncryptedStringConverter` com a chave `APP_EMAIL_CRYPTO_KEY`. Um valor sem o prefixo `gcm:` é lido como texto em claro legado e re-cifrado no arranque seguinte (`EmailProviderPasswordReEncryptRunner`). A API nunca a devolve. Rotação de chave: ver [[operations]] |
| `tasks.task` | `tasks` | Tarefa standalone (nome, descrição, prazo, estado), sem ligação a nenhum ativo/imóvel |
| `tasks.task_assignee` | `tasks` | Junção many-to-many entre `tasks.task` e `worksite.profile` — utilizadores atribuídos |
| `notification` | `worksite` | Notificações in-app dirigidas a um `profile` (`V20`). `title`/`body` guardados **já escritos**, não tipo + parâmetros: torna a leitura um `select` simples, ao custo de o histórico ficar na língua em que nasceu. `entity_id` **sem FK** de propósito — aponta para tabelas diferentes conforme o `type`, e o aviso deve sobreviver ao desaparecimento da origem. `read_at` nulo = por ler |

## Schema `attendance` — assiduidade, horas e férias

Módulo novo (`V42`), isolado fora de `worksite` pela mesma razão que o `tasks`: é um domínio inteiro
que liga a `worksite` só por FK a `profile` e `enterprises`. O desenho completo, com as decisões e o
porquê de cada uma, está em [[../notes/roadmap/assiduidade]]; aqui fica só o que existe hoje.

| Tabela | O que guarda |
|---|---|
| `work_schedule` | Catálogo de horários reutilizáveis ("08–17 c/ 1h almoço"), **atribuíveis** a funcionários — um horário não é um campo do funcionário, porque cada trabalhador pode ter o seu. Soft-delete (`deleted_at`): um horário apagado tem de continuar legível para recalcular meses passados. Único parcial no nome (`uq_work_schedule_name … where deleted_at is null`). Sem coluna `active` de propósito — `deleted_at` já exprime "não oferecer em atribuições novas" |
| `work_schedule_day` | O horário dia-a-dia da semana: `weekday` (1=segunda … 7=domingo, ISO-8601, igual ao `DayOfWeek` do Java), `start_time`, `end_time`, `break_minutes`. **Um dia que não está na tabela não é dia de trabalho** — é assim que fins de semana e horários parciais se exprimem, sem flag. A pausa vive aqui e não nas picagens: ninguém pica o almoço, o horário declara-o e as horas do dia descontam-no |

| `employment` | O vínculo de um funcionário: o que **não** muda — `profile_id` (único), `hired_at`, `ended_at`. Separado de `worksite.profile` de propósito: o perfil é identidade/login e um admin pode não ter dados de emprego |
| `employment_term` | As **condições durante um período**: `work_schedule_id`, `vacation_days_per_year` (default 22, editável por pessoa), `valid_from`, `valid_to`. É o que faz um relatório de janeiro continuar correto depois de mudar o horário em março. Único parcial `uq_employment_term_current … where valid_to is null` — só pode haver um período em vigor |
| `time_entry` | **A picagem**: `profile_id`, `enterprise_id` (nullable), `happened_at timestamptz`, `direction` (IN/OUT), `source` (MANUAL), `registered_by` (nullable = o próprio), `note`, `deleted_at`. Só soft-delete, nunca `delete` físico |
| `time_entry_revision` | O estado **anterior** de cada alteração a uma picagem, escrito na **mesma transação**. Append-only: não tem `updated_at` nem trigger — uma revisão que se pudesse alterar não provava nada |
| `holiday` | Feriados (`V44`): `holiday_date`, `name`, `scope` (nacional/municipal), `municipality`. **Tabela e não biblioteca de código**: os municipais variam por concelho e mudam de ano para ano, e uma tabela que o utilizador edita bate código que alguém tem de ir corrigir. `ck_holiday_municipality` obriga a concelho nos municipais e proíbe-o nos nacionais |
| `absence` | Ausências (`V44`): férias, baixa, falta justificada ou injustificada. `starts_on`/`ends_on` (intervalo fechado nos dois extremos), `half_day` (NONE/MORNING/AFTERNOON, só num dia só — `ck_absence_half_day`), `status` (PENDING/APPROVED/REJECTED), `approved_by`/`approved_at`, soft-delete |
| `absence_document` | Justificativos (`V44`), **0..N**: `bucket` + `storage_key`, `original_filename`, `mime_type`, `size_bytes`. Tabela própria e não colunas na `absence` porque **pode chegar um segundo ficheiro** — uma baixa de duas páginas, uma prorrogação. É a pergunta que a skill `add-file-upload` manda fazer, e foi ignorá-la que obrigou à `V24` nas faturas |

**O que a `V44` arranjou**: antes dela, um feriado e um dia de férias apareciam como *falta por
justificar*, porque o cálculo só sabia olhar para o horário e para as picagens.

`uq_holiday_date_scope` usa `coalesce(municipality, '')` porque **dois NULL não colidem num unique** —
sem isso, o mesmo feriado nacional entrava duas vezes na mesma data.

**Porque é que a `time_entry` não precisa de ser refeita**: duas colunas. `source` guarda o *método* como
dado (hoje só `MANUAL`; o QR e os outros entram por `alter type`, sem migrar nem recalcular), e
`enterprise_id` vive em **cada** picagem, não no dia — o QR por obra traz a obra de graça no momento da
picagem, e guardar por dia perderia quem andou em duas obras no mesmo dia.

**Porque é que há uma tabela de revisões e não só o `activity_log`**: o `ActivityLogger` é `@Async` e
pode perder linhas numa falha. Para faturas isso nunca importou; num registo legal de assiduidade é
precisamente o registo corrigido que uma auditoria põe em causa. O `activity_log` continua a receber a
sua linha, para o histórico geral. `changed_by_name` fica guardado já escrito (como no `activity_log`):
o nome numa auditoria tem de ser o que a pessoa tinha na altura e sobreviver a apagar o perfil.

**`on delete` escolhidos a dedo**: `time_entry.profile_id` é `restrict` (um funcionário com picagens não
se apaga — a lei obriga a guardar os registos); `enterprise_id` é `set null` (apagar uma obra não pode
apagar o registo de que alguém trabalhou); `employment_term.work_schedule_id` é `restrict` (um horário
de um período passado não desaparece, senão esse período deixava de poder ser recalculado).

**Horas locais, não instantes**: `start_time`/`end_time` são `time`, não `timestamptz`. A entrada é às
08:00 em janeiro e em julho mesmo que o instante UTC não seja o mesmo; o fuso (`Europe/Lisbon`) aplica-se
no cálculo. O resto do projeto é todo `timestamptz` + UTC (`jdbc.time_zone`), e para faturas isso é
inofensivo — para assiduidade seria errado.

`ck_work_schedule_day_order` (`end_time > start_time`) é o que garante que um dia de trabalho **não
atravessa a meia-noite**: turnos noturnos não são suportados (decisão de 2026-10-06), e a constraint
impede que o cálculo receba um caso que não sabe tratar. As mesmas regras existem no
`WorkScheduleService` — a duplicação é deliberada: a BD é a última defesa, mas só devolve uma violação
opaca; o service diz *qual* regra falhou, com um `SCHED_xxx`.

## Convenções

- Todas as PKs são `UUID DEFAULT gen_random_uuid()`, exceto `revoked_token` (BIGSERIAL).
- Trigger genérico `worksite.tg_set_updated_at()` (definido em `V1`) mantém `updated_at` automaticamente — aplicado a todas as tabelas `worksite` com essa coluna via loop dinâmico em `V11`, e explicitamente às tabelas de construção em `V15`.
- Enums nativos do Postgres: `role_enum` (`ADMIN`/`EMPLOYEE`), `account_status_enum` (`unlocked`/`blocked`/`deleted`), `media_type_enum`, `visibility_enum`, `activity_type`, `entity_type` (`V2`) e `budget_row_kind` (`ITEM`/`HEADING`/`NOTE`, `V15`). A fase 1 da paridade com o Excel acrescentou quatro: `invoice_document_kind` (`V24`), `invoice_scope` (`V26`), `invoice_document_status` (`V27`) e `invoice_document_type` (`V28`). A fase 2 acrescentou `payment_method` (`V31`).
- `notification.type` é **texto e não enum** (`V20`): um tipo novo não vale uma migração, e nada no backend decide nada com base no valor — serve ao frontend para escolher o ícone. Tipos hoje: `task_assigned`, `invoice_pending`, `budget_item_deadline` (este último gerado por job, com dedupe por `(recipient_id, type, entity_id)` **no serviço, não por unique index** — os gatilhos manuais podem legitimamente repetir um tipo para a mesma entidade).
- `entity_type` ganhou `budget_item` em `V15`, `construction_invoice` em `V16`, `supplier` em `V19`, `email_provider` em `V21`, `payment` em `V31` e `invoice_incident` em `V33`; `activity_type` ganhou `transfer` em `V33` (a transferência de fatura da fase 5). Os valores `construction_stage` e `construction_sub_stage` **mantêm-se de propósito**: há linhas históricas em `activity_log` que ainda os referenciam, e um valor não se remove de um enum do Postgres.
- `enterprises` tem `slug` e `is_test` desde a `V23`: o `slug` é o nome da pasta desta obra no vault Excel da Vilatro (`Vila Petrus`), único quando preenchido (`ENT_032`), e é ele que faz a ponte entre os dois sistemas — ver [[excel-parity.md]] §2; `is_test` (NOT NULL, `false`) marca as obras que existem só para experimentar, para os relatórios as poderem excluir.
- **Grants**: `V8` dá `GRANT ALL` em `worksite`/`settings` a `service_role` (o do próprio Supabase, para as suas APIs) — é o único grant que sobrevive. O `USAGE` que a `V8` dava a `anon`/`authenticated` e o role de leitura `worksite_expenses_ro` da `V30` (de um frontend descartado a 2026-09-06) foram **revogados na `V37`**: só o backend liga à base de dados, como `postgres`, e não há RLS — porquê em [[security]] → "Modelo de confiança na base de dados".
- `V9` cria a FK condicional `profile.auth_user_id → auth.users(id)` (só se o schema `auth` existir — é o caso quando a app corre contra um projeto Supabase real).

## Deixado de fora (deliberadamente)

Não copiado do Property-Management: `property_asset`, `buildings`, `agency`, `contact`, `license`, `characteristic_*`, `lead`, `banner`, o schema `payments`. Nenhuma destas tabelas foi pedida para este projeto — são candidatas a funcionalidades futuras, não uma lacuna.

`task`/`task_assignee` **foram** copiadas (`V14`), mas isoladas no seu próprio schema `tasks` em vez de `worksite` — e sem o campo opcional `asset_id` que existia no original (não há conceito de imóvel/ativo aqui).

## Relacionado

- [[architecture.md]] — Como o backend acede à base de dados
- [[security.md]] — `profile.role` e como é usado na autorização
- [[backend-conventions]] — Convenções e armadilhas do backend
