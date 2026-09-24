# 🛠️ Operações

O que se faz à volta da app quando ela já está a correr: o que é "produção", como se guarda e se
repõe uma cópia, o que fazer a uma migração que correu mal, e a rotação da chave de cifra.
Nada disto vive no código — é o que se perde quando só se lê o repo.

## O que é "produção"

**Não há servidor.** Produção é o **projeto Supabase real** (Postgres + Auth + Storage) com o
backend a correr **localmente** em `:8080` e o Backoffice em `:5173`, na máquina de quem usa a
app. O `.env` de `management/managementapi/` aponta para esse projeto — ver [[environment]].

Consequências que se pagam caro por não se saberem:

- **Tudo o que arranca o backend toca a base de dados real**: `spring-boot:run`,
  `-Dtest=ManagementApiApplicationTests` (a única classe que levanta o contexto — desde 2026-09-19
  fora do `./mvnw test` por defeito) e, com o devtools ligado, o próprio `mvn compile` — uma
  migração Flyway nova aplica-se no primeiro restart. Ver [[commands]] → "Testes".
- Não há ambiente de staging. As obras marcadas `is_test` (`enterprises.is_test`, `V23`) são o
  substituto: existem só para experimentar e os relatórios/exportações sabem excluí-las.
- Quem liga a quê: o backend liga ao Postgres como `postgres.<project-ref>` (o role `postgres`
  do Supabase, via pooler em modo transaction, porta `6543`) e ao Storage/Auth com a
  **service role key**. Ninguém mais liga à base de dados — ver [[security]].
- O vault Excel da Vilatro é uma **salvaguarda paralela dos dados de negócio** (obras,
  orçamentos, faturas, pagamentos — [[excel-parity]] §9), mas não de utilizadores, tarefas,
  notificações nem definições. Uma cópia de segurança a sério é a que está abaixo.

## Deploy — o que muda quando deixar de correr localmente

Hoje "produção" é o parágrafo acima: o mesmo Postgres/Auth/Storage reais, mas o backend a correr
na máquina de quem usa a app. O dia em que isto passar a correr num serviço a sério (um servidor,
um container, uma PaaS) muda um conjunto concreto de coisas — nenhuma delas é código por escrever,
são decisões e configuração. Levantado e testado a 2026-09-22 (roadmap completo em
`notes/roadmap/pre-deploy-security.md`, já implementado e testado o que dava para testar sem essa
hospedagem decidida).

### A decisão que destrava o resto: onde o backend vai correr

Sem isto decidido, quatro coisas ficam por afinar — todas configuráveis já, nenhuma tem valor de
produção hardcoded no código de propósito:

| Depende da hospedagem | Onde se define | Nota |
|---|---|---|
| Domínio real do Backoffice | `CORS_ALLOWED_ORIGINS`, `COOKIE_DOMAIN`, `APP_FRONTEND_URL` | Sem isto os links de convite/recuperação apontam para `localhost` e o CORS recusa o Backoffice real |
| Há proxy/load balancer a terminar TLS na frente? | `server.forward-headers-strategy=native` em `application.yml` (não existe hoje) | Sem isto, `request.isSecure()` nunca vê `true` mesmo com HTTPS real a montante, e o header HSTS (já ativo por omissão no Spring Security) nunca sai |
| Uma instância só, ou várias a escalar horizontalmente? | — | `RateLimitFilter` e `ExportRateLimitFilter` guardam o estado em **memória** (bucket4j + Caffeine). Com várias réplicas, cada uma tem o seu próprio contador — o limite efetivo multiplica-se pelo nº de réplicas. Não é um bug; se escalar, isto passa a exigir estado partilhado (Redis) ou mover o rate limiting para o proxy |
| Mesmo projeto Supabase de hoje, ou um novo? | `DB_URL`, `SUPABASE_*` no `.env` | Mesmo projeto → migrações `V1`–`V38` já aplicadas, nada a fazer. Projeto novo → o Flyway aplica tudo de raiz no primeiro arranque (confirmar no log) |

