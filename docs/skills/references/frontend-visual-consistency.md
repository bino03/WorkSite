# Consistência Visual & Estrutural do Frontend (router)

**Não é uma skill** — não é invocada sozinha. É o ponto de entrada que qualquer skill lê antes de escrever ou editar UI, para decidir qual sub-ficheiro de [[design|docs/skills/references/design/]] é relevante. Ver [[code-best-practices]] para a distinção entre skill e referência.

> 📐 Ver também [[code-best-practices]] (regras gerais de código) e [[skill-frontend-design-system]] (estrutura de pastas e naming).

---

## Qual sub-ficheiro cobre o que vais construir?

Lê **só** o(s) sub-ficheiro(s) relevante(s) da área que estás a tocar — não os 8 de cada vez. Todos vivem em `docs/skills/references/design/` e aplicam-se ao Backoffice (`management/managementfrontend/apps/backoffice/`), a única aplicação frontend deste projeto.

| Vais construir/editar... | Lê |
|---|---|
| Cores, sombras, raios, paleta de uma entidade | [[design/backoffice-tokens-and-colors]] |
| Um card com header (formulário por secções, view/edit) | [[design/backoffice-cards]] |
| Um Drawer ou Modal | [[design/backoffice-drawers-and-modals]] |
| Uma tabela/lista (colunas, ações, estado, pesquisa, paginação) | [[design/backoffice-tables-and-lists]] |
| Botões, confirmação de ações destrutivas, ícones | [[design/backoffice-buttons-and-icons]] |
| Um formulário (campos, validação, submit, erros por campo) | [[design/backoffice-forms-and-validation]] |
| Uma chamada à API, service novo, tratamento de erro | [[design/backoffice-services-and-error-handling]] |
| Uma rota, item de menu, ou gate por role (`ADMIN`/`EMPLOYEE`) | [[design/backoffice-app-shell-and-auth]] |

---

## Princípios

1. **Nunca hardcode um hex novo** se já existe um token (`index.css` `--ind-*` / `theme.ts`) com esse valor ou perto dele.
2. **Nunca reimplementes um padrão visual que já existe como classe/componente partilhado** — antes de escrever CSS/estilo novo, verifica se uma classe (`.ind-card`, `.ind-tag`, `.ind-elev-*`) ou um componente partilhado (`ListActions`, `SectionCard`, `Label`) já resolve o problema.
3. **Nunca estilizes por manipulação imperativa do DOM** (`onMouseEnter` a escrever em `e.currentTarget.style`). Hover/foco vêm do tema AntD ou de CSS. Este padrão foi removido do código a 2026-08-05 — não voltar a introduzi-lo.

## Skills relacionadas
- [[code-best-practices]] — Regras gerais de qualidade de código
- [[skill-frontend-design-system]] — Estrutura de pastas, naming, formulários
- [[skill-frontend-error-handling]] — Padrão de erros e notificações
- [[skill-frontend-integration-guide]] — Guia para ligar uma feature de backend ao frontend
