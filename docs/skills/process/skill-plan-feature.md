# Skill: Planear uma feature

**When to use**: Quando uma funcionalidade vai levar **mais do que uma sessão** e atravessa camadas (migração → entidade → endpoint → Backoffice) — o caso do [[skill-refine-idea]] "grande demais para uma tarefa". Para algo de uma sessão, usa o `/refine-idea` e implementa.

**Time**: ~30-60 min de entrevista + escrita. **Não escreve código.**

**Produz**: `notes/roadmap/<slug>.md` (a spec) + uma secção no [[notes/ToDo.md]] com um bullet por fase, a apontar para ela + uma linha em `notes/roadmap/backlog.md`. O `/implement-todo` executa a partir daí, sem modo especial.

> 📐 O contexto morre entre sessões. A spec é o artefacto durável — **escrita para alguém que nunca
> viu esta conversa**. O molde é [[roadmap/assiduidade]]: decisões com o porquê, o que fica de fora,
> modelo de dados, fases pela ordem das dependências.

> ℹ️ Onde fica cada coisa, para não haver duas cópias:
> - **O desenho e o porquê** → a spec, em `notes/roadmap/<slug>.md`
> - **O que fazer a seguir** → os bullets no `ToDo.md`
> - **Em que pé está a execução** → o plano do `/implement-todo` em `notes/roadmap/plans/` e a linha do `backlog.md`
>
> A spec não tem secção "estado atual" — isso é o trabalho do plano, que já tem checkpoints e retoma.

> ℹ️ Nota sobre `AskUserQuestion`: a opção "Outro" é oferecida automaticamente — nunca a acrescentes como opção explícita.

---

## Step 0: Dimensionar — precisa mesmo de spec?

| Dimensão | O que fazer |
|---|---|
| Uma sessão, um ou dois ficheiros | **Sem spec.** `/refine-idea` → bullet no ToDo → `/implement-todo` |
| Várias sessões, atravessa camadas (BD → API → Backoffice) | **Spec.** Continuar |
| Não se sabe | Fazer as perguntas do Step 2 e decidir depois |

"Não precisa de spec" é um resultado válido e frequente. Uma spec de 300 linhas para uma feature de
uma hora custa mais do que a feature e apodrece.

## Step 1: Contexto antes de perguntar

- [ ] [[00-INDEX]], [[docs/architecture]] e [[docs/code-map]] — onde vive o que já existe
- [ ] [[docs/database]] e [[docs/api]] — tabelas e rotas que a feature vai tocar ou imitar
- [ ] `notes/ToDo.md`, `notes/ideas.md`, `notes/roadmap/backlog.md` — já lá está? relaciona-se? (uma entrada `> Contexto (via …)` em `ideas.md` é investigação já feita)
- [ ] `notes/roadmap/*.md` — há spec de uma frente relacionada?
- [ ] `notes/whatIveDone.md` — grep pelo tema: o porquê de como as coisas ficaram

Se já há código na área, investigar em paralelo (agentes `Explore`, um por área, numa só mensagem).
Devolvem conclusões com `file:line`, não ficheiros — o contexto principal fica barato.

## Step 2: A entrevista

Sete secções obrigatórias; as perguntas dentro de cada uma adaptam-se. **Em bloco**, com **defaults
propostos**, sem perguntar o que já está decidido. **"Não sei" é uma resposta válida** — fica na
secção "Em aberto", não se inventa. A skill sugere hipóteses, mas **não inventa requisitos de negócio**.

1. **Problema e valor** — o que se quer conseguir que hoje não se consegue? Como se sabe que está bem feito (o momento de uso, por quem)?
2. **Âmbito — sobretudo o que fica de fora.** Versão mínima? O que é tentador mas fica para depois? Segunda fase óbvia? *(A lista do que fica de fora é a secção mais útil da spec.)*
3. **Dados** — o que se guarda de novo? Relações a `worksite.profile`, `enterprise`, `construction_*`? Histórico ou só estado atual? Domínio novo com schema próprio (como `tasks` e `attendance`) ou dentro de `worksite`?
4. **API** — que operações? Trabalho em background? Integrações (Supabase Storage, email, QR)?
5. **Interface** — onde vive no Backoffice? Página ou `Drawer` (o padrão do projeto é `Drawer` para criar/ver/editar)? Como se chega lá (nav, menu de contexto)? Estado vazio?
6. **Erros e casos-limite** — o que pode correr mal? que códigos novos em `ErrorCode.java`? validação (Zod no Backoffice, Bean Validation no backend)?
7. **Permissões** — quem pode o quê (ADMIN, o próprio funcionário, outros roles)? Ver [[docs/security]].

