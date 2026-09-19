# ⌨️ Comandos

Tudo o que se corre neste projeto. Antes vivia espalhado por quatro `CLAUDE.md` diferentes,
com versões ligeiramente diferentes em cada um.

## Backend — `management/managementapi`

```bash
./mvnw spring-boot:run                # Arrancar (porta 8080)
./mvnw clean install                  # Build
./mvnw test                           # Todos os testes (sem BD — ver "Testes")
./mvnw -Dtest=NomeDoTeste test        # Uma classe só
./mvnw package -DskipTests            # Empacotar JAR
```

> ⚠️ **Com o `spring-boot-devtools` a correr, uma migração Flyway nova aplica-se ao primeiro
> `mvn compile`**, não quando se decide arrancar a app: o devtools reinicia sozinho assim que o
> `target/classes` muda, e o Flyway corre nesse restart. Custou uma sessão a perceber (2026-08-18)
> — a `V20` entrou na base de dados real quase duas horas antes de alguém a mandar entrar.

Precisa das variáveis de ambiente — ver [[environment]].

### Testes

36 classes em `src/test`, em quatro grupos — o que importa é **quais ligam à base de dados**:

| Grupo | Classes | Precisa de | Corre em `./mvnw test`? |
|---|---|---|---|
| **Contexto Spring** — `ManagementApiApplicationTests` (`@SpringBootTest`, só `contextLoads`) | 1 | o `.env` inteiro: liga à **base de dados real** e o Flyway **aplica migrações pendentes** | **Não** — excluída no `pom.xml` (surefire). Só com `-Dtest=ManagementApiApplicationTests`, de propósito |
| **Mockito** — services, controllers e handlers com repositórios/clients mockados (`@ExtendWith(MockitoExtension)`) | 29 | nada | Sim |
| **Unit puro** — `AtInvoiceQrServiceTest`, `InvoiceThumbnailServiceTest`, `EncryptedStringConverterTest`, `SecretCipherTest` | 4 | nada (o QR lê ficheiros de `src/test/resources`) | Sim |
| **Probes** — `AtInvoiceQrProbeTest`, `AtInvoiceQrBatchProbeTest` | 2 | um ficheiro/pasta real por `-Dinvoice.file=` / `-Dinvoice.dir=`; sem isso ficam *skipped* | Aparecem como 2 skipped |

Não há testes de slice (`@WebMvcTest`, `@DataJpaTest`) nem Testcontainers: ninguém testa SQL nem
mapeamentos JPA — as migrações só se provam ao arrancar o backend ([[operations]] → "Migração má").

```bash
./mvnw test                                        # 35 classes, ~168 testes, ~30 s, sem BD
./mvnw -Dtest=PaymentServiceTest test              # uma classe
./mvnw -Dtest=ManagementApiApplicationTests test   # o contexto — só com intenção (ver acima)
./mvnw -Dtest=AtInvoiceQrProbeTest -Dinvoice.file=C:/caminho/fatura.pdf test   # probe de um PDF real
```

> Até 2026-09-19 o `./mvnw test` corria o contexto, e isso escrevia na base de dados real sem ninguém
> mandar (a mesma armadilha do devtools acima). A exclusão no `pom.xml` fecha esse caminho; o do
> `mvn compile` + devtools continua aberto.

**Backoffice: 0 testes.** Não há Vitest, Jest nem Testing Library no `package.json`; os únicos
guarda-costas são o `npx tsc -b` (ver abaixo) e a verificação no browser, feita em lote a partir de
[[../notes/verificacao-browser-pendente]].

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

> ℹ️ `npm run build` falha hoje com **26** erros pré-existentes (20 de `erasableSyntaxOnly` em
> `errors/error.types.ts`, e tipagens em `api.ts`, `AuthContext.tsx`, `ErrorBoundary.tsx` e
> `components/enterprise/CreateEnterpriseDrawer.tsx`). Não são regressões — usar `npx tsc -b` e
> comparar o total antes de assumir que uma alteração partiu alguma coisa.
>
> 🚨 **`npx tsc --noEmit` neste projeto não verifica nada.** O `tsconfig.json` da raiz do
> Backoffice é só um stub de referências (`tsconfig.app.json` + `tsconfig.node.json`), por isso o
> comando sai com 0 erros e 0 ficheiros analisados — parece limpo e não é. **O único type-check
> real é `npx tsc -b`.** Já deixou passar um import partido para o `master` (commit `162ae87`:
> `CreditNoteDrawer` a importar o `BudgetItemPickerModal` como default quando ele só tem named
> export — só rebentava em runtime, ao abrir a repartição de uma nota de crédito).

## Relacionado

- [[environment]] — as variáveis que o backend e o frontend precisam
- [[architecture]] — o que é cada um destes dois projetos
- [[../notes/README]] — o ciclo do backlog
