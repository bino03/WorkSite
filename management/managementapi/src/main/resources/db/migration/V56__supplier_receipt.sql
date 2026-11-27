-- =============================================================
-- V56__supplier_receipt.sql
-- O recibo do fornecedor (RC/RG no QR da AT) é um documento próprio, ligado
-- às N faturas que liquida.
--
-- Até aqui o único sítio para o recibo era a prova de um pagamento
-- (payment.proof_*). Não chegava: faturas pagas uma a uma têm N pagamentos e o
-- recibo único que chega depois teria de ser copiado para cada um; um
-- pagamento que já tem o extrato anexado não tem onde o pôr; e o recibo tem
-- identidade própria (nº, data, valor, NIF do emissor).
--
-- O recibo NÃO muda o estado de pagamento: esse continua derivado só de
-- invoice_payment (V31). Um recibo prova que o fornecedor recebeu; o dinheiro
-- sai num payment.
--
-- Sem RLS nem grants, como a V31: só o backend liga à BD (docs/security.md).
-- Ver notes/roadmap/recibos.md e docs/faturas-modelo-alvo.md §5.
-- =============================================================

set search_path to worksite, public;

create table if not exists worksite.supplier_receipt (
    id                         uuid          not null default gen_random_uuid() primary key,
    created_at                 timestamptz   not null default now(),
    updated_at                 timestamptz   not null default now(),

    -- emissor: texto, como em construction_invoice (sem FK para supplier)
    supplier_name              text,
    supplier_nif               text,

    -- "REC 2026V/931" (campo G do QR), como vem escrito
    receipt_number             text,
    -- o mesmo, só letras/dígitos em maiúsculas: cada software escreve o número
    -- à sua maneira, e é por este que se deteta o duplicado
    receipt_number_normalized  text,
    receipt_date               date          not null,
    total_amount               numeric(14,2) not null check (total_amount >= 0),
    notes                      text,

    -- o documento; obrigatório (um recibo sem ficheiro não prova nada).
    -- Nunca a URL bruta, apenas bucket + chave.
    file_bucket                text          not null,
    file_key                   text          not null,
    file_filename              text,
    file_mime                  text,

    registered_by              uuid          references worksite.profile(id) on delete set null,
    registered_at              timestamptz   not null default now()
);

create trigger tg_supplier_receipt_updated_at
    before update on worksite.supplier_receipt
    for each row execute function worksite.tg_set_updated_at();

-- o mesmo recibo chegou várias vezes no import do email
create unique index if not exists uq_supplier_receipt_nif_number
    on worksite.supplier_receipt (supplier_nif, receipt_number_normalized)
    where supplier_nif is not null and receipt_number_normalized is not null;

-- ── a junção recibo ↔ fatura ─────────────────────────────────
-- Quanto deste recibo é para esta fatura: os recibos de conta corrente listam o
-- valor por fatura, e um recibo pode cobrir só parte de uma. Apagar a fatura
-- apaga a ligação; o recibo fica (pode ser religado).
create table if not exists worksite.invoice_receipt (
    receipt_id  uuid          not null references worksite.supplier_receipt(id) on delete cascade,
    invoice_id  uuid          not null references worksite.construction_invoice(id) on delete cascade,
    amount      numeric(14,2) not null check (amount >= 0),
    primary key (receipt_id, invoice_id)
);

create index if not exists idx_invoice_receipt_invoice on worksite.invoice_receipt(invoice_id);

-- ── os pagamentos que o recibo originou ──────────────────────
-- Quando o recibo regista também o pagamento das faturas por liquidar, cria um
-- payment POR OBRA (o agregado nunca atravessa obras). Por isso é uma junção e
-- não uma coluna. Só regista a origem: apagar o recibo não apaga o pagamento.
create table if not exists worksite.supplier_receipt_payment (
    receipt_id  uuid not null references worksite.supplier_receipt(id) on delete cascade,
    payment_id  uuid not null references worksite.payment(id) on delete cascade,
    primary key (receipt_id, payment_id)
);

create index if not exists idx_supplier_receipt_payment_payment on worksite.supplier_receipt_payment(payment_id);

-- ── auditoria ────────────────────────────────────────────────
alter type worksite.entity_type add value if not exists 'supplier_receipt';
