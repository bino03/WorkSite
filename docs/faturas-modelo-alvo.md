# Faturas de obra — modelo alvo

> **Estado: desenho aprovado, por implementar.** Descreve o que a app **vai** ter para chegar à
> paridade com o Excel da Vilatro. O que a app tem **hoje** está em [[database.md]] e [[api.md]].
> O mapa coluna-a-coluna com o Excel está em [[excel-parity.md]]. O faseamento e os critérios de
> aceitação estão em `notes/roadmap/plans/2026-09-04-alinhamento-excel-app.md`.
>
> Desenhado em 2026-09-04 com o utilizador. As decisões marcadas **[decidido]** foram tomadas por ele;
> as marcadas **[proposto]** são a recomendação do Claude, aceites por omissão até serem contrariadas.

## 1. A mudança de fundo: a fatura é o registo, não o ficheiro

Hoje `construction_invoice` **é** o ficheiro: `bucket`/`storage_key` são `NOT NULL`. No Excel a
unidade é a **linha de despesa**, que pode ter zero documentos ("Imprimir fatura", "Pedir fatura",
27 linhas do Vila Petrus lançadas à mão) ou vários (a foto do WhatsApp e depois o PDF do Bizdocs;
a página 1 e a página 2 digitalizadas em dias diferentes).

**[decidido]** Uma fatura tem **0..N documentos**. O ficheiro sai de `construction_invoice` para
uma tabela própria.

```
construction_invoice            (o registo: nº, data, fornecedor, valor, âmbito, estado)
 ├── construction_invoice_document  0..N  (cada ficheiro: foto, PDF, página)
 ├── construction_expense           0..N  (afetação a rubricas — pode repartir)
 ├── invoice_payment                0..N  → payment (pagamentos, agregáveis)
 └── construction_invoice (related_invoice_id)  0..N  notas de crédito desta fatura
```

## 2. Tabelas

Convenções de sempre: PK `uuid`, `created_at`/`updated_at` com o trigger `tg_set_updated_at`,
`created_by → profile`. Só se listam as colunas que mudam ou nascem.

### 2.1 `construction_invoice` — alterações

| Coluna | Mudança | Porquê |
|---|---|---|
| `bucket`, `storage_key`, `original_filename`, `mime_type`, `size_bytes`, `original_size_bytes`, `thumbnail_key`, `thumbnail_mime`, `checksum_sha256` | **saem** para `construction_invoice_document` | 0..N documentos |
| `enterprise_id` | passa a **nullable** | quarentena e despesas da empresa |
| `scope` | **nova**, enum `invoice_scope`: `PROJECT` / `COMPANY` / `UNIDENTIFIED` | `PROJECT` exige `enterprise_id`; os outros dois exigem `enterprise_id IS NULL` (check constraint). Ver §3 |
| `document_type` | **nova**, enum `invoice_document_type`: `INVOICE` / `CREDIT_NOTE` | o QR da AT traz o tipo no campo `D` (`FT`, `FR`, `FS`, `NC`, `RC`…): `NC` → `CREDIT_NOTE`; `RC`/`RG` (recibos) **não** são faturas, são prova de pagamento (§5) |
| `related_invoice_id` | **nova**, FK auto-referenciada, `ON DELETE RESTRICT` | obrigatória quando `document_type = CREDIT_NOTE`, proibida quando `INVOICE` (check constraint). Ver §6 |
| `document_status` | **nova**, enum `invoice_document_status`: `ARCHIVED` / `MISSING` / `TO_PRINT` / `TO_REQUEST` | traduz as observações "Imprimir fatura" / "Pedir fatura" / linha sem nº. Derivável em parte (tem documento → `ARCHIVED`), mas `TO_PRINT` e `TO_REQUEST` são intenção, não estado — por isso é coluna |
| `description` | **nova** (texto) | o "Produto/Serviço" do Excel. Hoje esse texto vai parar a `construction_expense.name`, o que obriga a ter despesa para ter descrição |
| `possible_enterprises`, `ask_whom` | **novas** (texto, nullable) | só fazem sentido em `UNIDENTIFIED` — as colunas `Obras possíveis` e `Perguntar a` da quarentena. `Aqui desde` = `created_at` |
| `total_amount` | mantém-se `numeric(14,2)`, **sempre positivo** | o sinal vem do `document_type`, nunca do valor (§6) |

