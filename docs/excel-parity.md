# Contrato de dados App ↔ Excel (Vilatro)

> **Este ficheiro é a única cópia do contrato.** A app (este vault) é dona do schema; o vault Vilatro
> é dono do processo e das decisões operacionais. Quando um dos lados muda algo que toca dados, é
> **aqui** que a mudança se regista primeiro — e o outro lado ganha um item de ToDo. Ver "Como se
> mantém" no fim.
>
> Criado em 2026-09-04. O modelo alvo da app que este contrato assume está em [[faturas-modelo-alvo.md]];
> enquanto esse modelo não estiver implementado, a coluna "BD" descreve o **destino**, não o que existe.

## 1. Os dois projetos

|                        | App (Worksite)                                                   | Excel (Vilatro)                                                                                        |
| ---------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Vault                  | `C:\Users\jlalv\Desktop\utad\projetos\Worksite\Worksite`         | `C:\Users\jlalv\Desktop\VILATRO\Vilatro`                                                               |
| GitHub                 | `https://github.com/bino03/WorkSite`                             | `https://github.com/bino03/Vilatro` (privado)                                                          |
| Ponto de entrada       | `CLAUDE.md` → `00-INDEX.md`                                      | `CLAUDE.md` → `Início.md`                                                                              |
| Onde estão as decisões | `docs/` + `notes/whatIveDone.md`                                 | `Decisões.md` (25 decisões numeradas — as referências "decisão N" neste ficheiro são a esse documento) |
| Estado (2026-09-04)    | pausada; backend + Backoffice até faturas/orçamento (`V1`–`V22`) | em uso diário; 3 obras; 8 skills Claude; scripts PowerShell + COM                                      |
| Papel                  | a solução definitiva; salvaguarda do Excel                       | a solução em uso; salvaguarda da app                                                                   |

Os dois têm de continuar a poder trocar dados **sem conversão à mão**. É isso que este contrato garante.

## 2. Identidade das obras

