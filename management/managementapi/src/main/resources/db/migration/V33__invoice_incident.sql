-- =============================================================
-- V33__invoice_incident.sql
-- Fase 5 da paridade com o Excel — transferências e inconsistências.
--
-- Duas coisas:
--
-- 1. `invoice_incident` + junção `invoice_incident_invoice`. Um incidente é uma
--    nota livre (markdown) sobre uma ou mais faturas que ficaram inconsistentes
--    — tipicamente depois de uma transferência atrapalhada, ou de um cruzamento
--    manual que não bate certo. Não é a transferência que o cria: a resposta da
--    transferência apenas devolve `suggestIncident=true` e o utilizador
--    escreve-o à mão na página "Inconsistências" (decisão de 2026-09-09, ver
--    notes/roadmap/plans/2026-09-09-fase5-transferencias.md). Sem soft-delete:
--    um incidente enganado apaga-se, um resolvido fica com `resolved_at`.
--
-- 2. Dois valores de enum novos:
--    - `entity_type` ganha `invoice_incident` (para o activity_log do CRUD).
--    - `activity_type` ganha `transfer`. A transferência de uma fatura grava um
--      `activity_log` com este tipo e a repartição antiga (allocations[] da
--      fatura e das NC ligadas) em `metadata` JSONB, antes de as despesas serem
--      apagadas. O DTO de detalhe da fatura reconstrói `transfers[]` a partir
--      destas linhas — daí precisar de um tipo próprio, não de `edit`.
--
-- A transferência em si não mexe no schema: `construction_invoice.scope` e
-- `.enterprise_id` já existem (V26) e são mutáveis; o check
-- `ck_invoice_scope_enterprise` continua a garantir a coerência.
--
-- Ver docs/faturas-modelo-alvo.md §4.
-- =============================================================

set search_path to worksite, public;

alter type worksite.entity_type   add value if not exists 'invoice_incident';
alter type worksite.activity_type add value if not exists 'transfer';

create table if not exists worksite.invoice_incident (
    id          uuid        not null default gen_random_uuid() primary key,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    title       text        not null,
    body        text        not null,          -- markdown

    resolved_at timestamptz,
    resolved_by uuid        references worksite.profile(id) on delete set null,
    created_by  uuid        references worksite.profile(id) on delete set null
);

create trigger tg_invoice_incident_updated_at
    before update on worksite.invoice_incident
    for each row execute procedure worksite.tg_set_updated_at();

create index if not exists idx_invoice_incident_unresolved
    on worksite.invoice_incident(created_at desc) where resolved_at is null;

-- Um incidente toca N faturas; uma fatura pode aparecer em N incidentes.
create table if not exists worksite.invoice_incident_invoice (
    incident_id uuid not null references worksite.invoice_incident(id)   on delete cascade,
    invoice_id  uuid not null references worksite.construction_invoice(id) on delete cascade,
    primary key (incident_id, invoice_id)
);

create index if not exists idx_incident_invoice_invoice
    on worksite.invoice_incident_invoice(invoice_id);