**Paridade com o Excel — obrigatória se a feature toca `construction_*`, `supplier` ou `payment`.**
Rever [[docs/excel-parity]] e perguntar: isto continua a poder trocar dados com o Excel da Vilatro sem
conversão? Se o Excel tiver de mudar, a decisão escreve-se em `Vilatro\Decisões.md` e o pedido em
`Vilatro\Início.md` → "Por fazer" — não aqui (ver o `CLAUDE.md`).

**Resposta estrutural** (novo tipo de dado central, superfície nova para o funcionário, nova integração,
mudança no modelo de autenticação) → não se decide calada dentro da spec: vai para
[[docs/architecture]] (ou um `docs/<tema>-modelo-alvo.md`, como o [[docs/faturas-modelo-alvo]], se o
desenho é grande e versionado) e a spec aponta para lá.

## Step 3: Desenhar

Antes de partir em fases, concretizar:

- **Tabelas e colunas** — SQL da migração `V<N>` seguinte (`management/managementapi/src/main/resources/db/migration/`)
- **Endpoints** — método + caminho + quem acede
- **DTOs** — campos
- **Backoffice** — páginas, componentes e ficheiros em `management/managementfrontend/apps/backoffice/src/` (`pages/`, `components/<domínio>/`, `services/`, `types/`)
- **Códigos de erro** novos e a mensagem no frontend

**Se algo não é evidente, voltar a perguntar** — um desenho vago dá uma spec vaga, e uma spec vaga não
sobrevive à perda de contexto.

## Step 4: Partir em fases

Ordem pela cadeia de dependências do Worksite: `migração → entidade/repository → service/controller → integration guide → Backoffice`.
Cada fase **entra em uso sozinha** (como as fases do [[roadmap/assiduidade]]) e tem, sem exceção:

| Campo | Regra |
|---|---|
| **Ficheiros concretos** | `management/managementapi/src/main/java/com/management/managementapi/<domínio>/controller/XController.java`, não "o controller" |
| **Skill a seguir** | `add-database-table`, `add-backend-feature`, `add-file-upload`, `permissions-and-auth`, `frontend-integration-guide`, `frontend-design-system`, `frontend-error-handling` — a que cobre a fase |
| **Critério de aceitação** | Verificável: um teste que passa (`./mvnw test -Dtest=…`) e/ou um comportamento a ver no browser (vai para `/verify-in-browser`). Uma fase sem isto fica "quase feita" para sempre |

Fases **sequenciais** — tocam nos mesmos ficheiros. Paralelismo só na investigação.

## Step 5: Escrever a spec

Criar `notes/roadmap/<slug-kebab>.md` com esta forma:

```markdown
# <emoji> Roadmap — <Nome da feature>

**Objetivo**: <2-4 frases. O problema, não a solução.>
**Como sei que está bem feito**: <o momento concreto de uso>

> 📌 Desenhado via `/plan-feature` a AAAA-MM-DD. <Que entradas de ideas/ToDo/backlog substitui ou funde.>
> O ToDo tem as fases como bullets; **o porquê de cada decisão vive aqui**.

**Fora de âmbito, por decisão do utilizador (AAAA-MM-DD)**: <lista — e porquê, numa frase cada>

---

## O ponto de partida
<o que existe hoje no código e na BD, com migrações e ficheiros — o que se reaproveita e o molde a seguir>

## As decisões que moldam tudo (AAAA-MM-DD)
| Decisão | Porquê |
|---|---|
| **<escolha>** | <a razão, e a alternativa rejeitada> |

## Modelo de dados
<SQL ou tabela de colunas da(s) migração(ões); relação às tabelas existentes>

## API
| Método | Rota | Acesso | Descrição |
|---|---|---|---|

## Backoffice
- **Onde vive** · **Como se chega lá** · **Ficheiros** · **Estado vazio** · **Visibilidade por role**

## Códigos de erro novos
| Código | HTTP | Mensagem no frontend |
|---|---|---|

## As fases, na ordem das dependências

### Fase 1 — <o que passa a ser possível>
- **Ficheiros**: `<caminhos concretos>`
- **Skills**: `<skill>`
- **Aceite quando**: <critério verificável>

## Em aberto (não é fase)
| Pergunta | Bloqueia | Notas |
|---|---|---|

## Relacionado
[[backlog]] · [[../ToDo]] · <docs tocados: [[database]], [[api]], [[excel-parity]]…>
```

