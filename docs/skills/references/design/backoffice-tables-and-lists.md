# Backoffice — Tables & Lists

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Baseado em `TasksList.tsx`, `EnterprisesList.tsx`, `EmployeesList.tsx`, `ConstructionStagesPage.tsx`, `ConstructionSubStagesPage.tsx`, `ConstructionExpensesPage.tsx`. Auditoria 2026-08-05 (após a migração das páginas de Construção para o sistema Industry).

## Definição de colunas — consistente, manter

Todas as listas definem as colunas da mesma forma: um array (dentro de `useMemo` quando depende de `t`/estado) que devolve `ColumnsType<T>`, inline no próprio ficheiro. Nenhuma extrai para um `columns.ts` separado. **Segue este padrão em listas novas.**

---

## 1. Coluna de ações — padrão único: `components/common/ListActions.tsx`

**Não repitas o objeto de estilo por ficheiro, e não uses `<Space direction="vertical">` com botões estilizados à mão.** Importa os componentes partilhados:

```tsx
import { ListActions, ListActionPrimary, ListActionSecondary, ListActionDanger }
  from "@/components/common/ListActions";

{
  title: "",            // a coluna de ações não tem cabeçalho
  key: "actions",
  width: 170,
  render: (_, record) => (
    <ListActions>
      <ListActionPrimary onClick={() => onView(record)}>Ver detalhes</ListActionPrimary>
      {canEdit(record) && (
        <ListActionSecondary onClick={() => onEdit(record)}>Editar</ListActionSecondary>
      )}
      {isAdmin() && (
        <ListActionDanger onClick={() => confirmDelete(record)}>Eliminar</ListActionDanger>
      )}
    </ListActions>
  ),
}
```

Hierarquia visual (definida uma vez, dentro do componente):

| Componente | AntD | Uso |
|---|---|---|
| `ListActionPrimary` | `<Button size="small">` (default) | Ação principal — "Ver detalhes", "Ver sub-etapas", "Ver despesas" |
| `ListActionSecondary` | `<Button type="text" size="small">` | Ação secundária — "Editar" |
| `ListActionDanger` | `<Button type="text" size="small">` + `opacity .75` + `color: var(--ind-color-accent)` | Ação destrutiva — "Eliminar" |

Notas de comportamento já resolvidas pelo componente:
- Botões empilhados na vertical, alinhados à esquerda, `minWidth: 110`.
- **`stopPropagation` no contentor** — em listas cuja linha é clicável (`onRow.onClick`, como as de Construção), clicar num botão de ação já não dispara também a navegação da linha. Era um bug real antes da migração.
- **Sem `onMouseEnter`/`onMouseLeave` a mexer em `style` diretamente.** O hover vem do tema AntD. Se precisares de um estado de hover novo, é CSS, não manipulação imperativa do DOM.

> **Exceção conhecida**: `EmployeesList.tsx:391-414` usa `<Space size="small">` horizontal com um botão só-ícone (`DeleteOutlined` + `Tooltip`) para a ação destrutiva. É a única lista que ainda diverge — migrar para `ListActions` quando for tocada.

## 2. Cabeçalho da página — kicker + `h1`

Todas as páginas de lista abrem com o mesmo bloco (ver `TasksPage.tsx:57-67`, `EnterprisesList.tsx`, e as três de Construção):

```tsx
<div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
  <div>
    <h6 style={{ color: "var(--ind-accent-700)" }}>Gestão</h6>   {/* kicker/secção */}
    <h1 style={{ margin: 0 }}>Tarefas</h1>
  </div>
  <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>Nova Tarefa</Button>
</div>
```

- O `<h6>` é o **kicker** (secção/contexto), não um subtítulo em prosa. Nas páginas aninhadas de Construção leva o nome do pai (projeto / etapa / sub-etapa).
- **Não** usar `<Typography.Title>` com `fontFamily: "Georgia, serif"` — é o estilo legacy, já removido das páginas de Construção.
- **Não** pôr `padding`/`minHeight` no contentor da página: `AppLayout` já aplica `padding: 27.2px 20.4px` ao `<main>`. As páginas de Construção tinham `padding: 24` a duplicar isso — corrigido.

