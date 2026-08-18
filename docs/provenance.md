# 🧬 Proveniência — o que veio do Property-Management

Este projeto começou como uma cópia reduzida do monorepo
[Property-Management](https://github.com/bino03/Property-Management). Saber o que foi herdado
explica nomes que de outra forma não fazem sentido — e evita procurar funcionalidades que nunca
existiram aqui.

## Herdado

- **Autenticação/contas** — auth por JWT do Supabase, `worksite.profile` (roles `ADMIN`/`EMPLOYEE`),
  fluxo de convite por admin.
- **Enterprises** — conceptualmente passaram a chamar-se "projetos", mas **os nomes da tabela e do
  pacote ficaram como estavam**, para reduzir o risco da cópia. É por isso que o código diz
  `enterprise` e a interface diz "empreendimento".
- **Gestão de construção** — originalmente `construction_stage` → `construction_sub_stage` →
  `construction_expense`. Substituído na `V15` por uma árvore auto-referenciada,
  `construction_budget_item` → `construction_expense`, para um orçamento de profundidade
  arbitrária mapear 1:1 no Excel que a empresa recebe — com importador `.xlsx`.
- **Funcionários** — CRUD sobre `worksite.profile`, sem entidade separada.
- **Tarefas** — tarefas isoladas no seu próprio schema `tasks`, atribuíveis a vários perfis, sem
  ligação a nenhum ativo/imóvel (esse conceito não existe aqui).

## Deliberadamente não trazido

Anúncios de imóveis (`property_asset`, `buildings`, agência/características/contactos/licenças),
leads, banners, pagamentos, o portal público, e SSE/tempo real. Nada disto foi pedido para este
projeto — são candidatos a funcionalidades futuras, não lacunas.

> Cuidado ao ler código e traduções: há restos desta origem que **não** correspondem a nada neste
> projeto — chaves i18n de "Contactos: clientes, proprietários e leads" e segmentos de rota
> (`edificios`, `propriedades`, `certificados`) ainda listados em skills. Não os tomar como
> funcionalidades reais.
>
> Mas nem todo o resto é lixo: as chaves `notifications.*` estavam por usar em pt **e** en e
> serviam exactamente a funcionalidade construída a 2026-08-18 — o sino usa-as. Antes de descartar
> uma chave órfã, ver se não é a que falta.

## O que nasceu aqui

- **Faturas de obra** com leitura do QR da AT — ver [[api]] e [[backend-conventions]].
- **Catálogo de fornecedores** (`V19`) — ver [[api]].
- **Notificações in-app** (`V20`, 2026-08-18) — ver [[api]]. Note-se a nuance: a lista acima diz
  que notificações não foram trazidas, e continua verdade quanto a SSE/push — os avisos in-app
  foram construídos aqui de raiz.

## Relacionado

- [[architecture]] · [[database]] · [[commands]]
