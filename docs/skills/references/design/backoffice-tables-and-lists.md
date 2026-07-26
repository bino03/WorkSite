# Backoffice — Tables & Lists

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Baseado em `PropertiesList.tsx`, `EmployeesList.tsx`, `BuildingsList.tsx`, `ContactsList.tsx`, `LicensesList.tsx`.

## Definição de colunas — já é consistente, manter

Todos os 5 ficheiros definem as colunas da mesma forma: um `useMemo(() => [...], [deps])` que devolve `ColumnsType<T>`, inline no próprio ficheiro de lista (nenhum extrai para um `columns.ts` separado). **Isto é 100% consistente — segue este padrão em listas novas.**

## Drift encontrado — e a convenção escolhida para cada um

### 1. Coluna de ações: dois layouts a competir

- **Botões verticais empilhados** (Properties, Buildings, Licenses, Leads, Tasks): `<Space direction="vertical" size={2}>` com botões de largura total, ícone + texto — `view`/ação principal = botão preenchido terracota (`D.terracotta`, hover `D.coral`), ação secundária = contorno `D.warmSand`/`D.borderWarm`, apagar = mesmo contorno mas texto `#b53333` com hover `background: '#fff5f5'`, sempre dentro de `Popconfirm`. Ver [[../../frontend/skill-frontend-design-system]] e `TasksList.tsx` para o template completo.
- **Ícones horizontais compactos** (Employees, Contacts): `<Space size="small">` com botões pequenos só de ícone, `type="text"`, envolvidos em `Tooltip`.

**Convenção escolhida (revista): botões verticais empilhados de largura total com a paleta `D`** (o padrão de Properties/Leads/Tasks) — é agora a maioria real (5 de 7 listas) e foi o estilo pedido explicitamente para unificar toda a app. Cada ação tem um rótulo visível (não obriga o utilizador a passar o rato para descobrir o que faz um ícone), e a cor comunica hierarquia (preenchido = ação principal, contorno = secundária, vermelho = destrutiva) sem depender de `Tooltip`. Migrar `EmployeesList`/`ContactsList` para este padrão oportunisticamente quando forem tocados — não é preciso reescrever já.

Importar sempre `D` e `actionButtonBaseStyle` de `src/config/entityColors.ts` (ver [[backoffice-tokens-and-colors]]) em vez de redefinir localmente — `TasksList.tsx` já segue isto.

### 2. Renderização de "estado": 4 técnicas diferentes

- `PropertiesList.tsx:771-776` e `BuildingsList.tsx:432-436` — `<span>` simples com estilo inline, sem `Tag`/`Badge`.
- `EmployeesList.tsx:121-133,379-395` — `<Badge status={...} text={...}>` mapeado para os estados semânticos do próprio AntD (`success`/`error`/`default`) via `statusBadge()`.
- `LicensesList.tsx:363-368` — também `<Badge status={...}>`, mas mapeia diretamente para strings de cor com `as any`.

**Convenção escolhida: `<Badge status="..." text="..." />` com os estados semânticos do Ant Design** (`success`/`processing`/`error`/`default`/`warning`) — o padrão de `EmployeesList`, sem `as any`. Para uma categoria/tipo (não um estado binário), usar `<Tag>` com cor vinda dos tokens do tema, não estilo manual por ficheiro.

### 3. Barra de pesquisa/filtros: 3 layouts diferentes

- Tailwind flex (`className="flex flex-col gap-3 px-6 py-4 md:flex-row"`) — Properties, Buildings.
- `style={{display:'flex', ...}}` inline (CSS-in-JS) — Employees.
- `<Row gutter>`/`<Col>` do AntD — Contacts, Licenses.

**Convenção escolhida: Tailwind flex** (o padrão de Properties/Buildings) — é a ferramenta de estilo primária da app (Tailwind 4 + `theme.ts`/`index.css`), evita depender do sistema de grelha do AntD para layout que o Tailwind já resolve, e já tem 2 dos 5 ficheiros a usá-lo.

### 4. Debounce da pesquisa: só em 3 de 5 listas

`EmployeesList`, `ContactsList`, `LicensesList` definem um hook local `useDebounced` **copiado** (não partilhado) em cada ficheiro. `PropertiesList`/`BuildingsList` não têm debounce nenhum — a pesquisa dispara a cada tecla.

**Convenção**: toda a lista nova precisa de debounce (300ms). Extrair o `useDebounced` para `src/hooks/` em vez de continuar a copiar — e adicionar às duas listas que ainda não têm (é bug de performance, não só de estilo).

### 5. Paginação hardcoded — mais espalhado do que parecia

Só `PropertiesList.tsx` e `BuildingsList.tsx` importam `DEFAULT_PAGE_SIZE`/`PAGE_SIZE_OPTIONS` de `src/config/pagination.ts`. **Employees, Contacts, Catalog, Locations e Licenses hardcodeiam** `pageSize: 20` e duplicam manualmente o array `[10, 20, 50, 100]` cada um — não é um caso isolado (`EmployeesList`), é a maioria das listas.

**Convenção**: importar sempre de `config/pagination.ts`. Não hardcode o tamanho nem o array de opções em nenhuma lista nova ou editada.

## Empty state & Loading

- **Loading**: `loading={loading}` (boolean local) diretamente no `<Table>` — consistente em todos os ficheiros, manter.
- **Empty state**: `PropertiesList` usa `<Empty description={...} />` com a ilustração por omissão; `BuildingsList`/`EmployeesList` usam `image={Empty.PRESENTED_IMAGE_SIMPLE}`; `LicensesList` usa a ilustração por omissão; `ContactsList` não usa `Empty` — tem um bloco totalmente à mão (`ContactsOutlined` + `Title` + subtexto condicional).

**Convenção escolhida: `<Empty description={...} image={Empty.PRESENTED_IMAGE_SIMPLE} />`** — mais discreta, adequada a uma UI administrativa densa. Retirar o bloco à mão do `ContactsList` quando for tocado.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tokens-and-colors]] — a paleta `D` duplicada usada nas células de entidade destas listas
- [[backoffice-buttons-and-icons]]
