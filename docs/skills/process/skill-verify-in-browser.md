# Skill: Verify in Browser

**When to use**: Quando uma feature do Backoffice precisa de ser vista a funcionar na app real — o passo 5.5 do [[skill-implement-todo]], ou uma passagem em lote sobre [[verificacao-browser-pendente]]. Não substitui `npx tsc -b`, o eslint nem os testes do backend: vem **depois** deles.

**Time**: ~10 min a preparar a sessão (a primeira vez do dia) + ~2-5 min por verificação

> 📐 Skill de processo — não escreve código de produto. Corre **inline, nunca num subagente**: a extensão do Chrome pertence à sessão principal, e um subagente não a herda nem devolve o que viu de forma que se possa confiar.

---

## Porque é que esta skill existe

O procedimento vivia em [[learning]] e na memória do Claude, e cada sessão o redescobria: a extensão que não liga, os cliques que falham na escala de 156 %, o `file_upload` que recusa ficheiros fora do repo, a obra de teste que fica para trás. Três passagens da paridade (2026-09-08 → 09-15) e a migração de 09-17/18 pagaram esse custo. Aqui fica o que funciona, para ser invocado e não lembrado.

Duas regras de fundo, antes dos passos:

1. **Verificar por DOM + rede, não por screenshot.** Um screenshot diz o que parece; `fetch` à API com a sessão da página e leitura do DOM dizem o que **é**. Só se tira screenshot para mostrar ao utilizador, nunca para decidir.
2. **Só se escreve na base de dados real através de uma obra `is_test`.** Leitura contra obras reais é livre; qualquer verificação que crie, altere ou apague dados corre numa obra criada para isso e apagada no fim (Step 6). Não há staging ([[operations]]).

---

## Step 0: O que vai ser verificado

Antes de abrir o browser, ter a lista em mãos — vem de um destes sítios:

- A tarefa do `implement-todo` que acabou de ser implementada (a skill chama esta no passo 5.5).
- Uma secção de [[verificacao-browser-pendente]] (passagem em lote — a forma preferida quando a implementação foi feita sem browser, ver a memória "browser verification deferred").

Para cada item, decidir já: **lê ou escreve?** Se algum escreve, o Step 3 cria a obra de teste; se todos só lêem, salta-se.

---

## Step 1: Arrancar a app

Backend em `:8080` e Backoffice em `:5173` — comandos e armadilhas (a porta 5173 é a única com CORS; o devtools reinicia a cada compile) em [[commands]]. Confirmar antes de ir ao browser:

```bash
curl -s -m 3 http://localhost:8080/ping        # backend
curl -s -m 3 -o /dev/null -w "%{http_code}" http://localhost:5173/   # Vite → 200
```

Se o backend não estiver a correr, arrancá-lo em background e esperar por `Started ManagementApiApplication` no log — e ler o bloco do Flyway: **uma migração pendente aplica-se neste arranque** ([[operations]] → "Migração má" se não for essa a intenção).

---

## Step 2: Ligar a extensão do Chrome

Carregar as ferramentas numa só chamada `ToolSearch` (`select:` com `tabs_context_mcp, navigate, javascript_tool, read_page, tabs_create_mcp, tabs_close_mcp, list_connected_browsers, select_browser`; `file_upload` só se for preciso anexar ficheiros).

Sequência que funciona nesta máquina — **não saltar o 2, mesmo que pareça ligada**:

1. `list_connected_browsers` → se vier vazio, ou `tabs_context_mcp` responder `No group with id` / `Browser extension is not connected`: pedir ao utilizador para **fechar o Chrome todo, abrir de novo, e escolher o browser na confirmação da própria extensão**. Nada a partir do Claude Code resolve isto.
2. Depois do reinício, `list_connected_browsers` mostra um **`deviceId` novo** — `select_browser` explicitamente; a sessão continua a apontar para o antigo se não se fizer.
3. `tabs_create_mcp` com `http://localhost:5173/login`. Nunca reutilizar ids de tab de uma sessão anterior.
4. Login pelo formulário da página (o browser preenche as credenciais; o utilizador confirma). Confirmar a sessão pela API, não pelo ecrã:

