-- =============================================================
-- purge-storage.sql — esvazia o Supabase Storage a partir do SQL editor.
--
-- ⚠️  `delete from storage.objects` NÃO FUNCIONA. A Supabase tem um
--     trigger que o bloqueia:
--
--       ERROR: Direct deletion from storage tables is not allowed.
--              Use the Storage API instead.        (storage.protect_delete)
--
--     E o trigger tem razão: apagar a linha removia os metadados mas
--     deixava os bytes no backend S3, órfãos e a contar para a quota.
--
-- Este ficheiro faz o que o trigger manda — chama a Storage API — mas de
-- dentro do SQL editor, através da extensão `http`. Lê os nomes de
-- `storage.objects` (ler é permitido) e envia-os para a API em lotes.
--
-- Alternativa sem SQL: `node scripts/purge-storage.mjs --all --yes`,
-- que faz o mesmo sem extensão nem chave colada numa query.
-- =============================================================


-- ── 0. preparação ────────────────────────────────────────────
-- A extensão http chega com a Supabase, mas pode não estar ativa.
create extension if not exists http with schema extensions;


-- ── 1. o que existe (correr sozinho primeiro) ────────────────
select bucket_id,
       count(*)                                                   as ficheiros,
       pg_size_pretty(sum((metadata->>'size')::bigint))           as tamanho
  from storage.objects
 group by bucket_id
 order by count(*) desc;

-- E o que aponta para lá, do lado da app:
select 'construction_invoice' as tabela, count(*) as linhas from worksite.construction_invoice
union all
select 'enterprises_media',            count(*) from worksite.enterprises_media
union all
select 'profile (com foto)',           count(*) from worksite.profile where photo_key is not null;


-- ── 2. apagar os ficheiros de um bucket ──────────────────────
-- Preenche v_url e v_key. A chave fica no texto da query e portanto no
-- histórico do SQL editor — usa a service_role key do TEU projeto e não
-- partilhes o screenshot.
--
-- Muda v_bucket e corre uma vez por bucket:
--   documents = faturas · media = banner/galeria · private = fotos de perfil

/*
do $$
declare
  v_url    text := 'https://SEU-REF.supabase.co';
  v_key    text := 'SERVICE_ROLE_KEY';
  v_bucket text := 'documents';

  v_batch  text[];
  v_resp   extensions.http_response;
  v_total  int := 0;
  v_guard  int := 0;
begin
  loop
    -- Guarda contra ciclo infinito se a API responder 200 sem apagar.
    v_guard := v_guard + 1;
    if v_guard > 1000 then
      raise exception 'Demasiadas iterações — parei aos % ficheiros.', v_total;
    end if;

    select array_agg(name) into v_batch
      from (select name from storage.objects where bucket_id = v_bucket limit 100) s;

    exit when v_batch is null;

    select * into v_resp from extensions.http((
      'DELETE',
      v_url || '/storage/v1/object/' || v_bucket,
      array[
        extensions.http_header('Authorization', 'Bearer ' || v_key),
        extensions.http_header('apikey', v_key)
      ],
      'application/json',
      json_build_object('prefixes', to_jsonb(v_batch))::text
    )::extensions.http_request);

    if v_resp.status <> 200 then
      raise exception 'Storage API devolveu % — %', v_resp.status, v_resp.content;
    end if;

    v_total := v_total + coalesce(array_length(v_batch, 1), 0);
    raise notice '% — % apagados', v_bucket, v_total;
    v_batch := null;
  end loop;

  raise notice 'Terminado: % ficheiro(s) apagados de %.', v_total, v_bucket;
end $$;
*/


-- ── 3. as referências do lado da app ─────────────────────────
-- O passo 2 esvaziou o Storage; estas linhas ficaram a apontar para o
-- vazio. Corre o bloco correspondente aos buckets que apagaste.
--
-- ⚠️  As faturas levam os LANÇAMENTOS atrás por ON DELETE CASCADE
--     (V16__construction_invoice.sql): o gasto sai do orçamento e as
--     rubricas afetadas passam a mostrar menos despesa. As despesas
--     lançadas à mão (invoice_id is null) não são tocadas.

-- begin;
--   delete from worksite.construction_invoice;                                  -- bucket documents
--   delete from worksite.enterprises_media;                                      -- bucket media
--   update worksite.profile set photo_key = null where photo_key is not null;    -- bucket private
--
--   select (select count(*) from worksite.construction_invoice) as faturas,
--          (select count(*) from worksite.enterprises_media)    as media;
-- commit;   -- ou: rollback;


-- ── 4. só um projeto, em vez de tudo ─────────────────────────
-- No passo 2, troca a query do lote por:
--     select name from storage.objects
--      where bucket_id = 'documents'
--        and name like 'construction-invoices/SEU-UUID/%'
--      limit 100
-- e aqui:
-- delete from worksite.construction_invoice where enterprise_id = 'SEU-UUID';


-- ── 5. confirmar que não sobrou nada pendurado ───────────────
-- Linhas da app a apontar para objetos que já não existem — deve dar 0.

-- select count(*) as faturas_sem_ficheiro
--   from worksite.construction_invoice i
--   left join storage.objects o
--     on o.bucket_id = i.bucket and o.name = i.storage_key
--  where o.id is null;

-- select count(*) as media_sem_ficheiro
--   from worksite.enterprises_media m
--   left join storage.objects o
--     on o.bucket_id = m.bucket and o.name = m.storage_key
--  where o.id is null;

-- E o inverso — objetos sem dono do lado da app (lixo de uploads falhados):
-- select o.bucket_id, o.name
--   from storage.objects o
--   left join worksite.construction_invoice i
--     on i.bucket = o.bucket_id and (i.storage_key = o.name or i.thumbnail_key = o.name)
--  where o.bucket_id = 'documents' and i.id is null;
