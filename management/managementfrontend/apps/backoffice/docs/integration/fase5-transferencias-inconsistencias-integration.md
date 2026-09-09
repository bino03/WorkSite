# Frontend Integration — Fase 5: Transferências de faturas + Inconsistências

Gerado a 2026-09-09 pela skill `frontend-integration-guide`, a partir do backend feito nesta
sessão. Plano: `notes/roadmap/plans/2026-09-09-fase5-transferencias.md`.

Design a seguir (ler só estes): [[design/backoffice-drawers-and-modals]],
[[design/backoffice-forms-and-validation]], [[design/backoffice-tables-and-lists]],
[[design/backoffice-services-and-error-handling]], [[design/backoffice-app-shell-and-auth]],
[[design/backoffice-buttons-and-icons]].

---

## 1. Overview

Duas entregas, ambas **ADMIN**:

1. **Transferir uma fatura** de âmbito/obra — nunca edição direta de `scope`/`enterprise`. Razão
   obrigatória. Apaga as despesas, guarda a repartição antiga, move as notas de crédito ligadas
   junto. Ponto de entrada: botão no `InvoiceDetailDrawer` e ação na `InvoicesList`; na quarentena
   ("Por identificar") **atribuir uma obra é transferir**.
2. **Página "Inconsistências"** — lista + markdown de notas sobre faturas por conciliar. Nova rota
   `/backoffice/invoices/incidents`, `NavLink` fixo (isAdmin). A transferência **sugere** criar uma
   (`suggestIncident`), mas a criação é sempre manual.

---

## 2. API Contract

### Transferir

`POST /construction-invoices/{id}/transfer` — **ADMIN**

```jsonc
// request
{ "targetScope": "PROJECT" | "COMPANY" | "UNIDENTIFIED",
  "targetEnterpriseId": "uuid",   // obrigatório sse targetScope === "PROJECT"; proibido nos outros
  "reason": "string não vazia" }

// response 200
{ "invoice": ConstructionInvoiceResponseDTO,   // já no novo âmbito, com transfers[] preenchido
  "suggestIncident": true }                     // true se a fatura tinha repartição, pagamentos ou NC
```

Erros:

| errorCode | Significado | Onde mostrar |
|---|---|---|
| `VALIDATION_ERROR` | `reason` ou `targetScope` em falta (`@NotBlank`) | erro por campo no form |
| `INVOICE_014` | `PROJECT` sem `targetEnterpriseId` | por campo (obra) |
| `INVOICE_015` | `COMPANY`/`UNIDENTIFIED` com `targetEnterpriseId` | não deve acontecer se o form esconder o campo |
| `INVOICE_016` | `targetScope` desconhecido | — |
| `INVOICE_002` | obra de destino não existe | por campo (obra) |
| `INVOICE_031` | obra de destino é `is_test` | toast — o picker não filtra `is_test`, o erro é aqui |
| `INVOICE_032` | destino = âmbito/obra atual | toast / inline |
| `INVOICE_033` | chamada sobre uma nota de crédito | não expor o botão numa NC |

O **detalhe** da fatura (`GET /construction-invoices/{id}`) passou a trazer:

```ts
transfers: {
  transferredAt: string;        // ISO
  fromScope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";
  fromEnterpriseId: string | null;
  fromEnterpriseName: string | null;
  toScope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";
  toEnterpriseId: string | null;
  toEnterpriseName: string | null;
  reason: string;
  byName: string;
}[]   // da mais recente para a mais antiga; VAZIO nas listas (só vem no detalhe)
```

### Inconsistências

Base `/invoice-incidents` — **ADMIN** em tudo.

