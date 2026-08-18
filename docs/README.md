# 📚 Documentation

Central documentation for the Worksite project (backend + backoffice).

## Structure

- **code-map.md** — Onde vive cada funcionalidade (domínio → ficheiros) + tabela "onde procurar, por sintoma". É por aqui que se começa quando a pergunta é *"onde está o código disto?"*
- **commands.md** — Como correr, testar e fazer build de cada projeto, com as armadilhas (testes que escrevem na BD real, portas e CORS, migrações que se aplicam sozinhas)
- **environment.md** — As variáveis de ambiente de backend e Backoffice, e o que acontece sem elas
- **backend-conventions.md** — Decisões do `managementapi` que não se deduzem do código: ordem dos annotation processors, tratamento de erros, bibliotecas de leitura de ficheiros, compressão, signed URLs
- **provenance.md** — O que veio do Property-Management e o que nasceu aqui. Explica nomes herdados (`enterprise`) e restos que não correspondem a funcionalidades reais
- **architecture.md** — System architecture overview
- **database.md** — Database schema and design
- **security.md** — Security & authorization
- **api.md** — API endpoint reference (rotas, métodos, regras de acesso por controller)
- **vault-sync-hooks.md** — O hook `pre-commit` que avisa quando um commit precisa de uma atualização de docs
- **skills/** — Everything related to invocable skills, organized by topic:
  - **[[SKILLS-INDEX]]** / **[[SKILLS-QUICK-REFERENCE]]** — Master index of all skills and references
  - **backend/** — 4 backend skills (`skill-add-backend-feature`, `skill-add-database-table`, `skill-add-file-upload`, `skill-permissions-and-auth`)
  - **frontend/** — 4 frontend skills (`skill-frontend-design-system`, `skill-frontend-error-handling`, `skill-frontend-integration-guide`, `skill-frontend-structure-brief`)
  - **process/** — 3 process/workflow skills (`skill-create-new-skill`, `skill-git-commits`, `skill-implement-todo`)
  - **references/** — Conventions docs read by skills, not invocable on their own: [[code-best-practices]], [[frontend-visual-consistency]], and **design/** (8 Backoffice sub-files with verified visual/structural conventions, auditadas contra o código real deste projeto — as sub-files específicas do Portal foram descartadas por este projeto não ter portal público)

## Keeping this in sync

O hook `.githooks/pre-commit` avisa (sem bloquear) quando o diff staged toca ficheiros que normalmente exigem atualizar um destes documentos. Num clone novo, ativa-o com:

```bash
git config core.hooksPath .githooks
```

Ver [[vault-sync-hooks]] para a lista completa do que é detetado.

## Onde NÃO documentar

Os ficheiros `CLAUDE.md` (raiz e subpastas) são **ponteiros**, não documentação. Um facto sobre
o projeto escrito num `CLAUDE.md` é uma segunda cópia que vai divergir — e já divergiu: a tabela
de rotas do Backoffice esteve certa no `CLAUDE.md` e errada aqui durante meses. Além disso, o
hook `pre-commit` só vigia o `docs/`.

## Related

- Backlog & notas pessoais: [[../notes/README.md]]
- Ponteiro da raiz: [[../CLAUDE.md]]
