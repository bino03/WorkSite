-- Soft delete de rubricas do orçamento: eliminar passa a marcar deleted_at em vez de
-- apagar a linha. A sub-árvore inteira é marcada junto (nunca só o nó); a purga real
-- (hard delete) corre 30 dias depois, por job agendado — ver
-- ConstructionBudgetItemPurgeConfig. Eliminar uma rubrica com despesas continua proibido
-- (BUDGET_013), por isso uma linha marcada aqui nunca tem despesas por baixo.
ALTER TABLE worksite.construction_budget_item ADD COLUMN deleted_at timestamptz;

-- A zona de recuperação lista por deleted_at; o índice parcial só cobre as linhas
-- marcadas, que são sempre uma fração pequena da árvore.
CREATE INDEX idx_construction_budget_item_deleted_at
    ON worksite.construction_budget_item (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- uq_budget_item_code (V15) ignorava só os `code` nulos — uma rubrica eliminada mantinha
-- o código a ocupar o índice, e recuperar/criar outra com o mesmo código (o serviço já
-- trata o código de uma eliminada como livre) rebentava com um 500 em vez do BUDGET_010
-- esperado. Passa a ignorar também as eliminadas.
DROP INDEX worksite.uq_budget_item_code;
CREATE UNIQUE INDEX uq_budget_item_code
    ON worksite.construction_budget_item (enterprise_id, code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;
