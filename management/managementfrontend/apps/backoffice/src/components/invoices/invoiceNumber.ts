/**
 * O número de uma fatura portuguesa vem sempre em duas partes — "FR 2026/114":
 * o **tipo de documento** (um de meia dúzia de códigos fixos da AT) e a
 * **série/número** do fornecedor. É esse o formato do campo `G` do QR
 * (ver `AtInvoiceQrService` no backend), e é por isso o formato que fica
 * gravado em `invoiceNumber`.
 *
 * Quando o QR não se lê e alguém tem de preencher à mão, o tipo é a única
 * parte previsível: escolhe-se numa lista (já com o mais usado no projeto
 * pré-selecionado) em vez de se escrever. Só a série/número é que se escreve.
 */

export interface InvoiceTypeOption {
  value: string;
  label: string;
}

/**
 * Os tipos que aparecem numa obra, do mais comum para o menos. O código é o
 * campo `D` do QR da AT.
 */
export const INVOICE_TYPES: InvoiceTypeOption[] = [
  { value: "FT", label: "FT · Fatura" },
  { value: "FR", label: "FR · Fatura-recibo" },
  { value: "FS", label: "FS · Fatura simplificada" },
  { value: "VD", label: "VD · Venda a dinheiro" },
  { value: "NC", label: "NC · Nota de crédito" },
  { value: "ND", label: "ND · Nota de débito" },
];

/** Sugestão de recurso, quando não há faturas no projeto por onde adivinhar. */
export const DEFAULT_INVOICE_TYPE = "FT";

const KNOWN_TYPES = new Set(INVOICE_TYPES.map((option) => option.value));

export const isKnownInvoiceType = (type: string) => KNOWN_TYPES.has(type.toUpperCase());

/**
 * Duas a quatro letras seguidas de espaço ("FT 2026/114") ou coladas ao
 * número ("FT2026/114") — as duas grafias aparecem em faturas reais. Sem nada
 * depois das letras não há prefixo nenhum: "Fatura" é o número todo, não um
 * tipo.
 */
const SPLIT_PATTERN = /^([A-Za-z]{2,4})(?:\s+|(?=\d))(\S.*)$/;

/** "FR 2026/114" → `{ type: "FR", rest: "2026/114" }`. Sem prefixo, `type` fica vazio. */
export function splitInvoiceNumber(value: string | null | undefined): { type: string; rest: string } {
  const trimmed = (value ?? "").trim();
  if (!trimmed) return { type: "", rest: "" };

  const match = SPLIT_PATTERN.exec(trimmed);
  if (!match) return { type: "", rest: trimmed };

  return { type: match[1].toUpperCase(), rest: match[2].trim() };
}

/**
 * O inverso. Um tipo sozinho não identifica documento nenhum — sem série/número
 * devolve vazio, para a fatura ficar com o campo por preencher em vez de com um
 * "FR" solto.
 */
export function joinInvoiceNumber(type: string, rest: string): string {
  const number = rest.trim();
  if (!number) return "";

  const prefix = type.trim().toUpperCase();
  return prefix ? `${prefix} ${number}` : number;
}

/**
 * O tipo mais usado nas faturas já registadas — o que se pré-seleciona para
 * quem preenche à mão. Conta também tipos fora da lista: se a obra receber
 * sempre "FA", é "FA" que deve aparecer escolhido. Sem histórico nenhum fica
 * o {@link DEFAULT_INVOICE_TYPE}.
 */
export function suggestInvoiceType(invoices: { invoiceNumber: string | null }[]): string {
  const counts = new Map<string, number>();

  for (const invoice of invoices) {
    const { type } = splitInvoiceNumber(invoice.invoiceNumber);
    if (type) counts.set(type, (counts.get(type) ?? 0) + 1);
  }

  let best = DEFAULT_INVOICE_TYPE;
  let bestCount = 0;
  for (const [type, count] of counts) {
    if (count > bestCount) {
      best = type;
      bestCount = count;
    }
  }
  return best;
}
