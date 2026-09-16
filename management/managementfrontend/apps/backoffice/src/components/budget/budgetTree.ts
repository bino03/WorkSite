import type { BudgetItemNode, DatePropagationHint } from "@/types/budget";

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
