-- =============================================================
-- V23__enterprises_slug_is_test.sql
-- worksite.enterprises — slug + is_test
--
-- Fase 1 do alinhamento com o vault Excel da Vilatro.
-- O slug é exatamente o nome da pasta do vault (`Vila Petrus`, `Vila Aleu`,
-- `Villa Atrium`) — com espaços e acentos — e é a chave que faz a migração
-- funcionar nos dois sentidos (app <-> Excel). Ver docs/excel-parity.md §2.
-- is_test marca obras que nunca entram numa exportação, importação ou soma
-- da empresa (hoje: "Vila Sol").
-- =============================================================

set search_path to worksite, public;

alter table worksite.enterprises
    add column if not exists slug    text,
    add column if not exists is_test boolean not null default false;

-- Único apenas entre as obras que já estão ligadas a uma pasta do vault;
-- as restantes ficam com slug nulo e não colidem entre si.
create unique index if not exists uq_enterprises_slug
    on worksite.enterprises (slug)
    where slug is not null;
