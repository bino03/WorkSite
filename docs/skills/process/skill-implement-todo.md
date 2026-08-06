# Skill: Implement ToDo

**When to use**: Quando quiseres avançar o backlog em `notes/ToDo.md` — priorizar o que lá está, gerar um plano, consultar o estado atual, retomar uma sessão anterior, ou implementar, reutilizando as skills já existentes do projeto.

**Time**: Varia por âmbito — desde ~1 min (só consultar estado) até várias horas (implementar o backlog todo).

> 📐 Este skill não escreve código diretamente — orquestra as skills que o fazem. Cada skill invocada na Fase 5 já lê [[code-best-practices]] e, quando aplicável, [[frontend-visual-consistency]] por conta própria; não precisas de repetir isso aqui.

> ℹ️ Nota sobre `AskUserQuestion` usado ao longo desta skill: a opção "Outro" é sempre oferecida automaticamente pela ferramenta — nunca a acrescentes como opção explícita, isso desperdiça uma das 4 vagas disponíveis por pergunta.

---

## Visão geral do fluxo

```
Fase 0  Preflight (ToDo.md + ideias com contexto + planos por retomar)
Fase 1  Menu principal → âmbito → orçamento da sessão   ← perguntas ao utilizador
Fase 2  Investigação (paralela, por tema)                ← subagentes Explore/general-purpose
Fase 3  Esclarecimento em bloco                           ← pergunta ao utilizador, tudo de uma vez
Fase 4  Priorização + confirmação + gravar plano          ← pergunta ao utilizador
Fase 5  Execução sequencial (com checkpoints)              ← implementa, uma tarefa de cada vez
Fase 6  Bookkeeping por tarefa                             ← remove do ToDo, regista em whatIveDone.md
Fase 7  Fecho                                               ← resumo + commit
```

Regra de ouro: **nunca avanças para a Fase 5 sem teres passado pelas Fases 1-4 com o utilizador** (exceto ao retomar um plano já confirmado numa sessão anterior — ver Fase 1). E o inverso também é regra: **o orçamento apertado de uma sessão nunca é desculpa para saltar um teste ou o bookkeeping de uma tarefa** — reduz quantas tarefas tentas fazer, nunca o rigor de cada uma.

---

## Fase 0: Preflight

Lê `notes/ToDo.md` **sempre no momento da invocação** — nunca assumas o conteúdo de uma execução anterior, o utilizador edita o ficheiro livremente entre execuções.

Faz também, a baixo custo (leituras/greps rápidos, sem subagentes — isto alimenta o menu da Fase 1):

1. **Contagem por tema**: quantos itens `- [ ]` existem em cada secção temática do `ToDo.md`. Se o ficheiro não tiver nenhum item (só a secção "How to Use"), guarda esse facto — afeta que opções fazem sentido no menu.
2. **Planos por retomar**: lista `notes/roadmap/plans/` (pode não existir ainda — nesse caso não há nada a retomar). Qualquer ficheiro com `**Estado**: em curso` é candidato a retoma.
3. **Ideias com contexto já recolhido**: grep rápido a `notes/ideas.md` por `> Contexto (via implement-todo`. Guarda quantas encontraste (não precisas de as ler todas agora, só confirmar que existem e quantas).
4. **Últimas entradas do work log**: as 1-3 entradas mais recentes de `notes/whatIveDone.md` (só os títulos `## AAAA-MM-DD — ...`, não o conteúdo todo).

---

## Fase 1: Menu principal, âmbito e orçamento da sessão

### 1.0 — Menu principal (primeira pergunta, sempre)

Pergunta via `AskUserQuestion` (single-select, opções dinâmicas consoante o que a Fase 0 encontrou — mínimo 3, máximo 4 opções reais):

- **"Retomar um plano anterior"** — só aparece se a Fase 0 encontrou pelo menos um plano `em curso`
- **"Implementar"** — trabalhar o backlog até ao código
- **"Só planear"** — gerar e gravar a ordem de prioridade, sem implementar nada agora
- **"Ver estado atual"** — resumo rápido, sem alterar nada

Se o ToDo estiver vazio E não houver plano por retomar, salta a pergunta: reporta isso diretamente e para aqui.