Em páginas aninhadas, o `<Breadcrumb>` vem **antes** do cabeçalho, e um botão "Voltar" (`type="text" size="small"` + `ArrowLeftOutlined`, `opacity: 0.7`) fica por cima do kicker.

## 3. Tabela — moldura e estados

```tsx
<div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
  <Table ... pagination={false} />
</div>
```

- **Loading**: `loading={loading}` (booleano local) no próprio `<Table>` — consistente, manter. `TasksList` envolve em `<Spin>` por ter o `<Table>` dentro de um componente separado; qualquer das duas formas serve.
- **Empty**: `<Empty description="…" image={Empty.PRESENTED_IMAGE_SIMPLE} />` — discreto, adequado a UI administrativa densa.
- **Célula de nome/identificador**: `<span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>` — é o que marca a coluna identificadora da linha. Valores monetários usam o mesmo tratamento.
- **Célula vazia**: `"—"` simples. Não envolver num `<Text>` com cor própria.
- **Estado/categoria**: `<span className={`ind-tag ${cls}`}>` com as classes `ind-tag-outline` / `ind-tag-accent` / `ind-tag-neutral` (ver `TasksList.tsx:26-36` e `EnterprisesList` `STATUS_MAP`/`TYPE_MAP`). Não usar `<Badge>` nem estilo inline por ficheiro.

## 4. Rodapé de contagem e paginação

```tsx
<div style={{ marginTop: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
  <p style={{ fontSize: 12, opacity: 0.6, margin: 0 }}>{total} resultado(s)</p>
  <Pagination current={page + 1} total={total} pageSize={pageSize} onChange={(p) => onPageChange(p - 1)} />
</div>
```

As listas de Construção **não paginam** (`pagination={false}`, sem rodapé) — são listas curtas por natureza (etapas de um projeto, sub-etapas de uma etapa). Se alguma crescer, adota o bloco acima em vez de inventar outro.

Quando houver paginação, importar `DEFAULT_PAGE_SIZE`/`PAGE_SIZE_OPTIONS` de `config/pagination.ts` — não hardcodar o tamanho nem o array de opções.

## 5. Barra de pesquisa/filtros

`TasksPage.tsx:69-96` e `EnterprisesList` usam o mesmo bloco: `<Input>` com `prefix={<SearchOutlined style={{opacity:.5}}/>}` e `maxWidth: 320`, um `<select>` nativo estilizado com as vars `--ind-*`, e um `<Button>` "Limpar". **Segue este**, incluindo o `<select>` nativo — não trocar por `<Select>` do AntD só nesta lista.

## 6. Pele da tabela — vem do CSS global, não por ficheiro

`index.css` estiliza `.ant-table` uma vez para toda a app, segundo a spec `.table` do sistema Industry: fundo **transparente**, cabeçalhos em maiúsculas 11px com `letter-spacing:.08em` a 60% do texto, linhas separadas por hairline a 8%, hover a 4%. **Não redefinas cor de tabela por ficheiro** — herda daqui.

> Corrigido a 2026-08-09: até essa data o `index.css` ainda tinha a pele terracotta herdada (`.ant-table` a `--ivory`, cabeçalhos a `--warm-sand`, `padding: 24px !important`), que pintava **todas** as tabelas na paleta legacy e contradizia o resto da app. Foi substituída pela pele Industry. Se vires warm sand numa tabela, é drift a voltar — não o repitas.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-buttons-and-icons]] — variantes de botão e confirmação de ações destrutivas
- [[backoffice-tokens-and-colors]] — as variáveis `--ind-*` usadas aqui
- [[backoffice-services-and-error-handling]] — o `catch` das chamadas que alimentam estas listas
