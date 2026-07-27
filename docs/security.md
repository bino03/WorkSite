# 🔐 Segurança & Autenticação

Autenticação centralizada no backend (`managementapi`), baseada em JWTs emitidos pelo **Supabase**. Configuração em `SecurityConfig.java`. Idêntico ao fluxo do Property-Management original — ver [[../CLAUDE.md]] para o que foi mantido.

## Fluxo de autenticação

1. O Backoffice chama `POST /auth/login` e o backend troca as credenciais pelo Supabase Auth.
2. O token é devolvido em cookies HttpOnly (`access_token`, `refresh_token`).
3. Em todos os pedidos protegidos, o `CookieJwtFilter` promove o cookie para header `Authorization: Bearer {token}` antes do filtro OAuth2 correr.
4. O backend valida o token **localmente**, sem chamar o Supabase — usa `NimbusJwtDecoder` com algoritmo **HS256** e a chave partilhada (`SupabaseProperties` → `jwt.secret`).
5. `GET /auth/me` devolve o perfil (`role`, nome, foto) a partir da tabela `worksite.profile`.

## Autorização (roles)

- Todo o token válido recebe a authority `ROLE_AUTHENTICATED`.
- Roles adicionais (`ROLE_ADMIN`, `ROLE_EMPLOYEE`) são derivadas de três fontes, por ordem: claim `role` no topo do JWT, `app_metadata.role`, e como fallback uma consulta a `worksite.profile.role`.
- O tipo `role_enum` na base de dados só define `ADMIN` e `EMPLOYEE`.
- Autorização fina feita maioritariamente por `@PreAuthorize` em cada controller/método (`hasRole('ADMIN')`, `hasAnyRole('ADMIN','EMPLOYEE')`, `isAuthenticated()`).

### Regras globais (`SecurityConfig`)

| Padrão | Acesso |
|---|---|
| `/actuator/health`, `/ping` | público |
| `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/accept-invite` | público |
| `POST /auth/admin/**` | `ADMIN` |
| `GET /employees/**` | `ADMIN` ou `EMPLOYEE` |
| `/auth/me` | qualquer utilizador autenticado |
| Tudo o resto | autenticado (role específica validada por `@PreAuthorize` no controller) |

## Filtros de segurança (ordem relevante)

- **`TokenRevocationFilter`** — corre antes da autenticação Bearer; verifica se o `jti` do token está na tabela `worksite.revoked_token`.
- **`CookieJwtFilter`** — lê o cookie `access_token` e injeta-o como header `Authorization` se ainda não existir.
- **Filtro Bearer JWT (OAuth2 Resource Server)** — valida assinatura/expiração e constrói as authorities.
- **`AccountLockFilter`** — corre depois da autenticação; bloqueia pedidos de contas com `account_status` de bloqueada/eliminada.

## CORS

Configurado via `CorsConfigurationSource` em `SecurityConfig.java` — ajustar as origens permitidas para o domínio real do Backoffice (dev: `http://localhost:5173`) e o de produção quando existir.

## Outros detalhes

- Sessão **stateless**; **CSRF desativado** (esperado numa API pura consumida por SPA/JWT).
- Existe um bean `BCryptPasswordEncoder`, usado apenas para casos locais/legados — a autenticação principal é delegada ao Supabase.
- Erros de autenticação (401/403) são tratados de forma centralizada no Backoffice pela instância `api.ts` (redirect automático para `/login` em 401 após tentativa de refresh, notificação em 403/500).

## Relacionado

- [[architecture.md]] — Visão geral do sistema
- [[database.md]] — `worksite.profile`, `worksite.revoked_token`
- [[../management/managementapi/CLAUDE.md]] — Guia do backend
- [[../management/managementfrontend/apps/backoffice/CLAUDE.md]] — Fluxo de autenticação no Backoffice
