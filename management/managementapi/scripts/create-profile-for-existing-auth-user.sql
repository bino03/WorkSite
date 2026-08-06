-- =============================================================
-- create-profile-for-existing-auth-user.sql
--
-- Cria uma conta interna (worksite.profile) associada a um utilizador
-- que já existe em auth.users (ex.: criado manualmente no dashboard do
-- Supabase, em Authentication > Users > Add user).
--
-- NÃO é uma migração Flyway — corre isto uma única vez, manualmente,
-- no SQL Editor do Supabase (dashboard do projeto > SQL Editor).
-- É útil sobretudo para criar o PRIMEIRO admin, já que o fluxo normal
-- de convite (AdminAuthController) exige que já exista um admin.
-- =============================================================

-- 1) Ajusta estes 3 valores:
--    - email do auth.users já criado
--    - nome a mostrar na app
--    - role: 'ADMIN' ou 'EMPLOYEE'
INSERT INTO worksite.profile (auth_user_id, name, role, account_status)
SELECT
  u.id,
  'Nome Completo',        -- <<< ajustar
  'ADMIN',                -- <<< 'ADMIN' ou 'EMPLOYEE'
  'unlocked'
FROM auth.users u
WHERE u.email = 'email@exemplo.com'   -- <<< ajustar
ON CONFLICT (auth_user_id) DO NOTHING
RETURNING *;

-- 2) Confirma que ficou criada:
-- SELECT p.*, u.email
-- FROM worksite.profile p
-- JOIN auth.users u ON u.id = p.auth_user_id
-- WHERE u.email = 'email@exemplo.com';
