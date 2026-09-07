import { z } from "zod";

/**
 * Formulários de pagamento (fase 2). Mensagens = chaves i18n
 * (`invoices.formErrors.*`), renderizadas com `t(...)`.
 */

const PAYMENT_METHODS = ["NUMERARIO", "MULTIBANCO", "TRANSFERENCIA", "OUTRO"] as const;

const notInFuture = (value: string) =>
  !value || value <= new Date().toISOString().slice(0, 10);

/** Marcar uma fatura como paga. `amount` opcional: por omissão paga o que falta. */
export const MarkPaidSchema = z
  .object({
    paidOn: z.string().min(1, "invoices.formErrors.paidOnRequired"),
    method: z.enum(PAYMENT_METHODS),
    amount: z.number().positive("invoices.formErrors.amountPositive").nullable(),
    reference: z.string().max(255, "invoices.formErrors.referenceTooLong"),
    notes: z.string().max(2000, "invoices.formErrors.notesTooLong"),
  })
  .refine((v) => notInFuture(v.paidOn), {
    message: "invoices.formErrors.paidOnInFuture",
    path: ["paidOn"],
  });

export type MarkPaidForm = z.infer<typeof MarkPaidSchema>;

/** Pagamento agregado: N faturas, 1 movimento. O `amount` do movimento é obrigatório. */
export const AggregatePaymentSchema = z
  .object({
    paidOn: z.string().min(1, "invoices.formErrors.paidOnRequired"),
    method: z.enum(PAYMENT_METHODS),
    amount: z.number().positive("invoices.formErrors.movementRequired"),
    reference: z.string().max(255, "invoices.formErrors.referenceTooLong"),
    notes: z.string().max(2000, "invoices.formErrors.notesTooLong"),
  })
  .refine((v) => notInFuture(v.paidOn), {
    message: "invoices.formErrors.paidOnInFuture",
    path: ["paidOn"],
  });

export type AggregatePaymentForm = z.infer<typeof AggregatePaymentSchema>;