Índices de unicidade (ver [[excel-parity.md]] §5 — decisão 18 do Vilatro): passam de por-projeto a
**globais**: `uq_invoice_atcud (invoice_atcud)`, `uq_invoice_nif_number (supplier_nif, invoice_number)`,
ambos parciais (`where … is not null`). O checksum vai com o ficheiro para a tabela de documentos, também
global.

### 2.2 `construction_invoice_document` — nova

| Coluna | Notas |
|---|---|
| `invoice_id` | FK → `construction_invoice`, `ON DELETE CASCADE` |
| `bucket`, `storage_key`, `original_filename`, `mime_type`, `size_bytes`, `original_size_bytes`, `thumbnail_key`, `thumbnail_mime` | tal como estão hoje na fatura |
| `checksum_sha256` | único **global** (parcial) — o mesmo ficheiro não entra duas vezes em lado nenhum |
| `kind` | enum `invoice_document_kind`: `ORIGINAL` / `PAGE` / `PHOTO` / `OTHER` — só para a UI ordenar; nada decide com base nisto |
| `page_number` | nullable; para PDFs partidos página a página |
| `uploaded_by`, `uploaded_at` | saem da fatura para aqui — cada ficheiro tem o seu |
| `qr_payload` | nullable; o texto bruto do QR lido **deste** ficheiro, para auditoria. Os campos interpretados continuam na fatura |

Regra de serviço: ao adicionar um documento a uma fatura que já tem os campos do QR preenchidos, o QR
novo **só avisa** se divergir (nº, NIF, total), não sobrescreve. Ao adicionar a uma fatura vazia
(registada à mão), o QR **preenche** os campos vazios.

### 2.3 `payment` e `invoice_payment` — novas

**[decidido]** "Dar como pago" e "método de pagamento" são uma só operação, e regista-se quem a fez.
**[decidido]** Um pagamento pode liquidar várias faturas (raro, mas acontece: o fornecedor recebe N
faturas numa transferência só — decisão 23 do Vilatro).

**[proposto]** O pagamento é uma entidade própria, e a fatura liga-se a ele por uma tabela de junção.
É a única forma de o caso raro (N faturas, 1 pagamento) e o caso normal (1 fatura, 1 pagamento) serem
**o mesmo modelo** — o caso normal é só um pagamento com uma ligação. Guardar `paid_at`/`paid_by`
diretamente na fatura obrigaria a repetir a mesma informação em N linhas e a manter as N iguais.

`payment`:

| Coluna | Notas |
|---|---|
| `paid_on` | `date` — a data em que o dinheiro saiu (do extrato, do recibo, ou a que o utilizador disser) |
| `method` | enum `payment_method`: `NUMERARIO` / `MULTIBANCO` / `TRANSFERENCIA` / `OUTRO`. Mapa dos valores do Excel em [[excel-parity.md]] §4 |
| `amount` | `numeric(14,2)` — o valor do movimento. No caso normal é igual ao líquido da fatura; no agregado é a soma |
| `reference` | texto, nullable — nº do movimento, "extrato ABANCA 28-08-2026", nome de quem pagou por conta da empresa ("Tabuada Pioneira") |
| `notes` | texto |
| `proof_bucket`, `proof_key`, `proof_filename`, `proof_mime` | nullable — o recibo ou a página do extrato. **Um** ficheiro chega; a conciliação bancária completa (fase 7) traz a tabela de extratos |
| `registered_by`, `registered_at` | quem marcou como pago e quando — é isto que o utilizador pediu. `registered_by` FK → `profile`, `ON DELETE SET NULL` |

`invoice_payment` (junção):

| Coluna | Notas |
|---|---|
| `payment_id`, `invoice_id` | PK composta; `ON DELETE CASCADE` dos dois lados |
| `amount` | `numeric(14,2)` — quanto **deste** pagamento cobre **esta** fatura. No caso normal = `payment.amount` |

Invariantes (serviço, não constraint): `sum(invoice_payment.amount) = payment.amount`; por fatura,
`sum(invoice_payment.amount) ≤ líquido(fatura)` onde líquido = `total_amount − Σ notas de crédito`.