**O backend não pode ficar num domínio à parte do Backoffice** (ex. Backoffice em `worksite.pt` e
API no subdomínio genérico da hospedagem, tipo `algo.onrender.com`). `CookieUtil.java` fixa
`sameSite("Lax")` nos três cookies (`access_token`, `refresh_token`, limpeza) — um cookie `Lax`
**não viaja em pedidos cross-site feitos por `fetch`/XHR** (só em navegação de topo), e é assim que
o Axios do Backoffice fala com a API. `SameSite` compara o domínio registável (eTLD+1), não a
origem exata, por isso dois subdomínios do mesmo domínio contam como "same-site" e o cookie viaja
na mesma. Ou seja: só é preciso **um** domínio comprado — o backend fica num subdomínio dele (ex.
`api.worksite.pt` a apontar por CNAME para o serviço da hospedagem), nunca no domínio "de fábrica"
da plataforma. `COOKIE_DOMAIN` tem de ser o domínio pai partilhado (ex. `.worksite.pt`) para o
cookie posto pela API em `api.worksite.pt` ser lido também pelo Backoffice em `app.worksite.pt`/
`worksite.pt`.

### Opções de hospedagem pesquisadas (2026-09-23)

Nenhuma decisão tomada ainda — isto é o levantamento para decidir, não uma escolha feita. Preços
de mercado mudam; confirmar no site antes de assinar.

**Backend** (precisa de correr um processo Java persistente — não serve nada serverless tipo
funções Vercel):

| Serviço | Grátis? | Preço pago | Nota |
|---|---|---|---|
| Render | Sim, mas adormece ao fim de 15min sem tráfego (~1min a acordar) | $7/mês (sempre ativo) | Mais simples de configurar (liga ao repo, deploy automático) |
| Railway | Só $5 de crédito único ao criar conta, sem grátis permanente | Hobby $5/mês + consumo acima disso (tipicamente $5-10/mês total) | Boa DX, ligeiramente mais caro no total que o Render |
| Fly.io | Trial inicial, depois pay-as-you-go | Máquina mínima (256MB) ≈ $2/mês, cresce com CPU/RAM/rede | Mais barato, mas exige mais configuração manual (Dockerfile, `fly.toml`, CLI) |

Qualquer um serve para uma única instância (ver tabela acima — o rate limiting em memória exige
isso de qualquer forma).

**Frontend** (só ficheiros estáticos do `vite build` — qualquer hospedagem de sites estáticos serve):

- **Vercel Hobby (grátis) não pode ser usado aqui.** Os termos do Vercel definem uso comercial
  como incluindo explicitamente **"o projeto pertencer a uma LLC ou outra entidade comercial"** —
  o Worksite pertence à empresa, cai diretamente nessa categoria, não é zona cinzenta. A violação
  não é só uma questão de faturação: a consequência documentada é **suspensão da conta/projeto**,
  o que para uma ferramenta interna de uso diário é um risco real, não hipotético. Usar Vercel
  implica o plano **Pro, $20/mês**.
- Alternativa sem essa cláusula e sem custo: **Cloudflare Pages** (tier gratuito utilizável
  comercialmente) — e se o domínio também for comprado na Cloudflare, fica tudo (registo, DNS,
  frontend) no mesmo sítio.

**Domínio** — ao registar via um agente do DNS.pt (ex. OVHcloud) ou noutro registrador, o NIF
entra em dois sítios com propósitos diferentes: no registo do domínio é o **titular legal**
(para `.pt` a DNS.pt exige NIF/NIPC válido; para `.com` não é obrigatório mas convém preencher
para o domínio ficar em nome da empresa); na hospedagem (Vercel/Render/Railway/Cloudflare) é só
para a **fatura** (billing/tax info nas settings de conta), sem relação com a titularidade de nada.

### Variáveis a preencher (produção real)

Nenhuma tem valor de produção no repo, de propósito — ver [[environment]] para a lista completa
com os defaults de dev. As que mudam mesmo:

```
COOKIE_SECURE=true                          # false só em dev
COOKIE_DOMAIN=<domínio real>
CORS_ALLOWED_ORIGINS=<domínio real>
APP_FRONTEND_URL=<domínio real>
SUPABASE_SERVICE_ROLE_KEY, DB_PASS, SUPABASE_JWT_SECRET, APP_EMAIL_CRYPTO_KEY
                                             # pelo mecanismo de secrets da hospedagem, nunca em ficheiro versionado
```

O histórico do git já foi confirmado limpo (nenhum `.env` alguma vez commitado, `.gitignore` cobre
`.env`/`.env.local`/`.env.*`) — 2026-09-22.

