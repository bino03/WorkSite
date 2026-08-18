# CLAUDE.md — Backoffice

> A documentação vive no vault: `docs/` e `notes/`. **Não documentar aqui.**
> Ver a regra em [[../../../../CLAUDE]].

Dashboard administrativo (`ADMIN`/`EMPLOYEE`). React 18, Vite 7, TypeScript 5.8 (strict),
Ant Design 5, Tailwind CSS 4. É a única app frontend do projeto.

## Onde ir

| A pergunta | O ficheiro |
|---|---|
| Comandos, portas, o build que já falha | [[../../../../docs/commands]] |
| `.env` | [[../../../../docs/environment]] |
| Onde vive o código de X, e "onde procurar por sintoma" | [[../../../../docs/code-map]] |
| Rotas do Backoffice, nav, gates de role | [[../../../../docs/skills/references/design/backoffice-app-shell-and-auth]] |
| Rotas da API que consome | [[../../../../docs/api]] |
| Autenticação e cookies | [[../../../../docs/security]] |
| **Todas as convenções visuais** (tokens, cards, drawers, tabelas, botões, formulários) | [[../../../../docs/skills/references/frontend-visual-consistency]] |
| `api.ts`, camada de serviços, `useApiCall`, paginação, erros | [[../../../../docs/skills/references/design/backoffice-services-and-error-handling]] |

## Ao mexer aqui

- Componente novo → skill `frontend-design-system`. Erros → `frontend-error-handling`.
  Antes de um redesign → `frontend-structure-brief`.
- **Nada de cores ou raios hardcoded**: os tokens `--ind-*` em `index.css` são a fonte de verdade,
  o `theme.ts` espelha-os para o Ant Design.
- **Ações destrutivas** passam por `useConfirm()`, nunca `Popconfirm`. E o `confirm()` tem valores
  por omissão de *eliminação* — numa ação que não elimina, passar `title` e `actionLabel`, senão o
  utilizador lê "Confirmar eliminação" ao marcar uma fatura como enviada. Já aconteceu.
