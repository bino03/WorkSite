/**
 * As contas da repartição de uma fatura por rubricas.
 *
 * Vivem fora do componente porque o ecrã de classificação e a drawer de detalhe
 * precisam das mesmas regras, e porque a soma tem de bater certo com a que o
 * backend impõe (`INVOICE_028`) — duas cópias divergiriam.
 */

/** Uma linha em edição: a rubrica pode ainda não estar escolhida. */
export interface DraftSplitLine {
  budgetItemId: string | null;
  label: string | null;
  amount: number | null;
}

/** Soma das linhas, arredondada aos cêntimos para não acumular erro de vírgula flutuante. */
export function splitSum(lines: DraftSplitLine[]): number {
  return Math.round(lines.reduce((sum, line) => sum + (line.amount ?? 0), 0) * 100) / 100;
}

/**
 * A repartição está pronta a gravar?
 *
 * Sem total, a soma **não** se impõe: é a fatura que foi pedida ao fornecedor e
 * ainda não chegou, e o backend grava-lhe as linhas a zero de propósito.
 */
export function splitIsValid(lines: DraftSplitLine[], total: number | null): boolean {
  if (lines.length === 0) return false;
  if (lines.some((line) => !line.budgetItemId)) return false;
  if (total == null) return true;
  return Math.abs(splitSum(lines) - total) < 0.005;
}
