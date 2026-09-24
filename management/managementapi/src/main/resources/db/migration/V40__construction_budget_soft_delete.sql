-- =============================================================
-- V40__construction_budget_soft_delete.sql
-- O lote (construction_budget, V39) passa a soft-delete, como já é o
-- resto do modelo (rubricas, empreendimentos). Antes desta migração,
-- eliminar um lote sem despesas fazia um hard delete que arrastava, em
-- cascata, todas as suas rubricas sem despesas — sem hipótese de repor.
-- =============================================================

set search_path to worksite, public;

alter table worksite.construction_budget add column deleted_at timestamptz;

-- o nome só tem de ser único entre os lotes vivos — um nome reutiliza-se
-- depois de o lote anterior com esse nome ser eliminado
alter table worksite.construction_budget drop constraint uq_budget_enterprise_name;
create unique index uq_budget_enterprise_name
    on worksite.construction_budget (enterprise_id, name)
    where deleted_at is null;
