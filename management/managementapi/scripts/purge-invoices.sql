-- =============================================================
-- purge-invoices.sql — apaga as linhas das faturas de obra.
--
-- Corre no SQL editor do Supabase (ou em qualquer cliente Postgres).
-- O par deste ficheiro é `purge-invoices.mjs`, que apaga os ficheiros
-- do Storage. Ver README.md ao lado.
--
-- ⚠️  ATENÇÃO AO CASCADE (V16__construction_invoice.sql):
--     construction_expense.invoice_id tem ON DELETE CASCADE.
--     Apagar uma fatura apaga também o LANÇAMENTO que dela nasceu,
--     ou seja, o gasto sai do orçamento e as rubricas afetadas
--     passam a mostrar menos despesa.
--     As despesas lançadas à mão (invoice_id IS NULL) NÃO são tocadas.
--
-- Isto não tem desfazer. Confirma os números do passo 1 antes do 3.
-- =============================================================

set search_path to worksite, public;


-- ── 1. o que vai desaparecer (correr sozinho primeiro) ───────
select
    (select count(*) from worksite.construction_invoice)                          as faturas,
    (select count(*) from worksite.construction_expense
      where invoice_id is not null)                                                as lancamentos_que_vao_atras,
    (select count(*) from worksite.construction_expense
      where invoice_id is null)                                                    as lancamentos_manuais_preservados,
    (select coalesce(sum(total_price), 0) from worksite.construction_expense
      where invoice_id is not null)                                                as valor_que_sai_do_orcamento;

-- Repartido por projeto, para o caso de só quereres um:
select e.id            as enterprise_id,
       e.name          as projeto,
       count(i.id)     as faturas
  from worksite.enterprises e
  join worksite.construction_invoice i on i.enterprise_id = e.id
 group by e.id, e.name
 order by faturas desc;


-- ── 2. as chaves do Storage (só se apagares as linhas primeiro) ──
-- O purge-invoices.mjs lista a partir do próprio Storage, por isso
-- normalmente não precisas disto. Serve para conferir, ou para
-- recuperar as chaves se apagares as linhas antes dos ficheiros.
-- select bucket, storage_key, thumbnail_key
--   from worksite.construction_invoice
--  order by uploaded_at;


-- ── 3. apagar ────────────────────────────────────────────────
-- Descomenta o bloco que queres. Está em transação de propósito:
-- confere o RAISE NOTICE e só depois faz COMMIT.

-- TODAS as faturas de todos os projetos:
-- begin;
--   delete from worksite.construction_invoice;
--   -- confere aqui antes de confirmar
--   select count(*) as faturas_restantes from worksite.construction_invoice;
-- commit;   -- ou: rollback;

-- Só um projeto:
-- begin;
--   delete from worksite.construction_invoice
--    where enterprise_id = '00000000-0000-0000-0000-000000000000';
--   select count(*) as faturas_restantes from worksite.construction_invoice;
-- commit;   -- ou: rollback;


-- ── 4. confirmar que não sobrou nada pendurado ───────────────
-- select count(*) as despesas_com_fatura_inexistente
--   from worksite.construction_expense ex
--   left join worksite.construction_invoice i on i.id = ex.invoice_id
--  where ex.invoice_id is not null and i.id is null;
-- Deve dar 0 — o cascade trata disto sozinho.
