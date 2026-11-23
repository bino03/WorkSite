-- Aviso de picagem com QR antigo (pedido 2026-11-23).
--
-- "Gerar novo QR" sobrepõe o código na mesma linha de `enterprise_clock_code`,
-- por isso um QR antigo (fotografado antes de ser substituído) e um código
-- inventado davam o mesmo `ATT_020`, e ninguém ficava a saber que alguém tentou
-- picar com um QR já retirado. Guardar os códigos retirados é o que permite
-- distinguir os dois: só o antigo gera aviso aos ADMIN.
--
-- O histórico começa aqui: os códigos substituídos antes desta migração já se
-- perderam, e continuam a dar `ATT_020`.
create table if not exists attendance.retired_clock_code (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    enterprise_id uuid        not null references worksite.enterprises(id) on delete cascade,
    code          text        not null,
    -- quando aquele código tinha sido gerado, e quando foi substituído
    generated_at  timestamptz,
    retired_at    timestamptz not null,
    -- um código é aleatório (24 bytes): nunca volta a sair, nem noutra obra
    constraint uq_retired_clock_code_code unique (code)
);

create index if not exists idx_retired_clock_code_enterprise
    on attendance.retired_clock_code(enterprise_id);

create trigger tg_upd__retired_clock_code
    before update on attendance.retired_clock_code
    for each row execute function worksite.tg_set_updated_at();

-- Cada tentativa de picar com um código retirado: é o rasto (quem, que QR, que
-- dia) e é o dedupe do aviso — uma notificação por funcionário, código e dia,
-- garantida pela unique, para várias leituras seguidas não encherem o sino.
create table if not exists attendance.retired_clock_code_attempt (
    id               uuid        not null default gen_random_uuid() primary key,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    retired_code_id  uuid        not null references attendance.retired_clock_code(id) on delete cascade,
    profile_id       uuid        not null references worksite.profile(id) on delete cascade,
    attempted_on     date        not null,
    constraint uq_retired_clock_code_attempt unique (retired_code_id, profile_id, attempted_on)
);

create index if not exists idx_retired_clock_code_attempt_profile
    on attendance.retired_clock_code_attempt(profile_id);

create trigger tg_upd__retired_clock_code_attempt
    before update on attendance.retired_clock_code_attempt
    for each row execute function worksite.tg_set_updated_at();
