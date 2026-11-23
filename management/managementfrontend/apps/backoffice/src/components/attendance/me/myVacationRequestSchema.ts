import { z } from "zod";

/**
 * Pedir férias pelo próprio. Espelha o `MyVacationRequestDTO`: as duas datas são
 * obrigatórias, o meio dia só vale num dia só (o campo desaparece num intervalo) e a
 * nota é livre. Mesmas regras e mensagens do `AbsenceFormSchema` do admin.
 */

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
/** Prosa livre: rejeita `< > ` { } $`. */
const FREE_TEXT = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª+&-]*$/u;

export const MyVacationRequestSchema = z
  .object({
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

export type MyVacationRequestForm = z.infer<typeof MyVacationRequestSchema>;
