-- =============================================================
-- V30__worksite_expenses_grant.sql
-- O role de leitura do worksite-expenses passa a ver os documentos.
--
-- O `worksite-expenses` (repo à parte, Next.js na Vercel) lê a base de dados
-- diretamente, sem passar pelo `managementapi`, com um role só de leitura
-- criado à mão em `worksite-expenses/sql/create-readonly-role.sql`. Esse
-- ficheiro concede SELECT em cinco tabelas — e o ficheiro da fatura vivia
-- então na própria `construction_invoice`.
--
-- A V24/V25 mudaram isso: o ficheiro é agora uma linha de
-- `construction_invoice_document`. Sem o SELECT nesta tabela, o
-- worksite-expenses deixa de conseguir abrir seja que documento for.
--
-- A migração é defensiva e idempotente por duas razões:
--   • o role foi criado fora do Flyway, por isso pode não existir num
--     ambiente novo (dev local, base de dados de raiz) — aí é criado NOLOGIN,
--     porque a password vive no `.env` do outro repo e não pode ser inventada
--     aqui; quem quiser usá-lo dá-lhe LOGIN e password à mão;
--   • os GRANTs repetem os que já existem, para que um ambiente novo fique
--     com o conjunto completo sem ter de correr o SQL do outro repo.
--
-- Ver docs/excel-parity.md §5 e o roadmap da fase 1.
-- =============================================================

set search_path to worksite, public;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'worksite_expenses_ro') then
        -- NOLOGIN de propósito: a password real está no .env do worksite-expenses.
        create role worksite_expenses_ro nologin;
    end if;
end
$$;

do $$
begin
    execute format('grant connect on database %I to worksite_expenses_ro', current_database());
end
$$;

grant usage on schema worksite to worksite_expenses_ro;

-- As cinco que já lia. Repetidas para que um ambiente novo não dependa do
-- SQL do outro repo; um GRANT que já existe é um no-op.
grant select on
    worksite.enterprises,
    worksite.construction_invoice,
    worksite.construction_expense,
    worksite.construction_budget_item,
    worksite.profile
to worksite_expenses_ro;

-- A tabela nova: é aqui que o ficheiro passou a viver.
grant select on worksite.construction_invoice_document to worksite_expenses_ro;