**Se "Retomar um plano anterior"**: lê o ficheiro de plano indicado (se houver mais do que um `em curso`, pergunta qual), mostra ao utilizador o resumo (âmbito, ordem confirmada, o que já está feito, "Próximo passo") e confirma que quer continuar dali. Se confirmado, **salta diretamente para a Fase 5** no ponto indicado por "Próximo passo" — não repitas Fases 2-4, os esclarecimentos e a ordem já estão gravados no ficheiro.

**Se "Ver estado atual"**: mostra, em texto simples (sem gastar mais subagentes):
- Contagem de itens por tema no `ToDo.md` (da Fase 0.1)
- Planos em `notes/roadmap/plans/` e o respetivo estado/próximo passo (da Fase 0.2)
- Se houver itens em `ideas.md` com contexto (Fase 0.3), lista-os um a um (texto verbatim + contexto) e pergunta, item a item ou em bloco, se algum já está pronto para voltar ao `ToDo.md` — se sim, move-o (mesma mecânica da Fase 6, edição pontual em ambos os ficheiros)
- As últimas entradas do work log (Fase 0.4), só para dar sentido de progresso recente

Depois de mostrar, pergunta em texto simples se o utilizador quer agora avançar para "Implementar" ou "Só planear" com base no que viu, ou terminar aqui. Não repitas o menu 1.0 como `AskUserQuestion` outra vez — é só uma pergunta de sim/não/qual.

Se "Implementar" ou "Só planear" (diretamente no menu 1.0, ou a partir de "Ver estado atual"), continua em 1.1.

### 1.1 — Âmbito

`AskUserQuestion` (single-select, 3 opções): **Tudo** · **Só um tema** · **Só um item específico**

**Se "Só um tema"**: segunda chamada `AskUserQuestion` com 2 perguntas multiSelect (o limite é 4 perguntas/4 opções por chamada, e os temas deste ToDo são mais do que 4 — divide-os em dois grupos). Os temas atuais de `notes/ToDo.md` são:
- Pergunta A (multiSelect, 4 opções): Projetos (Enterprises) · Construção & Despesas · Funcionários & Perfis · Tarefas
- Pergunta B (multiSelect, 4 opções): Assiduidade & Férias · Notificações · UI & Navegação · Por Clarificar

**Estes grupos não são fixos** — se o utilizador tiver criado/removido secções no `ToDo.md`, usa as secções que existirem realmente no ficheiro lido na Fase 0, não esta lista.

**Se "Só um item específico"**: imprime a lista atual do ToDo numerada (é texto que já tens em mãos, sem custo) e pergunta por intervalo(s) numérico(s) como opções (ex.: "#1-3" · "#4-6" · "#7-9 + soltos").

Guarda o resultado como a lista de bullets exatos (texto verbatim) em âmbito para as fases seguintes. Nada fora deste âmbito é tocado.

### 1.2 — Orçamento da sessão (só se "Implementar")

`AskUserQuestion` (single-select, 2 opções): **Sem limite (recomendado)** · **Pouco tempo/tokens — otimizar para fechar em segurança**

Se "Pouco tempo/tokens": isto não muda o que é investigado/perguntado (Fases 2-3 continuam completas — a ambiguidade não desaparece por haver pouco orçamento), mas muda a Fase 4 (ordenação enviesada para tarefas curtas primeiro, dentro do que as dependências permitirem) e a Fase 5 (parar entre tarefas em vez de a meio, ver Fase 5).

---

## Fase 2: Investigação (paralela por tema)

Agrupa os bullets em âmbito pelos seus temas (mesmo em modo "Tudo" — investiga por tema, não bullet a bullet, já que vários bullets do mesmo tema tocam as mesmas entidades). Para cada tema em âmbito, lança **um agente `Explore` ou `general-purpose` em paralelo** (uma única mensagem, várias chamadas do tool `Agent`) com este prompt fixo:

```
Tema: <nome do tema>
Bullets do ToDo (texto verbatim): <lista>

Investiga o código atual relevante a estes bullets (entidades, endpoints, DTOs,
páginas frontend, etc.) e devolve um relatório com exatamente estas secções:

### Findings
Código relevante já existente, com file:line.

### Files Likely Touched
Tabela: caminho | novo/modificado | porquê

### Applicable Skills
Quais destas skills existentes se aplicam a este trabalho:
add-backend-feature, add-database-table, add-file-upload, permissions-and-auth,
frontend-design-system, frontend-error-handling, frontend-integration-guide,
frontend-structure-brief

### Open Questions
O que ficou por resolver depois de olhares para o código (não inventes
requisitos) — cada pergunta com 2-4 hipóteses de resposta quando fizer sentido.

### Dependencies
Nomes verbatim de outros bullets do ToDo que têm de ser implementados ANTES
deste tema, se algum.

### Effort Estimate
S/M/L + 1 linha de justificação.
```

