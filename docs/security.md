# 🔐 Segurança & Autenticação

Autenticação centralizada no backend (`managementapi`), baseada em JWTs emitidos pelo **Supabase**. Configuração em `SecurityConfig.java`. Idêntico ao fluxo do Property-Management original — ver [[../CLAUDE.md]] para o que foi mantido.

## Fluxo de autenticação

1. O Backoffice chama `POST /auth/login` e o backend troca as credenciais pelo Supabase Auth.
2. O token é devolvido em cookies HttpOnly (`access_token`, `refresh_token`).
3. Em todos os pedidos protegidos, o `CookieJwtFilter` promove o cookie para header `Authorization: Bearer {token}` antes do filtro OAuth2 correr.
4. O backend valida o token **localmente** contra o **JWKS** do projeto (`<SUPABASE_URL>/auth/v1/.well-known/jwks.json`, `NimbusJwtDecoder` com **ES256** — os projetos Supabase novos assinam com chave assimétrica, não com o segredo HS256 legado). O `SUPABASE_JWT_SECRET` continua a ser lido para `SupabaseProperties` mas não entra na validação.
5. `GET /auth/me` devolve o perfil (`role`, nome, foto) a partir da tabela `worksite.profile`.

## Autorização (roles)

- Todo o token válido recebe a authority `ROLE_AUTHENTICATED`.
- Roles adicionais (`ROLE_ADMIN`, `ROLE_EMPLOYEE`) são derivadas de três fontes, por ordem: claim `role` no topo do JWT, `app_metadata.role`, e como fallback uma consulta a `worksite.profile.role`.
- O tipo `role_enum` na base de dados só define `ADMIN` e `EMPLOYEE`.
- Autorização fina feita maioritariamente por `@PreAuthorize` em cada controller/método (`hasRole('ADMIN')`, `hasAnyRole('ADMIN','EMPLOYEE')`, `isAuthenticated()`).

### Regras globais (`SecurityConfig`)

| Padrão | Acesso |
|---|---|
| `/actuator/health` | público |
| `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/accept-invite`, `/auth/forgot-password`, `/auth/reset-password` | público |
| `POST /auth/admin/**` | `ADMIN` |
| `/settings/**` | `ADMIN` — credenciais SMTP; reforçado com `@PreAuthorize` na classe |
| `GET /employees/**` | `ADMIN` ou `EMPLOYEE` |
| `/auth/me` | qualquer utilizador autenticado |
| Tudo o resto | autenticado (role específica validada por `@PreAuthorize` no controller) |

## Filtros de segurança (ordem relevante)

- **`TokenRevocationFilter`** — corre antes da autenticação Bearer; verifica se o `jti` do token está na tabela `worksite.revoked_token`.
- **`CookieJwtFilter`** — lê o cookie `access_token` e injeta-o como header `Authorization` se ainda não existir.
- **Filtro Bearer JWT (OAuth2 Resource Server)** — valida assinatura/expiração e constrói as authorities.
- **`AccountLockFilter`** — corre depois da autenticação; bloqueia pedidos de contas com `account_status` de bloqueada/eliminada.

## CORS

Configurado via `CorsConfigurationSource` em `SecurityConfig.java`, com as origens permitidas lidas
de `app.security.cors.allowed-origins` (env var `CORS_ALLOWED_ORIGINS`, lista separada por vírgulas)
— deixou de haver domínios hardcoded no código desde 2026-09-22. Sem valor de produção por omissão
de propósito: o domínio real do Backoffice ainda não está decidido. `allowCredentials(true)` está
ativo, por isso uma origem errada em produção dá a qualquer site acesso à sessão de um utilizador
autenticado — ver `notes/roadmap/pre-deploy-security.md`.

## Modelo de confiança na base de dados

**Só o backend liga à base de dados.** Tudo o resto — Backoffice, scripts, o vault Excel — passa
por ele ou por ficheiros. É esta frase que decide o que se segue.

| Quem | Liga como | A quê | Sujeito a RLS? |
|---|---|---|---|
| `managementapi` (única ligação) | `DB_USER=postgres.<project-ref>` — o role `postgres` do Supabase, pelo pooler em modo transaction (`:6543`) | schemas `worksite`, `settings`, `tasks` | não — `postgres` ignora RLS |
| `managementapi` → Storage e Auth | `SUPABASE_SERVICE_ROLE_KEY` (REST, via OkHttp) | buckets `documents`, `media`; `auth.users` | n/a (chave de serviço, ignora RLS) |
| Supabase (Auth, Storage, PostgREST) | `service_role` | os seus próprios schemas; em `worksite`/`settings` tem `GRANT ALL` da `V8` | n/a |
| Browser, `anon`/`authenticated` via PostgREST | — | **nada**: `worksite` não está nos schemas expostos pela API do Supabase, e desde a `V37` nem `USAGE` no schema têm | — |

