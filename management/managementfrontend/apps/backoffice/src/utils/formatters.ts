import dayjs from "dayjs";

/**
 * Format number as currency (EUR)
 */
export function formatCurrency(value: number | undefined): string {
  if (!value) return "€ 0,00";
  return new Intl.NumberFormat("pt-PT", {
    style: "currency",
    currency: "EUR"
  }).format(value);
}

/**
 * Format date as DD/MM/YYYY
 */
export function formatDate(date: string | undefined | null): string {
  if (!date) return "-";
  return dayjs(date).format("DD/MM/YYYY");
}

/**
 * Format date and time as DD/MM/YYYY HH:mm
 */
export function formatDateTime(date: string | undefined | null): string {
  if (!date) return "-";
  return dayjs(date).format("DD/MM/YYYY HH:mm");
}

/**
 * Format percentage
 */
export function formatPercentage(value: number | undefined | null): string {
  if (value === undefined || value === null) return "-";
  return `${value.toFixed(2)}%`;
}

/**
 * Check if date is in the past
 */
export function isPastDate(date: string | undefined | null): boolean {
  if (!date) return false;
  return dayjs(date).isBefore(dayjs(), "day");
}

/**
 * Check if date is today
 */
export function isToday(date: string | undefined | null): boolean {
  if (!date) return false;
  return dayjs(date).isSame(dayjs(), "day");
}

/**
 * Get days until date
 */
export function daysUntil(date: string | undefined | null): number | null {
  if (!date) return null;
  const diff = dayjs(date).diff(dayjs(), "day");
  return diff;
}

/**
 * Format relative time (e.g., "há 2 dias", "em 3 dias")
 */
export function formatRelativeTime(date: string | undefined | null): string {
  if (!date) return "-";
  return dayjs(date).fromNow();
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Percentagem poupada, para se poder mostrar "4,2 MB → 380 KB (−91%)". */
export function savingsPercent(originalSize: number | null, finalSize: number | null): number | null {
  if (!originalSize || !finalSize || originalSize <= finalSize) return null;
  return Math.round((1 - finalSize / originalSize) * 100);
}

/** Minutos como horas legíveis: 450 → "7h30", 480 → "8h", 0 → "0h". Negativos com sinal. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return "—";
  const sign = minutes < 0 ? "−" : "";
  const abs = Math.abs(Math.round(minutes));
  const hours = Math.floor(abs / 60);
  const rest = abs % 60;
  return `${sign}${hours}h${rest ? String(rest).padStart(2, "0") : ""}`;
}

const WEEKDAY_PT = ["Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"];
const MONTH_PT = [
  "janeiro", "fevereiro", "março", "abril", "maio", "junho",
  "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
];

/**
 * "2026-10-07" → "Quarta-feira, 7 de outubro". Escrito à mão e não com o locale
 * do dayjs de propósito: o `dayjs.locale("pt")` da app é um efeito global posto
 * dentro do `InvitesDrawer`, pelo que um ecrã que não o carregue formataria em
 * inglês sem ninguém dar por isso.
 */
export function formatLongDate(isoDate: string, withYear = false): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  const base = `${WEEKDAY_PT[date.getDay()]}, ${day} de ${MONTH_PT[month - 1]}`;
  return withYear ? `${base} de ${year}` : base;
}

/** "2026-10" → "outubro de 2026". */
export function formatMonth(isoMonth: string): string {
  const [year, month] = isoMonth.split("-").map(Number);
  return `${MONTH_PT[month - 1]} de ${year}`;
}