```js
// javascript_tool — o cookie HttpOnly vai com credentials:'include'
const r = await fetch('http://localhost:8080/auth/me', { credentials: 'include' });
console.log('auth/me', r.status, r.status === 200 ? (await r.json()).role : '');
```

`200` + `ADMIN` é o ponto de partida da maior parte das verificações. `401` → repetir o login; `403` → sessão de `EMPLOYEE` onde se precisa de `ADMIN`.

> Se a extensão falhar 2-3 vezes seguidas, parar e dizer ao utilizador o que foi tentado — não continuar a bater na mesma ferramenta. A verificação fica registada como pendente (Step 7), que é o estado honesto.

---

## Step 3: Obra de teste (só se a verificação escreve)

Criar por `fetch` a partir da página, com a sessão do login — não pela UI (menos passos que podem falhar, e o `slug` fica logo certo):

```js
const fd = new FormData();
fd.append('enterprise', new Blob([JSON.stringify({
  name: 'Vila Teste Claude',      // o nome diz ao que vem, para ninguém a confundir com uma obra real
  slug: 'vila-teste-claude',
  isTest: true,                    // é isto que a tira dos relatórios, das exportações e dos duplicados
  type: 'residential',
  status: 'planning',
})], { type: 'application/json' }));
const r = await fetch('http://localhost:8080/enterprises', { method: 'POST', credentials: 'include', body: fd });
const e = await r.json();
window.__testEnterpriseId = e.id;   // guardar: a página pode recarregar e perder o estado JS
console.log('obra de teste', r.status, e.id);
```

Anotar o `id` **na conversa** (não só em `window.*` — a página reinicia sozinha às vezes e leva o estado com ela).

O que a flag `is_test` garante ([[api]] → Projetos): fora das exportações e dos painéis, ignorada na verificação global de duplicados, e o importador da "Despesas" só grava nela a partir de um ficheiro `TESTE - …`. O que **não** garante: nada a impede de aparecer na lista de obras — é por isso que se apaga no fim.

---

## Step 4: Verificar

Por cada item da lista do Step 0, o mesmo par:

- **Ação**: pela UI quando é isso que se está a verificar (um botão, uma drawer, um filtro) — `navigate` + `read_page`/`find` para chegar lá, `javascript_tool` para clicar por seletor (`document.querySelector(...).click()`) em vez de coordenadas. Para preparar dados (criar faturas, pagamentos, rubricas), pela API com `fetch` — é mais rápido e não é o que se está a testar.
- **Prova**: o que mudou, lido do DOM (`read_page` / `get_page_text`, ou `document.querySelectorAll` no `javascript_tool`) **e** da API (`fetch` ao `GET` correspondente). Os dois têm de concordar; quando não concordam, o bug é quase sempre a lista que não recarrega ([[code-map]] → "por sintoma").

  Exceção: **abrir/fechar de modais e dropdowns decide-se por screenshot**, e o "Aplicar"/"Cancelar" clica-se com o rato (`computer` → `left_click` com `ref`), não por `element.click()` — com a janela do Chrome sem foco a animação de saída do AntD nunca acaba e o DOM diz "aberto" para sempre (ver [[learning]] 2026-09-21). Popups presos fecham-se com `key Escape` antes do passo seguinte.

Registar por item: ✅ / 🔴 com o que se esperava vs. o que apareceu, e o erro da consola ou da rede (`read_console_messages` com `pattern`, `read_network_requests`) se houver. Uma 🔴 vai para `notes/ToDo.md` como bullet ⚠️ com esse detalhe — não se corrige a meio da passagem, senão a passagem nunca acaba.

> **`read_network_requests` não mostra headers de resposta** (`Set-Cookie`, CORS) e o `statusCode`
> que devolve pode divergir do que o `fetch()` da página recebeu de facto. Para inspecionar headers,
> usar `curl -i`/`curl -D-` a partir do Bash — várias rotas (logout, `permitAll`) toleram um `curl`
> sem sessão nenhuma, e não é preciso passar pelo browser para isto.

