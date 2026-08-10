package com.management.managementapi.enterprises.service;

import lombok.extern.slf4j.Slf4j;

import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.StringVector;
import org.bytedeco.opencv.opencv_wechat_qrcode.WeChatQRCode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Último degrau da escalada do {@link AtInvoiceQrService}: um detetor de QR
 * baseado numa CNN (o mesmo que o WeChat usa), com um modelo de
 * super-resolução treinado especificamente para QR pequeno/desfocado dentro
 * de uma foto maior.
 *
 * O ZXing (usado em todos os degraus anteriores) só binariza — decide
 * preto/branco pixel a pixel e tenta ler a grelha. Contra uma foto de
 * WhatsApp em que o QR real tem 2-3 px por módulo, isso não chega: não há
 * módulos distintos para binarizar, só uma mancha de ruído. O modelo daqui
 * foi treinado para essa exata situação — deteta a região do QR e reconstrói-a
 * antes de decodificar, em vez de assumir que os módulos já lá estão.
 *
 * Corre em processo (OpenCV via JavaCPP), nada sai do servidor — mantém a
 * mesma garantia que motivou escolher ZXing em vez de OCR na nuvem. O custo se
 * o servidor local não conseguir carregar a biblioteca nativa (falta o
 * classifier certo, JVM sem permissões, etc.) é apenas este degrau ficar
 * desligado — nunca a fatura falhar por causa disto.
 */
@Slf4j
@Service
public class WeChatQrCodeService {

    private static final String MODEL_BASE = "models/wechat-qrcode/";
    private static final String DETECT_PROTOTXT = "detect.prototxt";
    private static final String DETECT_CAFFEMODEL = "detect.caffemodel";
    private static final String SR_PROTOTXT = "sr.prototxt";
    private static final String SR_CAFFEMODEL = "sr.caffemodel";

    // `static`, não de instância: o detetor carrega um modelo nativo a partir
    // de ficheiros extraídos para uma pasta temporária — um recurso do
    // processo, não desta bean. Partilhado entre instâncias, um teste que crie
    // vários `new WeChatQrCodeService()` (como este projeto faz, sem Spring)
    // paga a extração e o carregamento do modelo uma vez só por JVM.
    private static volatile WeChatQRCode detector;
    private static volatile boolean unavailable;

    /**
     * Carrega o detetor em segundo plano assim que o servidor arranca, para a
     * primeira fatura real não pagar o custo do carregamento (~15-20 s a
     * extrair e inicializar o modelo nativo) em plena resposta HTTP.
     * {@code @Async} porque não há razão para atrasar o arranque do resto da
     * app à espera disto — o degrau fica só indisponível uns segundos a mais
     * se alguém carregar uma fatura mesmo nesse intervalo.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        detector();
    }

    /**
     * @return os textos que o detetor encontrou (pode ser mais do que um QR
     *         na imagem); lista vazia se não achar nada ou se o detetor não
     *         estiver disponível nesta máquina
     */
    public List<String> decode(BufferedImage image) {
        WeChatQRCode d = detector();
        if (d == null) {
            return List.of();
        }

        byte[] encoded = toPng(image);
        if (encoded == null) {
            return List.of();
        }

        // Sincronizado na classe, não na instância: `detector` é partilhado
        // por todas as instâncias (ver o comentário nos campos). Este degrau
        // só corre depois de o ZXing falhar, é raro face ao volume total de
        // faturas, e serializar aqui evita apostar na segurança concorrente
        // interna do detetor nativo.
        synchronized (WeChatQrCodeService.class) {
            try (Mat buffer = new Mat(encoded);
                 Mat image2 = opencv_imgcodecs.imdecode(buffer, opencv_imgcodecs.IMREAD_COLOR)) {
                if (image2 == null || image2.empty()) {
                    return List.of();
                }
                try (StringVector results = d.detectAndDecode(image2)) {
                    List<String> texts = new ArrayList<>();
                    for (long i = 0; i < results.size(); i++) {
                        String text = results.get(i).getString();
                        if (text != null && !text.isBlank()) {
                            texts.add(text);
                        }
                    }
                    return texts;
                }
            } catch (Throwable t) {
                // Throwable e não Exception: uma falha na biblioteca nativa sai
                // como Error (UnsatisfiedLinkError, etc.), não Exception.
                log.warn("[fatura-qr-wechat] falha a descodificar: {}", t.toString());
                return List.of();
            }
        }
    }

    private static WeChatQRCode detector() {
        if (unavailable) {
            return null;
        }
        WeChatQRCode d = detector;
        if (d != null) {
            return d;
        }
        synchronized (WeChatQrCodeService.class) {
            if (detector != null) {
                return detector;
            }
            try {
                Path dir = extractModels();
                detector = new WeChatQRCode(
                        dir.resolve(DETECT_PROTOTXT).toString(),
                        dir.resolve(DETECT_CAFFEMODEL).toString(),
                        dir.resolve(SR_PROTOTXT).toString(),
                        dir.resolve(SR_CAFFEMODEL).toString());
                log.info("[fatura-qr-wechat] detetor carregado a partir de {}", dir);
            } catch (Throwable t) {
                log.warn("[fatura-qr-wechat] não foi possível carregar o detetor — "
                        + "este degrau da escalada fica desligado, o resto do leitor "
                        + "continua normal: {}", t.toString());
                unavailable = true;
                return null;
            }
        }
        return detector;
    }

    /**
     * O detetor lê de ficheiro, não de classpath — copia os quatro modelos
     * (menos de 1,1 MB ao todo) para uma pasta temporária uma única vez por
     * arranque do servidor.
     */
    private static Path extractModels() throws IOException {
        Path dir = Files.createTempDirectory("wechat-qrcode-models");
        for (String name : new String[]{ DETECT_PROTOTXT, DETECT_CAFFEMODEL, SR_PROTOTXT, SR_CAFFEMODEL }) {
            try (InputStream in = WeChatQrCodeService.class.getClassLoader().getResourceAsStream(MODEL_BASE + name)) {
                if (in == null) {
                    throw new IOException("recurso em falta no classpath: " + MODEL_BASE + name);
                }
                Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dir;
    }

    /** PNG por ser sem perdas — a imagem já passou pelo pior degrau de compressão antes de chegar aqui. */
    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
