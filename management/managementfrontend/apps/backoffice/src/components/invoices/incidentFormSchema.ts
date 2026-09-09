import { z } from "zod";

/**
 * Registar uma inconsistência. O `body` é markdown, por isso não leva regex de
 * lista branca (o markdown precisa de `# * [ ] -` …); rejeita-se só `<`/`>` para
 * cortar HTML em bruto — o `react-markdown` já não o interpreta, isto é reforço.
 *
 * Mensagens são chaves i18n (`invoices.formErrors.*`).
 */
const SAFE_TITLE = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª-]+$/u;

export const IncidentSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "invoices.formErrors.titleRequired")
    .max(200)
    .regex(SAFE_TITLE, "invoices.formErrors.transferReasonInvalid"),
  body: z
    .string()
    .trim()
    .min(1, "invoices.formErrors.bodyRequired")
    .max(10000)
    .refine((v) => !/[<>]/.test(v), "invoices.formErrors.transferReasonInvalid"),
  invoiceIds: z.array(z.string().uuid()).min(1, "invoices.formErrors.invoicesRequired"),
});

export type IncidentForm = z.infer<typeof IncidentSchema>;