| Método | Rota | Corpo / resposta |
|---|---|---|
| GET | `/invoice-incidents` | `InvoiceIncidentResponseDTO[]` (por resolver primeiro, depois recentes) |
| GET | `/invoice-incidents/{id}` | `InvoiceIncidentResponseDTO` — `INVOICE_034` se não existir |
| POST | `/invoice-incidents` | `{ title, body, invoiceIds: string[] }` → `201` `InvoiceIncidentResponseDTO`. `body` é markdown; `invoiceIds` não vazio; fatura inexistente → `INVOICE_035` |
| POST | `/invoice-incidents/{id}/resolve` | sem corpo → `InvoiceIncidentResponseDTO`. Idempotente |

```ts
interface InvoiceIncidentResponseDTO {
  id: string;
  title: string;
  body: string;                 // markdown
  resolvedAt: string | null;    // null = por resolver
  resolvedBy: string | null;
  resolvedByName: string | null;
  createdBy: string | null;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  invoices: {
    id: string;
    invoiceNumber: string | null;
    supplierName: string | null;
    scope: "PROJECT" | "COMPANY" | "UNIDENTIFIED";
    enterpriseId: string | null;
  }[];
}
```

---

## 3. Onde construir

```
src/
├── components/invoices/
│   ├── TransferInvoiceDrawer.tsx      ← novo — RHF + Zod
│   ├── transferFormSchema.ts          ← novo
│   ├── IncidentDrawer.tsx             ← novo — criar / resolver
│   ├── incidentFormSchema.ts          ← novo
│   └── InvoiceDetailDrawer.tsx        ← mod — botão "Transferir", bloco "Âmbito", bloco "Histórico de transferências"
├── components/invoices/InvoicesList.tsx        ← mod — ação "Transferir" (ListActionSecondary)
├── pages/backoffice/invoices/
│   ├── ScopedInvoicesPage.tsx         ← mod — quarentena: "Atribuir a uma obra" abre o drawer com PROJECT fixo
│   └── InvoiceIncidentsPage.tsx       ← novo — lista + painel de detalhe (markdown)
├── pages/backoffice/enterprise/EnterpriseInvoicesPage.tsx  ← mod — fio de "Transferir"
├── services/invoiceService.ts         ← mod — transferInvoice()
├── services/incidentService.ts        ← novo
├── types/invoice.ts                   ← mod — InvoiceTransferPayload, InvoiceTransferSummary, transfers[]
├── types/incident.ts                  ← novo
├── errors/errorMessages.ts            ← mod — INVOICE_031..035
├── locales/pt.json + en.json          ← mod — invoices.transfer.*, incidents.*
├── main.tsx                           ← mod — Route "invoices/incidents"
└── layouts/AppLayout.tsx              ← mod — NavLink "Inconsistências" (isAdmin)
```

`package.json`: adicionar **`react-markdown`** e **`remark-gfm`** (o corpo do incidente é markdown;
não há lib hoje). Instalar com a versão que o `npm` resolver e fixar no lockfile.

---

## 4. Implementation Steps

1. `errorMessages.ts`: acrescentar `INVOICE_031`–`INVOICE_035` (strings PT, espelho do
   `ErrorCode.java`). Ver [[design/backoffice-services-and-error-handling]] §2.
2. `types/invoice.ts`: `InvoiceTransferPayload`, `InvoiceTransferSummary`, e `transfers: InvoiceTransferSummary[]`
   no `ConstructionInvoice`.
3. `invoiceService.ts`: `transferInvoice(id, payload)` → `POST .../{id}/transfer`. Sem `try/catch`
   (o erro sobe ao `ErrorHandler` do componente).