Isto mantém o contexto principal barato — quem orquestra nunca lê os ficheiros de código diretamente, só os relatórios estruturados que voltam.

**Sobre roadmaps técnicos por domínio**: ao contrário do projeto Property-Management de onde este fluxo veio, o Worksite **ainda não tem** roadmaps técnicos por domínio (`docs/security/roadmap.md`, `docs/migrations/roadmap.md`, `docs/tests/roadmap.md`, `docs/i18n/roadmap.md` — nenhum existe neste repo). Por isso não há secção `Known Gaps` no prompt acima. Se um dia forem criados, acrescenta ao prompt do subagente uma secção `### Known Gaps` e a regra de os consultar **só por grep dirigido por palavras-chave do tema** (nunca leitura integral), e só quando o tema tocar diretamente no domínio do roadmap — o custo fica confinado ao subagente desse tema, nunca ao contexto principal.

---

## Fase 3: Esclarecimento em bloco

Junta os `Open Questions` de todos os relatórios da Fase 2. Pergunta tudo de uma vez via `AskUserQuestion` — se couberem em 4 perguntas, uma chamada só; se ultrapassar, várias chamadas **agrupadas por tema**, mas sempre completas antes de avançar para a Fase 4.

Neste backlog há vários bullets escritos em nota rápida, que quase de certeza vão precisar de esclarecimento — por exemplo, quais os campos/rubricas de orçamento de uma construção (o utilizador referiu um Excel como fonte), como se calcula assiduidade/férias, ou o que conta como "entrada de funcionário". A Fase 2 pode sugerir candidatos se encontrar um modelo parcial no código, **mas não pode inventar requisitos de negócio** — pergunta.

**Se o utilizador não souber responder a uma pergunta** (a ideia ainda não amadureceu, não é só falta de investigação): oferece mover esse item para `notes/ideas.md` com o contexto já recolhido, em vez de o deixar preso no ToDo — inclui isto como uma das opções da própria pergunta (ex.: "Ainda não sei — mover para ideas.md com o que já descobri"). Formato exato a acrescentar em `ideas.md`:

```markdown
- <texto original do bullet>
  > Contexto (via implement-todo, AAAA-MM-DD): <o que foi investigado/perguntado e porque ficou por resolver — o que falta para desbloquear>
```

Remove o bullet do `ToDo.md` ao fazer isto (edição pontual, como na Fase 6) e deixa-o fora do resto desta ronda — não é uma tarefa "concluída" (não vai para `whatIveDone.md`), é uma tarefa adiada com contexto preservado. Pode ser revista mais tarde pelo menu "Ver estado atual" (Fase 1.0).

---

## Fase 4: Priorização, confirmação e gravar o plano

Constrói a ordem de implementação:

1. **Dependências duras primeiro** — topological sort usando o campo `Dependencies` de cada relatório da Fase 2 (ex.: expor as tarefas no GET do utilizador tem de vir antes das notificações por utilizador, porque a notificação precisa da relação já exposta).
2. **Agrupar por ficheiro partilhado ("hot file")** — cruza as tabelas `Files Likely Touched` de todos os temas em âmbito; qualquer ficheiro que apareça em mais do que um tema é um hot file (candidatos previsíveis neste repo: `SecurityConfig.java`, `ErrorCode.java`, `main.tsx`, entidades/DTOs de `Profile`, `Enterprise`, `Task`, `ConstructionStage`). Tarefas que partilham um hot file ficam adjacentes na ordem.
3. **Desempate** — esforço (S antes de L), depois impacto. **Se o orçamento da sessão (Fase 1.2) for "pouco"**, o desempate por esforço passa a critério principal (não só desempate) dentro de cada grupo que as dependências permitirem — o objetivo é fechar o máximo de tarefas completas possível nesta sessão, nunca fazer uma tarefa grande pela metade.

Apresenta a ordem final como lista numerada com 1 linha de razão por item. Pergunta via `AskUserQuestion` (single-select, 3 opções): **Confirmar** · **Reordenar** (segue-se pergunta de texto livre) · **Remover um item desta ronda**. Repete até o utilizador confirmar.

