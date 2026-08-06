-- =============================================================
-- V15__construction_budget.sql
-- Substitui a hierarquia rígida de 2 níveis (construction_stage →
-- construction_sub_stage) por uma árvore de rubricas de orçamento com
-- profundidade livre, espelhando o Excel de orçamento da obra:
--   Art | Descrição | Un. | Quant | Preço Un | Preço total | Obs.
-- =============================================================

set search_path to worksite, public;

-- ── 1. fora com o modelo antigo ──────────────────────────────
-- a ordem importa: as despesas referenciam a sub-etapa, que referencia a etapa
drop table if exists worksite.construction_expense;
drop table if exists worksite.construction_sub_stage;
drop table if exists worksite.construction_stage;

-- ── 2. papel de cada linha do orçamento ──────────────────────
-- ITEM    rubrica normal (com ou sem código no Excel — as linhas "Alternativa"
--         não têm código mas são elas que trazem o preço efectivo)
-- HEADING sub-título sem numeração ("Paredes", "Pavimentos", "Tectos") que
--         agrupa todas as rubricas seguintes até ao título seguinte
-- NOTE    nota de contexto entre parêntesis, colada à rubrica anterior
create type worksite.budget_row_kind as enum ('ITEM', 'HEADING', 'NOTE');

-- ── 3. a árvore de rubricas ──────────────────────────────────
create table if not exists worksite.construction_budget_item (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    enterprise_id uuid        not null references worksite.enterprises(id)              on delete cascade,
    parent_id     uuid                 references worksite.construction_budget_item(id) on delete cascade,
    row_kind      worksite.budget_row_kind not null default 'ITEM',
    code          text,                 -- coluna A "Art" — "4.2.1" tal como no Excel; null em HEADING/NOTE
    sort_order    int           not null,   -- ordem dentro do pai = ordem da linha no Excel
    name          text          not null,   -- coluna B "Descrição"
    unit          text,                 -- coluna C "Un."
    quantity      numeric(14,3),        -- coluna D "Quant"
    unit_price    numeric(14,2),        -- coluna E "Preço Un"
    total_price   numeric(14,2),        -- coluna F "Preço total" (null nas alternativas não escolhidas)
    observations  text,                 -- coluna G "Obs."
    start_date    date,
    end_date      date,
    created_by    uuid references worksite.profile(id) on delete set null,
    constraint ck_budget_item_dates check (end_date is null or start_date is null or end_date >= start_date)
);

create trigger tg_construction_budget_item_updated_at
    before update on worksite.construction_budget_item
    for each row execute function worksite.tg_set_updated_at();

-- o código é único dentro do projeto, mas há muitas linhas sem código
create unique index if not exists uq_budget_item_code
    on worksite.construction_budget_item(enterprise_id, code)
    where code is not null;
create index if not exists idx_budget_item_enterprise_id on worksite.construction_budget_item(enterprise_id);
create index if not exists idx_budget_item_parent_id     on worksite.construction_budget_item(parent_id, sort_order);

-- ── 4. despesas — mesmos campos do orçamento + fatura + contabilista ──
create table if not exists worksite.construction_expense (
    id                    uuid        not null default gen_random_uuid() primary key,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    budget_item_id        uuid        not null references worksite.construction_budget_item(id) on delete cascade,
    name                  text          not null,
    description           text,
    -- data da fatura, não a de registo: sem ela qualquer relatório mensal fica
    -- errado assim que se lançarem faturas atrasadas em bloco
    expense_date          date          not null,
    unit                  text,
    quantity              numeric(14,3),
    unit_price            numeric(14,2),
    total_price           numeric(14,2) not null,
    observations          text,
    -- dados lidos do QR code da AT (obrigatório nas faturas portuguesas desde
    -- 2022): permitem preencher a despesa sem transcrição e detetar faturas
    -- lançadas em duplicado
    supplier_nif          text,
    invoice_number        text,
    invoice_atcud         text,
    -- fatura (nunca a URL bruta — apenas bucket + chave)
    bucket                text,
    storage_key           text,
    original_filename     text,
    mime_type             text,
    size_bytes            bigint,
    uploaded_by           uuid        references worksite.profile(id) on delete set null,
    uploaded_at           timestamptz,
    -- envio para a contabilidade
    sent_to_accountant    boolean     not null default false,
    sent_to_accountant_by uuid        references worksite.profile(id) on delete set null,
    sent_to_accountant_at timestamptz,
    created_by            uuid        references worksite.profile(id) on delete set null
);

create trigger tg_construction_expense_updated_at
    before update on worksite.construction_expense
    for each row execute function worksite.tg_set_updated_at();

create index if not exists idx_construction_expense_budget_item_id
    on worksite.construction_expense(budget_item_id);
create index if not exists idx_construction_expense_date
    on worksite.construction_expense(expense_date desc);
-- não é único: é normal uma fatura ser repartida por várias rubricas da obra,
-- por isso a duplicação é avisada e não bloqueada
create index if not exists idx_construction_expense_atcud
    on worksite.construction_expense(invoice_atcud)
    where invoice_atcud is not null;
create index if not exists idx_construction_expense_accountant
    on worksite.construction_expense(sent_to_accountant)
    where sent_to_accountant = false;

-- ── 5. auditoria ─────────────────────────────────────────────
-- 'construction_stage' e 'construction_sub_stage' ficam no enum de propósito:
-- há linhas históricas em worksite.activity_log que ainda os referenciam.
alter type worksite.entity_type add value if not exists 'budget_item';
