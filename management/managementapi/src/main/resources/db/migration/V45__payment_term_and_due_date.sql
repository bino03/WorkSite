-- =============================================================
-- V45__payment_term_and_due_date.sql
-- Prazo de pagamento por fornecedor e vencimento manual por fatura.
--
-- Até aqui a fatura não tinha vencimento nenhum, e por isso não havia como
-- dizer o que se paga primeiro. O vencimento passa a ser
-- `invoice_date + supplier.payment_term_days`, calculado na leitura e nunca
-- gravado: mudar o prazo de um fornecedor muda o vencimento de todas as
-- faturas dele, que é o que se quer de uma regra do fornecedor.
--
-- `payment_term_days` nulo é "sem prazo" e 0 é "pronto pagamento" — não são
-- o mesmo, e por isso a coluna não tem default. Não há prazo global por
-- omissão: fornecedor sem prazo dá fatura sem vencimento.
--
-- `due_date_override` é o vencimento escrito à mão numa fatura, e prevalece
-- sobre o calculado. Existe porque a ligação fatura → fornecedor é o NIF, e
-- as faturas importadas do Excel não o têm: sem isto ficavam sem vencimento
-- para sempre. Serve também para a fatura que traz a sua própria data.
--
-- Nenhuma das duas colunas tem equivalente no Excel da Vilatro — ver
-- docs/excel-parity.md.
-- =============================================================

set search_path to worksite, public;

alter table worksite.supplier add column if not exists payment_term_days integer;

alter table worksite.supplier
    add constraint ck_supplier_payment_term_days
    check (payment_term_days is null or payment_term_days between 0 and 365);

alter table worksite.construction_invoice add column if not exists due_date_override date;

-- Uma nota de crédito não se paga, logo não vence.
alter table worksite.construction_invoice
    add constraint ck_invoice_due_date_override_not_credit_note
    check (due_date_override is null or document_type = 'INVOICE');
