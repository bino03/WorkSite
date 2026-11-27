import { z } from "zod";

/** Formulário do recibo. Mensagens = chaves i18n (`invoices.receipt.formErrors.*`). */

const PAYMENT_METHODS = ["NUMERARIO", "MULTIBANCO", "TRANSFERENCIA", "OUTRO"] as const;

// Lista branca: letras, números, espaço e a pontuação que um nº de recibo ou um nome usam.
const SAFE_TEXT = /^[\p{L}\p{N}\s.,'&/()\-–]*$/u;

const today = () => new Date().toISOString().slice(0, 10);

export const ReceiptSchema = z
  .object({
    receiptNumber: z.string().max(60, "invoices.receipt.formErrors.numberTooLong").regex(SAFE_TEXT, "invoices.receipt.formErrors.invalidChars"),
    receiptDate: z.string().min(1, "invoices.receipt.formErrors.dateRequired"),
    totalAmount: z
      .number({ message: "invoices.receipt.formErrors.totalRequired" })
      .min(0, "invoices.receipt.formErrors.totalRequired")
      .max(1_000_000_000, "invoices.receipt.formErrors.totalRequired"),
    supplierName: z.string().max(255, "invoices.receipt.formErrors.supplierTooLong").regex(SAFE_TEXT, "invoices.receipt.formErrors.invalidChars"),
    supplierNif: z.string().max(20, "invoices.receipt.formErrors.nifInvalid").regex(/^[A-Za-z0-9\s]*$/, "invoices.receipt.formErrors.nifInvalid"),
    notes: z.string().max(2000, "invoices.receipt.formErrors.notesTooLong"),
    registerPayment: z.boolean(),
    method: z.enum(PAYMENT_METHODS),
    paidOn: z.string(),
    reference: z.string().max(255, "invoices.formErrors.referenceTooLong"),
  })
  .refine((v) => !v.registerPayment || v.paidOn.length > 0, {
    message: "invoices.formErrors.paidOnRequired",
    path: ["paidOn"],
  })
  .refine((v) => !v.registerPayment || v.paidOn <= today(), {
    message: "invoices.formErrors.paidOnInFuture",
    path: ["paidOn"],
  });

export type ReceiptForm = z.infer<typeof ReceiptSchema>;