4. `transferFormSchema.ts` + `TransferInvoiceDrawer.tsx` — ver §5/§6.
5. Ligar no `InvoiceDetailDrawer.tsx`:
   - bloco **"Âmbito"** (novo — hoje o drawer não mostra `scope` nem a obra): tag do `scope` +
     nome da obra (ou "Despesas da empresa" / "Por identificar").
   - botão **"Transferir"** na linha de ações ADMIN, ao lado de "Registar nota de crédito". Não
     renderizar numa `CREDIT_NOTE`.
   - bloco **"Histórico de transferências"** (só se `invoice.transfers.length > 0`) — molde do
     `InvoicePaymentSection` (cartão `ind-card`, `ind-card-kicker`, um item por transferência:
     `fromEnterpriseName ?? fromScope` → `toEnterpriseName ?? toScope`, `reason`, `byName`,
     `transferredAt`).
   - `onTransferred`: fechar o `TransferInvoiceDrawer`, `void fetchInvoice()`, `onChanged()`. Se
     `result.suggestIncident`, abrir o `IncidentDrawer` já com esta fatura pré-selecionada.
6. `InvoicesList.tsx`: ação "Transferir" (`ListActionSecondary`), prop `onTransfer(row)`. Só
   `isAdmin()`. Ver [[design/backoffice-tables-and-lists]] e [[design/backoffice-buttons-and-icons]].
7. `ScopedInvoicesPage.tsx` (quarentena `UNIDENTIFIED`): o CTA "Atribuir a uma obra" abre o
   `TransferInvoiceDrawer` com `targetScope` fixo em `PROJECT` (campo de âmbito escondido/bloqueado).
   A razão continua obrigatória.
8. `EnterpriseInvoicesPage.tsx`: passar `onTransfer` à `InvoicesList` e montar o `TransferInvoiceDrawer`.
9. Incidentes: `types/incident.ts`, `incidentService.ts` (`listIncidents`, `getIncident`,
   `createIncident`, `resolveIncident`), `incidentFormSchema.ts`, `IncidentDrawer.tsx`,
   `InvoiceIncidentsPage.tsx`.
10. `main.tsx`: `<Route path="invoices/incidents" element={<InvoiceIncidentsPage />} />` (segmento
    em inglês — [[design/backoffice-app-shell-and-auth]] §1).
11. `AppLayout.tsx`: `NavLink` "Inconsistências" dentro do bloco `{isAdmin() && (...)}`, junto de
    "Por identificar" / "Despesas da empresa". Ícone `WarningOutlined` ou `ExceptionOutlined`.
12. `pt.json` / `en.json`: blocos `invoices.transfer.*` e `incidents.*`.
13. `npx tsc -b` (linha de base = 26) e `npm run lint` — sem erros novos.

---

## 5. Form schemas (Zod)

```ts
// transferFormSchema.ts
import { z } from "zod";

export const transferFormSchema = z
  .object({
    targetScope: z.enum(["PROJECT", "COMPANY", "UNIDENTIFIED"]),
    targetEnterpriseId: z.string().uuid().optional().nullable(),
    reason: z.string().trim().min(1, "invoices.formErrors.transferReasonRequired"),
  })
  .refine(
    (v) => v.targetScope !== "PROJECT" || !!v.targetEnterpriseId,
    { path: ["targetEnterpriseId"], message: "invoices.formErrors.transferEnterpriseRequired" }
  );

export type TransferFormValues = z.infer<typeof transferFormSchema>;
```

```ts
// incidentFormSchema.ts
export const incidentFormSchema = z.object({
  title: z.string().trim().min(1, "incidents.formErrors.titleRequired").max(200),
  body: z.string().trim().min(1, "incidents.formErrors.bodyRequired"),
  invoiceIds: z.array(z.string().uuid()).min(1, "incidents.formErrors.invoicesRequired"),
});
```

Mensagens = chaves i18n, renderizadas com `t(msg)` no helper `fieldError` (mesmo padrão de
`creditNoteFormSchema`/`paymentFormSchema`). Ver [[design/backoffice-forms-and-validation]].

---

## 6. TransferInvoiceDrawer — esqueleto

Segue `CreditNoteDrawer` / `MarkPaidDrawer`: `createPortal`, `Drawer` AntD (`width="min(560px,94vw)"`),
`useForm({ resolver: zodResolver(transferFormSchema), mode: "onChange" })`, `reset()` no
`useEffect([open])`, footer com `disabled={!isValid}` / `loading={isSubmitting}`.

