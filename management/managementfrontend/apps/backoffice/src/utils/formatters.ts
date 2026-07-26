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
