import { z } from "zod";

/**
 * Os três formulários da ficha de emprego, no mesmo ficheiro porque partilham as
 * regras de data e de dias de férias (molde: `constructionFormSchemas.ts`).
 *
 * As regras espelham o `EmploymentService`: o fim não é antes da admissão, e um
 * período não começa antes dela (`ATT_003`). O que o formulário **não** valida
 * sozinho — um período novo ter de começar depois do que está em vigor
 * (`ATT_004`) — é verificado contra o período atual no próprio drawer.
 * Mensagens são chaves i18n (`attendance.formErrors.*`).
 */

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

const dateField = z.string().regex(ISO_DATE, "attendance.formErrors.dateRequired");

const vacationDaysField = z
  .number({ message: "attendance.formErrors.vacationDaysRequired" })
  .int("attendance.formErrors.vacationDaysRange")
  .min(0, "attendance.formErrors.vacationDaysRange")
  .max(365, "attendance.formErrors.vacationDaysRange");

const scheduleField = z.string().uuid("attendance.formErrors.scheduleRequired");

/** `""` em `endedAt` = ainda ao serviço. */
const endedAtField = dateField.or(z.literal(""));

const endNotBeforeHire = (
  values: { hiredAt: string; endedAt: string },
  ctx: z.RefinementCtx
) => {
  if (values.endedAt && values.endedAt < values.hiredAt) {
    ctx.addIssue({ code: "custom", message: "attendance.formErrors.endBeforeHired", path: ["endedAt"] });
  }
};

/**
 * Criar a ficha. Leva o primeiro período junto porque o backend não aceita uma
 * ficha sem horário nem dias de férias (`EmploymentUpsertDTO`).
 */
export const EmploymentCreateFormSchema = z
  .object({
    hiredAt: dateField,
    endedAt: endedAtField,
    workScheduleId: scheduleField,
    vacationDaysPerYear: vacationDaysField,
  })
  .superRefine(endNotBeforeHire);

export type EmploymentCreateForm = z.infer<typeof EmploymentCreateFormSchema>;

/** Corrigir as datas de uma ficha que já existe. */
export const EmploymentDatesFormSchema = z
  .object({
    hiredAt: dateField,
    endedAt: endedAtField,
  })
  .superRefine(endNotBeforeHire);

export type EmploymentDatesForm = z.infer<typeof EmploymentDatesFormSchema>;

/** Um período novo de condições: horário, dias de férias, a partir de uma data. */
export const EmploymentTermFormSchema = z.object({
  workScheduleId: scheduleField,
  vacationDaysPerYear: vacationDaysField,
  validFrom: dateField,
});

export type EmploymentTermForm = z.infer<typeof EmploymentTermFormSchema>;
