package com.management.managementapi.enterprises.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O QR da AT é a alternativa determinística ao OCR nas faturas portuguesas.
 * Os fixtures são gerados aqui — o teste não depende de nenhum ficheiro externo.
 */
class AtInvoiceQrServiceTest {

    /** Exemplo no formato da AT: pares campo:valor separados por '*'. */
    private static final String AT_QR = String.join("*",
            "A:509442013", "B:999999990", "C:PT", "D:FT", "E:N", "F:20260115",
            "G:FT 2026/114", "H:CSDF7T5H-0114", "I1:PT", "I7:12000.00", "I8:2760.00",
            "N:2760.00", "O:14760.00", "Q:kLp0", "R:9999");

    private final AtInvoiceQrService service = new AtInvoiceQrService(new WeChatQrCodeService());

    @Test
    @DisplayName("lê os campos fiscais de um QR numa imagem")
    void readsFieldsFromImage() throws Exception {
        var data = service.read(qrAsPng(AT_QR), "image/png").orElseThrow();

        assertThat(data.issuerNif()).isEqualTo("509442013");
        assertThat(data.buyerNif()).isEqualTo("999999990");
        assertThat(data.documentType()).isEqualTo("FT");
        assertThat(data.documentNumber()).isEqualTo("FT 2026/114");
        assertThat(data.atcud()).isEqualTo("CSDF7T5H-0114");
        assertThat(data.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(data.totalAmount()).isEqualByComparingTo("14760.00");
        assertThat(data.taxAmount()).isEqualByComparingTo("2760.00");
        // base tributável = total - impostos
        assertThat(data.taxableAmount()).isEqualByComparingTo("12000.00");
        assertThat(data.warnings()).isEmpty();
    }

    @Test
    @DisplayName("encontra o QR numa fatura em PDF")
    void readsFromPdf() throws Exception {
        var data = service.read(qrInsidePdf(AT_QR), "application/pdf").orElseThrow();

        assertThat(data.atcud()).isEqualTo("CSDF7T5H-0114");
        assertThat(data.invoiceDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(data.totalAmount()).isEqualByComparingTo("14760.00");
    }

    @Test
    @DisplayName("varre as páginas seguintes quando o QR não está na primeira")
    void readsFromLaterPdfPage() throws Exception {
        var data = service.read(qrInsidePdf(AT_QR, 3), "application/pdf").orElseThrow();
        assertThat(data.atcud()).isEqualTo("CSDF7T5H-0114");
    }

    /**
     * O caso que motivou o mosaico: a fotografia da fatura inteira, com o QR a
     * ocupar uma nesga da página. Na imagem toda o detetor não o localiza — é
     * preciso recortar e ampliar.
     */
    @Test
    @DisplayName("lê o QR pequeno numa fotografia da fatura inteira")
    void readsSmallQrInFullPagePhoto() throws Exception {
        var data = service.read(photoOfInvoice(AT_QR, 15), "image/jpeg").orElseThrow();

        assertThat(data.issuerNif()).isEqualTo("509442013");
        assertThat(data.totalAmount()).isEqualByComparingTo("14760.00");
    }

    @Test
    @DisplayName("ignora QR codes que não são da AT")
    void ignoresNonAtQr() throws Exception {
        Optional<AtInvoiceQrService.AtInvoiceData> data =
                service.read(qrAsPng("https://exemplo.pt/pagamento/123"), "image/png");
        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("devolve vazio quando não há QR nenhum")
    void emptyWhenNoQr() throws Exception {
        BufferedImage blank = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(blank, "png", out);

        assertThat(service.read(out.toByteArray(), "image/png")).isEmpty();
    }

    @Test
    @DisplayName("avisa quando o documento está anulado na AT")
    void warnsOnCancelledDocument() throws Exception {
        var data = service.read(qrAsPng(AT_QR.replace("E:N", "E:A")), "image/png").orElseThrow();
        assertThat(data.warnings()).anyMatch(w -> w.contains("anulado"));
    }

    @Test
    @DisplayName("avisa quando é nota de crédito em vez de fatura")
    void warnsOnCreditNote() throws Exception {
        var data = service.read(qrAsPng(AT_QR.replace("D:FT", "D:NC")), "image/png").orElseThrow();
        assertThat(data.warnings()).anyMatch(w -> w.contains("nota de crédito"));
    }

    @Test
    @DisplayName("não rebenta com uma data mal formada — avisa e segue")
    void toleratesBadDate() throws Exception {
        var data = service.read(qrAsPng(AT_QR.replace("F:20260115", "F:15-01-2026")), "image/png")
                .orElseThrow();

        assertThat(data.invoiceDate()).isNull();
        assertThat(data.totalAmount()).isEqualByComparingTo("14760.00"); // o resto continua a ler-se
        assertThat(data.warnings()).anyMatch(w -> w.contains("AAAAMMDD"));
    }

    // ── fixtures ──────────────────────────────────────────────

    private static byte[] qrAsPng(String content) throws Exception {
        return qrAsPng(content, 400);
    }

    private static byte[] qrAsPng(String content, int pixels) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels);
        var out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }

    private static byte[] qrInsidePdf(String content) throws Exception {
        return qrInsidePdf(content, 1);
    }

    /**
     * O que o servidor recebe quando alguém fotografa a fatura: uma página A4
     * com texto e o QR a {@code qrMillimetres}, rasterizada, reduzida a 2200 px
     * de lado maior pelo browser e reencodada em JPEG.
     *
     * O texto à volta não é enfeite — é ele que dá à binarização o contraste de
     * uma página real, em vez do branco liso onde qualquer QR se lê.
     */
    private static byte[] photoOfInvoice(String content, int qrMillimetres) throws Exception {
        float points = qrMillimetres * 72f / 25.4f;

        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                for (int line = 0; line < 40; line++) {
                    stream.beginText();
                    stream.newLineAtOffset(60, 760 - line * 16);
                    stream.showText("Rubrica " + line
                            + "   Betao C25/30   12,50 EUR   qtd 4,00   total 50,00 EUR");
                    stream.endText();
                }
                // 600 px reduzidos a 15 mm é a reamostragem que a impressora e a
                // câmara fazem ao QR — desenhá-lo já pequeno daria um teste mais
                // fácil do que a realidade.
                PDImageXObject qr = PDImageXObject.createFromByteArray(
                        document, qrAsPng(content, 600), "qr");
                stream.drawImage(qr, 60, 60, points, points);
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        BufferedImage rendered;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            rendered = new PDFRenderer(document).renderImageWithDPI(0, 300);
        }
        return asJpeg(downscale(rendered, 2200), 0.82f);
    }

    /** A redução que o browser faz antes do upload (`compressInvoiceFile`). */
    private static BufferedImage downscale(BufferedImage image, int maxEdge) {
        double factor = maxEdge / (double) Math.max(image.getWidth(), image.getHeight());
        int width = (int) Math.round(image.getWidth() * factor);
        int height = (int) Math.round(image.getHeight() * factor);

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private static byte[] asJpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        var out = new ByteArrayOutputStream();
        writer.setOutput(new MemoryCacheImageOutputStream(out));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        return out.toByteArray();
    }

    /** PDF com {@code pages} páginas e o QR só na última, como numa fatura real. */
    private static byte[] qrInsidePdf(String content, int pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                if (i == pages - 1) {
                    PDImageXObject qr = PDImageXObject.createFromByteArray(
                            document, qrAsPng(content), "qr");
                    try (var stream = new PDPageContentStream(document, page)) {
                        stream.drawImage(qr, 60, 60, 160, 160);
                    }
                }
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
