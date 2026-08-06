package com.management.managementapi.enterprises.service;

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
 */
@Slf4j
@Service
public class AtInvoiceQrService {

    /** Chega para descodificar o QR sem tornar a rasterização cara. */
    private static final int PDF_RENDER_DPI = 200;

    /** O QR costuma estar na primeira ou última página; não vale a pena varrer tudo. */
    private static final int MAX_PDF_PAGES = 5;

    private static final DateTimeFormatter AT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * @return os campos do QR, ou {@link Optional#empty()} se não houver QR
     *         legível ou o conteúdo não for um QR da AT
     */
    public Optional<AtInvoiceData> read(byte[] content, String mimeType) {
        return decodeQr(content, mimeType).flatMap(this::parse);
    }

    // ── descodificação ────────────────────────────────────────

    private Optional<String> decodeQr(byte[] content, String mimeType) {
        try {
            if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
                return decodeFromPdf(content);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            return image == null ? Optional.empty() : decodeFromImage(image);
        } catch (Exception e) {
            log.warn("Não foi possível ler o QR da fatura: {}", e.toString());
            return Optional.empty();
        }
    }

    private Optional<String> decodeFromPdf(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);
            for (int page = 0; page < pages; page++) {
                Optional<String> found = decodeFromImage(renderer.renderImageWithDPI(page, PDF_RENDER_DPI));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Duas binarizações: a {@code Hybrid} lida melhor com fundos irregulares de
     * digitalizações, a {@code GlobalHistogram} com imagens limpas de contraste
     * uniforme. Uma apanha o que a outra falha.
     */
    private Optional<String> decodeFromImage(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        for (BinaryBitmap bitmap : List.of(
                new BinaryBitmap(new HybridBinarizer(source)),
                new BinaryBitmap(new GlobalHistogramBinarizer(source)))) {
            try {
                Result result = new MultiFormatReader().decode(bitmap, hints);
                if (result != null && result.getText() != null) {
                    return Optional.of(result.getText());
                }
            } catch (NotFoundException ignored) {
                // esta binarização não encontrou nada; tenta a seguinte
            }
        }
        return Optional.empty();
    }

    // ── parsing ───────────────────────────────────────────────

    private Optional<AtInvoiceData> parse(String raw) {
        Map<String, String> fields = new HashMap<>();
        for (String pair : raw.split("\\*")) {
            int sep = pair.indexOf(':');
            if (sep > 0) {
                fields.put(pair.substring(0, sep).trim(), pair.substring(sep + 1).trim());
            }
        }

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
