import { z } from "zod";

/**
 * Registar uma fatura sem ficheiro.
 *
 * Todos os campos fiscais são opcionais de propósito: a fatura que ainda está
 * por pedir ao fornecedor não tem número nem total, e obrigar a inventá-los era
 * o que impedia a app de a representar antes da V24.
 *
 * As mensagens são chaves i18n (`invoices.formErrors.*`), renderizadas com
 * `t(...)` — uma string PT fixa aqui é um buraco de tradução silencioso.
 */
export const InvoiceRegisterSchema = z
  .object({
    scope: z.enum(["PROJECT", "COMPANY", "UNIDENTIFIED"]),
    enterpriseId: z.string().uuid().nullable(),
    supplierName: z.string().max(255, "invoices.formErrors.supplierNameTooLong"),
    supplierNif: z.string().regex(/^[0-9]*$/, "invoices.formErrors.nifInvalid").max(20),
    invoiceType: z.string(),
    invoiceNumber: z.string().max(100, "invoices.formErrors.numberTooLong"),
    invoiceAtcud: z.string().max(100, "invoices.formErrors.atcudTooLong"),
    /** ISO, ou vazio. O `PastOrPresent` do backend é espelhado no refine abaixo. */
    invoiceDate: z.string(),
    totalAmount: z.number().nonnegative("invoices.formErrors.totalNegative").nullable(),
    description: z.string().max(500, "invoices.formErrors.descriptionTooLong"),
    documentStatus: z.enum(["MISSING", "TO_PRINT", "TO_REQUEST"]),
    possibleEnterprises: z.string().max(500, "invoices.formErrors.textTooLong"),
    askWhom: z.string().max(255, "invoices.formErrors.textTooLong"),
    notes: z.string().max(2000, "invoices.formErrors.notesTooLong"),
  })
  // O mesmo par que o `ck_invoice_scope_enterprise` impõe na base de dados.
  // Duplicá-lo aqui poupa uma ida ao servidor para descobrir o óbvio.
  .refine((v) => (v.scope === "PROJECT" ? v.enterpriseId !== null : v.enterpriseId === null), {
    message: "invoices.formErrors.enterpriseRequired",
    path: ["enterpriseId"],
  })
  .refine((v) => !v.invoiceDate || v.invoiceDate <= new Date().toISOString().slice(0, 10), {
    message: "invoices.formErrors.dateInFuture",
    path: ["invoiceDate"],
  });

export type InvoiceRegisterForm = z.infer<typeof InvoiceRegisterSchema>;
