package com.management.managementapi.enterprises.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lê o QR code que a AT tornou obrigatório nas faturas portuguesas.
 *
 * É preferível a OCR por ser determinístico: o QR traz os campos fiscais já
 * estruturados e assinados, não há adivinhação de layout. O formato é uma lista
 * de pares {@code campo:valor} separados por {@code *}:
 *
 * <pre>
 * A:509442013*B:999999990*C:PT*D:FT*E:N*F:20260115*G:FT 2026/114*
 * H:CSDF7T5H-0114*I1:PT*I7:12000.00*I8:2760.00*N:2760.00*O:14760.00*Q:kLp0*R:9999
 * </pre>
 *
 * Interessam-nos: {@code A} NIF do emitente · {@code D} tipo de documento ·
 * {@code E} estado · {@code F} data · {@code G} nº do documento · {@code H}
 * ATCUD · {@code N} total de impostos · {@code O} total com impostos.
 *
 * Nem toda a fatura tem QR (fornecedores estrangeiros, documentos antigos,
 * digitalizações más), por isso nada aqui lança exceção por não encontrar —
 * devolve vazio e o preenchimento segue manual.
 *
 * A procura é uma escalada, do barato para o caro: imagem inteira primeiro e
 * mosaico ampliado depois (ver {@link #decodeInTiles}). O caso que obriga ao
 * segundo degrau é a fotografia da fatura inteira tirada ao telemóvel, em que o
 * QR ocupa uma nesga da imagem.
 */
@Slf4j
@Service
public class AtInvoiceQrService {

    /**
     * Resoluções a tentar, por ordem. A 200 DPI resolve-se a esmagadora maioria
     * dos casos depressa; os 300 são a rede de segurança para o QR pequeno, e
     * só se pagam quando a primeira passagem falhou.
     */
    private static final int[] PDF_RENDER_DPI = { 200, 300 };

    /** O QR costuma estar na primeira ou última página; não vale a pena varrer tudo. */
    private static final int MAX_PDF_PAGES = 5;

    /**
     * Mosaico da segunda passagem: quadros de meia largura e meio quarto de
     * altura, avançando meio quadro de cada vez para nenhum QR cair sempre numa
     * junta. Os valores saíram de uma varredura contra 19 fotos reais de
     * faturas — grelhas mais finas do que isto não leram nenhuma a mais e
     * grelhas mais grossas perderam duas.
     */
    private static final int TILE_COLUMNS = 4;
    private static final int TILE_ROWS = 5;

    /** Quadros mais pequenos do que isto não chegam para conter um QR. */
    private static final int MIN_TILE_PIXELS = 60;

    /**
     * Ampliar o quadro dá ao detetor mais pixels por módulo. Só até um limite:
     * acima dele o quadro já tem resolução de sobra e duplicá-lo só gastava
     * memória — uma página A4 rasterizada a 300 DPI chega aqui com 1240 px de
     * largura por quadro.
     */
    private static final int TILE_UPSCALE = 2;
    private static final int UPSCALE_BELOW_PIXELS = 1400;

    private static final DateTimeFormatter AT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @return os campos do QR, ou {@link Optional#empty()} se não houver QR
     *         legível ou o conteúdo não for um QR da AT
     */
    public Optional<AtInvoiceData> read(byte[] content, String mimeType) {
        List<String> codes = decodeQr(content, mimeType);

        if (codes.isEmpty()) {
            log.info("[fatura-qr] nenhum QR encontrado no documento ({}, {} KB)",
                    mimeType, content.length / 1024);
            return Optional.empty();
        }

        for (String raw : codes) {
            Optional<AtInvoiceData> parsed = parse(raw);
            if (parsed.isPresent()) {
                log.info("[fatura-qr] QR da AT lido — NIF {} · doc {}",
                        parsed.get().issuerNif(), parsed.get().documentNumber());
                return parsed;
            }
        }

        // Encontrou QR, mas nenhum é da AT: multibanco, tracking do
        // transportador, link do fornecedor. Distinguir isto de "não há QR" é o
        // que evita horas a olhar para o scanner quando o problema é o papel.
        log.info("[fatura-qr] encontrado(s) {} QR mas nenhum é da AT (falta o campo A ou F). Início: \"{}\"",
                codes.size(), abbreviate(codes.get(0)));
        return Optional.empty();
    }

    // ── descodificação ────────────────────────────────────────

    private List<String> decodeQr(byte[] content, String mimeType) {
        List<String> found = new ArrayList<>();
        try {
            if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
                decodeFromPdf(content, found);
            } else {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                if (image == null) {
                    log.warn("[fatura-qr] o ficheiro não é uma imagem legível ({})", mimeType);
                    return List.of();
                }
                decodeFromImage(image, found);
            }
        } catch (Exception e) {
            log.warn("[fatura-qr] falha a descodificar: {}", e.toString());
        }
        return found;
    }

    /**
     * Escalada em três degraus, do barato para o caro: páginas inteiras a 200
     * DPI, depois a 300, e só então o mosaico. Cada degrau só se paga quando o
     * anterior falhou, e o PDF nascido de um ERP resolve-se logo no primeiro.
     */
    private void decodeFromPdf(byte[] content, List<String> found) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);

            for (int dpi : PDF_RENDER_DPI) {
                for (int page = 0; page < pages; page++) {
                    if (decodeWholeImage(renderer.renderImageWithDPI(page, dpi), found)) {
                        return;
                    }
                }
            }

            int finestDpi = PDF_RENDER_DPI[PDF_RENDER_DPI.length - 1];
            for (int page = 0; page < pages; page++) {
                if (decodeInTiles(renderer.renderImageWithDPI(page, finestDpi), found)) {
                    return;
                }
            }
        }
    }

    /**
     * A página inteira primeiro; o mosaico só se aquela não trouxer o QR da AT.
     *
     * A fatura pode ter mais do que um QR — o da AT e o do multibanco — por isso
     * encontrar um qualquer não é motivo para parar. Quem decide é
     * {@link #containsAtQr}, e é também por isso que se devolvem todos: o filtro
     * de qual interessa faz-se em {@link #read}.
     */
    private boolean decodeFromImage(BufferedImage image, List<String> found) {
        return decodeWholeImage(image, found) || decodeInTiles(image, found);
    }

    /**
     * Duas binarizações: a {@code Hybrid} lida melhor com fundos irregulares de
     * digitalizações, a {@code GlobalHistogram} com imagens limpas de contraste
     * uniforme. Uma apanha o que a outra falha.
     *
     * @return {@code true} quando já há um QR da AT e não vale a pena insistir
     */
    private boolean decodeWholeImage(BufferedImage image, List<String> found) {
        for (BinaryBitmap bitmap : binarizations(image)) {
            try {
                collect(new MultiFormatReader().decode(bitmap, hints()), found);
            } catch (NotFoundException ignored) {
                // esta binarização não encontrou nada; tenta a seguinte
            }
            if (containsAtQr(found)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recorta a imagem em quadros sobrepostos e tenta cada um ampliado.
     *
     * É isto que apanha a fotografia de fatura tirada ao papel inteiro. O QR da
     * AT ocupa ali uns 150 px de 1600, com três pixels por módulo e o borrão do
     * JPEG por cima; na imagem toda o detetor não o localiza, e as duas
     * binarizações não ajudam porque estimam o limiar a partir de uma página
     * quase toda branca. Isolado num quadro e ampliado, lê-se — contra as 19
     * faturas que motivaram isto, passou de 8 para 14 lidas.
     *
     * A sobreposição de meio quadro existe para o QR não cair sempre em cima de
     * uma junta, que é o que estragaria o recorte.
     */
    private boolean decodeInTiles(BufferedImage image, List<String> found) {
        int tileWidth = Math.max(MIN_TILE_PIXELS, image.getWidth() * 2 / TILE_COLUMNS);
        int tileHeight = Math.max(MIN_TILE_PIXELS, image.getHeight() * 2 / TILE_ROWS);

        for (int y = 0; y < image.getHeight(); y += tileHeight / 2) {
            for (int x = 0; x < image.getWidth(); x += tileWidth / 2) {
                int width = Math.min(tileWidth, image.getWidth() - x);
                int height = Math.min(tileHeight, image.getHeight() - y);
                if (width < MIN_TILE_PIXELS || height < MIN_TILE_PIXELS) {
                    continue;
                }
                if (decodeWholeImage(upscale(image.getSubimage(x, y, width, height)), found)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ampliação suave; devolve o original quando o quadro já é grande. */
    private static BufferedImage upscale(BufferedImage tile) {
        if (Math.max(tile.getWidth(), tile.getHeight()) >= UPSCALE_BELOW_PIXELS) {
            return tile;
        }
        int width = tile.getWidth() * TILE_UPSCALE;
        int height = tile.getHeight() * TILE_UPSCALE;

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        graphics.drawImage(tile.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();
        return scaled;
    }

    private static List<BinaryBitmap> binarizations(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        return List.of(
                new BinaryBitmap(new HybridBinarizer(source)),
                new BinaryBitmap(new GlobalHistogramBinarizer(source)));
    }

    private static Map<DecodeHintType, Object> hints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        // Digitalizações e fotos com pouca luz saem por vezes com o QR em
        // negativo; sem isto passavam por ilegíveis.
        hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
        return hints;
    }

    private static void collect(Result result, List<String> found) {
        if (result != null && result.getText() != null && !found.contains(result.getText())) {
            found.add(result.getText());
        }
    }

    /**
     * Reconhece o QR da AT pelos dois campos que ele tem sempre — NIF do
     * emitente e data. É a mesma condição que {@link #parse} aplica; aqui serve
     * para parar a procura mal apareça o código certo, sem confundir com o QR do
     * multibanco ou do tracking impressos na mesma fatura.
     */
    private static boolean containsAtQr(List<String> codes) {
        return codes.stream().anyMatch(raw -> {
            Map<String, String> fields = fields(raw);
            return fields.containsKey("A") && fields.containsKey("F");
        });
    }

    private static String abbreviate(String raw) {
        String clean = raw.replaceAll("\\s+", " ").trim();
        return clean.length() <= 60 ? clean : clean.substring(0, 60) + "…";
    }

    // ── parsing ───────────────────────────────────────────────

    /** Parte o conteúdo do QR nos seus pares {@code campo:valor}. */
    private static Map<String, String> fields(String raw) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : raw.split("\\*")) {
            int sep = pair.indexOf(':');
            if (sep > 0) {
                fields.put(pair.substring(0, sep).trim(), pair.substring(sep + 1).trim());
            }
        }
        return fields;
    }

    private Optional<AtInvoiceData> parse(String raw) {
        Map<String, String> fields = fields(raw);

        // sem NIF do emitente nem data não é um QR da AT — provavelmente é
        // outro QR qualquer impresso na fatura (multibanco, tracking, …)
        if (!fields.containsKey("A") || !fields.containsKey("F")) {
            return Optional.empty();
        }

        List<String> warnings = new ArrayList<>();
        LocalDate date = parseDate(fields.get("F"), warnings);
        BigDecimal total = parseAmount(fields.get("O"), warnings, "total do documento");
        BigDecimal tax = parseAmount(fields.get("N"), warnings, "total de impostos");

        String status = fields.get("E");
        if ("A".equalsIgnoreCase(status)) {
            warnings.add("O documento está anulado na AT (campo de estado = \"A\").");
        }
        String type = fields.get("D");
        if ("NC".equalsIgnoreCase(type)) {
            warnings.add("Isto é uma nota de crédito, não uma fatura — o valor abate à despesa.");
        }

        BigDecimal taxable = (total != null && tax != null) ? total.subtract(tax) : null;

        return Optional.of(new AtInvoiceData(
                fields.get("A"), fields.get("B"), type, status,
                fields.get("G"), fields.get("H"),
                date, taxable, tax, total, warnings));
    }

    private LocalDate parseDate(String value, List<String> warnings) {
        try {
            return LocalDate.parse(value, AT_DATE);
        } catch (Exception e) {
            warnings.add("A data no QR (\"" + value + "\") não está no formato AAAAMMDD — preencha à mão.");
            return null;
        }
    }

    private BigDecimal parseAmount(String value, List<String> warnings, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "."));
        } catch (NumberFormatException e) {
            warnings.add("O " + label + " no QR (\"" + value + "\") não é um número — preencha à mão.");
            return null;
        }
    }

    /** Campos úteis do QR da AT. */
    public record AtInvoiceData(
            String issuerNif,
            String buyerNif,
            String documentType,
            String documentStatus,
            String documentNumber,
            String atcud,
            LocalDate invoiceDate,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            List<String> warnings
    ) {}
}
