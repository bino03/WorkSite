import type { BudgetItemNode, BudgetLot, BudgetTree, DatePropagationHint } from "@/types/budget";

/**
 * Um lote como nó de topo da árvore — para os seletores de rubrica numa obra com
 * vários lotes, onde o 4.2.1 existe em todos e só o lote os distingue. É um
 * título (`HEADING`): não aceita despesas, abre-se para chegar às rubricas. O id
 * leva o prefixo `lot:` para nunca colidir com o de uma rubrica.
 */
export function lotAsNode(lot: BudgetLot, tree: BudgetTree): BudgetItemNode {
  return {
    id: `lot:${lot.id}`,
    parentId: null,
    rowKind: "HEADING",
    acceptsExpenses: false,
    code: null,
    sortOrder: lot.sortOrder,
    depth: 0,
    name: lot.name,
    unit: null,
    quantity: null,
    unitPrice: null,
    totalPrice: null,
    observations: null,
    startDate: null,
    endDate: null,
    rolledUpBudget: tree.budgetTotal,
    budgetMismatch: false,
    budgetVariance: null,
    spentTotal: tree.spentTotal,
    remaining: tree.remaining,
    percentSpent: tree.percentSpent,
    overBudget: tree.overBudgetCount > 0,
    expenseCount: tree.expenseCount,
    ownExpenseCount: 0,
    missingInvoiceCount: tree.missingInvoiceCount,
    pendingAccountantCount: tree.pendingAccountantCount,
    pendingAccountantTotal: tree.pendingAccountantTotal,
    createdAt: "",
    updatedAt: "",
    children: tree.roots.map((root) => ({ ...root, parentId: `lot:${lot.id}` })),
  };
}

/** Percorre a árvore em profundidade, na ordem em que se lê no Excel. */
export function flattenTree(roots: BudgetItemNode[]): BudgetItemNode[] {
  const out: BudgetItemNode[] = [];
  const walk = (nodes: BudgetItemNode[]) => {
    for (const node of nodes) {
      out.push(node);
      walk(node.children);
    }
  };
  walk(roots);
  return out;
}

/** Procura no índice e na descrição — é o que se tem à frente na tabela. */
export function matchesQuery(node: BudgetItemNode, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return (
    (node.code ?? "").toLowerCase().includes(q) || node.name.toLowerCase().includes(q)
  );
}

/** Devolve o caminho da raiz até ao nó, inclusive. */
export function pathTo(roots: BudgetItemNode[], id: string): BudgetItemNode[] {
  const walk = (nodes: BudgetItemNode[], trail: BudgetItemNode[]): BudgetItemNode[] | null => {
    for (const node of nodes) {
      const next = [...trail, node];
      if (node.id === id) return next;
      const found = walk(node.children, next);
      if (found) return found;
    }
    return null;
  };
  return walk(roots, []) ?? [];
}

/** Rótulo curto para o aviso de propagação vindo do backend. */
export function describeHints(hints: DatePropagationHint[]): string {
  const names = [...new Set(hints.map((h) => h.ancestorCode ?? h.ancestorName))];
  return names.join(", ");
}

/** Ascendentes de uma rubrica-mãe (inclusive) — a mãe conta como ascendente de quem está por baixo dela. */
export function ancestorsOf(roots: BudgetItemNode[], parentId: string | null): BudgetItemNode[] {
  return parentId ? pathTo(roots, parentId) : [];
}

/** O nó, ou `null` se não existir na árvore (ex.: já foi eliminado). */
export function findNode(roots: BudgetItemNode[], id: string): BudgetItemNode | null {
  for (const node of roots) {
    if (node.id === id) return node;
    const found = findNode(node.children, id);
    if (found) return found;
  }
  return null;
}

/** O nó e todos os seus descendentes, em qualquer profundidade. */
export function collectSubtreeIds(node: BudgetItemNode): Set<string> {
  const ids = new Set<string>();
  const walk = (n: BudgetItemNode) => {
    ids.add(n.id);
    n.children.forEach(walk);
  };
  walk(node);
  return ids;
}

/** Os irmãos de um nó (mesma mãe), na ordem da árvore — para as setas ↑ ↓. */
export function siblingsOf(roots: BudgetItemNode[], id: string): BudgetItemNode[] {
  const trail = pathTo(roots, id);
  if (trail.length === 0) return [];
  if (trail.length === 1) return roots;
  return trail[trail.length - 2].children;
}

export interface ParentTreeOption {
  value: string;
  title: string;
  children?: ParentTreeOption[];
}

/**
 * Opções de "mãe" para o `TreeSelect` do formulário de rubrica: exclui `NOTE`
 * (nunca aceita filhos) e a própria sub-árvore de quem se está a editar — uma
 * rubrica não pode ficar dentro de si própria (o backend recusa com
 * `BUDGET_009`, mas nem vale a pena oferecer a opção).
 */
export function buildParentTreeOptions(
  roots: BudgetItemNode[],
  excludeSubtreeOf?: string
): ParentTreeOption[] {
  const excludeRoot = excludeSubtreeOf ? findNode(roots, excludeSubtreeOf) : null;
  const excluded = excludeRoot ? collectSubtreeIds(excludeRoot) : new Set<string>();

  const build = (nodes: BudgetItemNode[]): ParentTreeOption[] =>
    nodes
      .filter((n) => n.rowKind !== "NOTE" && !excluded.has(n.id))
      .map((n) => ({
        value: n.id,
        title: n.code ? `${n.code} — ${n.name}` : n.name,
        children: build(n.children),
      }));

  return build(roots);
}
