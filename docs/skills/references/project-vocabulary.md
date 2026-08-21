# Vocabulário do Projeto — como nomear as coisas

**Não é uma skill** — não é invocada sozinha. É o dicionário partilhado entre quem escreve e quem pede: os termos abaixo têm significados **exatos** neste código, e usar o errado manda quem lê para o sítio errado. Ver [[code-best-practices]] para a distinção entre skill e referência.

> 📐 Ver também [[frontend-visual-consistency]] (qual sub-ficheiro de design ler) e [[skill-frontend-design-system]] (estrutura de pastas e naming de ficheiros).

---

## Drawer vs. Modal

A confusão mais comum, e a que mais custa: até [[skill-frontend-design-system]] escorrega nisso (diz literalmente *"use Drawers for modals"*).

Ambos vêm do Ant Design 5, ambos são **overlays** — aparecem por cima da página, escurecem o resto e prendem o foco. O que muda é a forma, e por consequência o uso natural:

| | **Modal** | **Drawer** |
|---|---|---|
| Onde aparece | Caixa centrada no ecrã | Desliza de um bordo (à direita, por omissão) |
| Tamanho | `width` — cresce em altura com o conteúdo | `width` — ocupa **sempre a altura toda** |
| Estrutura | Corpo único | Cabeçalho e rodapé fixos, corpo com scroll |
| Serve bem para | Coisa curta, uma decisão só | Conteúdo comprido, formulário, detalhe |

O rodapé fixo do Drawer é o detalhe prático: num formulário longo os botões "Cancelar / Guardar" ficam sempre visíveis sem scroll. Num Modal alto, desaparecem por baixo.

### A regra deste projeto

Não é estética — está escrita em [[design/backoffice-drawers-and-modals]]:

- **Drawer** → criar, editar ou ver uma entidade. É o padrão dominante.
- **Modal** → utilitário curto e autocontido: importar um ficheiro, pré-visualizar um documento, reordenar itens.
- **Nem um nem outro** para confirmar uma ação destrutiva — isso é `useConfirm()` (`context/ConfirmDialogContext`). Nunca `Popconfirm` em código novo.

Dizer "modal" quando é um Drawer faz com que se leia o sub-ficheiro de design errado e se aplique a convenção do contentor errado.

---

## Vocabulário — contentores de UI

| Termo | O que é | Exemplos reais |
|---|---|---|
| **Drawer** (ou "painel lateral") | `<Drawer>` do AntD | `InvoiceDetailDrawer`, `InvoiceUploadDrawer`, `SuppliersDrawer`, `EmailProvidersDrawer`, `CreateEnterpriseDrawer`, `EnterpriseViewDrawer` |
| **Modal** | `<Modal>` do AntD | `BudgetItemPickerModal`, `BudgetImportModal`, `InvoicePreviewModal`, `MyProfileModal` |
| **Diálogo de confirmação** | `useConfirm()`, partilhado | o "Eliminar esta fatura?" |
| **Página** | tem rota em `main.tsx` | `EnterpriseInvoicesPage`, `ConstructionBudgetPage` |
| **Lista** / **tabela** | `<Table>` do AntD + `ListActions` | `InvoicesList`, `EnterprisesList` |
| **Card** | bloco de conteúdo dentro de uma página | `EditEnterpriseOverviewCard`, classe `.ind-card` |
| **Notificação** / **toast** | `notificationService` | o aviso de "Fatura associada" |

> `MyProfileModal` e `StatusChangeModal` são formulários de entidade dentro de um Modal — drift conhecido, **não copiar**. Ver [[design/backoffice-drawers-and-modals]].

---

## Vocabulário — domínio

O que mais poupa tempo, porque estas palavras mapeiam 1:1 para entidades do backend:

| Termo | Significa | No código |
|---|---|---|
| **Fatura** | o **documento** (o PDF/foto que veio da obra) | `ConstructionInvoice` |
| **Despesa** | a **afetação** dessa fatura a uma rubrica | `ConstructionExpense` |
| **Rubrica** | linha do orçamento que aceita despesas | `ConstructionBudgetItem` com `rowKind = ITEM` |
| **Título** / **nota** | linhas do orçamento que **não** aceitam despesas | `rowKind = HEADING` / `NOTE` |
| **Projeto** / **empreendimento** | a obra | `Enterprise` (tabela e package ficaram com o nome antigo — ver [[../../architecture]]) |
| **Associar** | ligar fatura → rubrica, criando a despesa | `allocate` |
| **Caixa de entrada** | as faturas com `allocated: false` | filtro "Por associar" |
| **Por rever** | falta a data ou o total; não dá para associar | `needsReview` (derivado, não é coluna) |
| **Fornecedor** | a empresa que emitiu a fatura, no catálogo NIF → nome | `Supplier` / `SuppliersDrawer` |