### Verificado a 2026-09-22, antes de haver hospedagem

- **`GET /actuator/health` dava 404** — o `pom.xml` dependia de `spring-boot-actuator` (só o
  núcleo) em vez de `spring-boot-starter-actuator` (traz a auto-configuração que regista o
  endpoint). Corrigido; confirmado ao vivo: `200 {"status":"UP"}`. Quase toda a hospedagem exige um
  health-check real — sem isto, o deploy falhava logo à primeira verificação da plataforma.
- **Bucket `documents` do Supabase Storage é privado** — confirmado de forma definitiva pela
  própria API de Storage (`GET /storage/v1/bucket/documents` com a service role key →
  `"public": false`), não só por inferência do comportamento do endpoint público.
- **Headers de segurança HTTP** — `X-Frame-Options`, `X-Content-Type-Options` e CSP confirmados
  por `curl -I` contra o backend real; HSTS já ativo por omissão (só visível sobre HTTPS real).
- **Rate limiting** (login, forgot-password, exportações do orçamento) testado ao vivo com JWT
  real — ver `notes/verificacao-browser-pendente.md` §19.

### Só testável depois de decidir a hospedagem

- **CORS** com o domínio real (hoje só testado com origens de dev).
- **`server.forward-headers-strategy`**, se houver TLS-termination num proxy à frente.
- **Comportamento sob carga concorrente** do `/export/zip` (streaming de documentos) — precisa de
  produção ou staging reais para fazer sentido; `ab`/`hey` concorrente contra o endpoint, monitorizar
  heap/threads.
- Um **provedor SMTP real** configurado em *Definições → Provedores de email* — bloqueia convites
  de funcionários e recuperação de password até existir; não é do roadmap de segurança, mas é
  necessário para produção a sério.

## Cópia de segurança

### O que há para guardar

| O quê | Onde vive | Quem o gere | Como se guarda |
|---|---|---|---|
| Tabelas da app | schemas `worksite`, `settings`, `tasks` | Flyway (`V1`…) | `pg_dump` (abaixo) |
| Utilizadores de autenticação | `auth.users` | Supabase | `pg_dump` do schema `auth`, ou os backups do Supabase |
| Ficheiros de faturas, miniaturas e provas de pagamento | bucket `documents` | Storage | `scripts/backup-storage.mjs` |
| Banners, fotos e vídeos das obras | bucket `media` | Storage | `scripts/backup-storage.mjs --bucket media` |
| Metadados dos ficheiros | `storage.objects` | Supabase | reconstrói-se ao fazer o restore do bucket |

`profile.auth_user_id` aponta para `auth.users` (`V9`), por isso um restore só dos nossos schemas
deixa os perfis a apontar para utilizadores que podem não existir — ver "Repor" abaixo. O bucket
`private` já não é referenciado por nenhuma tabela desde a `V35` (as fotos de perfil saíram).

### Base de dados — `pg_dump`

> ⚠️ **Por testar nesta máquina** (2026-09-19): não há `pg_dump` instalado e o instalador
> (`winget install PostgreSQL.PostgreSQL.17`) precisa de aceitar um pedido de UAC, o que ficou
> para depois. O comando abaixo é o padrão para Supabase; confirmar na primeira execução real e
> apagar este aviso.

Ligar pelo **session pooler (porta `5432`)** ou pela ligação direta — nunca pelo pooler em modo
transaction (`6543`) que o backend usa: o `pg_dump` precisa de uma sessão estável e de prepared
statements, que esse modo não suporta. Host e utilizador são os mesmos do `DB_URL`/`DB_USER`.

```bash
cd management/managementapi
mkdir -p backups/db

pg_dump "postgresql://postgres.<project-ref>:<DB_PASS>@<pooler-host>:5432/postgres?sslmode=require" \
  --format=custom --no-owner --no-privileges \
  --schema=worksite --schema=settings --schema=tasks \
  --file="backups/db/worksite-$(date +%F).dump"

# Utilizadores de autenticação, à parte (só dados — o schema é do Supabase)
pg_dump "postgresql://postgres.<project-ref>:<DB_PASS>@<pooler-host>:5432/postgres?sslmode=require" \
  --format=custom --data-only --schema=auth \
  --file="backups/db/auth-$(date +%F).dump"
```

