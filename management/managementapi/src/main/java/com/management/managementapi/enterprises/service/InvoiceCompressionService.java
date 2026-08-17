package com.management.managementapi.enterprises.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Optional;

/**
 * Comprime o documento de uma fatura para poupar Storage — chamado só
 * <b>depois</b> de {@link AtInvoiceQrService} já ter lido o QR a partir do
 * ficheiro original, e só quando a leitura teve sucesso.
 *
 * A compressão costumava acontecer no browser, antes do upload — o servidor
 * nunca via os bytes originais. Isso degradava exatamente o que
 * {@code AtInvoiceQrService} mais precisa: a textura fina do QR. Faturas que
 * liam bem em qualidade total deixavam de ler depois de recomprimidas. Mudar a
 * compressão para depois da leitura, e só quando ela teve sucesso, resolve os
 * dois lados: o QR lê-se sempre do melhor material possível, e uma fatura sem
 * QR legível fica guardada em qualidade total — a melhor hipótese para
 * revisão manual ou para um {@code /rescan} futuro, se o pipeline de leitura
 * melhorar.
 *
 * MaxEdge e qualidade espelham os valores que estavam em
 * {@code imageCompression.ts} no frontend, já afinados por tentativa e erro
 * contra faturas reais.
 */
@Slf4j
@Service
public class InvoiceCompressionService {

    private static final int MAX_EDGE = 2200;
    private static final float JPEG_QUALITY = 0.82f;

    /** Abaixo disto comprimir não poupa Storage que se note. */
    private static final long SKIP_BELOW_BYTES = 1_500_000L;

    public static final String COMPRESSED_MIME = "image/jpeg";

    public record Result(byte[] content, String mimeType) {}

    /**
     * @return vazio quando não vale a pena comprimir — PDF, ficheiro já
     *         pequeno, imagem ilegível, ou o resultado não ficou mais pequeno.
     *         Nesse caso o chamador guarda o original tal como está.
     */
    public Optional<Result> compress(byte[] content, String mimeType) {
        if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
            return Optional.empty();
        }
        if (content.length <= SKIP_BELOW_BYTES) {
            return Optional.empty();
        }

        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return Optional.empty();
            }

            byte[] compressed = toJpeg(scaleDown(source));
            if (compressed.length >= content.length) {
                return Optional.empty();
            }
            return Optional.of(new Result(compressed, COMPRESSED_MIME));
        } catch (Exception e) {
            log.warn("Não foi possível comprimir a fatura: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Só reduz — ampliar uma imagem já pequena só gastaria bytes. */
    private BufferedImage scaleDown(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double factor = longest <= MAX_EDGE ? 1.0 : (double) MAX_EDGE / longest;

        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        // TYPE_INT_RGB, não ARGB: o writer de JPEG não sabe escrever canal
        // alfa. Fundo branco é o que faz sentido para um documento.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] toJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("Nenhum writer de JPEG disponível nesta JVM");
        }
        ImageWriter writer = writers.next();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);

            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
