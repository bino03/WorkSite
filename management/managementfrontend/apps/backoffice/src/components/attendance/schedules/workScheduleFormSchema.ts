import { z } from "zod";
import { WEEKDAYS } from "@/types/attendance";

/**
 * O formulário de um horário. Tem sempre as 7 linhas da semana; `enabled` diz
 * se é dia de trabalho — um dia desligado não vai para o backend (é assim que
 * fins de semana e horários parciais se exprimem, sem flag nenhuma).
 *
 * Desde 2026-11-20 o almoço pica-se, e o horário diz **quando** é (`lunchStart`/
 * `lunchEnd`, as duas ou nenhuma — vazio = dia de um só período). As regras espelham
 * as do backend: saída depois da entrada (`SCHED_005` — um dia não atravessa a
 * meia-noite), almoço dentro do dia (`SCHED_009`), pelo menos um dia (`@NotEmpty`).
 * Mensagens são chaves i18n (`attendance.formErrors.*`).
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

/** Minutos previstos de um dia: o período menos o almoço, se houver. */
export const expectedDayMinutes = (day: { startTime: string; endTime: string; lunchStart: string; lunchEnd: string }) => {
  if (!TIME.test(day.startTime) || !TIME.test(day.endTime)) return 0;
  const span = toMinutes(day.endTime) - toMinutes(day.startTime);
  if (span <= 0) return 0;
  const lunch =
    TIME.test(day.lunchStart) && TIME.test(day.lunchEnd) ? Math.max(0, toMinutes(day.lunchEnd) - toMinutes(day.lunchStart)) : 0;
  return Math.max(0, span - lunch);
};

const DaySchema = z
  .object({
    weekday: z.enum(WEEKDAYS as [string, ...string[]]),
    enabled: z.boolean(),
    startTime: z.string(),
    endTime: z.string(),
    /** "" = sem almoço. */
    lunchStart: z.string(),
    lunchEnd: z.string(),
  })
  .superRefine((day, ctx) => {
    if (!day.enabled) return;
    if (!TIME.test(day.startTime) || !TIME.test(day.endTime)) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.timeInvalid", path: ["startTime"] });
      return;
    }
    const start = toMinutes(day.startTime);
    const end = toMinutes(day.endTime);
    if (end <= start) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.endBeforeStart", path: ["endTime"] });
      return;
    }
    const hasLunchStart = day.lunchStart !== "";
    const hasLunchEnd = day.lunchEnd !== "";
    if (!hasLunchStart && !hasLunchEnd) return;
    if (hasLunchStart !== hasLunchEnd || !TIME.test(day.lunchStart) || !TIME.test(day.lunchEnd)) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.lunchInvalid", path: ["lunchStart"] });
      return;
    }
    const lunchStart = toMinutes(day.lunchStart);
    const lunchEnd = toMinutes(day.lunchEnd);
    if (!(start < lunchStart && lunchStart < lunchEnd && lunchEnd < end)) {
      ctx.addIssue({ code: "custom", message: "attendance.formErrors.lunchInvalid", path: ["lunchStart"] });
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
