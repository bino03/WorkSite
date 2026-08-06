import { z } from "zod";

/**
 * Espelha as validações do `ConstructionExpenseUpsertDTO`: quatro campos
 * obrigatórios. As mensagens são chaves i18n, renderizadas com `t(...)`.
 */
export const expenseFormSchema = z.object({
  name: z.string().min(1, "budget.formErrors.nameRequired"),
  description: z.string().optional(),
  expenseDate: z.string().min(1, "budget.formErrors.dateRequired"),
  unit: z.string().optional(),
  quantity: z.number().nonnegative("budget.formErrors.quantityNonNegative").nullable().optional(),
  unitPrice: z.number().nonnegative("budget.formErrors.unitPriceNonNegative").nullable().optional(),
  totalPrice: z
    .number({ message: "budget.formErrors.totalRequired" })
    .positive("budget.formErrors.totalPositive"),
  observations: z.string().optional(),
  supplierNif: z.string().optional(),
  invoiceNumber: z.string().optional(),
  invoiceAtcud: z.string().optional(),
});

export type ExpenseFormValues = z.infer<typeof expenseFormSchema>;

/** As datas da rubrica são ambas opcionais; o backend recusa fim antes de início. */
export const budgetDatesSchema = z
  .object({
    startDate: z.string().optional(),
    endDate: z.string().optional(),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, {
    message: "budget.formErrors.endBeforeStart",
    path: ["endDate"],
  });

export type BudgetDatesValues = z.infer<typeof budgetDatesSchema>;
