# Backoffice — Buttons & Icons

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Auditoria 2026-08-05 (após a migração das páginas de Construção para o sistema Industry).

## Tipos de botão

| Uso | Como |
|---|---|
| Ação principal da página (criar/gravar) | `<Button type="primary" icon={<PlusOutlined />}>` — tamanho por omissão, **não** `size="large"` |
| Ação principal numa linha de tabela | `<ListActionPrimary>` (ver [[backoffice-tables-and-lists]]) |
| Ação secundária | `<Button type="text" size="small">` |
| Ação destrutiva | `<ListActionDanger>` em tabelas; fora delas, `type="text"` com `opacity: .75` e `color: var(--ind-color-accent)` |
| Navegação "Voltar" | `<Button type="text" size="small" icon={<ArrowLeftOutlined />} style={{ paddingLeft: 0, opacity: .7 }}>` |

### ❌ Não simular botões preenchidos com estilo inline

O padrão antigo — `type="text"` com `background`/`border`/`boxShadow` inline (paleta terracotta/warmSand) mais `onMouseEnter`/`onMouseLeave` a escrever em `e.currentTarget.style` — **foi removido do código** a 2026-08-05, junto com `D` e `actionButtonBaseStyle` de `config/entityColors.ts`.

Motivo: o hover imperativo contorna o tema, duplica-se por ficheiro, e não reage a estados desativado/foco. Se um botão precisa de aparência própria, isso vem do tema AntD (`theme.ts`) ou de uma classe em `index.css` — nunca de manipulação directa do DOM.

## Confirmação antes de apagar — `useConfirm()`, não `Popconfirm`

O padrão da app é o diálogo partilhado de `context/ConfirmDialogContext.tsx`:

```tsx
const confirm = useConfirm();

confirm({
  message: `Eliminar "${record.name}"? Esta ação não pode ser desfeita.`,
  onConfirm: () => handleDelete(record.id),
});
```

Já é usado em `TasksList`, `EnterprisesList`, `EmployeesList`, `AppLayout` (logout) e nas três páginas de Construção. Dá um diálogo centrado consistente, com `title`/`actionLabel` por omissão ("Confirmar eliminação" / "Eliminar").

> **Ainda por migrar** (usam `Popconfirm`): `EditEnterpriseGalleryCard.tsx`, `EditEnterpriseLocationCard.tsx`, `ProfileView.tsx`. Migrar quando forem tocados.

**Regra sem exceções**: qualquer ação destrutiva passa por confirmação. Nenhuma apaga direto.

> ⚠️ **Os valores por omissão são de eliminação** — `title` é "Confirmar eliminação" e
> `actionLabel` é "Eliminar" (`ConfirmDialogContext.tsx:24-27`). Numa ação **não destrutiva**
> tens de passar os dois, senão o utilizador lê "Confirmar eliminação" ao marcar uma fatura
> como enviada à contabilidade. Aconteceu em quatro sítios e foi corrigido a 2026-08-18
> (`InvoiceDetailDrawer`, `EnterpriseInvoicesPage` — desassociar da rubrica e contabilidade).

## Ícone + texto vs. só ícone

- **Texto visível** nas colunas de ação — o `ListActions` usa rótulos, não ícones. O utilizador não deve ter de passar o rato para descobrir o que um botão faz.
- **Só ícone + `Tooltip`**: aceitável fora de colunas de ação, onde o espaço é limitado e a ação é óbvia — ex. o botão de ver fatura em `ConstructionExpensesPage.tsx` (`FileTextOutlined` dentro de `Tooltip`).

## Biblioteca de ícones

O código real usa maioritariamente `@ant-design/icons`, e é isso que as páginas migradas passaram a usar (`PlusOutlined`, `ArrowLeftOutlined`, `FileTextOutlined`) — as três páginas de Construção usavam `lucide-react` e foram alinhadas com o resto da app.

**Convenção**: `@ant-design/icons` como omissão, por coerência com o resto do Backoffice e por integrar melhor com os componentes AntD (`icon={...}` em `Button`, `prefix` em `Input`). `lucide-react` continua instalado e usado em pontos avulsos (ex. `EnterprisesList`) — não é preciso reescrever, mas não misturar as duas famílias no mesmo ecrã.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tables-and-lists]] — a coluna de ações e o componente `ListActions`
- [[backoffice-drawers-and-modals]] — Drawer vs Modal
- [[backoffice-tokens-and-colors]] — as variáveis `--ind-*`
