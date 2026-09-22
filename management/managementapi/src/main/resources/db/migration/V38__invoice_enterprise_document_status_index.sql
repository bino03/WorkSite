-- =============================================================
-- V38__invoice_enterprise_document_status_index.sql
-- Índice composto para a pesquisa avançada de faturas por obra + estado do documento.
--
-- idx_invoice_document_status (V27) é parcial e só por document_status — serve a lista
-- global de "o que falta pedir/imprimir" (todas as obras). A pesquisa avançada do
-- Backoffice (InvoiceFiltersModal, 2026-09-21) filtra sempre por enterprise_id **e**,
-- opcionalmente, documentStatus — sem um índice composto, o planeador tem de combinar
-- idx_invoice_enterprise com idx_invoice_document_status via bitmap AND em vez de um
-- único index scan. Achado no roadmap pré-deploy (notes/roadmap/pre-deploy-security.md).
-- =============================================================

set search_path to worksite, public;

create index if not exists idx_invoice_enterprise_document_status
    on worksite.construction_invoice(enterprise_id, document_status);
