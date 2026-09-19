-- =============================================================
-- V37__revoke_legacy_grants.sql
-- Só o backend liga à base de dados. Sai o que dizia o contrário.
--
-- Duas heranças que davam a entender que havia outros clientes:
--
--   • V8 deu USAGE no schema `worksite` a `anon` e `authenticated`, os roles
--     com que o PostgREST do Supabase serve pedidos vindos do browser. Nunca
--     houve tabela concedida a esses roles, e `worksite` não está nos schemas
--     expostos pelo PostgREST — o USAGE era inerte, mas sugeria um caminho de
--     acesso direto que não existe (e obrigaria a RLS se existisse).
--
--   • V30 alargou o role `worksite_expenses_ro`, de um frontend de consulta
--     (`worksite-expenses`) que lia a base de dados sem passar pelo backend.
--     Esse frontend foi descartado a 2026-09-06; o role continuou em produção
--     com LOGIN e a password em texto claro no `.env.local` desse repo.
--
-- Os grants a `service_role` (V8) ficam: são os do próprio Supabase para as
-- suas APIs de Auth/Storage, não um cliente nosso. Ver docs/security.md →
-- "Modelo de confiança na base de dados".
--
-- Idempotente: o role pode já não existir (ambiente novo, ou apagado à mão).
-- O DROP ROLE pode falhar se o role tiver sido criado por outro utilizador ou
-- tiver grants fora deste schema — nesse caso os REVOKE já lhe tiraram o
-- acesso, e a migração avisa em vez de impedir o backend de arrancar.
-- =============================================================

revoke usage on schema worksite from anon, authenticated;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'worksite_expenses_ro') then
        return;
    end if;

    revoke all on all tables in schema worksite from worksite_expenses_ro;
    revoke usage on schema worksite from worksite_expenses_ro;
    execute format('revoke connect on database %I from worksite_expenses_ro', current_database());

    begin
        drop owned by worksite_expenses_ro;
        drop role worksite_expenses_ro;
    exception when others then
        raise warning 'worksite_expenses_ro ficou sem acesso mas não pôde ser apagado (%): apagar à mão no SQL editor com DROP ROLE.', sqlerrm;
    end;
end
$$;
