/**
 * Entrega um Blob ao browser como download, com o nome dado.
 *
 * Cria um `<a download>` temporário porque é a única forma de dar nome ao
 * ficheiro sem sair da página; o `objectURL` é revogado logo a seguir para
 * não ficar a ocupar memória (o `AuthenticatedImage` faz o mesmo ao desmontar).
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/**
 * O nome do ficheiro do `Content-Disposition`. Prefere o `filename*=UTF-8''…`
 * (RFC 5987), que é o que o backend escreve porque os slugs têm acentos e
 * espaços; cai para o `filename="…"` cru, e por fim para o `fallback`.
 */
export function fileNameFromDisposition(header: string | undefined, fallback: string): string {
  if (!header) return fallback;
  const utf8 = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header);
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1].trim());
    } catch {
      // percent-encoding partido — tenta o filename simples
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header);
  return plain ? plain[1].trim() : fallback;
}
