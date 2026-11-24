-- =============================================================
-- V53__supplier_iban.sql
-- IBAN do fornecedor, para pagar por transferência.
--
-- Um só por fornecedor: se mudar, edita-se. Guardado normalizado (sem
-- espaços, maiúsculas) e validado pelo mod-97 no service — a constraint aqui
-- só garante a forma, para um IBAN mal escrito nunca chegar a ser gravado
-- por outro caminho.
--
-- A app só o mostra e copia; a transferência continua no homebanking.
-- Sem equivalente no Excel da Vilatro — ver docs/excel-parity.md.
-- =============================================================

set search_path to worksite, public;

alter table worksite.supplier add column if not exists iban varchar(34);

alter table worksite.supplier
    add constraint ck_supplier_iban
    check (iban is null or iban ~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$');