**Teste**: *uma sessão nova, sem nada desta conversa, consegue começar a fase 1 sem perguntar?*

- [ ] Decisões registadas **com o porquê** e a alternativa rejeitada
- [ ] "Fora de âmbito" preenchido
- [ ] Cada fase com ficheiros, skill e critério
- [ ] Perguntas sem resposta em "Em aberto", não esquecidas
- [ ] Paridade com o Excel revista, se aplicável

## Step 6: Ligar ao resto

- [ ] **`notes/ToDo.md`** — uma secção própria (como "Assiduidade & Férias"): um parágrafo a apontar para a spec com **"ler antes de implementar qualquer fase"**, e **um bullet por fase**, na ordem, cada um com o critério de aceitação:
  ```markdown
  - [ ] Fase 1 — <título> — <critério de aceitação numa frase> · [[roadmap/<slug>]] → "Fase 1"
  ```
- [ ] **`notes/roadmap/backlog.md`** — linha da iniciativa em "Em curso" ou "Por começar" (estado + data + o que a desbloqueia)
- [ ] **Entradas absorvidas** — se a spec funde bullets do ToDo ou entradas de `ideas.md`, retirá-las (edição pontual) e dizê-lo no `📌` da spec
- [ ] **Decisões estruturais** → [[docs/architecture]] (ou `docs/<tema>-modelo-alvo.md`), escritas agora, não depois
- [ ] **Commit** — só se o Step 6 tocou em `docs/` (a spec e o ToDo vivem em `notes/`, que é git-ignored): `docs: …` na branch `dev`, seguindo [[skill-git-commits]]. Nunca `git push` sem perguntar

## Step 7: Entregar (e parar)

Três linhas: onde ficou a spec; quantas fases e o que cada uma entrega; como arrancar
(`/implement-todo` → "Só um tema" → a secção nova). **Não começar a implementar.**

---

## Erros comuns

| Erro | Consequência |
|---|---|
| Spec para uma feature de uma sessão | Custa mais que a feature e apodrece |
| Saltar "o que fica de fora" | Sessões futuras expandem o âmbito sozinhas |
| Fases sem critério de aceitação | Ficam "quase feitas" |
| Fases sem ficheiro concreto | A sessão seguinte redescobre onde mexer |
| Decisão estrutural só dentro da spec | A spec é git-ignored; o porquê perde-se e volta a discutir-se |
| Estado de execução escrito na spec | Duas cópias do progresso (spec + plano) — divergem; o estado é do plano e do `backlog.md` |
| Esquecer a paridade com o Excel | Uma migração em `construction_*`/`supplier`/`payment` parte a troca de dados com a Vilatro |
| Spec que assume esta conversa | Uma sessão nova não a consegue seguir |

---

## Final Checklist

- [ ] Dimensionado — confirmado que precisa de spec (ou encaminhado para `/refine-idea`)
- [ ] Vault e código lidos antes de perguntar; investigação em paralelo se havia código
- [ ] Sete secções da entrevista cobertas, em bloco, com defaults; paridade com o Excel se aplicável
- [ ] Desenho concreto (SQL, rotas, DTOs, ficheiros do Backoffice, códigos de erro)
- [ ] `notes/roadmap/<slug>.md` escrita e passa o teste da sessão nova
- [ ] Secção no `ToDo.md` com um bullet por fase + linha no `backlog.md`
- [ ] Decisões estruturais em `docs/`; commit na `dev` se `docs/` mudou
- [ ] Entrega em três linhas, sem implementar

---

## Related Skills

- [[skill-refine-idea]] — Para ideias que cabem numa sessão; encaminha para aqui as "grandes demais"
- [[skill-implement-todo]] — Executa as fases a partir dos bullets do ToDo, com plano e retoma
- [[skill-add-database-table]] · [[skill-add-backend-feature]] · [[skill-permissions-and-auth]] · [[skill-frontend-integration-guide]] · [[skill-frontend-design-system]] — As skills que as fases citam
- [[skill-git-commits]] — Commit no fim, na branch `dev`
