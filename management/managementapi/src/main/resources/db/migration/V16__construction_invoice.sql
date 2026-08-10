-- =============================================================
-- V16__construction_invoice.sql
-- Separa o DOCUMENTO da AFETAÇÃO.
--
-- Até aqui a fatura vivia dentro de worksite.construction_expense, cujo
-- budget_item_id é NOT NULL: era impossível registar uma fatura sem decidir
-- logo a rubrica. Na prática isso travava o registo em massa — quem chega da
-- obra com 15 faturas tem de as lançar primeiro e classificar depois.
--
-- A partir daqui:
--   construction_invoice  = o documento (ficheiro + dados do QR da AT)
--   construction_expense  = a afetação desse documento a uma rubrica
--
-- Uma fatura sem despesa associada é uma fatura "por associar".
-- =============================================================

set search_path to worksite, public;

-- ── 1. o documento ───────────────────────────────────────────
create table if not exists worksite.construction_invoice (
    id                    uuid        not null default gen_random_uuid() primary key,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    enterprise_id         uuid        not null references worksite.enterprises(id) on delete cascade,

    -- lidos do QR da AT (ver AtInvoiceQrService); todos corrigíveis à mão
    supplier_name         text,
    supplier_nif          text,
    invoice_number        text,
    invoice_atcud         text,

    -- NULLABLE de propósito. Sem QR legível — fornecedor estrangeiro, documento
    -- antigo, digitalização má — a fatura entra na mesma e fica "por rever".
    -- Exigir estes campos aqui reintroduzia exactamente a fricção que esta
    -- tabela vem eliminar. São validados no momento de associar à rubrica,
    -- porque aí sim construction_expense exige data e total.
    invoice_date          date,
    total_amount          numeric(14,2),
    taxable_amount        numeric(14,2),
    tax_amount            numeric(14,2),
    notes                 text,

    -- documento: nunca a URL bruta, apenas bucket + chave
    bucket                text        not null,
    storage_key           text        not null,
    original_filename     text,
    mime_type             text,
    size_bytes            bigint,     -- o que ficou no Storage, já comprimido
    -- tamanho antes da compressão no browser; guardado só para se poder mostrar
    -- a poupança e detectar regressões na qualidade da compressão
    original_size_bytes   bigint,

    -- miniatura para as listas (~480px). Vale a pena: sem ela, abrir a lista de
    -- faturas descarregava o documento completo de cada linha.
    thumbnail_key         text,
    thumbnail_mime        text,

    uploaded_by           uuid        references worksite.profile(id) on delete set null,
    uploaded_at           timestamptz not null default now(),

    -- envio para a contabilidade: é uma propriedade do documento, não da
    -- afetação — por isso migra da despesa para aqui
    sent_to_accountant    boolean     not null default false,
    sent_to_accountant_by uuid        references worksite.profile(id) on delete set null,
    sent_to_accountant_at timestamptz,

    created_by            uuid        references worksite.profile(id) on delete set null
);

create trigger tg_construction_invoice_updated_at
    before update on worksite.construction_invoice
    for each row execute function worksite.tg_set_updated_at();

create index if not exists idx_invoice_enterprise
    on worksite.construction_invoice(enterprise_id);
create index if not exists idx_invoice_date
    on worksite.construction_invoice(enterprise_id, invoice_date desc);
-- alimenta a sugestão de rubrica: "as faturas deste fornecedor costumam ir para..."
create index if not exists idx_invoice_supplier_nif
    on worksite.construction_invoice(enterprise_id, supplier_nif)
    where supplier_nif is not null;
-- não é único: o ATCUD repetido é avisado, não bloqueado
create index if not exists idx_invoice_atcud
    on worksite.construction_invoice(enterprise_id, invoice_atcud)
    where invoice_atcud is not null;

-- ── 2. a despesa passa a ser a afetação ──────────────────────
-- Nullable: continua a poder existir uma despesa lançada à mão, sem documento.
-- Cascade: apagar a fatura apaga o lançamento que dela nasceu — sem isso ficava
-- um valor no orçamento sem documento que o justifique.
alter table worksite.construction_expense
    add column if not exists invoice_id uuid
        references worksite.construction_invoice(id) on delete cascade;

-- 1 fatura = 1 rubrica. Se um dia for preciso repartir uma fatura por várias
-- rubricas, basta largar este índice — o resto do modelo já o suporta.
create unique index if not exists uq_expense_invoice
    on worksite.construction_expense(invoice_id)
    where invoice_id is not null;

create index if not exists idx_construction_expense_invoice_id
    on worksite.construction_expense(invoice_id);

-- ── 3. os dados do documento saem da despesa ─────────────────
-- Estes índices assentavam em colunas que vão desaparecer.
drop index if exists worksite.idx_construction_expense_atcud;
drop index if exists worksite.idx_construction_expense_accountant;

alter table worksite.construction_expense
    drop column if exists supplier_nif,
    drop column if exists invoice_number,
    drop column if exists invoice_atcud,
    drop column if exists bucket,
    drop column if exists storage_key,
    drop column if exists original_filename,
    drop column if exists mime_type,
    drop column if exists size_bytes,
    drop column if exists uploaded_by,
    drop column if exists uploaded_at,
    drop column if exists sent_to_accountant,
    drop column if exists sent_to_accountant_by,
    drop column if exists sent_to_accountant_at;

create index if not exists idx_invoice_accountant
    on worksite.construction_invoice(enterprise_id, sent_to_accountant)
    where sent_to_accountant = false;

-- ── 4. auditoria ─────────────────────────────────────────────
alter type worksite.entity_type add value if not exists 'construction_invoice';
