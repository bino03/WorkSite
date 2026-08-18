# CLAUDE.md — Worksite

> **Toda a documentação deste projeto vive no vault Obsidian: `docs/` e `notes/`.**
> Este ficheiro é um ponteiro, não um sítio para documentar.

## Antes de responder: ler o vault

Perante qualquer pedido sobre este projeto, **o primeiro passo é consultar o vault** — não
responder de memória nem só a partir do código:

1. **[[00-INDEX]]** para navegar.
2. O ficheiro do `docs/` que corresponde à pergunta (ver a tabela de atalhos abaixo).
3. **[[notes/ToDo]]** e **[[notes/whatIveDone]]** quando a pergunta é sobre o que falta ou o que
   já foi feito — o work log guarda *porque* é que as coisas ficaram como estão, que é o que o
   código não diz.

O código é a verdade sobre o que a aplicação faz **hoje**; o vault é a verdade sobre o que foi
decidido e porquê. Uma resposta que ignore o segundo repete decisões já tomadas.

Ao terminar um trabalho, o resultado escreve-se no vault (`docs/` para factos,
`notes/whatIveDone.md` para o que foi feito e porquê) — nunca num `CLAUDE.md`.

## Regra

**Não escrever documentação aqui nem em nenhum outro `CLAUDE.md`.** Se um facto sobre o projeto
precisa de ficar registado, o sítio é `docs/`. Um `CLAUDE.md` só pode conter: para onde ir, e
convenções sobre *como trabalhar* neste repo.

O motivo não é estético. Havia duas cópias da tabela de rotas do Backoffice — uma aqui, outra em
`docs/` — e a do `docs/` ficou a listar três páginas apagadas na `V15` durante meses. Duas cópias
do mesmo facto divergem sempre, e nunca se sabe qual é a boa. O `.githooks/pre-commit` só vigia o
`docs/`, o que torna qualquer cópia fora de lá ainda mais frágil.

## Começar aqui

**[[00-INDEX]]** — o índice do vault. Tudo se alcança a partir de lá.

## Atalhos, por pergunta

| A pergunta | O ficheiro |
|---|---|
| Onde vive o código disto? | [[docs/code-map]] |
| Como arranco, testo, faço build? | [[docs/commands]] |
| Que variáveis de ambiente preciso? | [[docs/environment]] |
| Que rotas tem a API? | [[docs/api]] |
| Como é o schema da base de dados? | [[docs/database]] |
| Autenticação, roles, CORS? | [[docs/security]] |
| Convenções e armadilhas do backend? | [[docs/backend-conventions]] |
| Convenções visuais do Backoffice? | [[docs/skills/references/frontend-visual-consistency]] |
| Porque é que a tabela se chama `enterprise`? | [[docs/provenance]] |
| Que skills existem? | [[docs/skills/SKILLS-INDEX]] |
| O que está por fazer? | [[notes/ToDo]] |
| O que já foi feito, e porquê? | [[notes/whatIveDone]] |

## O repositório

```
Worksite/                     ← o vault Obsidian é a raiz do repo
├── docs/                     ← documentação (fonte de verdade)
├── notes/                    ← backlog e notas pessoais (git-ignored)
└── management/               ← o código, repo único
    ├── managementapi/        ← Backend — Spring Boot 3.5, Java 21
    └── managementfrontend/
        └── apps/backoffice/  ← Backoffice — React 18, Vite, TypeScript
```

Não há portal público: é uma ferramenta interna. Detalhe em [[docs/architecture]].

## Fluxo de trabalho

`notes/ideas.md` → `notes/ToDo.md` → `notes/roadmap/plans/` → `notes/whatIveDone.md`.
A skill `implement-todo` (`/implement-todo`) percorre este ciclo. Ver [[notes/README]].
