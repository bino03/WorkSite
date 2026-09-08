import { z } from "zod";

/**
 * Registar uma nota de crédito a partir de uma fatura lançada.
 *
 * Só o valor é obrigatório: o âmbito, a obra e (por omissão) o NIF herdam da
 * fatura de origem e nem sequer se enviam. O valor é **positivo** — o sinal
 * negativo aparece só nas despesas que a NC gera.
 *
 * As mensagens são chaves i18n (`invoices.formErrors.*`), renderizadas com
 * `t(...)`, como no resto dos formulários de fatura.
 */
export const CreditNoteSchema = z
  .object({
    /** Null enquanto a caixa está vazia; o `refine` abaixo é que a torna obrigatória. */
    totalAmount: z.number().positive("invoices.formErrors.amountPositive").nullable(),
    invoiceType: z.string(),
    invoiceNumber: z.string().max(100, "invoices.formErrors.numberTooLong"),
    invoiceAtcud: z.string().max(100, "invoices.formErrors.atcudTooLong"),
    /** ISO, ou vazio. Espelha o `PastOrPresent` do backend no refine abaixo. */
    invoiceDate: z.string(),
    supplierNif: z.string().regex(/^[0-9]*$/, "invoices.formErrors.nifInvalid").max(20),
    description: z.string().max(500, "invoices.formErrors.descriptionTooLong"),
    documentStatus: z.enum(["MISSING", "TO_PRINT", "TO_REQUEST"]),
    notes: z.string().max(2000, "invoices.formErrors.notesTooLong"),
  })
  .refine((v) => v.totalAmount !== null, {
    message: "invoices.formErrors.creditNoteAmountRequired",
    path: ["totalAmount"],
  })
  .refine((v) => !v.invoiceDate || v.invoiceDate <= new Date().toISOString().slice(0, 10), {
    message: "invoices.formErrors.dateInFuture",
    path: ["invoiceDate"],
  });

export type CreditNoteForm = z.infer<typeof CreditNoteSchema>;