**Estado de pagamento da fatura é derivado, não coluna**: `UNPAID` (sem ligações), `PARTIAL`
(soma < líquido), `PAID` (soma = líquido). É o que a UI mostra e o que a exportação para o Excel escreve
na coluna `Liquidada`. Uma coluna `paid` a duplicar isto divergiria à primeira edição de um pagamento.

Fluxos na UI:

1. **Marcar como paga** (uma fatura): drawer com data, método, referência, prova opcional. Cria um
   `payment` de `amount = líquido` e uma ligação. Um clique, como no Excel.
2. **Registar pagamento agregado**: seleção múltipla de faturas por liquidar do **mesmo fornecedor**
   (aviso, não bloqueio, se forem de fornecedores diferentes), a app mostra a soma e o utilizador confirma
   o valor do movimento. Se o valor não bater com a soma, a app diz quais ficam de fora ou pede a
   repartição — nunca decide sozinha (decisão 23). O drawer tem os mesmos campos do fluxo 1 (data,
   método, referência, prova, **notas**).
   **"Pagas juntas" não se escreve à mão** [decidido 2026-09-04]: é um facto estrutural — um `payment`
   com N ligações — e a UI gera-o em cada fatura ("pago em 28-08-2026 por transferência, junto com
   FT A e FT B"). Escrevê-lo em texto seria uma segunda cópia que fica errada ao tirar uma fatura do
   pagamento. `payment.notes` é para o que a app **não** deduz: "pago pela Tabuada Pioneira", "desconto
   de 2% por pronto pagamento", "inclui a caução". `payment.reference` é para a prova.
3. **Anular**: apagar o pagamento repõe as faturas a `UNPAID`. Fica em `activity_log`.

Notas de crédito **não** se pagam: liquidam a fatura a que pertencem (baixam o líquido).

### 2.4 `construction_expense` — alterações

| Coluna | Mudança | Porquê |
|---|---|---|
| índice `uq_expense_invoice` | **cai** | uma fatura reparte-se por várias rubricas (já acontece no Excel: `FRA4 2026V2/1608` em duas linhas). A `V16` previa isto |
| `total_price` | pode ser **negativo** só quando a fatura é `CREDIT_NOTE` (check via serviço) | §6 |
| `name` | passa a nullable; por omissão herda `invoice.description` | a descrição vive na fatura |

Invariante: por fatura, `Σ expense.total_price = ±total_amount` (positivo em `INVOICE`, negativo em
`CREDIT_NOTE`). Uma fatura com despesas cuja soma não bate fica assinalada "repartição incompleta" —
aviso, não bloqueio, para o utilizador poder repartir em dois momentos.

### 2.5 `enterprises` — alterações

| Coluna | Notas |
|---|---|
| `slug` | **nova**, `text`, único parcial. É o **nome da pasta no vault Vilatro** (`Vila Petrus`, `Vila Aleu`, `Villa Atrium`). Chave da migração nos dois sentidos — ver [[excel-parity.md]] §2 |
| `is_test` | **nova**, boolean, default `false`. "Vila Sol" é só de teste **[decidido]**; nunca entra numa exportação nem numa soma da empresa |

### 2.6 `invoice_incident` — nova (fase 5, opcional)

O `Registo de inconsistências` do Vilatro: uma nota por ocorrência, escrita para se mostrar a quem está
na obra. Mínimo viável na app: `occurred_on`, `title`, `body` (markdown), `resolved_at`, e uma tabela de
junção `invoice_incident_invoice` para as faturas envolvidas. Sem isto, as transferências e correções
ficam só no `activity_log`, que não é legível por quem não é da app.

### 2.7 `supplier_rubric_rule` — nova (fase 4)

"As faturas do fornecedor X nesta obra vão sempre para a rubrica Y." Colunas: `enterprise_id`
(nullable — regra global se nulo), `supplier_nif`, `budget_item_id`, `created_by`. A regra **sugere**,
nunca aplica sozinha (decisão 8 do Vilatro: rubrica só com confirmação). Ver §7.

## 3. Âmbito da fatura: obra, empresa, ou ainda não se sabe

**[decidido]** A app precisa de um sítio para faturas cuja obra não se sabe, e de mudar faturas de obra.

| `scope` | `enterprise_id` | Equivalente no Vilatro | O que a UI mostra |
|---|---|---|---|
| `PROJECT` | obrigatório | `Empreendimentos\<Obra>\` | dentro do projeto, como hoje |
| `COMPANY` | nulo | `Despesas da empresa\` | página "Despesas da empresa" (menu principal). Não entra em nenhuma soma de obra. Combustível de viatura, AdBlue, comissões bancárias, impostos — decisão 21 |
| `UNIDENTIFIED` | nulo | `Faturas por identificar\` | página "Por identificar" (menu principal), com `possible_enterprises`, `ask_whom` e "aqui desde" (`created_at`), ordenada da mais antiga para a mais recente |

Regras (decisões 16 e 24 do Vilatro, que a app **honra**):

- Uma fatura em `UNIDENTIFIED` **não pode ter despesas** — a rubrica é da obra, e não há obra.
- Pode ter pagamentos — uma fatura em quarentena pode já estar paga.
- Sair da quarentena é uma **transferência** (§4), nunca uma edição direta de `enterprise_id`.
- Regras de identificação automática ("data anterior a 15-05-2026 → Villa Atrium") **não** entram na
  app: são regras de um momento e de uma obra, decididas pelo utilizador; ficam no Vilatro.

## 4. Transferir uma fatura de obra

**[decidido]** Existe uma operação própria: `POST /enterprises/{id}/invoices/{invoiceId}/transfer` com
`{ targetScope, targetEnterpriseId?, reason }`. Cobre os três fluxos do Vilatro: obra → obra (Fluxo A),
quarentena → obra (Fluxo B), obra → empresa/quarentena (secções "→ Despesas da empresa" e "→ Faturas por
identificar" da decisão 20).

O que a operação faz, nesta ordem, numa transação:

1. Valida: `reason` obrigatório; destino ≠ origem; se destino é `PROJECT`, a obra existe e não é `is_test`.
2. Apaga as `construction_expense` da fatura — as rubricas eram da obra antiga e não têm correspondência
   na nova. Guarda a repartição antiga no `activity_log` (`details` JSON) para se poder reconstituir.
3. Atualiza `scope` + `enterprise_id`. Documentos e pagamentos **viajam com a fatura** sem mudar.
4. Escreve `activity_log` com `reason`, origem e destino. Se existir `invoice_incident` (§2.6), oferece
   criar um.

**Nunca** se "apaga e relança": perde-se o documento, a ordem de segurança, e o porquê (decisão 20).

## 5. Recibos

Um recibo (`RC`/`RG` no QR) **não é uma fatura**: não gera despesa nem entra em soma nenhuma. É prova de
pagamento — vai para `payment.proof_*`. Se chegar um recibo sem a fatura estar na app, a app pede para
registar a fatura primeiro (pode ser sem documento, §1).

## 6. Notas de crédito

**[decidido]** A app tem notas de crédito. **[decidido, com a opinião do Claude pedida]** Só existem
associadas a uma fatura já lançada.

**Opinião**: concordo, e por três razões que ajudam a fechar o desenho:

1. Uma nota de crédito sozinha não reduz nada — o seu único significado é "esta fatura vale menos X".
   Sem a fatura, o valor entrava no orçamento como um negativo órfão e falseava a rubrica.
2. Obrigar à ligação é o que torna o líquido calculável: `líquido = total − Σ NC`. É esse líquido que
   os pagamentos cobrem (§2.3) e que a comparação orçamento vs. gasto usa.
3. O caso "a fatura original é anterior à app" resolve-se com o §1: regista-se a fatura original
   **sem documento** (`document_status = MISSING`, com a nota "anterior à app"), e a NC liga-se a ela. Não é
   preciso uma exceção no modelo.

Desenho **[proposto]**:

- Mesma tabela (`construction_invoice`, `document_type = CREDIT_NOTE`, `related_invoice_id` obrigatória).
  Partilha tudo o resto: NIF, nº, ATCUD, QR, documentos, duplicados. Uma tabela à parte duplicava tudo isso.
- `total_amount` **positivo** (o valor da NC); o sinal vem do tipo. Evita valores negativos a
  espalharem-se por filtros e somas.
- Mesmo NIF da fatura de origem (validação; aviso se divergir, porque há grupos que faturam com NIFs diferentes).
- **Afetação a rubricas**: a NC gera as suas próprias `construction_expense` com `total_price`
  **negativo**, propostas automaticamente **na mesma proporção** da repartição da fatura de origem, e
  confirmadas pelo utilizador (pode alterar — uma NC pode devolver só um artigo, de uma rubrica só).
  Assim "gasto por rubrica" continua a ser um `SUM` simples, sem casos especiais.
- Uma NC maior do que o líquido restante da fatura é aviso, não erro (acontece com correções em cadeia).
- **Sem NC de NC**: `related_invoice_id` tem de apontar para um `INVOICE`.
- Casos do Excel que isto resolve: Civica (1 537,50 € lançados e devolvidos — hoje são duas linhas que se
  anulam), caução do Manitou (1 000 € reembolsados na entrega).

## 7. Associar a rubricas — "o mais completa e eficaz possível"

**[decidido]** É prioridade. O que "eficaz" quer dizer aqui, e como se consegue:

**Um ecrã de classificação em lote**, não fatura a fatura: a lista das faturas por associar de uma obra,
com o documento visível ao lado, e a rubrica escolhida sem sair da lista. É o `associar-rubricas` do
Vilatro, mas com o Claude substituído pela app.

**Sugestão com origem declarada**, por esta ordem, e a primeira que existir vem pré-selecionada:

1. `supplier_rubric_rule` desta obra para este NIF (§2.7);
2. regra global para este NIF;
3. a rubrica da última fatura deste NIF **nesta obra** (índice `idx_invoice_supplier_nif` já existe para isto);
4. a rubrica da última fatura deste NIF **em qualquer obra**, traduzida por `code` para a árvore desta obra
   (o código `4.2.1` é o mesmo em orçamentos do mesmo empreiteiro);
5. nada — o utilizador procura.

A UI diz **porquê** sugere ("última fatura da Casa Dolores nesta obra foi para 5.1.2"). Sugestão nunca
grava sozinha (decisão 8): o utilizador confirma, e a confirmação de uma sugestão de nível 3–5 oferece
"criar regra para este fornecedor nesta obra?".

**Procurar na árvore**: um só campo que aceita código (`4.2`) ou texto (`betão`), mostra o caminho
completo (`4. Estrutura › 4.2 Lajes › 4.2.1 …`) e o orçamentado vs. gasto de cada resultado. Rubricas
`HEADING` e `NOTE` não são selecionáveis; qualquer `ITEM` é, **a qualquer profundidade** — o capítulo
`4` é um `ITEM` com `code = "4"` e aceita despesas. Classificar ao capítulo (como o Vilatro faz hoje)
e refinar depois é legítimo; a app assinala "classificada ao capítulo" para se poder filtrar.

**Repartir**: "dividir por rubricas" abre N linhas com valores que têm de somar ao total; a app
propõe o resto automaticamente na última linha.

**Atalhos**: "igual à anterior", e classificar várias faturas selecionadas para a mesma rubrica de uma vez.

**Fatura sem valor ou sem data** (`MISSING`): pode ser classificada, mas a despesa nasce com
`total_price = 0` e fica assinalada — a comparação orçamento vs. gasto avisa quantas dessas existem.

## 8. O que se mantém como está

- Duas datas: `invoice_date` (documento) e `uploaded_at` (agora no documento) / `created_at` (registo).
- `sent_to_accountant{,_by,_at}` na fatura — é o `Bizdocs` do Excel.
- `supplier` global por NIF.
- Árvore de rubricas e importador do "Orçamento inicial".
- Ficheiros só como `bucket` + `storage_key`, nunca URL.

## 9. Impacto no `worksite-expenses` (Next.js só-leitura)

Lê diretamente `enterprises`, `construction_invoice`, `construction_expense`, `construction_budget_item`,
`profile` com o role `worksite_expenses_ro`. Cada fase que mexa nestas tabelas tem de:

- manter as colunas que ele lê, ou atualizá-lo no mesmo dia;
- dar `SELECT` ao role nas tabelas novas que ele precise (`construction_invoice_document` na fase 1 —
  é lá que passa a estar o ficheiro que ele abre);
- filtrar `scope = 'PROJECT'` na lista por projeto, ou a quarentena aparece sem obra.

## Relacionado

- [[excel-parity.md]] — o contrato coluna-a-coluna com o Excel, e a migração nos dois sentidos.
- [[database.md]] — o schema que existe hoje.
- [[api.md]] — os endpoints que existem hoje.
- `notes/roadmap/plans/2026-09-04-alinhamento-excel-app.md` — as fases e os critérios de aceitação.
