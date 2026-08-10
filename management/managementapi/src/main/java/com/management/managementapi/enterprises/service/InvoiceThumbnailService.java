package com.management.managementapi.enterprises.service;

import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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
 * Gera a miniatura de uma fatura para as listas.
 *
 * Sem isto, abrir a caixa de entrada obrigava a descarregar o documento
 * completo de cada linha — 20 faturas eram dezenas de MB só para mostrar
 * vinte quadradinhos. A miniatura fica nos 15-30 KB.
 *
 * Vale também para PDFs: a primeira página é rasterizada, o que dá
 * pré-visualização visual a documentos que de outra forma seriam só um ícone.
 * Reutiliza o PDFBox que já entrou no projeto por causa do
 * {@link AtInvoiceQrService}, portanto não traz dependências novas.
 *
 * <b>Falhar aqui nunca é fatal.</b> Uma fatura sem miniatura continua a ser uma
 * fatura válida — a lista cai num ícone de ficheiro. Por isso todos os métodos
 * devolvem {@link Optional} em vez de lançar.
 */
@Slf4j
@Service
public class InvoiceThumbnailService {

    /** Chega para um avatar de lista e para uma pré-visualização em hover. */
    private static final int MAX_EDGE = 480;

    private static final float JPEG_QUALITY = 0.7f;

    /**
     * Bem abaixo dos 200 DPI que o {@link AtInvoiceQrService} usa: aqui não é
     * preciso descodificar nada, só reconhecer o documento de relance.
     */
    private static final int PDF_RENDER_DPI = 72;

    public static final String THUMBNAIL_MIME = "image/jpeg";

    /**
     * @return o JPEG da miniatura, ou vazio se o ficheiro não for renderizável
     */
    public Optional<byte[]> render(byte[] content, String mimeType) {
        try {
            BufferedImage source = decode(content, mimeType);
            if (source == null) {
                return Optional.empty();
            }
            return Optional.of(toJpeg(scaleDown(source)));
        } catch (Exception e) {
            log.warn("Não foi possível gerar a miniatura da fatura: {}", e.toString());
            return Optional.empty();
        }
    }

    private BufferedImage decode(byte[] content, String mimeType) throws Exception {
        if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
            try (PDDocument document = Loader.loadPDF(content)) {
                if (document.getNumberOfPages() == 0) {
                    return null;
                }
                return new PDFRenderer(document).renderImageWithDPI(0, PDF_RENDER_DPI, ImageType.RGB);
            }
        }
        return ImageIO.read(new ByteArrayInputStream(content));
    }

    /**
     * Só reduz. Ampliar uma imagem já pequena não acrescentaria nada e só
     * gastaria bytes.
     */
    private BufferedImage scaleDown(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double factor = longest <= MAX_EDGE ? 1.0 : (double) MAX_EDGE / longest;

        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));

        // TYPE_INT_RGB, não ARGB: o writer de JPEG não sabe escrever canal alfa e
        // produziria cores invertidas num PNG com transparência. O fundo branco
        // é o que faz sentido para um documento.
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
