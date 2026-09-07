# Frontend Integration — Faturas, fase 1: a fatura é o registo

> Gerado pela skill `frontend-integration-guide` a 2026-09-07, a partir do backend das
> migrações `V23`–`V30`. Fonte de verdade do contrato: [[docs/api]] → "Faturas de obra" e
> [[docs/database]]. Contexto de negócio: [[docs/faturas-modelo-alvo]] §2.2 e
> [[docs/excel-parity]] §2 e §5. Plano: `notes/roadmap/plans/2026-09-04-fase1-fatura-e-registo.md`.

## 1. Overview

Até aqui uma fatura **era** um ficheiro: `bucket`/`storage_key` eram `NOT NULL` na própria
linha. A `V24`/`V25` inverteram isso — a fatura é o **registo**, e os ficheiros são 0..N
documentos seus. Isto desbloqueia os dois casos que o vault Excel da Vilatro tem todos os dias
e que a app não conseguia sequer representar:

- a fatura que **ainda não tem** documento (por pedir ao fornecedor, por imprimir);
- a fatura que tem **mais do que um** (a foto tirada na obra *e* o PDF que o fornecedor mandou
  depois, ou um PDF partido página a página).

E abre dois sítios novos onde uma fatura pode viver, além da obra: as **despesas da empresa**
(fatura sem obra, que não entra em orçamento nenhum) e a **quarentena** — faturas que chegaram
e ainda não se sabe de quem são.

**Nada disto está visível no Backoffice.** Os endpoints existem e estão testados; este
documento é o que falta construir.

## 2. API Contract

### 2.1 Endpoints novos

| Método | Rota | Auth | Notas |
|---|---|---|---|
| `POST` | `/construction-invoices/register` | `ADMIN` | **JSON, não multipart.** Regista fatura sem ficheiro. `201` |
| `POST` | `/construction-invoices/{id}/documents` | `ADMIN`+`EMPLOYEE` | multipart `file`. **Junta** um documento. `201` |
| `DELETE` | `/construction-invoices/{id}/documents/{documentId}` | `ADMIN` | Remove **um** documento. `204` |
| `GET` | `/construction-invoices/unidentified` | `ADMIN` | Quarentena, paginada, `createdAt` **ASC** por omissão |
| `GET` | `/construction-invoices/company` | `ADMIN` | Despesas da empresa, paginada, `createdAt` DESC |

Os dois `GET` aceitam `?q=` (pesquisa) e os parâmetros de `Pageable` do Spring
(`page`, `size`, `sort`).

> ⚠️ `GET /unidentified` e `GET /company` são **só `ADMIN` no backend** (`@PreAuthorize`).
> Esconder o `NavLink` não é controlo de acesso — é só para o `EMPLOYEE` não bater num 403.

### 2.2 Campos novos no `ConstructionInvoiceResponseDTO`

```ts
enterpriseId: string | null;          // era `string` — agora nullable
scope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";
documentType: "INVOICE" | "CREDIT_NOTE";
relatedInvoiceId: string | null;      // a fatura que a NC corrige (fase 3)
documentStatus: "ARCHIVED" | "MISSING" | "TO_PRINT" | "TO_REQUEST";
description: string | null;           // descrição da despesa, na própria fatura
possibleEnterprises: string | null;   // nota livre da quarentena
askWhom: string | null;               // nota livre da quarentena
documents: InvoiceDocument[];
```

```ts
interface InvoiceDocument {
  id: string;
  fileUrl: string | null;        // signed URL — só vem no detalhe
  thumbnailUrl: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  originalSizeBytes: number | null;
  kind: "ORIGINAL" | "PAGE" | "PHOTO" | "OTHER";
  pageNumber: number | null;
  uploadedBy: string | null;
  uploadedByName: string | null;
  uploadedAt: string;
}
```

**Os campos soltos de ficheiro continuam a existir** (`fileUrl`, `thumbnailUrl`,
`originalFilename`, `mimeType`, `sizeBytes`, `originalSizeBytes`, `uploadedBy`,
`uploadedByName`, `uploadedAt`) e descrevem o **primeiro** documento. É isso que faz o
Backoffice atual continuar a funcionar sem reescrita — não os removas ao adicionar
`documents[]`.

### 2.3 As três regras que o `scope` impõe

