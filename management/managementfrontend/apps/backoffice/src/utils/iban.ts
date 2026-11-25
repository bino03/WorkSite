/**
 * IBAN — o mesmo que `util/Iban.java` no backend: normaliza (sem espaços nem
 * hífenes, maiúsculas) e valida o formato + mod-97. O backend volta a validar;
 * isto só serve para avisar antes de gravar.
 */

export const normalizeIban = (raw: string | null | undefined): string =>
  (raw ?? "").replace(/[\s-]/g, "").toUpperCase();

export const isValidIban = (iban: string): boolean => {
  if (!/^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/.test(iban)) return false;
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  // mod-97 por blocos, sem BigInt: o resto parcial nunca passa de 2 dígitos.
  let remainder = 0;
  for (const char of rearranged) {
    const value = /[0-9]/.test(char) ? char : String(char.charCodeAt(0) - 55);
    for (const digit of value) remainder = (remainder * 10 + Number(digit)) % 97;
  }
  return remainder === 1;
};

/**
 * Código de banco português (dígitos 5-8 do IBAN) → nome. Só os bancos de que
 * se tem a certeza; um código fora desta lista mostra "IBAN", nunca um palpite.
 */
const PT_BANKS: Record<string, string> = {
  "0007": "Novo Banco",
  "0010": "BPI",
  "0018": "Santander",
  "0019": "BBVA",
  "0023": "ActivoBank",
  "0033": "Millennium bcp",
  "0035": "CGD",
  "0036": "Banco Montepio",
  "0045": "Crédito Agrícola",
  "0061": "Banco BiG",
  "0079": "EuroBic",
  "0170": "ABANCA",
  "0186": "Banco Best",
  "0193": "Banco CTT",
  "0269": "Bankinter",
  "0781": "IGCP (Tesouro)",
};

/** O banco de um IBAN normalizado, ou null se não é português ou o código não está na lista. */
export const bankOfIban = (iban: string): string | null =>
  iban.startsWith("PT") ? PT_BANKS[iban.slice(4, 8)] ?? null : null;

/** Agrupado de 4 em 4, como vem impresso: `PT50 0002 0123 …`. */
export const formatIban = (iban: string): string => iban.replace(/(.{4})(?=.)/g, "$1 ");
