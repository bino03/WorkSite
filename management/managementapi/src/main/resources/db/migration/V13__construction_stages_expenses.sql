set search_path to worksite, public;

create table if not exists worksite.construction_stage (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    name          text        not null,
    description   text,
    enterprise_id uuid        not null references worksite.enterprises(id) on delete cascade
);
create trigger tg_construction_stage_updated_at
    before update on worksite.construction_stage
    for each row execute function worksite.tg_set_updated_at();
create index if not exists idx_construction_stage_enterprise_id on worksite.construction_stage(enterprise_id);

create table if not exists worksite.construction_sub_stage (
    id          uuid        not null default gen_random_uuid() primary key,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    name        text        not null,
    description text,
    stage_id    uuid        not null references worksite.construction_stage(id) on delete cascade
);
create trigger tg_construction_sub_stage_updated_at
    before update on worksite.construction_sub_stage
    for each row execute function worksite.tg_set_updated_at();
create index if not exists idx_construction_sub_stage_stage_id on worksite.construction_sub_stage(stage_id);

create table if not exists worksite.construction_expense (
    id                uuid          not null default gen_random_uuid() primary key,
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    name              text          not null,
    price             numeric(12,2) not null,
    sub_stage_id      uuid          not null references worksite.construction_sub_stage(id) on delete cascade,
    bucket            text,
    storage_key       text,
    original_filename text,
    mime_type         text,
    size_bytes        bigint,
    created_by        uuid references worksite.profile(id) on delete set null
);
create trigger tg_construction_expense_updated_at
    before update on worksite.construction_expense
    for each row execute function worksite.tg_set_updated_at();
create index if not exists idx_construction_expense_sub_stage_id on worksite.construction_expense(sub_stage_id);
