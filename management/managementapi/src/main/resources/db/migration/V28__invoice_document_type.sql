-- =============================================================
-- V28__invoice_document_type.sql
-- Só o schema da nota de crédito — a lógica é a fase 3.
--
-- Uma nota de crédito é o mesmo documento com sinal contrário, e vive na
-- mesma tabela: mudar de tabela obrigaria a duplicar tudo o que já existe
-- (documentos, pagamentos, associação a rubricas). O que a distingue é o
-- `document_type` e a fatura a que se refere.
--
-- `total_amount` mantém-se sempre positivo: o sinal vem do tipo, nunca do
-- valor. O líquido de uma fatura é `total_amount − Σ notas de crédito`, e
-- isso calcula-se na fase 3, não aqui.
--
-- ON DELETE RESTRICT de propósito: apagar uma fatura que já tem uma nota de
-- crédito lançada deixaria a nota a apontar para o vazio. Quem quiser apagar
-- trata primeiro da nota.
-- Ver docs/faturas-modelo-alvo.md, secções 2.1 e 6.
-- =============================================================

set search_path to worksite, public;

create type worksite.invoice_document_type as enum ('INVOICE', 'CREDIT_NOTE');

alter table worksite.construction_invoice
    add column if not exists document_type worksite.invoice_document_type
        not null default 'INVOICE',
    add column if not exists related_invoice_id uuid
        references worksite.construction_invoice(id) on delete restrict;

-- Uma nota de crédito refere-se sempre a uma fatura; uma fatura nunca se
-- refere a outra. Sem NC de NC: o alvo tem de ser um INVOICE, e isso o check
-- não consegue exigir — fica para a validação de serviço da fase 3.
alter table worksite.construction_invoice
    add constraint ck_invoice_credit_note_target check (
        (document_type = 'CREDIT_NOTE' and related_invoice_id is not null)
        or (document_type = 'INVOICE' and related_invoice_id is null)
    );

create index if not exists idx_invoice_related_invoice
    on worksite.construction_invoice(related_invoice_id)
    where related_invoice_id is not null;