| App | Excel |
|---|---|
| `enterprises.slug` | nome da pasta `Empreendimentos\<Obra>\` — e do Excel `Despesas - <Obra>.xlsx` e da nota `<Obra>.md` (decisão 2) |

- O `slug` é **exatamente** o nome da pasta, com espaços e acentos (`Vila Petrus`, `Vila Aleu`, `Villa Atrium`).
  Renomear uma obra num lado obriga a renomear no outro — a skill `novo-empreendimento` já pede confirmação
  da grafia por isso.
- `enterprises.is_test = true` (hoje: "Vila Sol") **nunca** entra numa exportação, importação ou soma da empresa.
- `Despesas da empresa\` e `Faturas por identificar\` **não são obras**: mapeiam para `scope = COMPANY` e
  `scope = UNIDENTIFIED`, com `enterprise_id` nulo.

## 3. Folha `Despesas` ↔ base de dados

A folha `Despesas` de cada obra é uma tabela Excel chamada `TabelaDespesas`, com linha de totais.
**As colunas mapeiam-se pelo nome do cabeçalho, nunca pela letra** (decisão 5) — a ordem já mudou uma vez.

| Cabeçalho Excel | BD (modelo alvo) | Regras de conversão |
|---|---|---|
| `Nº Fatura` | `construction_invoice.invoice_number` | texto tal como está (`FT FA.2026S/3047`). Vazio, "Imprimir fatura" ou "Pedir fatura" → nº nulo + `document_status` (`TO_PRINT` / `TO_REQUEST`); vazio sem indicação → `MISSING` |
| `Data` | `invoice_date` | `dd/mm/aaaa`. Vazia → nula |
| `Produto/Serviço` | `description` | texto. O nome do fornecedor costuma vir no início ("Casa Dolores - …"): **não** se extrai daí — ver §3.1 |
| `Valor` | `total_amount` | `numeric(14,2)`. Aceitar `11 643,33 €`, `11.643,33 €`, `11643,33` — as três formas existem. Sempre positivo; uma linha negativa é uma nota de crédito (§3.2) |
| `Liquidada` | derivado de `payment` (§4) | `x`, `X`, `Sim` → paga; vazio → não paga. Na exportação escreve-se sempre `Sim` |
| `Metodo Pagamento` | `payment.method` | mapa em §4 |
| `Bizdocs` | `sent_to_accountant` | `x`/`X` → `true`; vazio → `false`. Na exportação escreve-se `X` |
| `Observações` | `notes` | texto. A prova de pagamento que hoje vive aqui ("Pago por transferência em 28-08-2026 (extrato ABANCA)") vai para `payment.reference` quando for possível separar; caso contrário fica em `notes` |
| `Rubrica` | `construction_expense.budget_item_id` via `construction_budget_item.code` | ver §6 |

### 3.1 O que falta no Excel para a migração ser limpa

A app identifica o fornecedor pelo **NIF** (`supplier`, decisão de não repetir o nome fatura a fatura).
A folha `Despesas` não tem NIF nem fornecedor em coluna própria.

**Pedido ao vault Vilatro** (registado como decisão 26 em `Decisões.md`): acrescentar as colunas
`Fornecedor` e `NIF` à folha `Despesas` (a quarentena já tem `Fornecedor`). Enquanto não existirem,
a migração Excel → app deixa `supplier_nif` nulo e a fatura fica "por rever" — o que a app já suporta.
O NIF está impresso em todas as faturas e no QR; a skill `importar-faturas` já o lê.

### 3.2 Linhas que se anulam, cauções, devoluções

No Excel são duas linhas (uma positiva, uma negativa, ou com a nota "devolvido"). Na app são uma fatura
(`INVOICE`) e uma nota de crédito (`CREDIT_NOTE`, `related_invoice_id` → a fatura). A migração
**pergunta** que linha negativa pertence a que fatura — não adivinha.

### 3.3 Uma fatura em várias linhas

Acontece (`FRA4 2026V2/1608` no Vila Aleu: 142,57 € + 3,26 €). Na app é **uma** fatura com **duas**
despesas (repartição por rubrica). A migração agrupa por `Nº Fatura`; o `total_amount` é a soma das linhas.

## 4. Pagamento

| Excel `Metodo Pagamento` | `payment.method` |
|---|---|
| `Numerário` | `NUMERARIO` |
| `Pagamento MB`, `MB`, `TPA` | `MULTIBANCO` |
| `Transferência` | `TRANSFERENCIA` |
| outro texto | `OUTRO` + o texto em `payment.notes` |

- `Liquidada` preenchida sem método → `payment.method = OUTRO`, com aviso na migração.
- Data do pagamento: o Excel não a tem em coluna; se `Observações` disser "Pago … em dd-mm-aaaa" usa-se
  essa, senão `paid_on = invoice_date` **com aviso**. Na exportação, a data do pagamento vai para `Observações`
  no formato já usado: `Pago por <método> em dd-mm-aaaa (<referência>)`.
- Pagamentos agregados (uma transferência, N faturas): no Excel são N linhas `Liquidada` com a mesma
  observação. Na app é **um** `payment` com N ligações. A migração agrupa por observação idêntica e pergunta.
  Na exportação, a observação de cada linha é **gerada** a partir das ligações, no formato que o Excel já
  usa à mão: `Pago por <método> em dd-mm-aaaa, <valor do movimento> junto com <nº das outras faturas>
  (<referência>)` — seguido de `payment.notes`, se existir. Nunca se pede ao utilizador para escrever
  "pagas juntas": a app sabe-o pelas ligações.
- `Liquidada` só se marca com prova (decisão 22). A app honra isto pedindo data + método + referência,
  e registando `registered_by`. **Não** exige ficheiro de prova — o Excel também não.

## 5. Unicidade e duplicados

| Regra Vilatro | App |
|---|---|
| Nº Fatura único **no vault inteiro** (decisão 18), incluindo quarentena e despesas da empresa | `uq_invoice_nif_number (supplier_nif, invoice_number)` e `uq_invoice_atcud (invoice_atcud)` **globais**, parciais. Antes: por projeto |
| Linhas sem nº comparadas por data + valor + descrição | verificação no serviço, **aviso** (não bloqueio) ao criar fatura sem nº |
| Mesmo documento não entra duas vezes | `construction_invoice_document.checksum_sha256` único global |
| Um nº repetido entre obras é sempre erro: **parar e perguntar**, nunca apagar sozinho | a app recusa o segundo com o erro a dizer **onde** está o primeiro (obra, quarentena ou empresa). Nunca apaga |
| "Uma linha sem número é um documento por chegar" (regra 12) | ao carregar um documento cujo nº/valor/data bate com uma fatura `MISSING`, a app propõe **completar essa** em vez de criar outra |

## 6. Rubricas

| | App | Excel |
|---|---|---|
| Fonte | `construction_budget_item`, árvore importada da folha "Orçamento inicial" (colunas `Art`, `Descrição`, `Un.`, `Quant`, `Preço Un`, `Preço total`, `Obs.` — mapa em [[database.md]]) | a mesma folha "Orçamento inicial", no mesmo Excel |
| Identificador | `code` = coluna `Art` (`4`, `4.2`, `4.2.1`) | coluna `Rubrica` da folha `Despesas` |
| Nível | qualquer `ITEM`, a qualquer profundidade | hoje só o capítulo (`4.` ou `4. Estrutura de Betão Armado`) |

**Contrato para a coluna `Rubrica`**: o valor é o `Art` da rubrica, e só ele. Ao **ler**, normaliza-se:
tira-se o ponto final (`4.` → `4`) e tudo a partir do primeiro espaço (`4. Estrutura…` → `4`). Ao
**escrever** (exportação), escreve-se `code` seguido de ` — ` e `name`, para se ler no Excel
(`4.2.1 — Lajes maciças`); a leitura tolera as duas formas.

**Pedido ao vault Vilatro** (decisão 26): a skill `associar-rubricas` passa a escrever o `Art` completo
da rubrica escolhida, e a oferecer sub-rubricas, não só capítulos. Classificar ao capítulo continua a
ser válido (a app aceita), mas o formato fica fixo.

Rubricas `HEADING` e `NOTE` (sem `Art`) não aceitam despesas em nenhum dos lados. Uma `Rubrica` no
Excel que não exista na árvore da obra é erro de migração, listado, nunca criado automaticamente.

## 7. Documentos e ficheiros

| Vilatro (decisão 17) | App |
|---|---|
| `Faturas\Lançadas\<dd-mm-aaaa>\` — a data em que entrou no Excel | `construction_invoice_document.uploaded_at` |
| `<aaaammdd>_<NºFatura>_<Fornecedor>.<ext>` — a data do documento no nome | `original_filename` guarda o nome tal como veio; a exportação **gera** este nome a partir de `invoice_date`, `invoice_number` (sanitizado: `\ / : * ? " < > \|` → `-`, sem espaços) e `supplier.name` em CamelCase |
| `Por lançar\` = ainda não está no Excel | não existe: na app o upload **é** o lançamento |
| `Não Reconhecido\` = a leitura falhou | fatura com QR ilegível e campos vazios, "por rever" — já existe |
| Vários ficheiros por fatura (foto + PDF do Bizdocs; páginas separadas) | 0..N `construction_invoice_document` |
| Recibo | não é fatura: `payment.proof_*` |

A exportação app → pasta produz **exatamente** a estrutura do Vilatro: `Empreendimentos\<slug>\Despesas - <slug>.xlsx`
+ `Faturas\Lançadas\<data da exportação>\<ficheiros renomeados>`. É o que permite recomeçar no Excel
a partir da app.

## 8. Decisões do Vilatro que a app honra

Não se repetem aqui — lêem-se em `Decisões.md`. Esta lista diz **como** a app as cumpre.

| Decisão | Na app |
|---|---|
| 1 — tudo por empreendimento | `enterprise_id` em tudo o que é de obra; `scope` para o que não é |
| 5 — colunas pelo nome do cabeçalho | importador/exportador da folha `Despesas` (fase 6) |
| 8 — rubrica só com confirmação | sugestões pré-selecionadas, nunca gravadas sem clique ([[faturas-modelo-alvo.md]] §7) |
| 12 — linha sem nº é documento por chegar | `document_status = MISSING` + proposta de completar (§5) |
| 16 / 24 — fatura em dúvida não entra em obra nenhuma | `scope = UNIDENTIFIED`, sem despesas possíveis |
| 17 — duas datas | `invoice_date` ≠ `uploaded_at`/`created_at` |
| 18 — nº único no vault inteiro | índices únicos globais (§5) |
| 20 — transferir, não apagar e relançar | operação `transfer` com `reason` obrigatório ([[faturas-modelo-alvo.md]] §4) |
| 21 — combustível segue quem o consumiu | `scope = COMPANY` para viatura; obra para equipamento. A pergunta continua a ser do utilizador |
| 22 — `Liquidada` com prova escrita | `payment.reference` + `registered_by/at` |
| 23 — pagamento agregado só com confirmação | fluxo "registar pagamento agregado" mostra soma e faturas de fora |

Decisões que **não** se transportam: 3, 4, 6, 7, 10, 11, 14, 15, 25 — são sobre ficheiros, OneDrive,
scripts e Git, e a app resolve esses problemas por não os ter (a equipa carrega diretamente; não há
cópias em conflito; não há `.xlsx` a proteger).

## 9. Migração nos dois sentidos

### Excel → app (`DespesasExcelImportService`, fase 6)

Entrada: a pasta de uma obra do vault (`Despesas - <Obra>.xlsx` + `Faturas\Lançadas\**`). Ou só o `.xlsx`.

1. Resolver a obra por `slug`; recusar `is_test`.
2. Ler `TabelaDespesas` por nome de cabeçalho. Ignorar a linha de totais e linhas totalmente vazias.
3. Agrupar por `Nº Fatura` (§3.3). Linhas sem nº ficam uma-a-uma.
4. Por grupo: criar `construction_invoice` (`scope = PROJECT`), com `document_status` conforme §3.
5. Se `Rubrica` preenchida: resolver `code` (§6) → `construction_expense` por linha. Se não existir na árvore: erro listado.
6. Se `Liquidada`: criar `payment` (§4). Agregar por observação idêntica → perguntar.
7. Ficheiros: procurar em `Faturas\Lançadas\**` por `<NºFatura sanitizado>` no nome; anexar como documentos,
   `uploaded_at` = data da pasta. Ficheiros sem correspondência: listados, não anexados.
8. `dryRun` (como o importador do orçamento): relatório com contagens, somas, erros — **nada gravado**.
9. Verificação obrigatória no fim: **nº de faturas = nº de grupos**, **Σ `total_amount` = total da folha**
   (a linha de totais), nº por liquidar igual nos dois lados.

Depois, os mesmos passos para `Faturas por identificar.xlsx` (→ `UNIDENTIFIED`, com `Empreendimento`
preenchido → transferência para essa obra) e para `Despesas da empresa` (só nota `.md` hoje → criar à mão
ou a partir da tabela da nota; → `COMPANY`).

### App → Excel (exportação, fase 6)

Uma obra → um `.xlsx` com as duas folhas ("Orçamento inicial" a partir da árvore; "Despesas" com as
colunas de §3 **mais** `Fornecedor` e `NIF`), `TabelaDespesas` com linha de totais, formatos de moeda e
data que os scripts do Vilatro esperam (`dd/mm/aaaa`; moeda `#.##0,00 €`). E a pasta `Faturas\Lançadas\`
com os ficheiros renomeados (§7). Uma fatura com N despesas exporta N linhas com o mesmo nº.

