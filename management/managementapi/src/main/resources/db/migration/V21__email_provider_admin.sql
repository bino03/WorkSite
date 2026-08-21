-- =============================================================
-- V21__email_provider_admin.sql
-- Põe `settings.email_providers` em condições de ser gerida pelo Backoffice.
--
-- A tabela existe desde a V7, mas era só lida: a configuração SMTP entrava por
-- INSERT à mão e, sem uma linha lá, o convite de funcionário falhava com
-- "Nenhum provedor de email configurado" sem forma de o resolver sem acesso à
-- base de dados.
--
-- Três coisas em falta para ela poder ser escrita por uma API:
--   · o tipo de entidade na auditoria (a V11 só cobre o schema worksite)
--   · updated_at que se mexa (idem — o trigger da V11 nunca chegou aqui)
--   · a garantia de que só há um predefinido, que o service assume
-- =============================================================

-- auditoria
alter type worksite.entity_type add value if not exists 'email_provider';

-- O trigger da V11 percorre apenas as tabelas de `worksite`, por isso o
-- updated_at desta tabela ficava para sempre com o valor do INSERT.
create or replace trigger tg_upd__email_providers
    before update on settings.email_providers
    for each row execute function worksite.tg_set_updated_at();

-- Defensivo: se por algum INSERT à mão ficou mais do que um predefinido, fica o
-- mais recente. Sem isto o índice a seguir não chegava a ser criado.
update settings.email_providers
   set is_default = false
 where is_default is true
   and id <> (
       select id from settings.email_providers
        where is_default is true
        order by created_at desc
        limit 1
   );

-- Um só predefinido. O EmailProviderService desmarca os outros antes de marcar o
-- novo; isto é a rede por baixo, para o caso de a escrita vir por outro caminho.
create unique index if not exists uq_email_provider_single_default
    on settings.email_providers (is_default)
    where is_default is true;
