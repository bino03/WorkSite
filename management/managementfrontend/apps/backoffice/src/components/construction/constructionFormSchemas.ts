/**
 * Regras da fatura de despesa — espelham as validações do
 * `ConstructionExpenseService` no backend (bucket `documents`).
 *
 * Os schemas Zod de etapa/sub-etapa/despesa foram removidos com a passagem do
 * modelo de duas camadas para a árvore de orçamento (`construction_budget_item`);
 * os novos entram quando a página de orçamento for construída.
 */

export const MAX_INVOICE_BYTES = 25 * 1024 * 1024; // 25 MB
export const INVOICE_MIME = ["application/pdf", "image/jpeg", "image/png"];

export function validateInvoiceFile(file: File): string | null {
  if (!INVOICE_MIME.includes(file.type)) {
    return "constructionExpenses.formErrors.invoiceInvalidType";
  }
  if (file.size > MAX_INVOICE_BYTES) {
    return "constructionExpenses.formErrors.invoiceTooLarge";
  }
  return null;
}
