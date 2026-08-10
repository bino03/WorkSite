-- =============================================================
-- V17__invoice_unique_atcud.sql
-- O ATCUD passa a ser único por projeto.
--
-- A V16 dizia explicitamente "não é único: o ATCUD repetido é avisado,
-- não bloqueado". Essa justificação caiu: a política passou a recusar o
-- carregamento de um duplicado (ErrorCode.INVOICE_DUPLICATE_ATCUD), e uma
-- regra que o serviço impõe mas a base não garante é uma regra com furo.
--
-- O furo é real e está medido. O cliente carrega faturas com três pedidos
-- em paralelo; cada um faz SELECT e depois INSERT em transações separadas.
-- Dois ficheiros iguais em voo ao mesmo tempo passam os dois, porque
-- nenhum vê o INSERT do outro. Aconteceu nesta base: duas linhas com o
-- ATCUD J66SS285-1146 gravadas com 20 ms de diferença.
--
-- Só o ATCUD leva índice. O par (NIF, número) também é verificado no
-- serviço, mas só entra em jogo na correção manual — uma pessoa, um
-- formulário, sem concorrência. Ali o SELECT-depois-INSERT chega, e um
-- índice a mais só criaria falsos positivos em gralhas de escrita.
-- =============================================================

set search_path to worksite, public;

-- ── 1. limpar os duplicados que não custam nada ──────────────
-- Mantém um por (projeto, ATCUD) e apaga os restantes, mas **só os que
-- não têm lançamento**. Uma fatura sem despesa associada é só o
-- documento; apagá-la não mexe em nenhum orçamento.
--
-- A ordenação põe as faturas já lançadas em primeiro lugar, para que a
-- que sobrevive seja sempre a que tem despesa agarrada — e não a mais
-- antiga por acaso.
--
-- Nota: as linhas apagadas deixam o ficheiro no Storage sem dono. A
-- query do passo 5 de scripts/purge-storage.sql encontra esses órfãos.
with ranked as (
    select i.id,
           row_number() over (
               partition by i.enterprise_id, i.invoice_atcud
               order by exists (
                            select 1 from worksite.construction_expense e
                             where e.invoice_id = i.id
                        ) desc,
                        i.uploaded_at,
                        i.id
           ) as rn
      from worksite.construction_invoice i
     where i.invoice_atcud is not null
)
delete from worksite.construction_invoice ci
 using ranked r
 where ci.id = r.id
   and r.rn > 1
   and not exists (
       select 1 from worksite.construction_expense e where e.invoice_id = ci.id
   );

-- ── 2. recusar se sobrou algum que custe ─────────────────────
-- Chega aqui quando duas faturas com o mesmo ATCUD estão ambas lançadas
-- no orçamento. Escolher qual fica é decisão de quem gere a obra, não
-- desta migração: apagar a errada tira dinheiro de uma rubrica.
do $$
declare
    v_conflitos text;
begin
    select string_agg(atcud, ', ')
      into v_conflitos
      from (
          select invoice_atcud as atcud
            from worksite.construction_invoice
           where invoice_atcud is not null
           group by enterprise_id, invoice_atcud
          having count(*) > 1
      ) d;

    if v_conflitos is not null then
        raise exception
            'V17: faturas duplicadas com lançamento no orçamento (ATCUD: %). '
            'Desassocie e elimine a repetida antes de aplicar esta migração — '
            'ver scripts/purge-invoices.sql.', v_conflitos
            using errcode = 'unique_violation';
    end if;
end $$;

-- ── 3. a garantia ────────────────────────────────────────────
-- Parcial: as faturas sem QR legível ficam com invoice_atcud a null e
-- não entram no índice. Em Postgres vários NULL não colidem, mas o
-- `where` torna a intenção explícita e mantém o índice pequeno.
create unique index if not exists uq_invoice_enterprise_atcud
    on worksite.construction_invoice (enterprise_id, invoice_atcud)
    where invoice_atcud is not null;

-- O idx_invoice_atcud da V16 (não único, mesmas colunas) deixa de servir
-- para alguma coisa: o índice único acima já cobre as mesmas pesquisas.
drop index if exists worksite.idx_invoice_atcud;
