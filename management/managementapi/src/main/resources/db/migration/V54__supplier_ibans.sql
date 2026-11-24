-- =============================================================
-- V54__supplier_ibans.sql
-- Um fornecedor pode ter vários IBAN (um por banco) — a V53 assumia um só.
--
-- Lista ordenada num `text[]` e não tabela própria: são só strings, sem
-- atributos (o banco lê-se do próprio IBAN), e vêm sempre com o fornecedor.
-- Normalizados (sem espaços, maiúsculas) e validados pelo mod-97 no service;
-- a constraint aqui só garante a forma de cada um.
--
-- A V53 já correu na base de dados partilhada, por isso não se edita: o IBAN
-- que lá estiver passa para a lista e a coluna sai.
-- Sem equivalente no Excel da Vilatro — ver docs/excel-parity.md.
-- =============================================================

set search_path to worksite, public;

alter table worksite.supplier add column if not exists ibans text[] not null default '{}';

update worksite.supplier set ibans = array[iban] where iban is not null;

alter table worksite.supplier drop constraint if exists ck_supplier_iban;
alter table worksite.supplier drop column if exists iban;

alter table worksite.supplier
    add constraint ck_supplier_ibans
    check (cardinality(ibans) = 0
           or array_to_string(ibans, ',') ~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}(,[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30})*$');
