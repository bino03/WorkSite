-- =============================================================
-- V29__invoice_unique_global.sql
-- A unicidade da fatura deixa de ser por obra e passa a ser global.
--
-- A V17 pôs o ATCUD único dentro de cada projeto, e a V18 fez o mesmo ao
-- checksum. Isso deixava passar o caso que mais custa no vault da Vilatro: a
-- mesma fatura lançada em duas obras diferentes — ninguém dá por ela, e o
-- total de cada obra fica certo enquanto o total da empresa fica errado. Uma
-- fatura é um documento fiscal único: o ATCUD e o par (NIF, número) não se
-- repetem em Portugal, muito menos entre obras da mesma empresa.
-- É a decisão 18 do Vilatro — ver docs/excel-parity.md, secção 5.
--
-- O checksum já ficou global na V24, com o ficheiro. Falta o ATCUD e o par
-- (NIF, número).
--
-- Sem limpeza de duplicados antes (ao contrário da V17): os registos
-- existentes são todos de teste — decidido com o utilizador a 2026-09-04.
--
-- O par (NIF, número) é comparado aqui em bruto. A comparação normalizada
-- ("FT 2024/123" == "FT2024-123") continua a ser feita no serviço, em
-- ConstructionInvoiceService#normalizeDocumentNumber: este índice é a rede de
-- segurança da base de dados, não a regra de negócio.
-- =============================================================

set search_path to worksite, public;

drop index if exists worksite.uq_invoice_enterprise_atcud;

create unique index if not exists uq_invoice_atcud
    on worksite.construction_invoice (invoice_atcud)
    where invoice_atcud is not null;

create unique index if not exists uq_invoice_nif_number
    on worksite.construction_invoice (supplier_nif, invoice_number)
    where supplier_nif is not null and invoice_number is not null;
