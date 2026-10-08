import { z } from "zod";

/**
 * Registar ou corrigir uma picagem. Em relação ao `TimeEntryUpsertDTO`, o
 * formulário é **mais estrito em dois pontos, por decisão** (2026-10-08):
 *
 * - **a obra é obrigatória** — sem ela as horas caem na linha "sem obra" do
 *   relatório por obra, que é metade do valor do relatório;
 * - **o motivo é obrigatório ao corrigir** — é o que fica no histórico de
 *   revisões, e uma correção sem justificação não se defende depois.
 *
 * O backend aceita ambos vazios; ser mais estrito aqui é permitido, o contrário
 * não seria. Mensagens são chaves i18n (`attendance.formErrors.*`).
 */

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
/** Prosa livre: rejeita `< > ` { } $`. */
const FREE_TEXT = /^[\p{L}\p{N}\s.,;:!?()'"/–—€%ºª+&-]*$/u;

export const TimeEntryFormSchema = z.object({
  /** Vem fixo quando o drawer é aberto de dentro de um dia; escolhe-se quando não. */
  profileId: z.string().uuid("attendance.formErrors.profileRequired"),
  day: z.string().regex(ISO_DATE, "attendance.formErrors.dateRequired"),
  time: z.string().regex(TIME, "attendance.formErrors.timeInvalid"),
  direction: z.enum(["IN", "OUT"]),
  enterpriseId: z.string().uuid("attendance.formErrors.enterpriseRequired"),
  note: z.string().max(500, "attendance.formErrors.notesTooLong").regex(FREE_TEXT, "attendance.formErrors.textInvalid"),
  /** Vazio é aceite ao registar; ao corrigir, o drawer exige-o. */
  reason: z.string().max(500, "attendance.formErrors.notesTooLong"),
});

export type TimeEntryForm = z.infer<typeof TimeEntryFormSchema>;