| Campo | O que decide |
|---|---|
| `scope` | `PROJECT` **exige** `enterpriseId`; `COMPANY` e `UNIDENTIFIED` **proíbem-no**. Uma fatura fora de `PROJECT` não pode ser associada a uma rubrica (`INVOICE_013`) |
| `documentStatus` | **Segue o papel, não é campo livre**: passa a `ARCHIVED` quando se junta um documento, volta a `MISSING` quando se largam todos. `POST /register` recusa `ARCHIVED` (`INVOICE_017`) |
| `documentType` | `CREDIT_NOTE` obriga a `relatedInvoiceId`. A coluna existe; **as regras da NC são a fase 3** — o frontend só a mostra, não a cria |

### 2.4 `InvoiceRegisterDTO` (corpo do `POST /register`)

```ts
{
  scope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";  // obrigatório
  enterpriseId?: string | null;                    // obrigatório sse scope === "PROJECT"
  supplierName?: string;      // max 255
  supplierNif?: string;       // max 20
  invoiceNumber?: string;     // max 100
  invoiceAtcud?: string;      // max 100
  invoiceDate?: string;       // ISO, não pode ser futura
  totalAmount?: number;       // >= 0
  description?: string;       // max 500
  documentStatus?: "MISSING" | "TO_PRINT" | "TO_REQUEST";  // ARCHIVED é recusado
  possibleEnterprises?: string;  // max 500
  askWhom?: string;              // max 255
  notes?: string;                // max 2000
}
```

### 2.5 Códigos de erro a acrescentar a `src/errors/errorMessages.ts`

O mapa espelha 1:1 o `ErrorCode.java` — não inventes chaves nem mensagens novas.

```ts
ENT_032: "Já existe um projeto com esta pasta do vault",
INVOICE_013: "Só uma fatura de obra pode ser associada a uma rubrica — identifique primeiro a obra",
INVOICE_014: "Uma fatura de obra tem de indicar a obra",
INVOICE_015: "Uma fatura da empresa ou por identificar não pode ter obra",
INVOICE_016: "Âmbito de fatura desconhecido",
INVOICE_017: "Uma fatura registada sem ficheiro não pode ficar como arquivada",
INVOICE_018: "Documento não encontrado nesta fatura",
```

