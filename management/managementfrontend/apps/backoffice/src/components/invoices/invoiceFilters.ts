import type { InvoiceFilters } from "@/types/invoice";
import { ADVANCED_INVOICE_FILTER_KEYS } from "@/types/invoice";

/** Quantos filtros da pesquisa avançada estão ligados — o número no ícone de filtro. */
export function countActiveInvoiceFilters(filters: InvoiceFilters): number {
  let n = 0;
  // `from`/`to` são um filtro só (o intervalo); `outstanding`/`paymentStatus` também.
  if (filters.from || filters.to) n++;
  if (filters.outstanding !== null || filters.paymentStatus) n++;
  for (const key of ADVANCED_INVOICE_FILTER_KEYS) {
    if (key === "from" || key === "to" || key === "outstanding" || key === "paymentStatus") continue;
    if (filters[key] !== null) n++;
  }
  return n;
}

/** Tudo a null — o que "Limpar filtros" aplica. */
export function clearedInvoiceFilters(): Partial<InvoiceFilters> {
  const cleared: Partial<InvoiceFilters> = {};
  for (const key of ADVANCED_INVOICE_FILTER_KEYS) {
    (cleared as Record<string, unknown>)[key] = null;
  }
  cleared.supplierName = null;
  cleared.budgetItemLabel = null;
  return cleared;
}
