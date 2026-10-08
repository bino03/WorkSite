/**
 * Tipos do módulo de assiduidade (espaço Equipa). Espelham os DTOs de
 * `dto/attendance/` do backend — ver docs/api.md, secções de assiduidade.
 * Datas `LocalDate` chegam como "YYYY-MM-DD"; horas `LocalTime` como "HH:mm";
 * instantes `OffsetDateTime` como ISO-8601 com offset.
 */

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export const WEEKDAYS: Weekday[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

export const WEEKDAY_LABEL: Record<Weekday, string> = {
  MONDAY: "Segunda",
  TUESDAY: "Terça",
  WEDNESDAY: "Quarta",
  THURSDAY: "Quinta",
  FRIDAY: "Sexta",
  SATURDAY: "Sábado",
  SUNDAY: "Domingo",
};

/** Para tabelas e resumos onde "Segunda" não cabe ("Seg–Sex", "Seg, Qua, Sex"). */
export const WEEKDAY_SHORT: Record<Weekday, string> = {
  MONDAY: "Seg",
  TUESDAY: "Ter",
  WEDNESDAY: "Qua",
  THURSDAY: "Qui",
  FRIDAY: "Sex",
  SATURDAY: "Sáb",
  SUNDAY: "Dom",
};

// ── Horários ──────────────────────────────────────────────

export interface WorkScheduleDay {
  id: string;
  weekday: Weekday;
  startTime: string;
  endTime: string;
  breakMinutes: number;
  /** Já sem a pausa. */
  expectedMinutes: number;
}

export interface WorkSchedule {
  id: string;
  name: string;
  notes: string | null;
  days: WorkScheduleDay[];
  weeklyMinutes: number;
  createdByName: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
}

export interface WorkScheduleDayUpsert {
  weekday: Weekday;
  startTime: string;
  endTime: string;
  breakMinutes: number;
}

export interface WorkScheduleUpsert {
  name: string;
  notes: string | null;
  /** Sempre a lista completa: gravar substitui, não acumula. */
  days: WorkScheduleDayUpsert[];
}

// ── Feriados ──────────────────────────────────────────────

export type HolidayScope = "NATIONAL" | "MUNICIPAL";

export interface Holiday {
  id: string;
  date: string;
  name: string;
  scope: HolidayScope;
  municipality: string | null;
}

export interface HolidayUpsert {
  date: string;
  name: string;
  scope: HolidayScope;
  municipality: string | null;
}

// ── Emprego ───────────────────────────────────────────────

export interface EmploymentTerm {
  id: string;
  workScheduleId: string;
  workScheduleName: string;
  vacationDaysPerYear: number;
  validFrom: string;
  validTo: string | null;
  current: boolean;
}

export interface Employment {
  id: string;
  profileId: string;
  profileName: string;
  hiredAt: string;
  endedAt: string | null;
  terms: EmploymentTerm[];
  currentTerm: EmploymentTerm | null;
}

export interface EmploymentCreate {
  profileId: string;
  hiredAt: string;
  endedAt: string | null;
  workScheduleId: string;
  vacationDaysPerYear: number;
}

export interface EmploymentTermCreate {
  workScheduleId: string;
  vacationDaysPerYear: number;
  validFrom: string;
}

// ── Picagens ──────────────────────────────────────────────

export type TimeDirection = "IN" | "OUT";
export type TimeEntryChange = "CREATE" | "UPDATE" | "DELETE" | "RESTORE";

export interface TimeEntry {
  id: string;
  profileId: string;
  profileName: string;
  enterpriseId: string | null;
  enterpriseName: string | null;
  happenedAt: string;
  /** O dia em Lisboa — não o dia do `happenedAt` em UTC. */
  localDate: string;
  direction: TimeDirection;
  source: "MANUAL";
  registeredByName: string | null;
  note: string | null;
  deletedAt: string | null;
}

export interface TimeEntryUpsert {
  profileId: string;
  enterpriseId: string | null;
  happenedAt: string;
  direction: TimeDirection;
  note: string | null;
  /** Obrigatório na prática ao corrigir: fica no rasto de auditoria. */
  reason: string | null;
}

/**
 * Os motivos que se repetem ao mexer numa picagem. Escolher de uma lista em vez
 * de escrever à mão faz o histórico de revisões ficar comparável — "Engano" e
 * "engano meu" são a mesma coisa e não deviam ser dois textos diferentes.
 * `OUTRO` abre um campo livre; o que vai para a API é sempre o texto final.
 */
export const TIME_ENTRY_REASONS = [
  "Engano no registo",
  "O funcionário esqueceu-se de picar",
  "Falta a saída do dia",
  "Hora errada",
  "Obra errada",
  "Picagem duplicada",
  "Avaria ou falha do sistema",
  "Correção pedida pelo encarregado",
] as const;

/** Valor do seletor que abre o campo de texto livre. */
export const REASON_OTHER = "OUTRO";

export interface TimeEntryRevision {
  id: string;
  change: TimeEntryChange;
  changedByName: string;
  changedAt: string;
  reason: string | null;
  previousHappenedAt: string | null;
  previousDirection: TimeDirection | null;
  previousEnterpriseId: string | null;
  previousNote: string | null;
  previousDeletedAt: string | null;
}

// ── Ausências ─────────────────────────────────────────────

export type AbsenceType = "VACATION" | "SICK_LEAVE" | "JUSTIFIED" | "UNJUSTIFIED" | "OTHER";
export type AbsenceHalfDay = "NONE" | "MORNING" | "AFTERNOON";
export type AbsenceStatus = "PENDING" | "APPROVED" | "REJECTED";

export const ABSENCE_TYPE_LABEL: Record<AbsenceType, string> = {
  VACATION: "Férias",
  SICK_LEAVE: "Baixa",
  JUSTIFIED: "Falta justificada",
  UNJUSTIFIED: "Falta injustificada",
  OTHER: "Outra",
};

export const ABSENCE_STATUS_LABEL: Record<AbsenceStatus, string> = {
  PENDING: "Pendente",
  APPROVED: "Aprovada",
  REJECTED: "Recusada",
};

export interface AbsenceDocument {
  id: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  uploadedAt: string;
  url: string | null;
}

export interface Absence {
  id: string;
  profileId: string;
  profileName: string;
  type: AbsenceType;
  startsOn: string;
  endsOn: string;
  halfDay: AbsenceHalfDay;
  status: AbsenceStatus;
  note: string | null;
  requestedByName: string | null;
  approvedByName: string | null;
  approvedAt: string | null;
  workingDays: number;
  /** Só vêm (assinados) no detalhe; na lista é vazio. */
  documents: AbsenceDocument[];
  deletedAt: string | null;
}

export interface AbsenceUpsert {
  profileId: string;
  type: AbsenceType;
  startsOn: string;
  endsOn: string;
  halfDay: AbsenceHalfDay;
  note: string | null;
}

export interface VacationBalance {
  profileId: string;
  profileName: string;
  year: number;
  entitled: number;
  taken: number;
  pending: number;
  available: number;
}

// ── Resumos e relatórios ──────────────────────────────────

export type DayStatus = "WORKED" | "MISSING" | "NOT_SCHEDULED" | "NO_SCHEDULE" | "HOLIDAY" | "ON_LEAVE";

export const DAY_STATUS_LABEL: Record<DayStatus, string> = {
  WORKED: "Trabalhou",
  MISSING: "Falta",
  NOT_SCHEDULED: "Folga",
  NO_SCHEDULE: "Sem horário",
  HOLIDAY: "Feriado",
  ON_LEAVE: "Ausência",
};

export interface WorkedAtEnterprise {
  /** null = picagem sem obra. */
  enterpriseId: string | null;
  enterpriseName: string | null;
  workedMinutes: number;
}

export interface DayAttendance {
  date: string;
  workedMinutes: number;
  expectedMinutes: number;
  overtimeMinutes: number;
  latenessMinutes: number;
  status: DayStatus;
  firstIn: string | null;
  lastOut: string | null;
  incomplete: boolean;
  needsAttention: boolean;
  holidayName: string | null;
  absenceType: AbsenceType | null;
  enterprises: WorkedAtEnterprise[];
}

export interface AttendanceSummary {
  profileId: string;
  profileName: string | null;
  from: string;
  to: string;
  workedMinutes: number;
  expectedMinutes: number;
  overtimeMinutes: number;
  latenessMinutes: number;
  daysWorked: number;
  daysMissing: number;
  daysIncomplete: number;
  daysOnLeave: number;
  daysHoliday: number;
  scheduleChanges: number;
  days: DayAttendance[];
}

export interface EmployeeAtEnterprise {
  profileId: string;
  profileName: string | null;
  workedMinutes: number;
  daysWorked: number;
}

export interface EnterpriseMonth {
  enterpriseId: string | null;
  enterpriseName: string | null;
  workedMinutes: number;
  employees: EmployeeAtEnterprise[];
}

export interface AttendanceMonthReport {
  from: string;
  to: string;
  employees: AttendanceSummary[];
  enterprises: EnterpriseMonth[];
}
