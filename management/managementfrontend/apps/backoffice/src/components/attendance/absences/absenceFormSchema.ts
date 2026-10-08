import { z } from "zod";

/**
 * Marcar ou corrigir uma ausência. Espelha o `AbsenceUpsertDTO`: funcionário,
 * tipo e as duas datas são obrigatórios; `halfDay` tem `NONE` por omissão (o
 * backend trata null como dia inteiro) e a nota é livre.
 *
 * A regra que o DTO não tem e o formulário impõe: **o fim não é antes do
 * início**. E meio-dia só faz sentido num único dia — num intervalo de vários
 * dias o campo desaparece, porque "metade da manhã de cinco dias" não quer
 * dizer nada. Mensagens são chaves i18n (`attendance.formErrors.*`).
 */

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
/** Prosa livre: rejeita `< > ` { } $`. */
const FREE_TEXT = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª+&-]*$/u;

export const AbsenceFormSchema = z
  .object({
    profileId: z.string().uuid("attendance.formErrors.profileRequired"),
    type: z.enum(["VACATION", "SICK_LEAVE", "JUSTIFIED", "UNJUSTIFIED", "OTHER"]),
    startsOn: z.string().regex(ISO_DATE, "attendance.formErrors.dateRequired"),
    endsOn: z.string().regex(ISO_DATE, "attendance.formErrors.dateRequired"),
    halfDay: z.enum(["NONE", "MORNING", "AFTERNOON"]),
    note: z.string().max(500, "attendance.formErrors.notesTooLong").regex(FREE_TEXT, "attendance.formErrors.textInvalid"),
  })
  .superRefine((values, ctx) => {
    if (values.endsOn < values.startsOn) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.endBeforeStart", path: ["endsOn"] });
    }
  });

export type AbsenceForm = z.infer<typeof AbsenceFormSchema>;
