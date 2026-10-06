-- =============================================================
-- V43__attendance_time_entries.sql
-- Assiduidade, parte 2: o emprego e as picagens.
--
-- `employment` vs `employment_term`: a data de admissão é um facto da pessoa e
-- não muda; o horário atribuído e os dias de férias por ano mudam ao longo do
-- tempo e os relatórios de meses passados têm de continuar a usar os valores
-- que estavam em vigor na altura (decisão de 2026-10-06: histórico com datas).
-- Meter tudo numa tabela com `valid_from` obrigaria a repetir a admissão em
-- cada período, e duas cópias do mesmo facto divergem sempre.
--
-- `time_entry` é a tabela que não pode ter de ser refeita. Duas colunas é que
-- lhe dão isso:
--   `source`  — o método de registo é um DADO, não uma tabela por método. Hoje
--               só `MANUAL`; o QR, o telemóvel ou um posto fixo entram como
--               valores novos por `alter type`, sem migrar nem recalcular nada.
--   `enterprise_id` — a obra vive em CADA picagem, não no dia. O QR por obra
--               que o utilizador descreveu traz a obra de graça no momento da
--               picagem; guardar por dia perderia quem andou em duas obras no
--               mesmo dia, e recuperar isso exigiria refazer a tabela.
--
-- `on delete restrict` em `time_entry.profile_id`: um funcionário com picagens
-- NÃO se apaga (decisão de 2026-10-06) — a lei obriga a guardar os registos de
-- assiduidade. O caminho certo passa a ser desativar a conta. A FK é a rede de
-- segurança; o `EmployeeServiceImpl` faz a verificação antes, para o erro ser
-- explicável em vez de uma violação de integridade opaca.
-- A obra é `on delete set null` (como `tasks.enterprise_id`): apagar uma obra
-- não pode apagar o registo de que alguém trabalhou.
--
-- `time_entry_revision` existe porque o `worksite.activity_log` é `@Async` e
-- pode perder linhas numa falha. Para faturas isso nunca importou; para um
-- registo legal de assiduidade importa, e é precisamente o registo corrigido
-- que uma auditoria põe em causa. Esta tabela escreve-se na MESMA transação da
-- alteração. O `activity_log` continua a receber a sua linha, para o histórico
-- geral da app.
--
-- Picagens só têm soft-delete, nunca `delete` físico.
-- =============================================================

set search_path to worksite, public;

create type attendance.time_direction as enum ('IN', 'OUT');

-- Um valor só, de propósito: os outros métodos entram com `alter type ... add
-- value` quando existirem (fase 5 do roadmap da assiduidade).
create type attendance.time_entry_source as enum ('MANUAL');

create type attendance.time_entry_change as enum ('CREATE', 'UPDATE', 'DELETE', 'RESTORE');

-- ── Emprego ───────────────────────────────────────────────────

create table if not exists attendance.employment (
    id         uuid        not null default gen_random_uuid() primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    profile_id uuid        not null unique references worksite.profile(id) on delete cascade,
    hired_at   date        not null,
    -- Fim do vínculo. Null = ainda cá está. Não apaga nada: as picagens e os
    -- relatórios de quem saiu continuam a ter de existir.
    ended_at   date,
    constraint ck_employment_dates check (ended_at is null or ended_at >= hired_at)
);

create index if not exists idx_employment_profile_id
    on attendance.employment (profile_id);

create table if not exists attendance.employment_term (
    id                     uuid        not null default gen_random_uuid() primary key,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    employment_id          uuid        not null references attendance.employment(id) on delete cascade,
    -- `restrict`: um horário atribuído a um período não desaparece, senão os
    -- meses desse período deixavam de poder ser recalculados.
    work_schedule_id       uuid        not null references attendance.work_schedule(id) on delete restrict,
    vacation_days_per_year int         not null default 22,
    valid_from             date        not null,
    -- Null = é o período em vigor. Só pode haver um por emprego (índice abaixo).
    valid_to               date,
    constraint ck_employment_term_dates check (valid_to is null or valid_to >= valid_from),
    constraint ck_employment_term_vacation_days check (vacation_days_per_year between 0 and 365),
    constraint uq_employment_term_start unique (employment_id, valid_from)
);

