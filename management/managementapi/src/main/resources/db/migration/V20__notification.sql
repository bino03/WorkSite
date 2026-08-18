-- =============================================================
-- V20__notification.sql
-- Notificações in-app, para funcionários.
--
-- O item original do backlog dizia "notificações para o cliente", mas não
-- existe cliente em lado nenhum do modelo (nem papel externo: role_enum é
-- ADMIN/EMPLOYEE) — ficou esclarecido a 2026-08-18 que o destinatário é
-- sempre um profile interno. Ver notes/whatIveDone.md.
--
-- O texto é guardado já escrito (title/body) em vez de type + parâmetros para
-- o frontend traduzir. É a escolha simples para a v1 e tem um custo conhecido:
-- uma notificação antiga fica na língua em que nasceu, mesmo que o utilizador
-- mude de idioma. Trocar isto mais tarde é reescrever a escrita, não a leitura.
-- =============================================================

set search_path to worksite, public;

create table if not exists worksite.notification (
    id           uuid        not null default gen_random_uuid() primary key,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),

    -- Se o perfil desaparece, as notificações dele não têm a quem pertencer.
    recipient_id uuid        not null references worksite.profile(id) on delete cascade,

    -- 'task_assigned' | 'invoice_pending'. Texto e não enum: um tipo novo não
    -- vale uma migração, e nada no backend faz decisões com base neste valor —
    -- serve para o frontend escolher o ícone.
    type         text        not null,

    title        text        not null,
    body         text,

    -- Rota do frontend para onde a notificação leva (ex. "/backoffice/tasks").
    -- Guardada em vez de derivada para o dia em que uma rota mudar não
    -- reescrever o histórico.
    link         text,

    -- A entidade que originou a notificação. Sem FK de propósito: aponta para
    -- tabelas diferentes conforme o `type`, e a notificação deve sobreviver ao
    -- desaparecimento da origem.
    entity_id    uuid,

    read_at      timestamptz
);

create trigger tg_notification_updated_at
    before update on worksite.notification
    for each row execute function worksite.tg_set_updated_at();

-- A consulta da lista é sempre "as minhas, das mais recentes para as mais
-- antigas" — este índice serve-a inteira.
create index if not exists idx_notification_recipient_created
    on worksite.notification(recipient_id, created_at desc);

-- O contador do sino só conta por ler. Índice parcial: as lidas acumulam-se
-- para sempre e não têm de pesar aqui.
create index if not exists idx_notification_unread
    on worksite.notification(recipient_id)
    where read_at is null;
