/**
 * Compressão de fotos de faturas antes do upload.
 *
 * Uma foto de fatura tirada com o telemóvel anda nos 4-8 MB. Multiplicado pelas
 * dezenas de faturas de uma obra, isso enche o Storage sem trazer nada: o que
 * interessa numa fatura é o texto e o QR, não a resolução do sensor. Reduzir o
 * lado maior e reencodar em JPEG corta ~90% sem tocar no que se lê.
 *
 * Sem dependências: `createImageBitmap` + `<canvas>` chegam.
 */

/**
 * 2200px é deliberadamente conservador. O QR da AT é lido **no servidor**, a
 * partir deste ficheiro já comprimido — se a resolução descer de mais, o
 * `AtInvoiceQrService` deixa de o descodificar e perde-se o preenchimento
 * automático, que é o que torna o registo em massa viável. Se algum dia o QR
 * falhar em faturas que antes liam, subir isto antes de mexer na qualidade.
 */
const DEFAULT_MAX_EDGE = 2200;
const DEFAULT_QUALITY = 0.82;

export interface CompressionResult {
  /** O ficheiro a enviar — o comprimido, ou o original se comprimir não compensou. */
  file: File;
  originalSize: number;
  compressedSize: number;
  /** `false` quando o ficheiro passou intacto (PDF, imagem já pequena, falha). */
  compressed: boolean;
}

export interface CompressionOptions {
  maxEdge?: number;
  quality?: number;
}

/**
 * Comprime uma imagem de fatura. **PDFs passam intactos** — já são pequenos e
 * recomprimi-los no browser é pouco fiável.
 *
 * Nunca lança: se algo correr mal devolve o ficheiro original. Falhar a
 * compressão não pode custar o carregamento da fatura.
 */
export async function compressInvoiceFile(
  file: File,
  { maxEdge = DEFAULT_MAX_EDGE, quality = DEFAULT_QUALITY }: CompressionOptions = {}
): Promise<CompressionResult> {
  const unchanged: CompressionResult = {
    file,
    originalSize: file.size,
    compressedSize: file.size,
    compressed: false,
  };

  if (!file.type.startsWith("image/")) return unchanged;

  try {
    // `imageOrientation` não é opcional: sem ele as fotos de telemóvel, que
    // guardam a rotação em EXIF, saem deitadas do canvas.
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });

    const longest = Math.max(bitmap.width, bitmap.height);
    const factor = longest <= maxEdge ? 1 : maxEdge / longest;
    const width = Math.max(1, Math.round(bitmap.width * factor));
    const height = Math.max(1, Math.round(bitmap.height * factor));

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;

    const ctx = canvas.getContext("2d");
    if (!ctx) {
      bitmap.close();
      return unchanged;
    }
    // Fundo branco: um PNG com transparência sobre JPEG sairia preto.
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", quality)
    );
    canvas.width = 0;
    canvas.height = 0;

    // Uma imagem já pequena ou já bem comprimida pode crescer ao ser
    // reencodada — nesse caso o original é a melhor escolha.
    if (!blob || blob.size >= file.size) return unchanged;

    return {
      file: new File([blob], toJpegName(file.name), {
        type: "image/jpeg",
        lastModified: file.lastModified,
      }),
      originalSize: file.size,
      compressedSize: blob.size,
      compressed: true,
    };
  } catch {
    return unchanged;
  }
}

/** O conteúdo passou a JPEG; a extensão tem de acompanhar. */
function toJpegName(name: string): string {
  const dot = name.lastIndexOf(".");
  const base = dot > 0 ? name.slice(0, dot) : name;
  return `${base}.jpg`;
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
