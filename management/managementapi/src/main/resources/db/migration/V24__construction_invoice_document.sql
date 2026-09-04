-- =============================================================
-- V24__construction_invoice_document.sql
-- O ficheiro sai da fatura.
--
-- Até aqui uma fatura era exatamente um ficheiro: bucket/storage_key eram
-- NOT NULL na própria linha. Isso torna impossíveis dois casos que aparecem
-- todos os dias no vault da Vilatro:
--   • a fatura que ainda não tem documento nenhum ("pedir fatura", "imprimir
--     fatura") — hoje não pode sequer ser registada;
--   • a fatura que tem mais do que um ficheiro — a foto tirada na obra e o
--     PDF que o fornecedor mandou depois, ou um PDF partido página a página.
--
-- A partir daqui a fatura é o registo e os ficheiros são 0..N documentos seus.
-- Ver docs/faturas-modelo-alvo.md §2.2.
-- =============================================================

set search_path to worksite, public;

-- Só para a UI agrupar/ordenar. Nenhuma regra de negócio decide com base nisto.
create type worksite.invoice_document_kind as enum ('ORIGINAL', 'PAGE', 'PHOTO', 'OTHER');

create table if not exists worksite.construction_invoice_document (
    id                  uuid        not null default gen_random_uuid() primary key,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),

    invoice_id          uuid        not null
                            references worksite.construction_invoice(id) on delete cascade,

    -- documento: nunca a URL bruta, apenas bucket + chave
    bucket              text        not null,
    storage_key         text        not null,
    original_filename   text,
    mime_type           text,
    size_bytes          bigint,     -- o que ficou no Storage, já comprimido
    original_size_bytes bigint,     -- antes da compressão no browser, só para mostrar a poupança

    -- miniatura para as listas (~480px)
    thumbnail_key       text,
    thumbnail_mime      text,

    checksum_sha256     varchar(64),

    kind                worksite.invoice_document_kind not null default 'ORIGINAL',
    page_number         integer,    -- só para PDFs partidos página a página

    -- texto bruto do QR lido DESTE ficheiro, para auditoria; os campos já
    -- interpretados (NIF, número, ATCUD, totais) continuam na fatura
    qr_payload          text,

    uploaded_by         uuid        references worksite.profile(id) on delete set null,
    uploaded_at         timestamptz not null default now()
);

create trigger tg_construction_invoice_document_updated_at
    before update on worksite.construction_invoice_document
    for each row execute function worksite.tg_set_updated_at();

create index if not exists idx_invoice_document_invoice
    on worksite.construction_invoice_document(invoice_id);

-- O mesmo ficheiro não entra duas vezes em lado nenhum: ao contrário do índice
-- que a V18 criou, este é GLOBAL, não por projeto. Um ficheiro carregado na
-- obra A é recusado na obra B, na quarentena e nas despesas da empresa — que é
-- o comportamento que o vault da Vilatro já assume (decisão 18).
-- Parcial: documentos antigos sem checksum não colidem entre si.
create unique index if not exists uq_invoice_document_checksum
    on worksite.construction_invoice_document (checksum_sha256)
    where checksum_sha256 is not null;