As mensagens de `INVOICE_010`/`011`/`012` (duplicados) mudaram de sentido: a unicidade é
**global** desde a `V29`, e o backend nomeia **onde** está a primeira ("já existe na obra Vila
Petrus", "nas despesas da empresa", "nas faturas por identificar"). O texto vem do backend —
não o reescrevas no frontend.

## 3. Onde construir

```
src/
├── types/
│   └── invoice.ts                    ← MODIFICAR: enterpriseId nullable, campos + documents[]
├── services/
│   └── invoiceService.ts             ← MODIFICAR: register, addDocument, deleteDocument,
│                                        listUnidentified, listCompany
├── components/invoices/
│   ├── InvoicesList.tsx              ← MODIFICAR: 1ª miniatura + badge +N, documentStatus,
│   │                                    colunas opcionais de quarentena
│   ├── InvoiceDetailDrawer.tsx       ← MODIFICAR: galeria de N documentos
│   ├── InvoiceDocumentGallery.tsx    ← NOVO: grid de miniaturas + juntar/remover
│   ├── InvoiceRegisterDrawer.tsx     ← NOVO: formulário da fatura sem ficheiro
│   └── invoiceFormSchema.ts          ← NOVO: Zod do registo
├── pages/backoffice/invoices/
│   ├── UnidentifiedInvoicesPage.tsx  ← NOVO
│   └── CompanyInvoicesPage.tsx       ← NOVO
├── errors/errorMessages.ts           ← MODIFICAR: 6 códigos novos
├── layouts/AppLayout.tsx             ← MODIFICAR: 2 NavLink com gate isAdmin()
└── main.tsx                          ← MODIFICAR: 2 rotas novas
```

Fora do domínio das faturas, e por decisão desta ronda:

```
src/components/enterprise/create/enterpriseFormSchema.ts       ← slug + isTest
src/components/enterprise/create/BasicInfoSection.tsx          ← os dois campos
src/components/enterprise/edit/EditEnterpriseOverviewCard.tsx  ← idem, na edição
src/types/enterprise.ts                                        ← slug, isTest
```

**Rotas** (segmento novo escreve-se em **inglês**, mesmo com pai português — ver
[[design/backoffice-app-shell-and-auth]] §1):

| Rota | Página |
|---|---|
| `/backoffice/invoices/unidentified` | `UnidentifiedInvoicesPage` |
| `/backoffice/invoices/company` | `CompanyInvoicesPage` |

São rotas **de topo**, portanto precisam de entrada no nav do `AppLayout` — não basta um card
no `BackofficeHome`.

## 4. Implementation Steps

1. `types/invoice.ts` — `InvoiceDocument`, campos novos, `enterpriseId: string | null`,
   `InvoiceRegisterPayload`. **Corre `npx tsc` já aqui**: `enterpriseId` a passar a nullable
   vai acender todo o lado que o usa sem verificar.
2. `services/invoiceService.ts` — as cinco funções novas.
3. `errors/errorMessages.ts` — os 6 códigos.
4. `InvoiceDocumentGallery.tsx` — grid, juntar, remover, substituir (com confirmação).
5. `InvoiceDetailDrawer.tsx` — trocar o bloco de ficheiro único pela galeria.
6. `InvoicesList.tsx` — miniatura + `+N`, tag de `documentStatus`, colunas opcionais.
7. `invoiceFormSchema.ts` + `InvoiceRegisterDrawer.tsx`.
8. Botão "Registar sem ficheiro" nas **três** listas, com `scope` pré-preenchido pela página.
9. `UnidentifiedInvoicesPage` + `CompanyInvoicesPage`.
10. `main.tsx` + `AppLayout.tsx`.
11. `slug`/`isTest` no formulário de projeto.
12. Verificar no browser (§8).

## 5. Code Templates

### 5.1 Serviço — funções nomeadas, sem `try/catch`

O erro sobe intacto até ao `ErrorHandler` do componente; um `try/catch` aqui engole o
`errorCode` que o backend enviou (ver [[design/backoffice-services-and-error-handling]] §3).

```ts
// services/invoiceService.ts
import api from "@/api";
import type { ConstructionInvoice, InvoiceRegisterPayload, InvoiceUploadResult } from "@/types/invoice";

export async function registerInvoice(payload: InvoiceRegisterPayload): Promise<ConstructionInvoice> {
  const { data } = await api.post<ConstructionInvoice>("/construction-invoices/register", payload);
  return data;
}

export async function addInvoiceDocument(invoiceId: string, file: File): Promise<InvoiceUploadResult> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<InvoiceUploadResult>(
    `/construction-invoices/${invoiceId}/documents`, form,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return data;
}

export async function deleteInvoiceDocument(invoiceId: string, documentId: string): Promise<void> {
  await api.delete(`/construction-invoices/${invoiceId}/documents/${documentId}`);
}

export async function listUnidentifiedInvoices(
  params: { q?: string; page?: number; size?: number },
) {
  const { data } = await api.get("/construction-invoices/unidentified", { params });
  return data;
}

export async function listCompanyInvoices(
  params: { q?: string; page?: number; size?: number },
) {
  const { data } = await api.get("/construction-invoices/company", { params });
  return data;
}
```

> **Paginação**: a resposta é um `Page` do Spring — `{ content, page: { size, number,
> totalElements, totalPages } }` — e `page.number` é **0-based**, contra o 1-based da
> `<Pagination>` do AntD. A conversão faz-se na página (`current={page + 1}`,
> `onChange={(p) => setPage(p - 1)}`); é uma fonte recorrente de listas que abrem na página
> errada. Importa `DEFAULT_PAGE_SIZE`/`PAGE_SIZE_OPTIONS` de `config/pagination.ts`, não
> hardcodes o tamanho.

### 5.2 Schema Zod — mensagens são chaves i18n, nunca PT fixo

```ts
// components/invoices/invoiceFormSchema.ts
import { z } from "zod";

export const InvoiceRegisterSchema = z
  .object({
    scope: z.enum(["PROJECT", "COMPANY", "UNIDENTIFIED"]),
    enterpriseId: z.string().uuid().nullable().optional(),
    supplierName: z.string().max(255, "invoices.formErrors.supplierNameTooLong").optional(),
    supplierNif: z.string().regex(/^[0-9]{0,20}$/, "invoices.formErrors.nifInvalid").optional(),
    invoiceNumber: z.string().max(100, "invoices.formErrors.numberTooLong").optional(),
    invoiceDate: z.string().optional(),
    totalAmount: z.number().nonnegative("invoices.formErrors.totalNegative").optional(),
    description: z.string().max(500, "invoices.formErrors.descriptionTooLong").optional(),
    documentStatus: z.enum(["MISSING", "TO_PRINT", "TO_REQUEST"]),
    possibleEnterprises: z.string().max(500).optional(),
    askWhom: z.string().max(255).optional(),
    notes: z.string().max(2000).optional(),
  })
  // O mesmo check que o backend faz (ck_invoice_scope_enterprise): vale a pena
  // duplicá-lo aqui para o utilizador não gastar um round-trip a descobrir.
  .refine((v) => (v.scope === "PROJECT" ? !!v.enterpriseId : !v.enterpriseId), {
    message: "invoices.formErrors.scopeEnterpriseMismatch",
    path: ["enterpriseId"],
  });

export type InvoiceRegisterForm = z.infer<typeof InvoiceRegisterSchema>;
```

`documentStatus` **não** inclui `ARCHIVED` de propósito: uma fatura registada sem ficheiro não
está arquivada, e o backend recusa-a com `INVOICE_017`. O estado passa a `ARCHIVED` sozinho
quando alguém juntar um documento.

### 5.3 Drawer de registo — RHF + Zod, `disabled={!isValid}`

Largura **600** (formulário simples, ver [[design/backoffice-drawers-and-modals]]). O `scope`
não é um campo escolhido pelo utilizador — vem da página onde o botão foi carregado.

```tsx
// components/invoices/InvoiceRegisterDrawer.tsx
type Props = {
  open: boolean;
  scope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";
  enterpriseId?: string;      // só quando scope === "PROJECT"
  onClose: () => void;
  onCreated: (invoice: ConstructionInvoice) => void;
};

const { control, handleSubmit, formState: { errors, isValid, isSubmitting } } =
  useForm<InvoiceRegisterForm>({
    resolver: zodResolver(InvoiceRegisterSchema),
    mode: "onChange",                       // necessário para isValid atualizar a escrever
    defaultValues: {
      scope,
      enterpriseId: scope === "PROJECT" ? enterpriseId : null,
      documentStatus: "TO_REQUEST",
    },
  });

const onSubmit = handleSubmit(async (values) => {
  try {
    const created = await registerInvoice(values);
    notificationService.success("Fatura registada");
    onCreated(created);
    onClose();
  } catch (err) {
    ErrorHandler.handle(err);               // nunca message.error com string fixa
  }
});

// rodapé: ações à direita, primária mais à direita, textos via t()
<Space style={{ justifyContent: "flex-end", width: "100%" }}>
  <Button onClick={onClose} disabled={isSubmitting}>{t("common.cancel")}</Button>
  <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
    {t("common.create")}
  </Button>
</Space>
```

Campos por `scope`: `possibleEnterprises` e `askWhom` só fazem sentido em `UNIDENTIFIED` — não
os mostres nas outras duas variantes, mesmo que o backend os aceite.

Erro por campo: usa um `<FieldError name="x" errors={errors} />` partilhado com
`<Text type="danger">` e `t(...)` — o bloco copiado campo a campo é drift conhecido, e num dos
ficheiros nem sequer é vermelho ([[design/backoffice-forms-and-validation]] §5).

### 5.4 Galeria de documentos

```tsx
// components/invoices/InvoiceDocumentGallery.tsx
// Grid de miniaturas. Clicar abre o fileUrl assinado; o hover revela remover.
<div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(120px, 1fr))", gap: 12 }}>
  {documents.map((doc) => (
    <figure key={doc.id} className="ind-card" style={{ margin: 0, padding: 8 }}>
      {doc.thumbnailUrl
        ? <img src={doc.thumbnailUrl} alt={doc.originalFilename ?? "documento"} />
        : <FileOutlined style={{ fontSize: 32, opacity: 0.4 }} />}
      <figcaption style={{ fontSize: 12, opacity: 0.6 }}>
        {doc.originalFilename ?? "—"}
      </figcaption>
    </figure>
  ))}
</div>
```

Três ações, e a diferença entre elas é o que mais importa acertar:

| Ação | Endpoint | Confirmação |
|---|---|---|
| Juntar documento | `POST /{id}/documents` | não |
| Remover **este** documento | `DELETE /{id}/documents/{documentId}` | `useConfirm()` |
| **Substituir** o ficheiro | `POST /{id}/file` | `useConfirm()` a dizer **quantos** documentos vão desaparecer |

`POST /{id}/file` larga **todos** os documentos e põe um só no lugar — é o comportamento
antigo, mantido por compatibilidade. Com N documentos na fatura isso deixou de ser óbvio pelo
nome do botão, por isso a confirmação tem de nomear o número: *"Isto vai apagar os 4
documentos desta fatura e deixar só o novo."*

> Usa `useConfirm()` (`context/ConfirmDialogContext`), **não `Popconfirm`** — o `Popconfirm` só
> sobrevive em três ficheiros por migrar e não é o padrão para código novo
> ([[design/backoffice-drawers-and-modals]]).

### 5.5 Lista — miniatura com `+N`, estado do documento

```tsx
// InvoicesList.tsx — coluna do documento
{
  title: "Doc.",
  key: "document",
  width: 80,
  render: (_, r) => (
    <div style={{ position: "relative", width: 44 }}>
      {r.thumbnailUrl
        ? <img src={r.thumbnailUrl} alt="" style={{ width: 44, borderRadius: 4 }} />
        : <FileOutlined style={{ fontSize: 20, opacity: 0.35 }} />}
      {r.documents.length > 1 && (
        <span className="ind-tag ind-tag-neutral" style={{ position: "absolute", right: -8, bottom: -6 }}>
          +{r.documents.length - 1}
        </span>
      )}
    </div>
  ),
}
```

Estado do documento como `ind-tag` — nunca `<Badge>` nem estilo inline
([[design/backoffice-tables-and-lists]] §3):

```tsx
const DOCUMENT_STATUS_MAP: Record<DocumentStatus, { label: string; cls: string }> = {
  ARCHIVED:   { label: "Arquivada",    cls: "ind-tag-neutral" },
  MISSING:    { label: "Sem ficheiro", cls: "ind-tag-outline" },
  TO_PRINT:   { label: "Por imprimir", cls: "ind-tag-accent" },
  TO_REQUEST: { label: "Por pedir",    cls: "ind-tag-accent" },
};
```

Coluna de ações por `ListActions`/`ListActionPrimary`/`ListActionSecondary`/`ListActionDanger`
de `components/common/ListActions` — não repitas o objeto de estilo nem uses `<Space
direction="vertical">` com botões estilizados à mão.

Na **quarentena**, três colunas a mais: `possibleEnterprises`, `askWhom` e "Aqui desde"
(`createdAt`) — e a ordenação por omissão é a mais antiga primeiro, que é o que torna a lista
uma fila de trabalho em vez de um arquivo.

### 5.6 Página nova — cabeçalho kicker + `h1`

`AppLayout` já aplica o padding do `<main>`; **não** acrescentes `padding`/`minHeight` na
página.

```tsx
<div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
  <div>
    <h6 style={{ color: "var(--ind-accent-700)" }}>Faturas</h6>
    <h1 style={{ margin: 0 }}>Por identificar</h1>
  </div>
  <Button type="primary" icon={<PlusOutlined />} onClick={() => setRegisterOpen(true)}>
    Registar sem ficheiro
  </Button>
</div>
```

Pesquisa: `<Input prefix={<SearchOutlined style={{ opacity: .5 }} />} />` com `maxWidth: 320` +
botão "Limpar", como em `TasksPage`/`EnterprisesList`. Tabela dentro de
`<div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>`, vazio com
`<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />`.

### 5.7 Rotas e nav

```tsx
// main.tsx — dentro do <Route path="/backoffice" element={<PrivateRoute><AppLayout/></PrivateRoute>}>
<Route path="invoices/unidentified" element={<UnidentifiedInvoicesPage />} />
<Route path="invoices/company" element={<CompanyInvoicesPage />} />
```

```tsx
// AppLayout.tsx — gate por useAuth(), nunca lendo role direto do contexto
{isAdmin() && (
  <>
    <NavLink to="/backoffice/invoices/unidentified">Por identificar</NavLink>
    <NavLink to="/backoffice/invoices/company">Despesas da empresa</NavLink>
  </>
)}
```

### 5.8 `slug` e `isTest` no projeto

```ts
slug: z.string()
  .max(120, "enterprises.formErrors.slugTooLong")
  .regex(/^[\p{L}\p{N} .\-_]*$/u, "enterprises.formErrors.slugInvalid")
  .optional(),
isTest: z.boolean().default(false),
```

O `slug` é **exatamente** o nome da pasta `Empreendimentos\<Obra>\` no vault da Vilatro, com
espaços e acentos (`Vila Petrus`) — daí a lista branca aceitar espaços e pontuação leve, e não
ser um slug no sentido de URL. Rótulo sugerido: *"Pasta no vault (Vilatro)"*, com ajuda a
explicar que renomear de um lado obriga a renomear do outro. Duplicado → `ENT_032`.

`isTest` é um `<Switch>`: marca obras que existem só para experimentar, para os relatórios as
poderem excluir.

> A **criação** de projeto usa RHF+Zod e a **edição** usa AntD Form — mesma entidade, duas
> bibliotecas ([[design/backoffice-forms-and-validation]] §1). Ao acrescentar estes dois campos
> aos dois sítios, a convenção escolhida é RHF+Zod; migrar o `EditEnterpriseOverviewCard` é
> oportunismo bem-vindo, não obrigação desta tarefa.

## 6. Error Handling

`ErrorHandler.handle(err)` em **todo** o `catch` de chamada à API — é o padrão prescrito, e é o
único que aproveita o `errorCode` que o backend envia. `message.error("...")` com string fixa é
o padrão numericamente dominante no código mas **não** é o que se segue em código novo
([[design/backoffice-services-and-error-handling]] §2).

Toasts de sucesso por `notificationService`, não `message.success`.

> 🐛 Nunca `try { … } finally { setLoading(false) }` **sem `catch`**: o spinner pára e dá a
> ilusão de sucesso, enquanto o erro sobe como promise rejeitada e o utilizador fica a olhar
> para uma lista vazia sem mensagem nenhuma. Já foi um bug real neste código.

Casos que valem tratamento próprio, além do mapa:

- **`INVOICE_010`/`011`/`012`** — a mensagem do backend nomeia onde está a fatura original.
  Mostra-a tal como vem; não a resumas para "duplicado".
- **`INVOICE_013`** — ao tentar associar uma fatura de quarentena a uma rubrica. O botão de
  associar deve estar `disabled` quando `scope !== "PROJECT"`, com tooltip — o erro é a rede de
  segurança, não a interface.

## 7. Integration Points

- `InvoiceDetailDrawer` é partilhada pelas três listas. O que muda com o `scope` é o que faz
  sentido mostrar: rubrica/associação só em `PROJECT`, notas de quarentena só em
  `UNIDENTIFIED`.
- `EnterpriseInvoicesPage` continua a passar `enterpriseId`; as páginas novas não passam
  nenhum. Se extraíres um componente comum, é o `enterpriseId` opcional que os separa.
- O `documentStatus` **não é um campo de formulário livre** na edição: mostra-o, mas quem o
  muda para `ARCHIVED`/`MISSING` é o backend, ao juntar ou largar documentos. Editável só
  entre `MISSING`, `TO_PRINT` e `TO_REQUEST`.

## 8. Testing Checklist

Type-check e lint não substituem o browser.

- [ ] `npx tsc` — sem erros novos além dos 35 pré-existentes documentados em [[docs/commands]]
- [ ] Registar uma fatura sem ficheiro numa obra, na empresa e na quarentena; aparece na lista certa
- [ ] `scope = PROJECT` sem obra → `INVOICE_014` legível; `COMPANY` com obra → `INVOICE_015`
- [ ] Registar com `documentStatus = ARCHIVED` é impossível pela UI (não está nas opções)
- [ ] Juntar foto **e** PDF à mesma fatura; as duas miniaturas na galeria, as duas abrem
- [ ] Lista mostra a 1ª miniatura com badge `+1`
- [ ] Remover um documento; ao remover o último, o estado volta a "Sem ficheiro"
- [ ] "Substituir" avisa **quantos** documentos vai apagar antes de o fazer
- [ ] O mesmo ficheiro numa segunda obra é recusado, e a mensagem **nomeia a primeira**
- [ ] Fatura em "Por identificar" não deixa associar a uma rubrica (botão inativo + `INVOICE_013`)
- [ ] Quarentena ordena a mais antiga primeiro e mostra "Aqui desde"
- [ ] `EMPLOYEE` não vê os dois `NavLink`; e a chamar `/unidentified` à mão recebe `403`
- [ ] Criar projeto com `slug` e `isTest`; repetir o slug → `ENT_032`
- [ ] Paginação abre na página certa (o `page.number` do Spring é 0-based)

## Related

- [[docs/api]] → "Faturas de obra" — o contrato, mantido à mão
- [[docs/database]] — as tabelas e os quatro enums novos
- [[docs/excel-parity]] §2 (slug) e §5 (unicidade global)
- [[docs/faturas-modelo-alvo]] §2.2 — porque é que a fatura deixou de ser o ficheiro
- [[design/backoffice-tables-and-lists]], [[design/backoffice-drawers-and-modals]],
  [[design/backoffice-forms-and-validation]], [[design/backoffice-services-and-error-handling]],
  [[design/backoffice-app-shell-and-auth]]
