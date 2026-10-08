import { z } from "zod";

/**
 * O formulário de um feriado. Espelha o `HolidayUpsertDTO`: data, nome e âmbito
 * obrigatórios, concelho só nos municipais — o backend recusa um municipal sem
 * concelho (`ABS_003`) e um nacional com concelho (`ABS_004`), e o formulário
 * resolve as duas coisas antes de chegar lá (o campo só aparece em municipal).
 * Mensagens são chaves i18n (`attendance.formErrors.*`).
 */

/** Nome de feriado: "Dia de Portugal", "S. João (concelho)". */
const HOLIDAY_NAME = /^[\p{L}\p{N}\s.,'()/–—-]+$/u;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const HolidayFormSchema = z
  .object({
    date: z.string().regex(ISO_DATE, "attendance.formErrors.dateRequired"),
    name: z
      .string()
      .trim()
      .min(1, "attendance.formErrors.nameRequired")
      .max(120, "attendance.formErrors.nameTooLong")
      .regex(HOLIDAY_NAME, "attendance.formErrors.nameInvalid"),
    scope: z.enum(["NATIONAL", "MUNICIPAL"]),
    municipality: z
      .string()
      .trim()
      .max(120, "attendance.formErrors.nameTooLong")
      .regex(HOLIDAY_NAME, "attendance.formErrors.nameInvalid")
      .or(z.literal("")),
  })
  .superRefine((values, ctx) => {
    if (values.scope === "MUNICIPAL" && !values.municipality) {
      ctx.addIssue({
        code: "custom",
        message: "attendance.formErrors.municipalityRequired",
        path: ["municipality"],
      });
    }
  });

export type HolidayForm = z.infer<typeof HolidayFormSchema>;
