/**
 * Uma página do Spring chega nesta API em duas formas:
 *
 *   - plana:    `{ content, number, size, totalElements, totalPages }`
 *   - `VIA_DTO`: `{ content, page: { number, size, totalElements, totalPages } }`
 *
 * O backend liga `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`
 * (em `ManagementApiApplication`), portanto a forma real hoje é a embrulhada.
 * Código que lê `res.totalElements` no topo recebe `undefined` — e o sintoma é
 * "0 resultado(s)" com linhas na tabela, ou um cartão a dizer "undefined".
 * `useTasks` e `invoiceService` já liam a forma certa; este normalizador aceita
 * as duas para o resto não ter de saber qual é.
 */
export interface SpringPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function normalizeSpringPage<T>(data: unknown): SpringPage<T> {
  const raw = (data ?? {}) as Record<string, unknown>;
  const meta = (raw.page ?? raw) as Record<string, unknown>;
  return {
    content: Array.isArray(raw.content) ? (raw.content as T[]) : [],
    number: Number(meta.number ?? 0),
    size: Number(meta.size ?? 0),
    totalElements: Number(meta.totalElements ?? 0),
    totalPages: Number(meta.totalPages ?? 0),
  };
}
