# ⌨️ Comandos

Tudo o que se corre neste projeto. Antes vivia espalhado por quatro `CLAUDE.md` diferentes,
com versões ligeiramente diferentes em cada um.

## Backend — `management/managementapi`

```bash
./mvnw spring-boot:run                # Arrancar (porta 8080)
./mvnw clean install                  # Build
./mvnw test                           # Todos os testes
./mvnw -Dtest=NomeDoTeste test        # Uma classe só
./mvnw package -DskipTests            # Empacotar JAR
```

> ⚠️ **`./mvnw test` sem `-Dtest=` dispara o `ManagementApiApplicationTests`, que levanta o
> contexto inteiro e escreve na base de dados real.** Ao correr testes durante o
> desenvolvimento, usar sempre `-Dtest=` com as classes que interessam.

> ⚠️ **Com o `spring-boot-devtools` a correr, uma migração Flyway nova aplica-se ao primeiro
> `mvn compile`**, não quando se decide arrancar a app: o devtools reinicia sozinho assim que o
> `target/classes` muda, e o Flyway corre nesse restart. Custou uma sessão a perceber (2026-08-18)
> — a `V20` entrou na base de dados real quase duas horas antes de alguém a mandar entrar.

Precisa das variáveis de ambiente — ver [[environment]].

## Backoffice — `management/managementfrontend/apps/backoffice`

```bash
npm install       # Instalar dependências
npm run dev       # Servidor de desenvolvimento (porta 5173)
npm run build     # Build de produção (corre `tsc -b` antes do Vite)
npm run lint      # ESLint
```

> ⚠️ **A porta importa.** O `SecurityConfig` só permite CORS de `localhost:5173` e `:5174`. Se a
> 5173 estiver ocupada, o Vite salta para a 5175 e **todas as chamadas à API são bloqueadas** —
> com sintomas que parecem de autenticação. Libertar a porta em vez de mudar de origem.

> ℹ️ `npm run build` falha hoje com 35 erros pré-existentes (`erasableSyntaxOnly` em
> `errors/error.types.ts`, e tipagens em `api.ts`, `AuthContext.tsx`, `ErrorBoundary.tsx` e dois
> ficheiros de `components/enterprise/`). Não são regressões — usar `npx tsc -b` e comparar o
> total antes de assumir que uma alteração partiu alguma coisa.

## Relacionado

- [[environment]] — as variáveis que o backend e o frontend precisam
- [[architecture]] — o que é cada um destes dois projetos
- [[../notes/README]] — o ciclo do backlog
