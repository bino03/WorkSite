-- Fase 5 da assiduidade: o funcionário pica o ponto lendo o QR da obra
-- (decidido a 2026-11-20 — conta própria, role EMPLOYEE; ver
-- notes/roadmap/assiduidade.md).
--
-- `source = 'QR'` é o valor novo que a V43 deixou previsto: o método é um dado
-- do registo, não uma tabela por método. Não é usado nesta migração — um valor
-- novo de enum não pode ser usado na mesma transação que o cria.
alter type attendance.time_entry_source add value if not exists 'QR';

-- O QR leva um **código opaco**, não o id da obra: um id não se muda, e um QR
-- fotografado e partilhado tem de se poder invalidar ("Gerar novo QR") sem mexer
-- na obra. Um código por obra (`uq_enterprise_clock_code_enterprise`); criado a
-- pedido, na primeira vez que o admin abre o QR dessa obra.
--
-- Tabela própria no schema `attendance` em vez de uma coluna em
-- `worksite.enterprises`: o módulo liga a `worksite` só por FK, como o resto.
create table if not exists attendance.enterprise_clock_code (
    id            uuid        not null default gen_random_uuid() primary key,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    enterprise_id uuid        not null references worksite.enterprises(id) on delete cascade,
    code          text        not null,
    constraint uq_enterprise_clock_code_enterprise unique (enterprise_id),
    constraint uq_enterprise_clock_code_code unique (code),
    -- 32 caracteres url-safe gerados pelo backend; o check impede um código
    -- adivinhável metido à mão.
    constraint ck_enterprise_clock_code_length check (char_length(code) >= 24)
);

create trigger tg_upd__enterprise_clock_code
    before update on attendance.enterprise_clock_code
    for each row execute function worksite.tg_set_updated_at();