**Grava o plano confirmado** em `notes/roadmap/plans/AAAA-MM-DD-<slug-curto-do-âmbito>.md` (cria a pasta `plans/` se ainda não existir), formato:

```markdown
# Plano — AAAA-MM-DD <slug>

**Estado**: em curso
**Âmbito escolhido**: <descrição>
**Orçamento da sessão**: sem limite | pouco

## Esclarecimentos já obtidos
- <pergunta> → <resposta>

## Ordem confirmada
1. [ ] <tarefa> — por começar
2. [ ] <tarefa> — por começar
...

## Próximo passo
Começar a tarefa 1.
```

**Se o modo era "Só planear"**: para aqui — não avances para a Fase 5. Reporta ao utilizador onde o plano ficou gravado e resume a ordem proposta.

---

## Fase 5: Execução sequencial

**Sempre uma tarefa de cada vez, nunca em paralelo, nunca com worktrees/branches isolados** — o objetivo da Fase 4 já foi ordenar para minimizar conflitos, não paralelizar a escrita.

Por tarefa, na ordem confirmada:

1. **Checkpoint "em curso"**: antes de começar, atualiza o ficheiro de plano (`notes/roadmap/plans/...`) — marca a tarefa como "em curso" e atualiza "Próximo passo" para descrever exatamente o que falta se a sessão for interrompida agora (ex.: "tarefa 3: implementação feita, testes pendentes"). Isto é o que permite retomar sem perdas mesmo que o contexto acabe sem aviso.
2. Relembra a tarefa + qualquer esclarecimento da Fase 3 que lhe diga respeito.
3. Invoca via `Skill` tool a(s) skill(s) mapeadas em `Applicable Skills` para esse tema, pela ordem lógica (ex.: `add-database-table` antes de `add-backend-feature` se precisar de tabela nova; `permissions-and-auth` como verificação cruzada se a tarefa tocar em roles/ownership; `frontend-design-system` + `frontend-error-handling` para UI; `frontend-integration-guide` se for uma feature backend que precisa de handoff para frontend). **Nunca reinventes os passos de uma skill existente** — segue-a.
4. Implementa.
5. Testa antes de considerar a tarefa concluída — **nunca saltar este passo, mesmo em orçamento apertado**:
   - Backend: corre os testes relevantes (`./mvnw test -Dtest=...` ou a suite toda se fizer sentido) até passarem.
   - Frontend/UI: verifica mesmo no browser, não só inspeção visual do código — conforme a regra do `CLAUDE.md` raiz.
6. **Fecha o `Final Checklist` completo da(s) skill(s) invocada(s) no passo 3** — incluindo o(s) item(ns) de documentação por atualizar à mão (ex.: `docs/api.md`, `docs/database.md`, `docs/security.md`, conforme a skill). Estas skills já dizem explicitamente que o hook de pre-commit (`.githooks/pre-commit`) só avisa no momento do commit, não escreve nada — aqui a documentação é atualizada de forma proativa, no momento em que a feature é implementada, em vez de reativa. Nunca marques a tarefa como concluída com um item de documentação do checklist por fazer.
7. Corre a Fase 6 para esta tarefa (bookkeeping completo, também nunca saltado).
8. **Checkpoint "concluída"**: marca a tarefa como `[x]` concluída no ficheiro de plano.
9. Se surgir uma ambiguidade que só o código revelou (não era visível na Fase 2/3), **para e pergunta** — não assumas.
10. **Se o orçamento da sessão é "pouco"**: antes de começar a próxima tarefa, avalia se cabe com folga no que resta de sessão (usa o `Effort Estimate` como proxy). Se houver dúvida, **para aqui entre tarefas** (nunca a meio de uma) — o checkpoint do passo 1 já garante que o plano fica correto para a próxima sessão continuar "a todo o gás" sem repetir Fases 1-4.

---

## Fase 6: Bookkeeping por tarefa concluída

**`notes/ToDo.md`** — remove a linha exata do bullet implementado com uma edição pontual (nunca reescrevas o ficheiro todo). Para bullets que fazem parte de um grupo temático maior, remove só o bullet implementado, deixa os irmãos por implementar intactos.

**`notes/whatIveDone.md`** — acrescenta (nunca sobrescrever) uma entrada no formato:

```markdown
## AAAA-MM-DD — <título curto>

- <resumo em 1-2 linhas do que mudou>
- Files: <lista de ficheiros tocados>
- Item original do ToDo: "<texto verbatim do bullet>"
```