**Não há Row Level Security, de propósito.** RLS serve para quando um cliente liga à base de dados
com a identidade do utilizador final (o padrão Supabase: browser → PostgREST → `authenticated` +
`auth.uid()`). Aqui nenhum utilizador final chega à base de dados: o backend liga como `postgres`,
que a ignora, e decide quem vê o quê em Java — `@PreAuthorize` nos controllers e as verificações
de ownership nos services ([[skill-permissions-and-auth]]). Uma policy não seria lida por
ninguém e daria a sensação errada de que existe uma segunda linha de defesa. Se um dia um cliente
ligar diretamente (uma app móvel via PostgREST, por exemplo), isto muda: expor o schema, dar
grants a `authenticated` **e** escrever policies — as três coisas juntas, ou nenhuma.

O que a `V37` tirou, e porquê:

- `USAGE` em `worksite` a `anon` e `authenticated` (`V8`): era inerte — nunca houve tabela
  concedida a esses roles nem o schema exposto — mas sugeria um caminho de acesso que não existe.
- O role `worksite_expenses_ro` (`V30`): servia o `worksite-expenses`, um frontend de consulta que
  lia a base de dados sem passar pelo backend. Foi descartado a 2026-09-06 e o role ficou em
  produção com `LOGIN` e a password em texto claro no `.env.local` desse repo. A `V37` revoga tudo
  e tenta o `DROP`; se falhar, avisa e o role fica sem qualquer acesso. **Foi o que aconteceu na
  base de dados real a 2026-09-19**: `permission denied to drop objects` — no Supabase o `postgres`
  não é superuser, e `DROP OWNED BY` exige ser membro do role (foi criado à mão antes da `V30`, não
  pela migração). O role existe, sem `USAGE`, sem `SELECT`, sem `CONNECT`. Para o apagar de vez, no
  SQL editor: `GRANT worksite_expenses_ro TO postgres; DROP OWNED BY worksite_expenses_ro; DROP ROLE
  worksite_expenses_ro;` — ou Database → Roles no dashboard, se o `GRANT` também for recusado.

Os grants a `service_role` (`V8`) ficam: são do próprio Supabase para as suas APIs, não um
cliente nosso — e é com essa chave que o backend fala com o Storage e o Auth.

`SecurityConfig` deixou de ter, na mesma passagem, os matchers herdados sem controller
(`GET /open/**`, `POST /open/leads`, `POST /assets`, `POST /banners`, e os `/api/auth/*` — nenhum
controller tem prefixo `/api`). A tabela acima é a superfície real.

## Bucket `documents` — privado, por fora do código

Nenhum caminho do código (faturas, comprovativos) usa `getPublicUrl()` — só `SignedUrlService`
(TTL de 3600s). Mas se o bucket `documents` estiver marcado como **público** na dashboard do
Supabase, isso não se vê no código nenhum: `SupabaseProperties` não guarda essa flag, é uma
configuração só do lado do Supabase. **Confirmar manualmente antes de produção** (Storage →
`documents` → toggle "Public" desligado), e voltar a confirmar sempre que o bucket for recriado —
não há teste automatizado que apanhe uma regressão aqui. Checklist completa em
`notes/verificacao-browser-pendente.md` §19.

## Segredos em repouso — password SMTP

A password de `settings.email_providers` é a única credencial que a app guarda na sua própria
base de dados (o resto é do Supabase). Cifrada em repouso desde a `V34`:

- **AES-256-GCM**, IV de 12 bytes aleatório por valor, tag de 128 bits. Valor no formato
  `gcm:<base64 iv>:<base64 ct+tag>` na coluna `password` (`text`).
- `util/SecretCipher` + `model/converters/EncryptedStringConverter` (`@Convert` no campo) —
  cifra/decifra transparente; `EmailService` e `EmailProviderService` não sabem que existe.
- A API **nunca** devolve a password (o mapper só expõe `hasPassword`); a cifra fecha a exposição
  por acesso direto à base de dados.
- Chave em `APP_EMAIL_CRYPTO_KEY` (base64 de 32 bytes). **Obrigatória** — sem ela o backend não
  arranca. Gerar: `openssl rand -base64 32`.
- Um valor sem prefixo `gcm:` é lido como texto em claro legado; `EmailProviderPasswordReEncryptRunner`
  re-cifra-o no arranque.

O procedimento para **rodar a chave** (com `APP_EMAIL_CRYPTO_KEY_PREVIOUS` como rede durante a troca)
está em [[operations]] — é operação, não desenho.

## Outros detalhes

- Sessão **stateless**; **CSRF desativado** (esperado numa API pura consumida por SPA/JWT).
- Existe um bean `BCryptPasswordEncoder`, usado apenas para casos locais/legados — a autenticação principal é delegada ao Supabase.
- Erros de autenticação (401/403) são tratados de forma centralizada no Backoffice pela instância `api.ts` (redirect automático para `/login` em 401 após tentativa de refresh, notificação em 403/500).

## Relacionado

- [[architecture.md]] — Visão geral do sistema
- [[database.md]] — `worksite.profile`, `worksite.revoked_token`
- [[backend-conventions]] — Convenções e armadilhas do backend
- [[skills/references/design/backoffice-app-shell-and-auth]] — Fluxo de autenticação no Backoffice (rotas, guards, `AuthContext`)
- [[operations]] — backup/restore, migração má, rotação de chave
