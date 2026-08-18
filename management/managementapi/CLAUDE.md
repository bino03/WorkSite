# CLAUDE.md — Backend (`managementapi`)

> A documentação vive no vault: `docs/` e `notes/`. **Não documentar aqui.**
> Ver a regra em [[../../CLAUDE]].

Spring Boot 3.5, Java 21, PostgreSQL via Supabase, Flyway, MapStruct.

## Onde ir

| A pergunta | O ficheiro |
|---|---|
| Comandos (correr, testar, empacotar) | [[../../docs/commands]] |
| Variáveis de ambiente | [[../../docs/environment]] |
| Rotas, acessos, códigos de erro | [[../../docs/api]] |
| Schema, migrações, enums | [[../../docs/database]] |
| Auth, roles, CORS, filtros | [[../../docs/security]] |
| MapStruct, erros, compressão, QR, signed URLs | [[../../docs/backend-conventions]] |
| Camadas e desenho do sistema | [[../../docs/architecture]] |
| Onde vive o código de X | [[../../docs/code-map]] |

## Ao mexer aqui

- Feature nova → skill `add-backend-feature`. Tabela nova → `add-database-table`.
  Upload de ficheiros → `add-file-upload`. Controlo de acesso → `permissions-and-auth`.
- **`./mvnw test` sem `-Dtest=` escreve na base de dados real** — ver [[../../docs/commands]].
- Uma migração nova aplica-se ao primeiro `compile` se o devtools estiver a correr, não quando se
  arranca a app. Mesmo sítio.