- **Âmbito** (`Radio.Group`): "Outra obra" (`PROJECT`) · "Despesas da empresa" (`COMPANY`) · "Por
  identificar" (`UNIDENTIFIED`). Quando o drawer é aberto pela quarentena, esconder e forçar `PROJECT`.
- **Obra de destino** (`Select showSearch`, só quando `PROJECT`): `onSearch` → `searchEnterprises(q)`
  de `services/enterpriseService.ts` (molde: `TaskFormDrawer` `handleEnterpriseSearch`). ⚠️
  `searchEnterprises` **não** filtra `is_test` — "Vila Sol"/obras de teste aparecem; a recusa vem do
  backend (`INVOICE_031`), mostrar o toast.
- **Razão** (`Input.TextArea`, obrigatória).
- **Aviso** fixo: "As despesas desta fatura nesta obra vão ser apagadas. As notas de crédito ligadas
  são transferidas junto." — não é um `useConfirm()` à parte; a razão obrigatória já é a paragem
  consciente ([[design/backoffice-buttons-and-icons]] → confirmação).
- `onSubmit`: `await transferInvoice(invoice.id, values)`; no `catch` → `ErrorHandler.handle(e)`.
  No sucesso: `onTransferred(result)`.

---

## 7. Página "Inconsistências"

Lista simples (sem paginação — volume baixo). Cada linha: `title`, badge "Por resolver"/"Resolvida"
(`ind-tag`), nº de faturas, `createdByName`, data. Clicar abre um painel/drawer de detalhe:

- `body` renderizado com `<ReactMarkdown remarkPlugins={[remarkGfm]}>` (sem HTML embutido).
- lista das faturas ligadas — cada uma clicável, abre o `InvoiceDetailDrawer` dessa fatura (reusar
  o `viewingId` já existente, ou navegar para a página da obra).
- botão "Marcar como resolvida" (some quando `resolvedAt != null`; mostra quem/quando).
- botão "Nova inconsistência" no topo → `IncidentDrawer` em modo criação.

`IncidentDrawer` (criar): `title`, `body` (textarea markdown com dica "aceita markdown"),
seleção de faturas. Quando aberto a partir de uma transferência (`suggestIncident`), vem com a
fatura já em `invoiceIds` e o `body` pré-preenchido com a razão da transferência.

Sem apagar incidentes nesta entrega (só criar/resolver).

---

## 8. Testing checklist (browser — passagem única diferida)

Registar em `notes/verificacao-browser-pendente.md`.

- [ ] `InvoiceDetailDrawer` mostra o âmbito e a obra da fatura.
- [ ] "Transferir" obra → obra: escolher obra, escrever razão, confirmar → a fatura muda de obra,
      as despesas somem, o "Histórico de transferências" mostra a linha.
- [ ] Transferir sem razão → botão desativado / erro no campo.
- [ ] Transferir para "Despesas da empresa" e para "Por identificar" → obra fica vazia.
- [ ] Transferir para a obra onde já está → `INVOICE_032` (toast PT).
- [ ] Transferir para a obra de teste → `INVOICE_031` (toast PT).
- [ ] Fatura repartida 70/30 com NC: transferir → NC vai junto, `suggestIncident` abre o `IncidentDrawer`.
- [ ] Numa `CREDIT_NOTE` o botão "Transferir" não aparece.
- [ ] Quarentena: "Atribuir a uma obra" → mesmo drawer, âmbito fixo em PROJECT, razão obrigatória.
- [ ] Página "Inconsistências" no menu (só ADMIN); criar uma com 2 faturas; o `body` markdown
      renderiza (lista, negrito); "Marcar como resolvida" e o badge muda; segundo resolve não muda a data.
- [ ] Fatura inexistente no `invoiceIds` → `INVOICE_035`.
- [ ] `npx tsc -b` = 26; `npm run lint` sem erros novos.
