package com.management.managementapi.enterprises.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.service.PaymentListExportService.PaymentList;

class PaymentListExportServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-24");
    private static final UUID OBRA = UUID.randomUUID();
    private static final String CGD = "PT50003500000000000000048";
    private static final String BCP = "PT50003300000000000000059";

    private static ConstructionInvoiceResponseDTO invoice(String name, String nif, List<String> ibans, String number,
                                                          String net, String paid, UUID enterpriseId) {
        ConstructionInvoiceResponseDTO dto = mock(ConstructionInvoiceResponseDTO.class);
        when(dto.supplierName()).thenReturn(name);
        when(dto.supplierNif()).thenReturn(nif);
        when(dto.supplierIbans()).thenReturn(ibans);
        when(dto.invoiceNumber()).thenReturn(number);
        when(dto.invoiceDate()).thenReturn(LocalDate.parse("2026-11-01"));
        when(dto.netAmount()).thenReturn(net == null ? null : new BigDecimal(net));
        when(dto.totalAmount()).thenReturn(net == null ? null : new BigDecimal(net));
        when(dto.paidAmount()).thenReturn(new BigDecimal(paid));
        when(dto.enterpriseId()).thenReturn(enterpriseId);
        return dto;
    }

    private static PaymentList sample() {
        return PaymentListExportService.group(List.of(
                invoice("Betão Liz, Lda.", "500000001", List.of(CGD, BCP), "FT 1/10", "1000.00", "250.00", OBRA),
                invoice("Açores Ferro", "500000002", List.of(), "FT 9/1", "40.50", "0", null),
                invoice("Betão Liz, Lda.", "500000001", List.of(CGD, BCP), "FT 1/11", "100.00", "0", OBRA)),
                Map.of(OBRA, "Vila Aleu"), TODAY);
    }

    @Test
    void groupsBySupplierWithRemainingAmountsAndTotals() {
        PaymentList list = sample();

        assertThat(list.invoiceCount()).isEqualTo(3);
        assertThat(list.total()).isEqualByComparingTo("890.50");
        assertThat(list.suppliers()).extracting(PaymentListExportService.SupplierGroup::name)
                .containsExactly("Açores Ferro", "Betão Liz, Lda.");

        PaymentListExportService.SupplierGroup betao = list.suppliers().get(1);
        assertThat(betao.total()).isEqualByComparingTo("850.00");
        assertThat(betao.invoices()).extracting(PaymentListExportService.Line::number).containsExactly("FT 1/10", "FT 1/11");
        assertThat(betao.invoices().get(0).owner()).isEqualTo("Vila Aleu");
        assertThat(betao.accounts()).extracting(PaymentListExportService.BankAccount::bank)
                .containsExactly("CGD", "Millennium bcp");
        assertThat(list.suppliers().get(0).invoices().get(0).owner()).isEqualTo("Empresa");
    }

    @Test
    void remainingNeverGoesNegative() {
        assertThat(PaymentListExportService.remainingOf(invoice("X", "1", List.of(), "1", "10", "12", null)))
                .isEqualByComparingTo("0");
    }

    @Test
    void xlsxHasSuppliersIbansAndAmounts() throws Exception {
        byte[] bytes = new PaymentListExportService(null).toXlsx(sample());

        StringBuilder all = new StringBuilder();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (Row row : workbook.getSheetAt(0)) {
                for (Cell cell : row) all.append(cell.toString()).append('|');
            }
        }
        assertThat(all.toString())
                .contains("Betão Liz, Lda.", "NIF 500000001", "CGD", "PT50 0035 0000 0000 0000 0004 8",
                        "Millennium bcp", "FT 1/10", "Vila Aleu", "Sem IBAN na ficha do fornecedor", "890.5");
    }

    @Test
    void pdfHasSuppliersIbansAndAmounts() throws Exception {
        byte[] bytes = new PaymentListExportService(null).toPdf(sample());

        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Pagamentos aprovados — 24/11/2026", "Betão Liz, Lda.", "NIF 500000001",
                    "PT50 0035 0000 0000 0000 0004 8", "Millennium bcp", "FT 1/11", "750,00 €", "890,50 €",
                    "Sem IBAN na ficha do fornecedor");
        }
    }
}
