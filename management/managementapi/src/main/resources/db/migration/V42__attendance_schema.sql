-- =============================================================
-- V42__attendance_schema.sql
-- Schema `attendance` — assiduidade, horas e férias. Primeira migração do
-- módulo: só o catálogo de horários. As picagens, o emprego e as revisões
-- entram na V43; as ausências e feriados na V44.
--
-- Porque é um schema próprio e não `worksite`: o módulo é um domínio inteiro
-- novo (seis tabelas no total), sem relação com o orçamento nem com as
-- faturas, e liga a `worksite` só por FK a `profile` e `enterprises`. É o
-- mesmo molde do schema `tasks` (V14), pela mesma razão.
--
-- Um horário é uma entidade reutilizável e atribuível, não um campo do
-- funcionário: cada trabalhador pode ter o seu, e mudar o horário de três
-- pessoas é mudar a atribuição, não editar três fichas.
--
-- `work_schedule_day` exprime o horário dia-a-dia da semana. Um dia que não
-- está na tabela NÃO é dia de trabalho — é assim que fins de semana e
-- horários parciais se dizem, sem precisar de flag nenhuma.
--
-- A pausa vive aqui (`break_minutes`) e não nas picagens: ninguém pica o
-- almoço, o horário declara-o e as horas do dia descontam-no por regra.
--
-- `start_time`/`end_time` são `time` (hora local), não `timestamptz`: o fuso
-- aplica-se no cálculo, com `Europe/Lisbon` explícito. Guardar "08:00" como
-- instante obrigaria a saber de que dia, e a hora de entrada é a mesma em
-- janeiro e em julho mesmo que o instante UTC não seja.
--
-- Soft-delete em `work_schedule` porque um horário apagado tem de continuar
-- legível: os relatórios de meses passados recalculam-se a partir do horário
-- que estava atribuído na altura. Sem coluna `active` — `deleted_at` já diz
-- "não oferecer em atribuições novas", e duas colunas para o mesmo conceito
-- divergem sempre.
-- =============================================================

set search_path to worksite, public;

-- O schema é criado pelo Flyway (`create-schemas: true`), mas só se estiver
-- em `spring.flyway.schemas` no application.yml — ver a nota lá.
grant usage on schema attendance to service_role;

create table if not exists attendance.work_schedule (
    id          uuid        not null default gen_random_uuid() primary key,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    name        text        not null,
    notes       text,
    created_by  uuid        references worksite.profile(id) on delete set null,
    deleted_at  timestamptz,
    constraint ck_work_schedule_name_not_blank check (btrim(name) <> '')
);

-- Nome único entre os horários vivos. Parcial, como a V36/V40 fazem: um
-- horário apagado não impede reutilizar o nome.
create unique index if not exists uq_work_schedule_name
    on attendance.work_schedule (name)
    where deleted_at is null;

-- Zona de recuperação (mesmo padrão da V36).
create index if not exists idx_work_schedule_deleted_at
    on attendance.work_schedule (deleted_at)
    where deleted_at is not null;

create table if not exists attendance.work_schedule_day (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    schedule_id   uuid        not null references attendance.work_schedule(id) on delete cascade,
    -- 1 = segunda … 7 = domingo (ISO-8601, igual ao DayOfWeek do Java)
    weekday       smallint    not null,
    start_time    time        not null,
    end_time      time        not null,
    break_minutes int         not null default 0,
    constraint uq_work_schedule_day_weekday unique (schedule_id, weekday),
    constraint ck_work_schedule_day_weekday check (weekday between 1 and 7),
    -- Um dia de trabalho não atravessa a meia-noite (decisão de 2026-10-06):
    -- turnos noturnos não são suportados, e esta constraint é o que garante
    -- que o cálculo da fase 2 nunca recebe um caso que não sabe tratar.
    constraint ck_work_schedule_day_order check (end_time > start_time),
    constraint ck_work_schedule_day_break check (
        break_minutes >= 0
        and break_minutes < extract(epoch from (end_time - start_time)) / 60
    )
);

create index if not exists idx_work_schedule_day_schedule_id
    on attendance.work_schedule_day (schedule_id);

-- O trigger de updated_at NÃO é automático fora de `worksite`: o DO $$ da V11
-- só percorre table_schema = 'worksite'. Cada tabela deste schema precisa do
-- seu, como a V14 fez para `tasks` e a V21 para `settings`.
create trigger tg_upd__work_schedule
    before update on attendance.work_schedule
    for each row execute function worksite.tg_set_updated_at();

create trigger tg_upd__work_schedule_day
    before update on attendance.work_schedule_day
    for each row execute function worksite.tg_set_updated_at();

-- Para o activity_log do CRUD de horários.
alter type worksite.entity_type add value if not exists 'work_schedule';

grant all on all tables in schema attendance to service_role;
grant all on all sequences in schema attendance to service_role;

alter default privileges in schema attendance
    grant all on tables to service_role;
