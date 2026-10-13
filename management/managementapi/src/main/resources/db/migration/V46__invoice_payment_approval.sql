-- =============================================================
-- V46__invoice_payment_approval.sql
-- A fatura ganha o passo "aprovada para pagamento".
--
-- Até aqui não havia nada entre "lançada" e "paga": quem pagava decidia
-- sozinho. A aprovação é um estado próprio — quem aprovou e quando — e não
-- uma lista de trabalho, para ficar na fatura depois de paga.
--
-- Mesmo desenho do `sent_to_accountant` (V16): boolean + quem + quando, e
-- desmarcar limpa os dois últimos.
--
-- Não bloqueia o pagamento: pagar uma fatura não aprovada avisa na app e
-- deixa. Por isso não há backfill — as faturas já pagas, e os pagamentos
-- que vêm do import do email e do extrato, continuam válidos sem aprovação
-- retroativa.
--
-- Sem equivalente no Excel da Vilatro — ver docs/excel-parity.md §3.4.
-- =============================================================

set search_path to worksite, public;

alter table worksite.construction_invoice
    add column if not exists approved_for_payment    boolean     not null default false,
    add column if not exists approved_for_payment_by uuid        references worksite.profile(id) on delete set null,
    add column if not exists approved_for_payment_at timestamptz;

-- Uma nota de crédito não se paga, logo não se aprova para pagamento.
alter table worksite.construction_invoice
    add constraint ck_invoice_payment_approval_not_credit_note
    check (approved_for_payment = false or document_type = 'INVOICE');
