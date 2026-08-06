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

/**
 * Ascendentes do nó que ainda não têm data de início/fim.
 *
 * O backend só avisa **depois** de gravar (`datePropagationHints`). Como o
 * cliente já tem a árvore toda em memória, consegue prever isto e oferecer a
 * opção no próprio formulário — poupa uma segunda gravação e evita um diálogo
 * pós-facto que se lê como erro.
 */
export function collectAncestorsMissingDates(
  roots: BudgetItemNode[],
  id: string
): { start: BudgetItemNode[]; end: BudgetItemNode[] } {
  const trail = pathTo(roots, id);
  const ancestors = trail.slice(0, -1);
  return {
    start: ancestors.filter((a) => !a.startDate),
    end: ancestors.filter((a) => !a.endDate),
  };
}

/** Rótulo curto para o aviso de propagação vindo do backend. */
export function describeHints(hints: DatePropagationHint[]): string {
  const names = [...new Set(hints.map((h) => h.ancestorCode ?? h.ancestorName))];
  return names.join(", ");
}