create unique index if not exists uq_employment_term_current
    on attendance.employment_term (employment_id)
    where valid_to is null;

create index if not exists idx_employment_term_employment_id
    on attendance.employment_term (employment_id);

create index if not exists idx_employment_term_work_schedule_id
    on attendance.employment_term (work_schedule_id);

-- ── Picagens ──────────────────────────────────────────────────

create table if not exists attendance.time_entry (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    profile_id    uuid        not null references worksite.profile(id) on delete restrict,
    enterprise_id uuid        references worksite.enterprises(id) on delete set null,
    happened_at   timestamptz not null,
    direction     attendance.time_direction    not null,
    source        attendance.time_entry_source not null default 'MANUAL',
    -- Null = registado pelo próprio. Na fase 1 (só admin) fica sempre preenchido.
    registered_by uuid        references worksite.profile(id) on delete set null,
    note          text,
    deleted_at    timestamptz
);

-- A consulta de base de todo o módulo: as picagens de uma pessoa num intervalo.
create index if not exists idx_time_entry_profile_happened_at
    on attendance.time_entry (profile_id, happened_at)
    where deleted_at is null;

-- Horas por obra (fase 4, e a pergunta aberta do custo de mão de obra).
create index if not exists idx_time_entry_enterprise_happened_at
    on attendance.time_entry (enterprise_id, happened_at)
    where deleted_at is null and enterprise_id is not null;

create index if not exists idx_time_entry_deleted_at
    on attendance.time_entry (deleted_at)
    where deleted_at is not null;

create table if not exists attendance.time_entry_revision (
    id                     uuid        not null default gen_random_uuid() primary key,
    created_at             timestamptz not null default now(),
    -- Sem `on delete cascade`: a revisão sobrevive a tudo. As picagens só têm
    -- soft-delete, por isso o pai não desaparece — mas se algum dia alguém
    -- forçar um delete à mão, o rasto não vai com ele.
    time_entry_id          uuid        not null,
    change                 attendance.time_entry_change not null,
    changed_by             uuid        references worksite.profile(id) on delete set null,
    changed_by_name        text        not null,
    reason                 text,
    -- O estado ANTES da alteração. Null em `CREATE`, que não tem antes.
    previous_happened_at   timestamptz,
    previous_direction     attendance.time_direction,
    previous_enterprise_id uuid,
    previous_note          text,
    previous_deleted_at    timestamptz
);

create index if not exists idx_time_entry_revision_time_entry_id
    on attendance.time_entry_revision (time_entry_id, created_at);

-- `changed_by_name` guardado já escrito, e não só a FK: o nome que aparece numa
-- auditoria tem de ser o que a pessoa tinha na altura, e tem de sobreviver a
-- apagar o perfil (a FK fica a null). Mesmo princípio do `activity_log`.

-- ── Triggers de updated_at ────────────────────────────────────
-- Não são automáticos fora de `worksite`: o DO $$ da V11 só percorre
-- table_schema = 'worksite'. `time_entry_revision` não leva trigger — é
-- append-only, nunca se altera.

create trigger tg_upd__employment
    before update on attendance.employment
    for each row execute function worksite.tg_set_updated_at();

create trigger tg_upd__employment_term
    before update on attendance.employment_term
    for each row execute function worksite.tg_set_updated_at();

create trigger tg_upd__time_entry
    before update on attendance.time_entry
    for each row execute function worksite.tg_set_updated_at();

alter type worksite.entity_type add value if not exists 'employment';
alter type worksite.entity_type add value if not exists 'time_entry';

grant all on all tables in schema attendance to service_role;
grant all on all sequences in schema attendance to service_role;
