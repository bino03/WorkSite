-- =============================================================
-- V26__invoice_scope.sql
-- A fatura deixa de ser obrigatoriamente de uma obra.
--
-- O vault da Vilatro tem três sítios onde uma fatura pode estar, e até aqui a
-- app só sabia representar o primeiro:
--   PROJECT      → Empreendimentos\<Obra>\  (o que já existia)
--   COMPANY      → despesas da empresa, sem obra
--   UNIDENTIFIED → Faturas por identificar\ (a quarentena)
--
-- O check garante o par: PROJECT exige obra, os outros dois exigem obra nula.
-- Ver docs/faturas-modelo-alvo.md, secção 3.
-- =============================================================

set search_path to worksite, public;

create type worksite.invoice_scope as enum ('PROJECT', 'COMPANY', 'UNIDENTIFIED');

-- O default serve só para as linhas que já existem, todas de obra.
alter table worksite.construction_invoice
    add column if not exists scope worksite.invoice_scope not null default 'PROJECT';

-- Colunas da quarentena: as duas do Excel que dizem por onde começar a
-- perguntar. Texto livre de propósito — não há lista fechada de "obras
-- possíveis", e "perguntar a" tanto é um nome como "o encarregado do Aleu".
-- O "aqui desde" da quarentena é o created_at, que já existe.
alter table worksite.construction_invoice
    add column if not exists possible_enterprises text,
    add column if not exists ask_whom             text;

-- A FK deixa de ser obrigatória. O on delete cascade mantém-se: apagar a obra
-- continua a apagar as faturas dela.
alter table worksite.construction_invoice
    alter column enterprise_id drop not null;

alter table worksite.construction_invoice
    add constraint ck_invoice_scope_enterprise check (
        (scope = 'PROJECT' and enterprise_id is not null)
        or (scope <> 'PROJECT' and enterprise_id is null)
    );

-- A quarentena e as despesas da empresa são listas próprias, e a da quarentena
-- ordena-se da mais antiga para a mais recente ("aqui desde").
create index if not exists idx_invoice_scope
    on worksite.construction_invoice(scope, created_at);
