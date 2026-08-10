package com.management.managementapi.enterprises.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Sonda manual: corre o leitor de QR contra um ficheiro real do disco.
 *
 * Quando uma fatura não preenche os campos sozinha, a pergunta é sempre a mesma
 * — o papel não tem QR da AT, ou o leitor é que não o apanha? Adivinhar isso a
 * partir da app é lento; aqui vê-se de uma vez.
 *
 * <pre>
 * ./mvnw test -Dtest=AtInvoiceQrProbeTest -Dinvoice.file="C:/caminho/fatura.pdf"
 * </pre>
 *
 * Sem a propriedade o teste é saltado, por isso não afeta o build normal.
 * Para simular o que o browser envia depois de comprimir, junta
 * {@code -Dinvoice.maxEdge=2200}.
 */
class AtInvoiceQrProbeTest {

    private final AtInvoiceQrService service = new AtInvoiceQrService(new WeChatQrCodeService());

    @Test
    @EnabledIfSystemProperty(named = "invoice.file", matches = ".+")
    @DisplayName("sonda: lê o QR de um ficheiro real indicado em -Dinvoice.file")
    void probe() throws Exception {
        Path path = Path.of(System.getProperty("invoice.file"));
        byte[] content = Files.readAllBytes(path);
        String mime = guessMime(path);

        System.out.println("\n===== SONDA DE QR =====");
        System.out.printf("Ficheiro : %s%n", path.toAbsolutePath());
        System.out.printf("Tamanho  : %.1f KB%n", content.length / 1024.0);
        System.out.printf("MIME     : %s%n", mime);

        Optional<AtInvoiceQrService.AtInvoiceData> data = service.read(content, mime);

        if (data.isEmpty()) {
            System.out.println("\nRESULTADO: nenhum QR da AT lido.");
            System.out.println("Ver a linha [fatura-qr] acima — diz se não há QR nenhum");
            System.out.println("ou se há QR mas não é da AT.");
            System.out.println("=======================\n");
            return;
        }

        AtInvoiceQrService.AtInvoiceData qr = data.get();
        System.out.println("\nRESULTADO: QR da AT lido.");
        System.out.printf("  NIF emitente : %s%n", qr.issuerNif());
        System.out.printf("  NIF cliente  : %s%n", qr.buyerNif());
        System.out.printf("  Tipo / estado: %s / %s%n", qr.documentType(), qr.documentStatus());
        System.out.printf("  Nº documento : %s%n", qr.documentNumber());
        System.out.printf("  ATCUD        : %s%n", qr.atcud());
        System.out.printf("  Data         : %s%n", qr.invoiceDate());
        System.out.printf("  Sem impostos : %s%n", qr.taxableAmount());
        System.out.printf("  Impostos     : %s%n", qr.taxAmount());
        System.out.printf("  TOTAL        : %s%n", qr.totalAmount());
        qr.warnings().forEach(w -> System.out.printf("  AVISO        : %s%n", w));
        System.out.println("=======================\n");
    }

    private String guessMime(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }
}