Chamadas longas (importações, exportações) ficam assíncronas em `window.__x = fetch(...)` e lêem-se depois; se a página recarregar entretanto, repetir — o backend é transacional, a chamada anterior ou entrou inteira ou não entrou.

`file_upload` **só aceita ficheiros de dentro do repo**: copiar o ficheiro para uma pasta temporária git-ignored (`notes/tmp-browser/`), carregar dali, apagar a pasta no Step 6.

---

## Step 5: Permissões, quando o item as tem

Se a lista inclui "só `ADMIN` vê X" ou "`EMPLOYEE` não pode Y", a prova é a API, não o menu escondido: com uma sessão `EMPLOYEE`, o `fetch` ao endpoint tem de dar `403` — um botão que não aparece não é segurança ([[skill-frontend-design-system]] → "Visibilidade de campos por role"). Trocar de sessão = logout pela UI + login com a outra conta; confirmar com `/auth/me` outra vez.

---

## Step 6: Limpar

Se houve obra de teste, apagar **pela ordem certa** — a ordem errada deixa lixo com FK:

1. Faturas da obra: **notas de crédito primeiro** (têm origem), depois as outras — `DELETE /construction-invoices/{id}` por cada uma (`GET /construction-invoices/enterprise/{id}` dá a lista; os pagamentos e documentos vão com elas).
2. A obra: `DELETE /enterprises/{id}` (soft delete).
3. Confirmar: `GET /enterprises/{id}` → `200` com `status: "deleted"` (é soft delete, não 404) e a obra fora de `GET /enterprises`; a pasta `notes/tmp-browser/` apagada se existiu.

Não deixar a obra "para a próxima" — a última que ficou (`Vila Teste Claude`, 2026-09-18) acabou por ser apagada à mão com 20 faturas de desenvolvimento dentro, e os números reais que lá estavam bloquearam uma importação por duplicado.

---

## Step 7: Registar

- Cada item verificado sai de [[verificacao-browser-pendente]] (edição pontual; se a secção ficar vazia, uma linha "✅ fechado a AAAA-MM-DD" no lugar dela — a numeração histórica mantém-se).
- Os 🔴 estão no `notes/ToDo.md` (Step 4). Se a passagem fechou um plano de `notes/roadmap/plans/`, o `implement-todo` trata do resto do bookkeeping (Fase 6/7).
- Se algo custou tempo que não fosse da app — a extensão, o Claude Code, o Chrome — um bullet datado em [[learning]] e, se for uma regra nova de procedimento, **este ficheiro** muda (o `learning.md` fica só com o link).

---

## Final Checklist

- [ ] Lista do que verificar em mãos antes de abrir o browser, com "lê/escreve" por item
- [ ] `tsc -b`, eslint e testes do backend já passaram — isto é o passo seguinte, não o substituto
- [ ] Backend e Vite confirmados por `curl`; bloco do Flyway lido no arranque
- [ ] Extensão ligada pela sequência do Step 2 (`select_browser` depois de reiniciar o Chrome); `/auth/me` = `200` com a role certa
- [ ] Nenhuma escrita na BD fora de uma obra `is_test`
- [ ] Cada item provado por DOM **e** API, não por screenshot
- [ ] Permissões provadas por `403` na API, não por botão escondido
- [ ] Obra de teste apagada (faturas primeiro, NC antes das origens), `GET` → `status: "deleted"`; `notes/tmp-browser/` apagada
- [ ] [[verificacao-browser-pendente]] atualizado; 🔴 no `ToDo.md`; lição de ferramenta no [[learning]]
- [ ] Corrido inline — nunca delegado a um subagente

---

## Related Skills

- [[skill-implement-todo]] — Chama esta skill no passo 5.5 de cada tarefa com UI (ou adia-a para [[verificacao-browser-pendente]])
- [[skill-frontend-design-system]] — As regras que se estão a verificar (formulários, visibilidade por role, "testar no browser")
- [[commands]] — Arrancar backend e Backoffice, portas, `tsc -b`
- [[operations]] — Não há staging; o que fazer se uma migração entrou sem querer
- [[learning]] — De onde este procedimento veio; para onde vão as lições novas de ferramenta