**"Fatura" e "despesa" não são sinónimos.** A separação é deliberada: registar e classificar são momentos diferentes — quem chega da obra com quinze faturas carrega-as todas sem decidir nada, e classifica depois. Ver [[../../api]] → "Faturas de obra".

---

## Vocabulário — o papel fiscal português

Estes quatro termos aparecem em conversa como se fossem intermutáveis e não são. Todos saem do **QR code da AT**, obrigatório nas faturas portuguesas desde 2022, que `AtInvoiceQrService` lê (campos `A:`, `D:`, `G:`, `H:` …).

| Termo | O que é | No código |
|---|---|---|
| **NIF** | número de contribuinte de **quem emitiu** a fatura. É a única identificação do fornecedor que o QR traz — **não há campo para o nome da empresa** | `supplierNif` (campo `A` do QR) |
| **Nome do fornecedor** | o nome da empresa. Nunca vem do QR: ou está no catálogo (`Supplier`), ou alguém o escreveu à mão | `supplierName` |
| **Tipo de documento** | `FT` fatura · `FR` fatura-recibo · `FS` simplificada · `VD` venda a dinheiro · `NC` nota de crédito · `ND` nota de débito | campo `D` do QR; lista em `invoiceNumber.ts` |
| **Nº da fatura** | o tipo **junto** com a série/número — `"FT 2026/114"`, tudo numa string | `invoiceNumber` (campo `G` do QR) |
| **ATCUD** | código único que a AT atribui ao documento (`"CSDF7T5H-0114"`). É a chave fiscal e a melhor chave de duplicado | `invoiceAtcud` (campo `H` do QR) |

Três consequências que se repetem em conversa:

- **"o fornecedor não aparece"** quase nunca é bug de leitura — o QR não traz o nome. É o catálogo de fornecedores que resolve; ver [[../../api]] → "Fornecedores".
- **"a fatura começa por FR"** é o *tipo de documento*, não parte da série. No preenchimento manual escolhe-se numa lista, não se escreve.
- **"fatura repetida"** compara-se por três chaves diferentes (checksum do ficheiro, ATCUD, e o par NIF+número) — ver [[../../api]] → "Duplicados".

---

## Vocabulário — estilo

| Termo | O que é |
|---|---|
| **Sistema Industry** (ou "blueprint") | o sistema visual atual — steel-blue, tipografia Barlow |
| **Token** | uma variável `--ind-*` em `index.css` — a fonte de verdade da paleta |
| **Classe utilitária** | `.ind-card`, `.ind-tag`, `.ind-elev-*`, `.ind-corner`, `.ind-blueprint` |
| **Drift** | código que contraria uma convenção já decidida (ex. `#1890ff` hardcoded por cima do tema) |

Detalhe completo em [[design/backoffice-tokens-and-colors]].

---

## Como referir um componente sem ambiguidade

O mais fiável não é acertar no termo técnico — é **dar uma âncora**. Qualquer uma destas chega:

- o **nome do ficheiro** — *"o `BudgetItemPickerModal`"*
- o **caminho até lá** — *"o painel que abre quando clico Associar na lista de faturas"*
- a **rota** — *"o ecrã em `/empreendimentos/:id/invoices`"*

Trocar "modal" por "drawer" numa conversa não é problema e não vale a pena policiar — corrige-se em silêncio. Só se assinala quando a diferença muda o que vai ser escrito.

---

## Skills relacionadas

- [[code-best-practices]] — Regras gerais de qualidade de código
- [[frontend-visual-consistency]] — Router para os sub-ficheiros de design do Backoffice
- [[design/backoffice-drawers-and-modals]] — A convenção Drawer vs. Modal na íntegra
- [[skill-frontend-design-system]] — Estrutura de pastas, naming de ficheiros, formulários
- [[skill-frontend-structure-brief]] — Descreve um componente existente usando este vocabulário
