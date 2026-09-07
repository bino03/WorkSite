# Frontend Integration — Faturas, fase 2: Pagamentos

> Gerado a 2026-09-07 a partir do backend da migração `V31`. Fonte de verdade do contrato:
> [[docs/api]] → "Pagamentos" e [[docs/database]] → `payment`. Contexto de negócio:
> [[docs/faturas-modelo-alvo]] §2.3, [[docs/excel-parity]] §4. Plano:
> `notes/roadmap/plans/2026-09-04-fase2-pagamentos.md`.

## 1. Overview

Uma fatura não tem estado de pagamento guardado — deriva-se sempre das ligações a `payment`.
"Marcar como paga" cria um movimento (`payment`) + uma ligação (`invoice_payment`); o estado
(`UNPAID` / `PARTIAL` / `PAID`) sai calculado em todos os DTOs de fatura. Um movimento pode
liquidar N faturas (agregado, raro). Tudo `ADMIN`.

## 2. API Contract

### 2.1 Endpoints novos (todos `ADMIN`)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/construction-invoices/{id}/payments` | multipart: `payment` (JSON `MarkPaidRequest`) + `proof` opcional | `201` `PaymentResponse` |
| `POST` | `/construction-invoices/payments` | multipart: `payment` (JSON `AggregatePaymentRequest`) + `proof` opcional | `201` `AggregatePaymentResult` (`created=true`) **ou** `200` (`created=false` + `leftOut[]`) |
| `DELETE` | `/construction-invoices/payments/{paymentId}` | — | `204` |

`MarkPaidRequest`: `{ paidOn: ISO date, method: "NUMERARIO"|"MULTIBANCO"|"TRANSFERENCIA"|"OUTRO",
amount?: number (por omissão = o que falta liquidar), reference?: string, notes?: string }`.

`AggregatePaymentRequest`: `MarkPaidRequest` + `{ invoiceIds: string[], amount: number (obrigatório) }`.

`AggregatePaymentResult`: `{ created: boolean, payment: PaymentResponse|null, leftOut:
{ invoiceId, invoiceNumber, supplierName, remaining }[], selectedTotal: number, movementAmount: number }`.

`PaymentResponse`: `{ id, paidOn, method, amount, reference, notes, proofUrl, proofFilename,
registeredBy, registeredByName, registeredAt, allocations: { invoiceId, invoiceNumber, supplierName, amount }[] }`.

### 2.2 Campos novos no DTO de fatura (`ConstructionInvoiceResponseDTO`)

```ts
paymentStatus: "UNPAID" | "PARTIAL" | "PAID";
paidAmount: number;
netAmount: number | null;         // = totalAmount até à fase 3
payments: InvoicePaymentSummary[];
```

```ts
interface InvoicePaymentSummary {
  paymentId: string;
  paidOn: string;
  method: string;
  amountOnThisInvoice: number;
  paymentAmount: number;
  reference: string | null;
  notes: string | null;
  proofUrl: string | null;        // só no detalhe
  proofFilename: string | null;
  registeredBy: string | null;
  registeredByName: string | null;
  registeredAt: string;
  alsoCovers: string[];           // nº das outras faturas do mesmo movimento
}
```

### 2.3 Filtro nas listas

`GET /construction-invoices/unidentified|company|enterprise/{id}` aceitam `?outstanding=true`
(só por liquidar). Aplicado no servidor — a paginação continua certa.

### 2.4 Códigos de erro (`errorMessages.ts`, espelho 1:1)

```
INVOICE_019  Esta fatura já está totalmente paga
INVOICE_020  O valor a pagar é superior ao que falta liquidar nesta fatura
INVOICE_021  (agregado) o valor do movimento é superior à soma — o caso inferior NÃO é erro (200 + leftOut)
INVOICE_022  Um pagamento agregado só pode juntar faturas da mesma obra
INVOICE_023  Pagamento não encontrado
INVOICE_024  Indique pelo menos uma fatura para o pagamento
INVOICE_025  A prova de pagamento tem de ser PDF ou imagem
```

## 3. Onde construir

```
src/types/invoice.ts                         ← PaymentStatus, PaymentMethod, InvoicePaymentSummary,
                                               campos no ConstructionInvoice, payloads, filtro outstanding
src/services/paymentService.ts               ← NOVO: markInvoicePaid, registerAggregatePayment, deletePayment
src/components/invoices/paymentFormSchema.ts  ← NOVO: Zod (mensagens = chaves i18n)
src/components/invoices/MarkPaidDrawer.tsx    ← NOVO: 600px, RHF+Zod, data/método/referência/valor/prova
src/components/invoices/InvoiceDetailDrawer.tsx ← botão "Marcar como paga"/"Anular", quem/quando, alsoCovers
src/components/invoices/InvoicesList.tsx      ← selo UNPAID/PARTIAL/PAID (ind-tag), filtro "Por liquidar"
src/pages/backoffice/enterprise/EnterpriseInvoicesPage.tsx ← filtro + ação em bloco "Registar pagamento"
src/pages/backoffice/invoices/ScopedInvoicesPage.tsx ← permitir marcar como paga no detalhe
src/errors/errorMessages.ts                  ← 7 códigos
src/locales/{pt,en}.json                     ← invoices.payment.* + invoices.formErrors.*
```

## 4. Regras que a UI tem de respeitar

- O `amount` do drawer "marcar como paga" é **editável**, default = `netAmount - paidAmount`. Abaixo → `PARTIAL`.
- O agregado só deixa selecionar faturas da **mesma obra** (ou todas COMPANY, ou todas UNIDENTIFIED);
  fornecedor diferente é **aviso**, não bloqueio.
- Quando o agregado devolve `created=false`, mostrar `leftOut[]` ("tire estas da seleção") e a
  diferença `movementAmount` vs `selectedTotal` — **nada foi gravado**.
- `useConfirm()` para "Anular pagamento" (title/actionLabel próprios — os defaults são de eliminação).
- `ErrorHandler.handle()` em todos os catch.
- Faturas `UNIDENTIFIED`/`COMPANY` **também** podem ser pagas.

## 5. Verificação no browser

Fica para a passagem única do fim da linha "Paridade com o Excel" — checklist em
`notes/verificacao-browser-pendente.md` §1c.
