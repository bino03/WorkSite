import { z } from "zod";
import { WEEKDAYS } from "@/types/attendance";

/**
 * O formulário de um horário. Tem sempre as 7 linhas da semana; `enabled` diz
 * se é dia de trabalho — um dia desligado não vai para o backend (é assim que
 * fins de semana e horários parciais se exprimem, sem flag nenhuma).
 *
 * As regras espelham as do backend: saída depois da entrada (`SCHED_005` — um dia
 * não atravessa a meia-noite), pausa menor que o período (`SCHED_006`), pelo
 * menos um dia (`@NotEmpty`). Mensagens são chaves i18n (`attendance.formErrors.*`).
 */

/** Nome de horário: letras, números e a pontuação de "08–17 c/ 1h (verão)". */
const SCHEDULE_NAME = /^[\p{L}\p{N}\s.,'()/:+&–—-]+$/u;
/** Prosa livre: rejeita `< > ` { } $`. */
const FREE_TEXT = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª+&-]*$/u;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

export const toMinutes = (time: string): number => {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * 60 + minutes;
};

const DaySchema = z
  .object({
    weekday: z.enum(WEEKDAYS as [string, ...string[]]),
    enabled: z.boolean(),
    startTime: z.string(),
    endTime: z.string(),
    breakMinutes: z.number().int().min(0, "attendance.formErrors.breakNegative").max(600),
  })
  .superRefine((day, ctx) => {
    if (!day.enabled) return;
    if (!TIME.test(day.startTime) || !TIME.test(day.endTime)) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.timeInvalid", path: ["startTime"] });
      return;
    }
    const span = toMinutes(day.endTime) - toMinutes(day.startTime);
    if (span <= 0) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.endBeforeStart", path: ["endTime"] });
    } else if (day.breakMinutes >= span) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.breakTooLong", path: ["breakMinutes"] });
    }
  });

export const WorkScheduleFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "attendance.formErrors.nameRequired")
    .max(120, "attendance.formErrors.nameTooLong")
    .regex(SCHEDULE_NAME, "attendance.formErrors.nameInvalid"),
  notes: z.string().max(500, "attendance.formErrors.notesTooLong").regex(FREE_TEXT, "attendance.formErrors.textInvalid"),
  days: z
    .array(DaySchema)
    .length(7)
    .refine((days) => days.some((day) => day.enabled), "attendance.formErrors.atLeastOneDay"),
});

export type WorkScheduleForm = z.infer<typeof WorkScheduleFormSchema>;
