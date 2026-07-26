import { z } from "zod";

export const MAX_INVOICE_BYTES = 25 * 1024 * 1024; // 25 MB
export const INVOICE_MIME = ["application/pdf", "image/jpeg", "image/png"];

export const stageFormSchema = z.object({
  name: z.string().min(1, "constructionStages.formErrors.nameRequired"),
  description: z.string().optional(),
});
export type StageFormValues = z.infer<typeof stageFormSchema>;

export const subStageFormSchema = z.object({
  name: z.string().min(1, "constructionSubStages.formErrors.nameRequired"),
  description: z.string().optional(),
});
export type SubStageFormValues = z.infer<typeof subStageFormSchema>;

export const expenseFormSchema = z.object({
  name: z.string().min(1, "constructionExpenses.formErrors.nameRequired"),
  price: z
    .number({ error: "constructionExpenses.formErrors.priceRequired" })
    .positive("constructionExpenses.formErrors.pricePositive"),
});
export type ExpenseFormValues = z.infer<typeof expenseFormSchema>;

export function validateInvoiceFile(file: File): string | null {
  if (!INVOICE_MIME.includes(file.type)) {
    return "constructionExpenses.formErrors.invoiceInvalidType";
  }
  if (file.size > MAX_INVOICE_BYTES) {
    return "constructionExpenses.formErrors.invoiceTooLarge";
  }
  return null;
}
