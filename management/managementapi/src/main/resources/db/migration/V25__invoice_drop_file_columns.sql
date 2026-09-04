-- =============================================================
-- V25__invoice_drop_file_columns.sql
-- Fecha a mudança da V24: as colunas de ficheiro saem da fatura.
--
-- Não há migração de dados: os registos existentes são todos de teste
-- (confirmado com o utilizador a 2026-09-04). As faturas que já existem ficam
-- sem documento — o que a partir da V24 é um estado legal, não uma anomalia.
-- Os ficheiros que estiverem no Storage ficam órfãos e são aceites como tal.
-- =============================================================

set search_path to worksite, public;

-- Assentava em construction_invoice.checksum_sha256, que desaparece abaixo.
-- A unicidade do ficheiro passa a viver em construction_invoice_document,
-- e global em vez de por projeto (ver V24).
drop index if exists worksite.uq_invoice_enterprise_checksum;

alter table worksite.construction_invoice
    drop column if exists bucket,
    drop column if exists storage_key,
    drop column if exists original_filename,
    drop column if exists mime_type,
    drop column if exists size_bytes,
    drop column if exists original_size_bytes,
    drop column if exists thumbnail_key,
    drop column if exists thumbnail_mime,
    drop column if exists checksum_sha256,
    -- cada documento passa a ter o seu carregador e a sua data
    drop column if exists uploaded_by,
    drop column if exists uploaded_at;
