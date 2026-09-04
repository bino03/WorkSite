-- =============================================================
-- V27__invoice_document_status.sql
-- O estado do papel, e a descrição do que foi comprado.
--
-- `document_status` é derivável em parte (tem documento → ARCHIVED), mas
-- TO_PRINT e TO_REQUEST são intenção, não estado: traduzem as observações
-- "Imprimir fatura" e "Pedir fatura" do Excel da Vilatro, que dizem o que
-- falta fazer e não o que existe. Por isso é coluna e não um derivado.
--
-- `description` é o "Produto/Serviço" do Excel. Hoje esse texto só existe em
-- construction_expense.name, o que obriga a haver despesa para haver
-- descrição — e uma fatura por classificar não tem despesa nenhuma.
--
-- Sem migração de dados: os registos existentes são todos de teste (decidido
-- com o utilizador a 2026-09-04) e ficaram sem documento na V25, pelo que o
-- default MISSING descreve-os corretamente.
-- Ver docs/faturas-modelo-alvo.md, secção 2.1.
-- =============================================================

set search_path to worksite, public;

create type worksite.invoice_document_status as enum ('ARCHIVED', 'MISSING', 'TO_PRINT', 'TO_REQUEST');

alter table worksite.construction_invoice
    add column if not exists document_status worksite.invoice_document_status
        not null default 'MISSING',
    add column if not exists description text;

-- A lista de "o que falta pedir/imprimir" é uma vista de trabalho própria.
create index if not exists idx_invoice_document_status
    on worksite.construction_invoice(document_status)
    where document_status <> 'ARCHIVED';
