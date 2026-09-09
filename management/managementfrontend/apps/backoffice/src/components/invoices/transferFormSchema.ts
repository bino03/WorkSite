import { z } from "zod";

/**
 * Transferir uma fatura de âmbito/obra (fase 5).
 *
 * `reason` é obrigatória (`@NotBlank` no backend). `targetEnterpriseId` só entra
 * quando o destino é uma obra (`PROJECT`) — é o mesmo `ck_invoice_scope_enterprise`
 * da base de dados.
 *
 * Mensagens são chaves i18n (`invoices.formErrors.*`), renderizadas com `t(...)`.
 */

/** Prosa livre: letras, números, espaços e pontuação corrente. Rejeita `< > ` { } $`. */
const REASON_TEXT = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª-]+$/u;

export const TransferInvoiceSchema = z
  .object({
    targetScope: z.enum(["PROJECT", "COMPANY", "UNIDENTIFIED"]),
    /** Null enquanto não se escolhe; o `refine` torna-o obrigatório em `PROJECT`. */
    targetEnterpriseId: z.string().uuid().nullable(),
    reason: z
      .string()
      .trim()
      .min(1, "invoices.formErrors.transferReasonRequired")
      .max(1000, "invoices.formErrors.transferReasonTooLong")
      .regex(REASON_TEXT, "invoices.formErrors.transferReasonInvalid"),
  })
  .refine((v) => v.targetScope !== "PROJECT" || !!v.targetEnterpriseId, {
    message: "invoices.formErrors.transferEnterpriseRequired",
    path: ["targetEnterpriseId"],
  });

export type TransferInvoiceForm = z.infer<typeof TransferInvoiceSchema>;
