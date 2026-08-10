package com.management.managementapi.enterprises.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Sonda em lote: corre o leitor de QR contra todos os ficheiros de uma pasta e
 * dá a taxa de leitura, para medir o efeito real de uma mudança no leitor —
 * sem isto, afinar {@link AtInvoiceQrService} é adivinhação.
 *
 * <pre>
 * ./mvnw test -Dtest=AtInvoiceQrBatchProbeTest -Dinvoice.dir="C:/caminho/faturas"
 * </pre>
 *
 * Sem a propriedade o teste é saltado, por isso não afeta o build normal.
 */
class AtInvoiceQrBatchProbeTest {

    private final AtInvoiceQrService service = new AtInvoiceQrService(new WeChatQrCodeService());

    @Test
    @EnabledIfSystemProperty(named = "invoice.dir", matches = ".+")
    @DisplayName("sonda em lote: taxa de leitura do QR contra uma pasta real de faturas")
    void batchProbe() throws IOException {
        Path dir = Path.of(System.getProperty("invoice.dir"));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(AtInvoiceQrBatchProbeTest::looksLikeInvoice)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        System.out.println("\n===== SONDA EM LOTE DE QR =====");
        System.out.printf("Pasta    : %s%n", dir.toAbsolutePath());
        System.out.printf("Ficheiros: %d%n%n", files.size());

        List<String> failed = new ArrayList<>();
        int ok = 0;

        for (Path path : files) {
            byte[] content = Files.readAllBytes(path);
            String mime = guessMime(path);
            long start = System.currentTimeMillis();
            Optional<AtInvoiceQrService.AtInvoiceData> data = service.read(content, mime);
            long elapsed = System.currentTimeMillis() - start;

            if (data.isPresent()) {
                ok++;
                System.out.printf("OK    %-55s %5dms  NIF %s  doc %s%n",
                        path.getFileName(), elapsed, data.get().issuerNif(), data.get().documentNumber());
            } else {
                failed.add(path.getFileName().toString());
                System.out.printf("FALHA %-55s %5dms%n", path.getFileName(), elapsed);
            }
        }

        System.out.printf("%nRESULTADO: %d/%d lidas (%.0f%%)%n", ok, files.size(),
                files.isEmpty() ? 0.0 : 100.0 * ok / files.size());
        if (!failed.isEmpty()) {
            System.out.println("Falharam:");
            failed.forEach(name -> System.out.println("  - " + name));
        }
        System.out.println("================================\n");
    }

    private static boolean looksLikeInvoice(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".pdf") || name.endsWith(".webp");
    }

    private static String guessMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