A verificação é a mesma do sentido contrário: importar o ficheiro exportado em `dryRun` tem de dar
**zero diferenças**. É esse round-trip que prova o contrato — e é o teste automático a escrever na fase 6.

## 10. Como se mantém

- **Mudou uma coluna, um valor permitido, ou uma decisão do Vilatro que toca dados** → atualizar este
  ficheiro, e acrescentar o item correspondente a `notes/ToDo.md` (secção "Paridade com o Excel").
- **Mudou uma migração em `construction_*`, `supplier` ou `payment`** → rever §3–§7 deste ficheiro. Se o
  Excel tiver de mudar por causa disso → escrever a decisão em `Vilatro/Decisões.md` e o pedido em
  `Vilatro/Início.md` → "Por fazer".
- Os dois `CLAUDE.md` apontam para aqui (secção "Projeto irmão"). Nenhum deles repete o conteúdo.
- O hook `.githooks/pre-commit` deste repo já avisa em migrações; quando a fase 6 existir, acrescentar
  aviso para `DespesasExcelImportService`/exportador → rever este ficheiro.

## Relacionado

- [[faturas-modelo-alvo.md]] — o modelo que este contrato assume.
- [[database.md]] — o schema de hoje.
- `notes/roadmap/plans/2026-09-04-alinhamento-excel-app.md` — fases e critérios de aceitação.
- `C:\Users\jlalv\Desktop\VILATRO\Vilatro\Decisões.md` — decisões 1–26 do Vilatro.
