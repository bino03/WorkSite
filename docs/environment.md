# 🔐 Variáveis de ambiente

Nenhum destes ficheiros está no git. Num clone novo há que criá-los.

## Backend — `.env` em `management/managementapi/`

```
DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require&preferQueryMode=simple&prepareThreshold=0
DB_USER=postgres.<project-ref>
DB_PASS=<password>
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service key>
SUPABASE_ANON_KEY=<anon key>
SUPABASE_JWT_SECRET=<jwt secret>
COOKIE_SECURE=false
COOKIE_DOMAIN=localhost
APP_FRONTEND_URL=http://localhost:5173
```

Template completo em `management/managementapi/.env.example`.

Notas que se pagam caro por não se saberem:

- Os parâmetros `preferQueryMode=simple&prepareThreshold=0` no `DB_URL` **não são decorativos** —
  a ligação é feita através do pooler do Supabase em modo transaction, que não suporta prepared
  statements.
- O `SUPABASE_JWT_SECRET` é a chave HS256 com que o backend **valida** os tokens emitidos pelo
  Supabase. Sem ele, toda a autenticação falha. Ver [[security]].
- O `APP_FRONTEND_URL` é a base dos links que saem nos emails (convite, recuperação de
  password). O default é `http://localhost:5173`; em produção tem de apontar para o domínio
  real do Backoffice, caso contrário os links chegam a apontar para localhost.
- A configuração de SMTP **não vive aqui** — está na tabela `settings.email_providers`, gerida no
  Backoffice em *Definições → Provedores de email* (`ADMIN`). Sem um provedor predefinido e ativo
  lá, o convite de funcionário e a recuperação de password falham com `EMAIL_002`.

## Backoffice — `.env` em `management/managementfrontend/apps/backoffice/`

```
VITE_API_URL=http://localhost:8080
VITE_GOOGLE_MAPS_API_KEY=          # opcional — só o seletor de localização no mapa precisa
```

Não há chaves do Supabase no frontend, de propósito: o Backoffice **não usa o SDK do Supabase**.
Fala só com o backend, e o JWT viaja em cookies HttpOnly. Ver [[security]].

## Relacionado

- [[commands]] — o que correr depois de ter isto preenchido
- [[security]] — como o JWT é validado e onde vivem os cookies
- [[database]] — o schema a que o `DB_URL` liga
