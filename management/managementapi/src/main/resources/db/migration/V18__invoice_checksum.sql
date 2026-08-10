-- =============================================================
-- V18__invoice_checksum.sql
-- Bloqueia o duplicado byte-a-byte, sem precisar do QR para nada.
--
-- notes/bugs.md, caso 3: duas cópias do mesmo ficheiro (mesmo sha256) passam
-- as duas quando nenhuma tem QR legível — ATCUD e (NIF, número) ficam ambos
-- vazios, e sem eles rejectIfDuplicate() não tem por onde comparar. Aconteceu
-- nos dados reais: "10.46.20.jpg" / "10.46.19.jpg", mesmo sha256, os dois sem
-- QR.
--
-- A coluna checksum_sha256 já existia como precedente em enterprises_media
-- (V12), mas nunca chegou a ser calculada lá — só passava o que viesse no
-- DTO, que nunca vinha. Aqui é diferente: calcula-se sempre a partir dos
-- bytes recebidos, em upload() e replaceFile(), porque esses bytes já estão
-- em memória de qualquer forma (é neles que o QR é lido).
-- =============================================================

set search_path to worksite, public;

alter table worksite.construction_invoice
    add column if not exists checksum_sha256 varchar(64);

-- Parcial, como o índice do ATCUD (V17): as faturas carregadas antes desta
-- migração ficam com checksum nulo, e Postgres não colide vários NULL.
create unique index if not exists uq_invoice_enterprise_checksum
    on worksite.construction_invoice (enterprise_id, checksum_sha256)
    where checksum_sha256 is not null;
