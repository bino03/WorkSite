-- =============================================================
-- V31__payment.sql
-- O pagamento é uma entidade própria; o estado da fatura é derivado.
--
-- No vault Excel da Vilatro "dar como pago" e "método de pagamento" são um só
-- gesto, e às vezes uma transferência liquida várias faturas de uma vez (o
-- fornecedor recebe N faturas num movimento — decisão 23 do Vilatro).
--
-- Modelar isto como colunas `paid_at`/`paid_by` na própria fatura obrigaria a
-- repetir a mesma informação em N linhas e a mantê-las iguais. Em vez disso:
--
--   payment          = o movimento (data, método, valor, prova, quem registou)
--   invoice_payment  = quanto DESTE movimento cobre CADA fatura (junção)
--
-- O caso normal (1 fatura, 1 pagamento) é só um `payment` com uma ligação.
-- O estado da fatura — UNPAID / PARTIAL / PAID — não é coluna: deriva-se de
-- Σ(invoice_payment.amount) vs. líquido da fatura. Uma coluna a duplicar isto
-- divergiria à primeira edição de um pagamento.
--
-- Ver docs/faturas-modelo-alvo.md §2.3 e docs/excel-parity.md §4.
-- =============================================================

set search_path to worksite, public;

-- Mapa dos valores do Excel (`Metodo Pagamento`) em docs/excel-parity.md §4.
-- Texto que não encaixe nos três primeiros → OUTRO, com o original em notes.
create type worksite.payment_method as enum ('NUMERARIO', 'MULTIBANCO', 'TRANSFERENCIA', 'OUTRO');

-- ── o movimento ──────────────────────────────────────────────
create table if not exists worksite.payment (
    id             uuid          not null default gen_random_uuid() primary key,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),

    -- a data em que o dinheiro saiu (do extrato, do recibo, ou a que o
    -- utilizador disser). Não é a data da fatura.
    paid_on        date          not null,
    method         worksite.payment_method not null,

    -- o valor do movimento. No caso normal = líquido da fatura; no agregado
    -- = soma do que cobre cada fatura.
    amount         numeric(14,2) not null check (amount >= 0),

    -- nº do movimento, "extrato ABANCA 28-08-2026", nome de quem pagou por
    -- conta da empresa...
    reference      text,
    -- o que a app não deduz: "pago pela Tabuada Pioneira", "desconto de 2% por
    -- pronto pagamento", "inclui a caução". "Pagas juntas" NUNCA se escreve
    -- aqui — é um facto estrutural (N ligações) que a UI gera sozinha.
    notes          text,

    -- recibo ou página do extrato: um ficheiro chega (a conciliação bancária
    -- completa é a fase 7). Nunca a URL bruta, apenas bucket + chave.
    proof_bucket   text,
    proof_key      text,
    proof_filename text,
    proof_mime     text,

    -- quem marcou como pago e quando — foi isto que o utilizador pediu
    registered_by  uuid          references worksite.profile(id) on delete set null,
    registered_at  timestamptz   not null default now()
);

create trigger tg_payment_updated_at
    before update on worksite.payment
    for each row execute function worksite.tg_set_updated_at();

create index if not exists idx_payment_paid_on on worksite.payment(paid_on);

-- ── a junção pagamento ↔ fatura ──────────────────────────────
-- Quanto DESTE movimento cobre ESTA fatura. Caso normal: uma linha com
-- amount = payment.amount. As invariantes são do serviço, não constraints:
--   • Σ(invoice_payment.amount por payment) = payment.amount
--   • por fatura, Σ(invoice_payment.amount) ≤ líquido(fatura)
create table if not exists worksite.invoice_payment (
    payment_id uuid          not null references worksite.payment(id) on delete cascade,
    invoice_id uuid          not null references worksite.construction_invoice(id) on delete cascade,
    amount     numeric(14,2) not null check (amount >= 0),
    primary key (payment_id, invoice_id)
);

-- para derivar o estado de pagamento por fatura sem varrer a tabela toda
create index if not exists idx_invoice_payment_invoice on worksite.invoice_payment(invoice_id);

-- ── auditoria ────────────────────────────────────────────────
-- Anular um pagamento fica em activity_log (EntityType.PAYMENT).
alter type worksite.entity_type add value if not exists 'payment';
