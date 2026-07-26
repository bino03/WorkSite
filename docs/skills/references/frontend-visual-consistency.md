# Consistência Visual & Estrutural do Frontend (router)

**Não é uma skill** — não é invocada sozinha. É o ponto de entrada que qualquer skill lê antes de escrever ou editar UI, para decidir qual sub-ficheiro de [[design|docs/skills/references/design/]] é relevante. Ver [[code-best-practices]] para a distinção entre skill e referência.

> 📐 Ver também [[code-best-practices]] (regras gerais de código) e [[skill-frontend-design-system]] (estrutura de pastas e naming).

---

## Qual sub-ficheiro cobre o que vais construir?

Lê **só** o(s) sub-ficheiro(s) relevante(s) da área que estás a tocar — não os 5 de cada vez. Todos vivem em `docs/skills/references/design/` e aplicam-se ao Backoffice (`management/managementfrontend/apps/backoffice/`), a única aplicação frontend deste projeto.

| Vais construir/editar... | Lê |
|---|---|
| Cores, sombras, raios, paleta de uma entidade | [[design/backoffice-tokens-and-colors]] |
| Um card com header (formulário por secções, view/edit) | [[design/backoffice-cards]] |
| Um Drawer ou Modal | [[design/backoffice-drawers-and-modals]] |
| Uma tabela/lista (colunas, ações, estado, pesquisa, paginação) | [[design/backoffice-tables-and-lists]] |
| Botões, confirmação de ações destrutivas, ícones | [[design/backoffice-buttons-and-icons]] |

---

## Princípios

1. **Nunca hardcode um hex novo** se já existe um token (`theme.ts`/`index.css`) com esse valor ou perto dele.
2. **Nunca reimplementes um padrão visual que já existe como classe/componente partilhado** — antes de escrever CSS/estilo novo, verifica se uma classe (`.chip`, `.card`, `.btn-*`) ou componente (`SectionCard`, `Button`) já resolve o problema. Cada sub-ficheiro tem exemplos concretos de onde isto foi ignorado (paleta `D` copiada em várias listas) — não repetir esses casos.

## Skills relacionadas
- [[code-best-practices]] — Regras gerais de qualidade de código
- [[skill-frontend-design-system]] — Estrutura de pastas, naming, formulários
- [[skill-frontend-error-handling]] — Padrão de erros e notificações
- [[skill-frontend-integration-guide]] — Guia para ligar uma feature de backend ao frontend
