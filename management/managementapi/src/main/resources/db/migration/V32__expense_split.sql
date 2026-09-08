-- =============================================================
-- V32__expense_split.sql
-- Uma fatura passa a poder ser repartida por várias rubricas.
--
-- A V16 criou `uq_expense_invoice` com o comentário: "1 fatura = 1 rubrica. Se
-- um dia for preciso repartir uma fatura por várias rubricas, basta largar este
-- índice — o resto do modelo já o suporta." Este é esse dia.
--
-- O modelo aguenta mesmo: `construction_expense` sempre teve `budget_item_id` e
-- `invoice_id` como colunas independentes, e os rollups da árvore de orçamento
-- (`ConstructionBudgetItemService.loadExpenseRollups`) sempre iteraram despesa a
-- despesa. O que **não** aguenta é o código Java que assumia 0..1 — tratado na
-- mesma entrega, não aqui.
--
-- A fatura de material de um armazém traz cimento e ferragens na mesma folha; a
-- app tinha de a forçar toda para uma rubrica só, o que é o principal motivo
-- para o gasto por rubrica não bater certo com o Excel da Vilatro.
--
-- `name` deixa de ser obrigatório: uma linha de repartição não tem nome próprio
-- — herda a descrição da fatura (docs/faturas-modelo-alvo.md §2.4). Continua a
-- ser obrigatório na prática para despesas lançadas à mão, mas isso valida-se no
-- serviço, onde há contexto para escolher o valor herdado.
--
-- Sem tabela `supplier_rubric_rule`: as regras de fornecedor→rubrica ficaram de
-- fora da fase 4 por decisão de 2026-09-08 (o histórico já cobre os casos que
-- interessam, e no Vilatro nunca existiram). Ver
-- notes/roadmap/plans/2026-09-08-fase4-rubricas.md.
--
-- Ver docs/faturas-modelo-alvo.md §7 e docs/excel-parity.md.
-- =============================================================

set search_path to worksite, public;

-- Uma fatura passa a poder ter N despesas. Continua a valer que cada despesa
-- pertence a no máximo uma fatura — isso é a coluna `invoice_id`, não o índice.
drop index if exists worksite.uq_expense_invoice;

-- Substitui-o por um índice **não** único: as queries que procuram as despesas
-- de uma fatura (`findByInvoiceId`, os loaders em lote das listas) passam a
-- devolver várias linhas e precisam dele na mesma.
create index if not exists idx_expense_invoice
    on worksite.construction_expense(invoice_id)
    where invoice_id is not null;

alter table worksite.construction_expense
    alter column name drop not null;
