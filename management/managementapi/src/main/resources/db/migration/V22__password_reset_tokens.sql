-- =============================================================
-- V22__password_reset_tokens.sql
-- Recuperação de password com fluxo próprio.
--
-- A página `/forgot-password` existia desde o início e não fazia nada: o
-- `onFinish` mostrava "email enviado" sem chamar API nenhuma, e não havia
-- endpoint nenhum do lado do backend. Quem lá chegasse ficava à espera de um
-- email que nunca saía.
--
-- Optou-se por fluxo próprio em vez do `/auth/v1/recover` do Supabase para o
-- email sair pelo SMTP configurado em `settings.email_providers` (o mesmo do
-- convite), e não pelo do dashboard do Supabase — que tem limites de rate
-- apertados e um template que não se controla a partir daqui.
--
-- A tabela espelha `settings.pending_invites` de propósito: mesmo schema, mesma
-- forma (token opaco + prazo + marca de uso), para os dois fluxos se lerem um ao
-- lado do outro.
-- =============================================================

create table if not exists settings.password_reset_tokens (
  id            uuid primary key default gen_random_uuid(),

  -- Aponta para auth.users sem FK: o schema `auth` é do Supabase e não se
  -- referencia a partir de migrações nossas (mesma decisão do `profile.auth_user_id`).
  auth_user_id  uuid        not null,

  -- Guardado além do id porque é para onde o email foi enviado. Se a conta
  -- mudar de email depois do pedido, o histórico mantém o destino real.
  email         varchar(255) not null,

  token         varchar(500) not null unique,
  expires_at    timestamptz not null,

  -- Nulo = por usar. Um token só serve uma vez.
  used_at       timestamptz,

  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- A leitura é sempre pelo token (vem no link do email); o índice do unique já
-- serve. Este é para a limpeza dos pedidos anteriores do mesmo utilizador, feita
-- a cada novo pedido.
create index if not exists idx_password_reset_user_pending
    on settings.password_reset_tokens (auth_user_id)
    where used_at is null;

-- O trigger da V11 só percorre o schema `worksite`.
create or replace trigger tg_upd__password_reset_tokens
    before update on settings.password_reset_tokens
    for each row execute function worksite.tg_set_updated_at();