Guardar o texto verbatim do ToDo importa porque vários bullets deste backlog são vagos/incompletos — o log preserva o que foi realmente decidido na Fase 3, não só o diff final.

---

## Fase 7: Fecho

No fim da execução (ou se o utilizador parar a meio, ou se o orçamento apertado da Fase 5.10 decidiu parar):

- Resume o que foi implementado nesta ronda.
- Lista o que ainda falta (se o âmbito era parcial, se algum item ficou de fora por dúvida genuína, ou se sobraram tarefas do plano por orçamento apertado) — e nesse último caso, diz explicitamente: "para continuar, corre `/implement-todo` outra vez e escolhe 'Retomar um plano anterior'".
- Marca `**Estado**: concluído` no ficheiro de plano se todas as tarefas ficaram feitas; deixa `em curso` caso contrário (já deve estar correto pelos checkpoints da Fase 5). **Se ficou concluído**, move o ficheiro de `notes/roadmap/plans/` para `notes/roadmap/plans/archive/` (cria a pasta se não existir) — um plano terminado não precisa de continuar entre os planos ativos que a Fase 0 verifica, mas o histórico (âmbito, esclarecimentos, ordem seguida) fica preservado caso seja útil mais tarde.
- Proposta de commit seguindo [[skill-git-commits]] — pergunta antes de qualquer `git push`, como é norma geral do resto do trabalho neste repo. O Worksite é **um único repositório git** (backend e Backoffice vivem ambos em `management/`), por isso uma ronda normal fecha com **um só commit** — não há aqui a separação por submódulo que existia no projeto de origem.
- Se notares drift entre `notes/ideas.md` e `notes/ToDo.md`, ou que `notes/whatIveDone.md` tem entradas que não seguem o formato desta skill, menciona uma vez como aviso — não corrijas automaticamente, são ficheiros pessoais do utilizador.

---

## Final Checklist

- [ ] Menu principal (retomar / implementar / só planear / ver estado) apresentado antes de qualquer investigação
- [ ] Âmbito e orçamento da sessão escolhidos pelo utilizador (quando aplicável)
- [ ] Temas oferecidos no âmbito correspondem às secções que existem mesmo no `ToDo.md` lido na Fase 0
- [ ] Investigação feita em paralelo, por tema, via subagentes (não leste os ficheiros de código diretamente)
- [ ] Todas as perguntas de esclarecimento feitas em bloco, antes da Fase 5
- [ ] Itens que ficaram sem resposta possível movidos para `ideas.md` com contexto, não deixados presos
- [ ] Ordem de prioridade construída por dependências + hot files + esforço/impacto (e esforço em primeiro lugar se orçamento apertado), e confirmada pelo utilizador
- [ ] Plano gravado em `notes/roadmap/plans/` antes de implementar
- [ ] Implementação sempre sequencial, uma tarefa de cada vez, com checkpoint no plano antes/depois de cada tarefa
- [ ] Cada tarefa testada (testes de backend a passar / UI verificada no browser) antes do bookkeeping — nunca saltado, mesmo com pouco orçamento
- [ ] `Final Checklist` da(s) skill(s) invocada(s) fechado por completo em cada tarefa, incluindo os itens de documentação (`docs/api.md`, `docs/database.md`, `docs/security.md`, etc.) — atualizados proativamente, não deixados para o aviso do hook de pre-commit
- [ ] `notes/ToDo.md` atualizado por tarefa concluída (remoção pontual, não reescrita)
- [ ] `notes/whatIveDone.md` com entrada nova por tarefa concluída
- [ ] Resumo final + proposta de commit, sem `git push` sem confirmação

---

## Related Skills

- [[code-best-practices]] — Lido pelas skills invocadas na Fase 5, não repetido aqui
- [[skill-add-backend-feature]] — Novo endpoint/feature backend
- [[skill-add-database-table]] — Nova tabela
- [[skill-add-file-upload]] — Upload de ficheiros/fotos
- [[skill-permissions-and-auth]] — Controlo de acesso
- [[skill-frontend-design-system]] — Componentes React
- [[skill-frontend-error-handling]] — Erros no frontend
- [[skill-frontend-integration-guide]] — Handoff backend → frontend
- [[skill-frontend-structure-brief]] — Documentar estrutura atual antes de um redesign
- [[skill-git-commits]] — Formato da mensagem de commit no fecho (Fase 7)
