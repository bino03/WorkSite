-- =============================================================
-- V39__construction_budget_lots.sql
-- Um empreendimento passa a ter N orçamentos — um por lote (edifício).
--
-- Até aqui a árvore de rubricas pendurava diretamente no projeto: um projeto,
-- um orçamento (decisão de 2026-09-15). Uma vila pode ter vários edifícios,
-- cada um com o seu orçamento do empreiteiro e a sua numeração — o `4.2.1` do
-- Lote A e o do Lote B são rubricas diferentes. Ver docs/excel-parity.md §6.
--
-- As faturas continuam no projeto: é só a repartição (a rubrica) que diz de
-- que lote é o gasto, e uma fatura pode dividir-se entre lotes.
--
-- `enterprise_id` fica na rubrica, desnormalizado: todas as verificações de
-- "rubrica da obra da fatura" e as queries de despesas por projeto continuam
-- válidas. A FK composta (budget_id, enterprise_id) impede que os dois
-- divirjam.
-- =============================================================

set search_path to worksite, public;

create table if not exists worksite.construction_budget (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    enterprise_id uuid        not null references worksite.enterprises(id) on delete cascade,
    name          text        not null,
    sort_order    int         not null default 0,
    created_by    uuid        references worksite.profile(id) on delete set null,
    constraint uq_budget_enterprise_name unique (enterprise_id, name),
    -- alvo da FK composta das rubricas
    constraint uq_budget_id_enterprise   unique (id, enterprise_id)
);

create trigger tg_construction_budget_updated_at
    before update on worksite.construction_budget
    for each row execute function worksite.tg_set_updated_at();

create index if not exists idx_budget_enterprise_id on worksite.construction_budget(enterprise_id, sort_order);

-- ── backfill: cada projeto com rubricas ganha um lote com a árvore que já tinha ──
insert into worksite.construction_budget (enterprise_id, name, sort_order)
select distinct enterprise_id, 'Orçamento', 0
from worksite.construction_budget_item;

alter table worksite.construction_budget_item add column budget_id uuid;

update worksite.construction_budget_item i
set budget_id = b.id
from worksite.construction_budget b
where b.enterprise_id = i.enterprise_id;

alter table worksite.construction_budget_item alter column budget_id set not null;

alter table worksite.construction_budget_item
    add constraint fk_budget_item_budget
    foreign key (budget_id, enterprise_id)
    references worksite.construction_budget(id, enterprise_id)
    on delete cascade;

create index if not exists idx_budget_item_budget_id on worksite.construction_budget_item(budget_id);

-- o código passa a ser único dentro do lote, não do projeto
drop index worksite.uq_budget_item_code;
create unique index uq_budget_item_code
    on worksite.construction_budget_item (budget_id, code)
    where code is not null and deleted_at is null;

alter type worksite.entity_type add value if not exists 'construction_budget';