- `--no-owner --no-privileges`: os roles do Supabase não existem fora dele, e as permissões que
  interessam estão nas migrações (`V8`, `V37`) — o restore volta a passá-las.
- `--format=custom` permite repor uma tabela só (`pg_restore --table=`), o que é o caso comum.
- A tabela `worksite.flyway_schema_history` vai no dump. É de propósito: um restore repõe o
  schema **e** o registo de que migrações lá estão, coerentes um com o outro.
- `backups/` está no `.gitignore` do backend. Um dump contém dados reais (NIFs, emails, passwords
  SMTP cifradas) — guardar fora do repo e fora de pastas sincronizadas com terceiros.

O Supabase faz os seus próprios backups diários no plano Pro (Dashboard → *Database → Backups*).
Ver lá qual é o plano deste projeto; no plano Free não há nenhum, e este procedimento é a única
cópia.

### Storage — `scripts/backup-storage.mjs`

Testado a 2026-09-19: 460 ficheiros, 82,8 MB, tamanhos conferidos contra o `manifest.json`.

```bash
cd management/managementapi
node scripts/backup-storage.mjs                 # bucket documents
node scripts/backup-storage.mjs --all           # todos os buckets
node scripts/backup-storage.mjs --out D:\cópias # outra pasta destino
```

Fica em `backups/storage/<data-hora>/<bucket>/<chave>` — a chave do Storage vira caminho no
disco, sem renomear nada, e ao lado um `manifest.json` com bucket, chave, tamanho e `updated_at`
de cada objeto. Precisa de `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` no `.env` e de Node 18+;
não tem dependências. Só lê.

### Quando

Antes de qualquer coisa que não se desfaz com um `git revert`:

- uma migração Flyway nova entrar (ou seja, **antes** do primeiro `mvn compile` com o ficheiro
  em `src/main/resources/db/migration/` — o devtools aplica-a nesse momento);
- os scripts de purga (`scripts/purge-*.mjs`, `purge-*.sql`);
- uma importação em massa do Excel com `dryRun=false` ([[excel-parity]] §9);
- uma rotação de chave (abaixo);
- e, sem motivo, de tempos a tempos — os ficheiros de faturas só existem no bucket.

## Repor

### Base de dados, no mesmo projeto

Uma tabela só (o caso normal — uma purga que apagou de mais, uma importação que gravou o que
não devia):

```bash
pg_restore --dbname="postgresql://postgres.<project-ref>:<DB_PASS>@<pooler-host>:5432/postgres?sslmode=require" \
  --no-owner --no-privileges --data-only \
  --table=construction_invoice backups/db/worksite-AAAA-MM-DD.dump
```

`--data-only` numa tabela cheia **duplica** as linhas ou falha nas PKs — esvaziar primeiro
(`truncate worksite.construction_invoice cascade` no SQL editor, com atenção ao que o `cascade`
leva atrás: ver `scripts/purge-invoices.sql` passo 1).

O schema inteiro:

```bash
pg_restore --dbname="…:5432/postgres?sslmode=require" \
  --no-owner --no-privileges --clean --if-exists \
  --schema=worksite --schema=settings --schema=tasks \
  backups/db/worksite-AAAA-MM-DD.dump
```

Com o backend **parado**. Depois arrancar: se o dump for anterior às migrações mais recentes, o
Flyway aplica as que faltam por cima — é o comportamento certo, e é por isso que a
`flyway_schema_history` vai no dump.

### Base de dados, num projeto Supabase novo

1. Criar o projeto; pôr `DB_URL`, `DB_USER`, `DB_PASS`, `SUPABASE_*` no `.env`.
2. **Não arrancar o backend ainda** — o Flyway criaria os schemas vazios e o restore chocaria com
   eles.
3. `pg_restore` do dump `auth-*.dump` primeiro (para os `auth_user_id` existirem), depois do
   `worksite-*.dump` inteiro (comando acima, sem `--clean`).
4. Arrancar o backend. Se os utilizadores de autenticação não puderam ser repostos, criá-los no
   Dashboard e ligar cada um ao seu `profile` com
   `scripts/create-profile-for-existing-auth-user.sql`.
5. Repor o Storage (abaixo) — as tabelas já apontam para as chaves certas.

