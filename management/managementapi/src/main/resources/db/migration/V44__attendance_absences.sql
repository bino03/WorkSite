-- =============================================================
-- V44__attendance_absences.sql
-- Assiduidade, parte 3: feriados e ausências (férias, baixas, faltas
-- justificadas).
--
-- Até aqui, um feriado e um dia de férias apareciam como "falta por
-- justificar" — o cálculo só sabia olhar para o horário e para as picagens.
-- É esse o buraco que esta migração fecha.
--
-- `holiday` é uma TABELA e não uma biblioteca de código: os feriados
-- municipais variam por concelho e mudam de ano para ano, e uma tabela que o
-- utilizador edita bate código que alguém tem de ir corrigir. `scope`
-- distingue nacional de municipal, e `municipality` só se aplica ao segundo
-- (constraint abaixo) — um feriado nacional com concelho seria contraditório.
--
-- `half_day`: NONE, MORNING ou AFTERNOON. Pedido explicitamente, e vale só
-- para ausências de um dia (constraint): "meio dia" num intervalo de cinco
-- dias não quer dizer nada.
--
-- O documento justificativo vai para `absence_document`, tabela própria, e
-- NÃO para colunas `document_bucket`/`document_key` na `absence` — que era o
-- que o roadmap previa. A skill `add-file-upload` diz explicitamente para não
-- repetir o erro das faturas (`V24`): uma baixa médica pode ter duas páginas,
-- ou vir uma prorrogação, e nessa altura as colunas na linha não chegam. A
-- pergunta da skill é "pode chegar um segundo ficheiro para a mesma coisa?" —
-- aqui pode.
--
-- `status`: PENDING, APPROVED, REJECTED. Só APPROVED conta para o saldo de
-- férias e só APPROVED deixa de ser falta; PENDING desconta do saldo
-- disponível (senão marcava-se o dobro dos dias que se tem) mas não altera o
-- cálculo do dia.
-- =============================================================

set search_path to worksite, public;

create type attendance.holiday_scope as enum ('NATIONAL', 'MUNICIPAL');

create type attendance.absence_type as enum (
    'VACATION',          -- férias
    'SICK_LEAVE',        -- baixa médica
    'JUSTIFIED',         -- falta justificada (com ou sem documento)
    'UNJUSTIFIED',       -- falta injustificada, registada como tal
    'OTHER'
);

create type attendance.absence_half_day as enum ('NONE', 'MORNING', 'AFTERNOON');

create type attendance.absence_status as enum ('PENDING', 'APPROVED', 'REJECTED');

-- ── Feriados ──────────────────────────────────────────────────

create table if not exists attendance.holiday (
    id           uuid        not null default gen_random_uuid() primary key,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    holiday_date date        not null,
    name         text        not null,
    scope        attendance.holiday_scope not null,
    -- Só para os municipais. Null nos nacionais.
    municipality text,
    constraint ck_holiday_municipality check (
        (scope = 'MUNICIPAL' and municipality is not null)
        or (scope = 'NATIONAL' and municipality is null)
    ),
    constraint ck_holiday_name_not_blank check (btrim(name) <> '')
);

-- Um feriado por data e por âmbito/concelho. `coalesce` porque dois NULL não
-- colidem num unique: sem isto, o mesmo feriado nacional podia entrar duas vezes.
create unique index if not exists uq_holiday_date_scope
    on attendance.holiday (holiday_date, scope, coalesce(municipality, ''));

create index if not exists idx_holiday_date
    on attendance.holiday (holiday_date);

-- ── Ausências ─────────────────────────────────────────────────

create table if not exists attendance.absence (
    id          uuid        not null default gen_random_uuid() primary key,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    profile_id  uuid        not null references worksite.profile(id) on delete restrict,
    type        attendance.absence_type     not null,
    starts_on   date        not null,
    ends_on     date        not null,
    half_day    attendance.absence_half_day not null default 'NONE',
    status      attendance.absence_status   not null default 'PENDING',
    note        text,
    requested_by uuid       references worksite.profile(id) on delete set null,
    approved_by  uuid       references worksite.profile(id) on delete set null,
    approved_at  timestamptz,
    deleted_at   timestamptz,
    constraint ck_absence_dates check (ends_on >= starts_on),
    -- "Meio dia" só faz sentido num dia só.
    constraint ck_absence_half_day check (half_day = 'NONE' or starts_on = ends_on)
);

create index if not exists idx_absence_profile_dates
    on attendance.absence (profile_id, starts_on, ends_on)
    where deleted_at is null;

-- O mapa de equipa: quem está fora num intervalo, de todos os funcionários.
create index if not exists idx_absence_dates
    on attendance.absence (starts_on, ends_on)
    where deleted_at is null;

create index if not exists idx_absence_status
    on attendance.absence (status)
    where deleted_at is null;

create index if not exists idx_absence_deleted_at
    on attendance.absence (deleted_at)
    where deleted_at is not null;

-- ── Documentos justificativos (0..N) ──────────────────────────

create table if not exists attendance.absence_document (
    id                uuid        not null default gen_random_uuid() primary key,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    absence_id        uuid        not null references attendance.absence(id) on delete cascade,
    -- bucket + chave, nunca a URL nem os bytes.
    bucket            text        not null default 'documents',
    storage_key       text        not null,
    original_filename text        not null,
    mime_type         text        not null,
    size_bytes        bigint      not null,
    uploaded_by       uuid        references worksite.profile(id) on delete set null,
    uploaded_at       timestamptz not null default now(),
    constraint uq_absence_document_key unique (bucket, storage_key)
);

create index if not exists idx_absence_document_absence_id
    on attendance.absence_document (absence_id);

-- ── Triggers de updated_at ────────────────────────────────────
-- Não automáticos fora de `worksite` (o DO $$ da V11 só percorre esse schema).

create trigger tg_upd__holiday
    before update on attendance.holiday
    for each row execute function worksite.tg_set_updated_at();

create trigger tg_upd__absence
    before update on attendance.absence
    for each row execute function worksite.tg_set_updated_at();

create trigger tg_upd__absence_document
    before update on attendance.absence_document
    for each row execute function worksite.tg_set_updated_at();

alter type worksite.entity_type add value if not exists 'absence';
alter type worksite.entity_type add value if not exists 'holiday';

grant all on all tables in schema attendance to service_role;
grant all on all sequences in schema attendance to service_role;
