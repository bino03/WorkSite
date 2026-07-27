# 🗄️ Base de Dados

PostgreSQL, gerido por **Flyway** em `management/managementapi/src/main/resources/db/migration/` (`V1` a `V14`). Três schemas: **`worksite`** (core do domínio), **`settings`** (convites/config) e **`tasks`** (tarefas standalone).

Só o backend (`managementapi`) tem acesso direto à base de dados — ver [[architecture.md]].

## Hierarquia Projeto → Construção

```
enterprises (projeto — nome de tabela/pacote mantido do Property-Management)
 ├── enterprises_location / enterprises_media (1:1 / 1:N)
 └── construction_stage (etapa, N:1 → enterprises)
      └── construction_sub_stage (sub-etapa, N:1 → construction_stage)
           └── construction_expense (despesa, N:1 → construction_sub_stage)
                — name, price, bucket/storage_key opcionais (fatura em PDF/imagem)
```

Segue eliminação em cascata (`ON DELETE CASCADE`) em toda a cadeia — eliminar um projeto remove em cascata as suas etapas, sub-etapas e despesas. O ficheiro de fatura da despesa é guardado apenas como `bucket`/`storage_key` (bucket `"documents"`), nunca a URL bruta — ver [[skill-add-file-upload]].

## Outras tabelas principais

| Tabela | Schema | Propósito |
|---|---|---|
| `profile` | `worksite` | Utilizador interno (liga a `auth_user_id` do Supabase); `role` = `ADMIN` ou `EMPLOYEE` |
| `location` | `worksite` | Localização standalone (endereço, cidade, coordenadas), reutilizada por `enterprises` |
| `activity_log` | `worksite` | Auditoria genérica (login/logout + CRUD em `enterprises`/`construction_*`) |
| `revoked_token` | `worksite` | Lista negra de JWTs revogados (logout/invalidação) |
| `settings.pending_invites` | `settings` | Convites de acesso pendentes (email, role, token) |
| `settings.email_providers` | `settings` | Configuração SMTP para envio de emails de convite |
| `tasks.task` | `tasks` | Tarefa standalone (nome, descrição, prazo, estado), sem ligação a nenhum ativo/imóvel |
| `tasks.task_assignee` | `tasks` | Junção many-to-many entre `tasks.task` e `worksite.profile` — utilizadores atribuídos |

## Convenções

- Todas as PKs são `UUID DEFAULT gen_random_uuid()`, exceto `revoked_token` (BIGSERIAL).
- Trigger genérico `worksite.tg_set_updated_at()` (definido em `V1`) mantém `updated_at` automaticamente — aplicado a todas as tabelas `worksite` com essa coluna via loop dinâmico em `V11`, e explicitamente às tabelas de construção em `V13`.
- Enums nativos do Postgres (`V2`): `role_enum` (`ADMIN`/`EMPLOYEE`), `account_status_enum` (`unlocked`/`blocked`/`deleted`), `media_type_enum`, `visibility_enum`, `activity_type`, `entity_type` (`enterprise`/`user`/`construction_stage`/`construction_sub_stage`/`construction_expense`).
- `V8` concede permissões explícitas aos roles do Supabase (`anon`, `authenticated`, `service_role`) — necessário porque a validação de JWT é feita localmente pelo backend, mas o Supabase continua a gerir os utilizadores de autenticação (`auth.users`).
- `V9` cria a FK condicional `profile.auth_user_id → auth.users(id)` (só se o schema `auth` existir — é o caso quando a app corre contra um projeto Supabase real).

## Deixado de fora (deliberadamente)

Não copiado do Property-Management: `property_asset`, `buildings`, `agency`, `contact`, `license`, `characteristic_*`, `lead`, `banner`, o schema `payments`. Nenhuma destas tabelas foi pedida para este projeto — são candidatas a funcionalidades futuras, não uma lacuna.

`task`/`task_assignee` **foram** copiadas (`V14`), mas isoladas no seu próprio schema `tasks` em vez de `worksite` — e sem o campo opcional `asset_id` que existia no original (não há conceito de imóvel/ativo aqui).

## Relacionado

- [[architecture.md]] — Como o backend acede à base de dados
- [[security.md]] — `profile.role` e como é usado na autorização
- [[../management/managementapi/CLAUDE.md]] — Guia do backend
