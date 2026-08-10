package com.management.managementapi.enterprises.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A miniatura existe para a caixa de entrada não descarregar o documento
 * completo de cada linha — por isso o que se verifica aqui é sobretudo que ela
 * fica <i>pequena</i>, e que falhar não rebenta nada.
 *
 * Os fixtures são gerados no próprio teste: não depende de ficheiros externos.
 */
class InvoiceThumbnailServiceTest {

    /** Uma foto de fatura de telemóvel anda por aqui em termos de dimensões. */
    private static final int SOURCE_WIDTH = 2200;
    private static final int SOURCE_HEIGHT = 3000;
    private static final int MAX_EDGE = 480;

    /** Bem acima do que uma miniatura de 480px deve pesar. */
    private static final int SIZE_BUDGET_BYTES = 60 * 1024;

    private final InvoiceThumbnailService service = new InvoiceThumbnailService();

    @Test
    @DisplayName("reduz uma foto grande a uma miniatura leve, mantendo a proporção")
    void scalesDownLargeImage() throws Exception {
        Optional<byte[]> thumbnail = service.render(photo(SOURCE_WIDTH, SOURCE_HEIGHT), "image/jpeg");

        assertThat(thumbnail).isPresent();
        assertThat(thumbnail.get().length).isLessThan(SIZE_BUDGET_BYTES);

        BufferedImage rendered = read(thumbnail.get());
        assertThat(Math.max(rendered.getWidth(), rendered.getHeight())).isEqualTo(MAX_EDGE);
        // 2200x3000 -> o lado maior fica em 480 e o outro acompanha
        assertThat(rendered.getWidth()).isEqualTo(Math.round(MAX_EDGE * (float) SOURCE_WIDTH / SOURCE_HEIGHT));
    }

    @Test
    @DisplayName("rasteriza a primeira página de um PDF")
    void rendersFirstPdfPage() throws Exception {
        Optional<byte[]> thumbnail = service.render(pdf(), "application/pdf");

        assertThat(thumbnail).isPresent();
        assertThat(thumbnail.get().length).isLessThan(SIZE_BUDGET_BYTES);
        assertThat(read(thumbnail.get())).isNotNull();
    }

    @Test
    @DisplayName("não amplia uma imagem já pequena")
    void doesNotUpscale() throws Exception {
        Optional<byte[]> thumbnail = service.render(photo(120, 90), "image/jpeg");

        BufferedImage rendered = read(thumbnail.orElseThrow());
        assertThat(rendered.getWidth()).isEqualTo(120);
        assertThat(rendered.getHeight()).isEqualTo(90);
    }

    @Test
    @DisplayName("um PNG com transparência não sai com as cores trocadas")
    void flattensTransparencyOntoWhite() throws Exception {
        BufferedImage source = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(new Color(0, 0, 0, 0)); // totalmente transparente
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        BufferedImage rendered = read(service.render(toBytes(source, "png"), "image/png").orElseThrow());

        // O writer de JPEG não escreve canal alfa; sem o fundo branco explícito
        // saía preto (ou com as cores invertidas).
        Color pixel = new Color(rendered.getRGB(100, 100));
        assertThat(pixel.getRed()).isGreaterThan(240);
        assertThat(pixel.getGreen()).isGreaterThan(240);
        assertThat(pixel.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("um ficheiro ilegível devolve vazio em vez de lançar")
    void failsQuietlyOnGarbage() {
        assertThat(service.render("isto não é uma imagem".getBytes(), "image/jpeg")).isEmpty();
        assertThat(service.render(new byte[0], "application/pdf")).isEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────

    private byte[] photo(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.DARK_GRAY);
            // umas linhas para a imagem não ser uniforme e o JPEG ter algo que comprimir
            for (int y = 0; y < height; y += Math.max(8, height / 40)) {
                g.drawLine(0, y, width, y);
            }
        } finally {
            g.dispose();
        }
        return toBytes(image, "jpg");
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(60, 700);
                content.showText("FATURA FT 2026/114");
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] toBytes(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private BufferedImage read(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }
}
