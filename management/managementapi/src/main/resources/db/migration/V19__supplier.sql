-- =============================================================
-- V19__supplier.sql
-- Catálogo de fornecedores: NIF → nome da empresa.
--
-- O QR da AT traz o NIF do emitente (campo A) mas NUNCA traz o nome — não
-- existe campo para isso na especificação. Resultado: as faturas entram com
-- supplier_nif preenchido e supplier_name vazio, e alguém escreve "Betão Liz,
-- Lda." à mão uma vez por fatura, para sempre.
--
-- Esta tabela guarda esse par uma única vez. A partir daí:
--   · faturas novas com um NIF conhecido nascem já com o nome preenchido
--   · dar nome a um NIF preenche as faturas antigas desse NIF que estejam
--     sem nome (nunca as que já têm — ver SupplierService)
--
-- Deliberadamente GLOBAL, não por projeto: o fornecedor é a mesma empresa em
-- todas as obras, e obrigar a reidentificá-lo obra a obra era repetir
-- exactamente o trabalho que isto vem eliminar.
-- =============================================================

set search_path to worksite, public;

create table if not exists worksite.supplier (
    id         uuid        not null default gen_random_uuid() primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    -- Sem formato imposto: o NIF português são 9 dígitos, mas um fornecedor
    -- estrangeiro (que nunca traz QR) tem outro formato de identificação
    -- fiscal e tem de caber aqui na mesma.
    nif        text        not null,
    name       text        not null,
    notes      text,

    created_by uuid        references worksite.profile(id) on delete set null
);

-- Um NIF = uma empresa. É esta constraint que torna a tabela um catálogo e não
-- mais uma lista de nomes soltos.
create unique index if not exists uq_supplier_nif on worksite.supplier(nif);

create trigger tg_supplier_updated_at
    before update on worksite.supplier
    for each row execute function worksite.tg_set_updated_at();

-- O índice que já existia em construction_invoice é (enterprise_id, supplier_nif)
-- e serve a sugestão de rubrica, que é sempre dentro de um projeto. A varredura
-- de NIFs por identificar é transversal aos projetos e não o consegue usar.
create index if not exists idx_invoice_supplier_nif_all
    on worksite.construction_invoice(supplier_nif)
    where supplier_nif is not null;

-- auditoria
alter type worksite.entity_type add value if not exists 'supplier';
