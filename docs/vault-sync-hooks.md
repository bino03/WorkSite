# Vault Sync — Pre-commit Reminder Hook

**O que é**: um git hook `pre-commit` que avisa (sem bloquear) quando um commit toca ficheiros que costumam exigir uma atualização correspondente no vault. Não é uma skill — é infraestrutura de repositório, git puro, sem envolver o Claude.

## Porque existe

Documentação viva desatualiza-se com o tempo se depender só de disciplina manual. Em vez de confiar em lembrar-me sempre de atualizar `docs/database.md` depois de uma migration, ou `docs/security.md` depois de mexer no `SecurityConfig.java`, o hook avisa automaticamente no momento exato em que a mudança acontece — sem bloquear o commit, só como lembrete.

## Onde vive

| Repo | Script | Config |
|---|---|---|
| Worksite (único repo — inclui `managementapi` + `managementfrontend`/Backoffice) | `.githooks/pre-commit` | `core.hooksPath = .githooks` |

> No projeto de origem (Property-Management) havia **dois** hooks, um por repo, porque o portal era um submódulo separado. O Worksite é um **único repositório git**, por isso há só um hook.

O script está **versionado** em `.githooks/` (não em `.git/hooks/`, que nunca é trackeado pelo git) precisamente para sobreviver a um clone novo — mas o git só o usa se `core.hooksPath` estiver configurado, e essa configuração em si **não é versionada** (vive em `.git/config`, local à máquina).

## Setup obrigatório num clone novo

```bash
git config core.hooksPath .githooks
```

Se um dia os avisos pararem de aparecer numa máquina nova, é a primeira coisa a verificar.

## O que o hook deteta

| Se o commit tocar... | Avisa para atualizar |
|---|---|
| Ficheiro em `managementapi/.../db/migration/` | `docs/database.md` — e verifica automaticamente se o `database.md` ainda menciona a versão de migração mais recente (aviso extra se a linha do intervalo de migrações estiver stale) |
| `managementapi/.../security/SecurityConfig.java` | `docs/security.md` (regras de auth, CORS, lista de endpoints públicos/protegidos) |
| Novo ficheiro de controller (`.../controller/*.java`) | `docs/api.md` (nova secção de endpoints) |
| Controller **existente** com anotações `@*Mapping` adicionadas/alteradas no diff staged | `docs/api.md` (rotas, parâmetros, regras de acesso) — apanha o caso mais comum de drift: endpoints novos num controller antigo |
| `dto/error/ErrorCode.java` | Espelhar códigos novos no mapa de erro do frontend ([[skill-frontend-error-handling]]) |
| `backoffice/src/theme.ts`, `index.css` ou `colors.css` | [[backoffice-tokens-and-colors]] |
| `backoffice/src/services/`, `errors/` ou `api.ts` | [[backoffice-services-and-error-handling]] |
| `backoffice/src/main.tsx`, `PrivateRoute.tsx`, `layouts/` ou `context/` | [[backoffice-app-shell-and-auth]] |
| Componente `*Form.tsx` / `*Drawer.tsx` / `*Modal.tsx` em `backoffice/src/components/` | [[backoffice-forms-and-validation]] + [[backoffice-drawers-and-modals]] |
| **Nova** página/serviço no Backoffice, ou novo controller/service no backend | [[code-map.md]] — se for uma porta de entrada nova (só ficheiros adicionados, não alterados: um componente a mais dentro de uma pasta que já existe não muda o mapa) |
| `apps/backoffice/package.json` com bump de `react`/`react-dom`/`typescript`/`vite`/`antd`/`tailwindcss` | Referências de stack no vault (`docs/architecture.md`, `00-INDEX.md`, ficheiros `CLAUDE.md`) |
| `managementapi/pom.xml` com bump de `java.version` ou `spring-boot.version` | Idem — referências de stack no vault |

## Comportamento

- **Nunca bloqueia o commit** — o script termina sempre com `exit 0`, mesmo quando avisa. É um lembrete, não uma validação.
- Só olha para ficheiros **staged** (`git diff --cached`) — editar um ficheiro sem o adicionar ao commit não dispara o aviso.
- Testar sem commitar de verdade: `git hook run pre-commit` (git ≥ 2.36) com algo staged, ou correr o script diretamente (`sh .githooks/pre-commit`).

## Estender

Se aparecer um novo padrão de drift recorrente (ex. um ficheiro que muda muitas vezes sem o doc correspondente ser atualizado), adicionar mais um bloco `grep` ao script — o padrão de cada bloco já existente serve de modelo.

## Skills relacionadas

- [[skill-git-commits]] — Onde este hook é mencionado no fluxo normal de commit
- [[skill-implement-todo]] — Fase 5.6: a documentação é atualizada **proativamente** ao implementar, em vez de reativamente quando o hook avisa
- [[code-best-practices]] — Checklist geral pré-commit
- [[skill-add-backend-feature]], [[skill-add-database-table]], [[skill-permissions-and-auth]], [[skill-frontend-design-system]] — Cada uma tem um item na checklist final a apontar para o doc que este hook também deteta