### Storage — `scripts/restore-storage.mjs`

Testado a 2026-09-19 (um ficheiro sob `restore-test/`, verificado e apagado a seguir).

```bash
node scripts/restore-storage.mjs --from backups/storage/2026-09-19T03-18-20            # simulação
node scripts/restore-storage.mjs --from backups/storage/2026-09-19T03-18-20 --yes
node scripts/restore-storage.mjs --from <pasta> --bucket documents --prefix construction-invoices/<enterpriseId>/ --yes
```

Envia cada ficheiro do `manifest.json` para o mesmo bucket e a mesma chave — as tabelas
(`construction_invoice_document.bucket` + `storage_key` + `thumbnail_key`,
`payment.proof_bucket` + `proof_key`, `enterprises_media`) apontam por chave, por isso ela tem de
ficar igual ao byte. Um objeto que já exista é substituído. Se o `.env` apontar para um projeto
diferente do que fez o backup, o script avisa mas continua: é o caso legítimo do restore num
projeto novo. Os buckets têm de existir antes (a app assume-os; criar no Dashboard com os mesmos
nomes, privados).

## Uma migração que correu mal

O Flyway está com `clean-disabled: true` e sem *undo* (`application.yml`). Três casos:

**Falhou a meio.** O DDL do Postgres é transacional, por isso a base de dados ficou como estava;
mas o Flyway grava a tentativa em `worksite.flyway_schema_history` com `success = false` e recusa
arrancar até isso sair. Corrigir o SQL e, no SQL editor:

```sql
delete from worksite.flyway_schema_history where version = '37' and success = false;
```

Arrancar outra vez; a migração corrigida aplica-se.

**Aplicou-se, mas estava errada.** Nunca editar um `V<n>` já aplicado — o checksum deixa de bater
e o backend não arranca. Escrever `V<n+1>` que compensa (`alter table … drop column`, `update …`),
como se fez na `V25` a seguir à `V24`. Se apagou dados, repor a tabela do dump (acima) **antes** da
compensatória, ou o `V<n+1>` corre sobre o buraco.

**Editaste um `V<n>` já aplicado** (checksum mismatch no arranque). Reverter o ficheiro (`git
checkout`). Se a edição tem mesmo de ficar (um comentário, um typo sem efeito), o log diz o
checksum esperado e o novo — atualizar à mão:

```sql
update worksite.flyway_schema_history set checksum = <novo> where version = '<n>';
```

Em qualquer dos casos, o que evita a maioria: **escrever a migração fora de
`src/main/resources/db/migration/`** (ou com o backend parado) até estar revista, porque com o
devtools ligado ela entra no primeiro `mvn compile`; e tirar o dump antes.

## Rodar a chave de cifra da password SMTP

A password de `settings.email_providers` está cifrada com `APP_EMAIL_CRYPTO_KEY` (AES-256-GCM,
detalhe em [[security]] → "Segredos em repouso"). Para trocar a chave sem perder as passwords:

1. `APP_EMAIL_CRYPTO_KEY_PREVIOUS` = a chave atual; `APP_EMAIL_CRYPTO_KEY` = a chave nova
   (`openssl rand -base64 32`).
2. Reiniciar o backend. O `EmailProviderPasswordReEncryptRunner` decifra cada password com a
   chave anterior e re-grava-a cifrada com a nova.
3. Confirmar nos logs (`SMTP: N password(s) ... re-cifradas`) e enviar um email de teste em
   *Definições → Provedores de email*.
4. Remover `APP_EMAIL_CRYPTO_KEY_PREVIOUS` e reiniciar.

Se se perder a chave sem ter a anterior, as passwords não se recuperam — apagar os provedores e
criá-los de novo no Backoffice.

## Relacionado

- `notes/roadmap/pre-deploy-security.md` — o roadmap completo de segurança/performance pré-deploy (3 níveis), com o que já está feito e testado
- [[environment]] — as variáveis que apontam para produção
- [[commands]] — o que arranca o backend (e, com isso, toca a base de dados real)
- [[security]] — quem liga à base de dados com que role; a cifra em repouso
- [[database]] — o schema que o dump guarda; as migrações
- [[excel-parity]] §9 — a salvaguarda paralela no Excel, e a importação em massa
- `management/managementapi/scripts/README.md` — os scripts de purga, o outro lado destes
