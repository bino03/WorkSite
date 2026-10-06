-- =============================================================
-- V41__invoice_budget_lot.sql
-- A fatura passa a guardar o lote (construction_budget) a que pertence.
--
-- Até aqui o lote só se deduzia da rubrica da despesa, e uma fatura podia
-- repartir-se entre lotes. Com a regra nova, a fatura de uma obra com vários
-- lotes tem de ter lote antes de ser classificada, e todas as rubricas
-- escolhidas pertencem a esse lote. Ver docs/faturas-modelo-alvo.md.
--
-- Sem backfill fora das obras de um só lote: aí não há escolha possível, o
-- lote é o único que existe. As faturas de obras com vários lotes ficam com
-- lote nulo até serem classificadas ou marcadas à mão.
--
-- A FK composta (budget_id, enterprise_id) impede um lote de outra obra; o
-- ON DELETE SET NULL limpa só budget_id, senão o enterprise_id também ia a
-- nulo e o check de âmbito falhava.
-- =============================================================

set search_path to worksite, public;

alter table worksite.construction_invoice add column if not exists budget_id uuid;

alter table worksite.construction_invoice
    add constraint fk_invoice_budget
    foreign key (budget_id, enterprise_id)
    references worksite.construction_budget(id, enterprise_id)
    on delete set null (budget_id);

alter table worksite.construction_invoice
    add constraint ck_invoice_budget_scope check (budget_id is null or scope = 'PROJECT');

create index if not exists idx_invoice_budget_id on worksite.construction_invoice(budget_id);

update worksite.construction_invoice i
set budget_id = b.id
from worksite.construction_budget b
where i.scope = 'PROJECT'
  and b.enterprise_id = i.enterprise_id
  and b.deleted_at is null
  and (
      select count(*) from worksite.construction_budget x
      where x.enterprise_id = i.enterprise_id and x.deleted_at is null
  ) = 1;
