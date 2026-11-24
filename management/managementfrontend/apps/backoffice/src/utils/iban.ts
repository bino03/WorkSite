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

/** Agrupado de 4 em 4, como vem impresso: `PT50 0002 0123 …`. */
export const formatIban = (iban: string): string => iban.replace(/(.{4})(?=.)/g, "$1 ");
