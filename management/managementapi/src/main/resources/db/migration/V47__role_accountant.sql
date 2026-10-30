-- =============================================================
-- V47__role_accountant.sql
-- Terceira role, para a contabilidade.
--
-- No espaço Obras faz o que o ADMIN faz (faturas, pagamentos,
-- fornecedores, orçamentos, obras); não entra na Equipa, nas
-- definições nem nas contas. Ver docs/security.md → "Autorização".
--
-- ADD VALUE corre dentro da transação do Flyway (PG >= 12) desde
-- que o valor novo não seja usado na mesma transação — por isso
-- nenhum UPDATE aqui; a role dá-se pela app (convite ou PATCH).
-- =============================================================

alter type worksite.role_enum add value if not exists 'ACCOUNTANT';
