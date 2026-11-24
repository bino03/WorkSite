package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.OutstandingFilter;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.OutstandingByUrgencyDTO;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.ExportFile;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.Look;
import com.management.managementapi.enterprises.service.BudgetExcelExportService.Styles;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.util.Iban;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A lista de pagamentos: as faturas <b>aprovadas para pagamento e ainda por
 * liquidar</b>, agrupadas por fornecedor, com os IBAN de cada um — para entregar
 * a quem faz as transferências no homebanking. Em {@code .xlsx} ou {@code .pdf}.
 *
 * <p>Por fornecedor e não por fatura porque se paga uma transferência por
 * fornecedor (como o pagamento agregado), e os IBAN não se repetem linha a linha.
 *
 * <p>As faturas são as de "A pagar" com os mesmos filtros, forçando
 * {@code approvedForPayment=true} — ver
 * {@link ConstructionInvoiceService#outstandingByUrgency}, que já exclui a
 * quarentena e as obras de teste. O montante é o que falta pagar (líquido das
 * notas de crédito, menos o já pago), como no cartão da página inicial.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentListExportService {

    public enum Format { XLSX, PDF }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String UNKNOWN_SUPPLIER = "Fornecedor por identificar";
    private static final String COMPANY = "Empresa";
    static final String[] INVOICE_HEADERS = {"Nº Fatura", "Data", "Vencimento", "Obra", "A pagar"};

    private final ConstructionInvoiceService invoiceService;

    // ── o modelo ──────────────────────────────────────────────

    record BankAccount(String bank, String iban) {}

    record Line(String number, LocalDate date, LocalDate dueDate, String owner, BigDecimal amount) {}

    record SupplierGroup(String name, String nif, List<BankAccount> accounts, List<Line> invoices, BigDecimal total) {}

    record PaymentList(LocalDate generatedOn, List<SupplierGroup> suppliers, int invoiceCount, BigDecimal total) {}

    @Transactional(readOnly = true)
    public ExportFile export(OutstandingFilter filter, Format format, LocalDate today) {
        PaymentList list = build(filter, today);
        if (list.invoiceCount() == 0) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_LIST_EMPTY);
        }
        String base = "Pagamentos aprovados - " + today;
        try {
            return format == Format.PDF
                    ? new ExportFile(base + ".pdf", toPdf(list))
                    : new ExportFile(base + ".xlsx", toXlsx(list));
        } catch (IOException | RuntimeException e) {
            log.error("Falha a gerar a lista de pagamentos ({}): {}", format, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_WRITE_ERROR);
        }
    }

    PaymentList build(OutstandingFilter filter, LocalDate today) {
        OutstandingByUrgencyDTO outstanding =
                invoiceService.outstandingByUrgency(filter.onlyApproved(), 0, Integer.MAX_VALUE, today);
        return group(outstanding.invoices(), outstanding.enterpriseNames(), today);
    }

    /** Agrupa por NIF (ou pelo nome, sem NIF); fornecedores por ordem alfabética, faturas pela de "A pagar". */
    static PaymentList group(List<ConstructionInvoiceResponseDTO> invoices, Map<java.util.UUID, String> enterpriseNames,
                             LocalDate today) {
        Map<String, List<ConstructionInvoiceResponseDTO>> byKey = new LinkedHashMap<>();
        for (ConstructionInvoiceResponseDTO invoice : invoices) {
            byKey.computeIfAbsent(keyOf(invoice), k -> new ArrayList<>()).add(invoice);
        }

        List<SupplierGroup> groups = new ArrayList<>();
        for (List<ConstructionInvoiceResponseDTO> members : byKey.values()) {
            ConstructionInvoiceResponseDTO first = members.get(0);
            String name = members.stream().map(ConstructionInvoiceResponseDTO::supplierName)
                    .filter(n -> n != null && !n.isBlank()).findFirst().orElse(UNKNOWN_SUPPLIER);
            List<BankAccount> accounts = (first.supplierIbans() == null ? List.<String>of() : first.supplierIbans())
                    .stream().map(iban -> new BankAccount(Iban.bankOf(iban), iban)).toList();
            List<Line> lines = members.stream().map(invoice -> new Line(
                    invoice.invoiceNumber(),
                    invoice.invoiceDate(),
                    invoice.dueDate(),
                    invoice.enterpriseId() == null
                            ? COMPANY
                            : enterpriseNames.getOrDefault(invoice.enterpriseId(), "Obra"),
                    remainingOf(invoice))).toList();
            BigDecimal total = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new SupplierGroup(name, blankToNull(first.supplierNif()), accounts, lines, total));
        }
        groups.sort(Comparator.comparing((SupplierGroup g) -> UNKNOWN_SUPPLIER.equals(g.name()))
                .thenComparing(g -> g.name().toLowerCase(Locale.ROOT)));

        BigDecimal total = groups.stream().map(SupplierGroup::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaymentList(today, groups, invoices.size(), total);
    }

    /** O que falta pagar: líquido (ou total) menos o pago, nunca negativo. */
    static BigDecimal remainingOf(ConstructionInvoiceResponseDTO invoice) {
        BigDecimal base = invoice.netAmount() != null ? invoice.netAmount()
                : invoice.totalAmount() != null ? invoice.totalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.paidAmount() == null ? BigDecimal.ZERO : invoice.paidAmount();
        return base.subtract(paid).max(BigDecimal.ZERO);
    }

    private static String keyOf(ConstructionInvoiceResponseDTO invoice) {
        if (invoice.supplierNif() != null && !invoice.supplierNif().isBlank()) {
            return "nif:" + invoice.supplierNif().trim();
        }
        if (invoice.supplierName() != null && !invoice.supplierName().isBlank()) {
            return "name:" + invoice.supplierName().trim().toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String accountLabel(BankAccount account) {
        return account.bank() == null ? "IBAN" : account.bank();
    }

    private static String summary(PaymentList list) {
        int suppliers = list.suppliers().size();
        return list.invoiceCount() + (list.invoiceCount() == 1 ? " fatura" : " faturas") + " · "
                + suppliers + (suppliers == 1 ? " fornecedor" : " fornecedores");
    }

    // ── Excel ─────────────────────────────────────────────────

    byte[] toXlsx(PaymentList list) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BudgetExcelExportService.applyVaultTheme(workbook);
            Styles styles = new Styles(workbook);
            XSSFSheet sheet = workbook.createSheet("Pagamentos");
            int[] widths = {30, 14, 14, 30, 16};
            for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);

            CellStyle title = styles.get(Look.of(14).bold());
            CellStyle meta = styles.get(Look.of(11).italic());
            CellStyle totalLabel = styles.get(Look.of(11).bold().h(HorizontalAlignment.RIGHT));
            CellStyle totalMoney = styles.get(Look.of(11).bold().format(BudgetExcelExportService.FORMAT_CURRENCY));
            Look supplierLook = Look.of(12).bold().fill("D9E1F2").top(BorderStyle.THIN);
            CellStyle supplier = styles.get(supplierLook);
            CellStyle supplierMoney = styles.get(supplierLook.format(BudgetExcelExportService.FORMAT_CURRENCY));
            CellStyle bank = styles.get(Look.of(11).h(HorizontalAlignment.RIGHT));
            CellStyle ibanStyle = styles.get(Look.of(11).bold().format(BudgetExcelExportService.FORMAT_TEXT));
            CellStyle noIban = styles.get(Look.of(11).italic().color("C00000"));
            CellStyle header = styles.get(Look.of(10).bold().color(BudgetExcelExportService.COLOR_WHITE)
                    .fill(BudgetExcelExportService.COLOR_HEADER_DARK).center());
            CellStyle text = styles.get(Look.of(11).format(BudgetExcelExportService.FORMAT_TEXT));
            CellStyle plain = styles.get(Look.of(11));
            CellStyle date = styles.get(Look.of(11).format(BudgetExcelExportService.FORMAT_DATE).h(HorizontalAlignment.CENTER));
            CellStyle money = styles.get(Look.of(11).format(BudgetExcelExportService.FORMAT_CURRENCY));

            int r = 0;
            BudgetExcelExportService.cell(sheet.createRow(r++), 0, "Pagamentos aprovados — " + list.generatedOn().format(DATE), title);
            Row totals = sheet.createRow(r++);
            BudgetExcelExportService.cell(totals, 0, summary(list), meta);
            BudgetExcelExportService.cell(totals, 3, "Total a pagar", totalLabel);
            BudgetExcelExportService.cell(totals, 4, list.total(), totalMoney);
            r++;

            for (SupplierGroup group : list.suppliers()) {
                Row head = sheet.createRow(r++);
                BudgetExcelExportService.cell(head, 0, group.name(), supplier);
                BudgetExcelExportService.cell(head, 1, group.nif() == null ? "sem NIF" : "NIF " + group.nif(), supplier);
                BudgetExcelExportService.cell(head, 2, (String) null, supplier);
                BudgetExcelExportService.cell(head, 3, (String) null, supplier);
                BudgetExcelExportService.cell(head, 4, group.total(), supplierMoney);

                if (group.accounts().isEmpty()) {
                    Row row = sheet.createRow(r++);
                    BudgetExcelExportService.cell(row, 0, "Sem IBAN na ficha do fornecedor", noIban);
                }
                for (BankAccount account : group.accounts()) {
                    Row row = sheet.createRow(r);
                    BudgetExcelExportService.cell(row, 0, accountLabel(account), bank);
                    BudgetExcelExportService.cell(row, 1, Iban.format(account.iban()), ibanStyle);
                    sheet.addMergedRegion(new CellRangeAddress(r, r, 1, 3));
                    r++;
                }

                Row columns = sheet.createRow(r++);
                for (int c = 0; c < INVOICE_HEADERS.length; c++) {
                    BudgetExcelExportService.cell(columns, c, INVOICE_HEADERS[c], header);
                }
                for (Line line : group.invoices()) {
                    Row row = sheet.createRow(r++);
                    BudgetExcelExportService.cell(row, 0, line.number(), text);
                    BudgetExcelExportService.cell(row, 1, line.date(), date);
                    BudgetExcelExportService.cell(row, 2, line.dueDate(), date);
                    BudgetExcelExportService.cell(row, 3, line.owner(), plain);
                    BudgetExcelExportService.cell(row, 4, line.amount(), money);
                }
                r++;
            }

            sheet.getPrintSetup().setLandscape(false);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ── PDF ───────────────────────────────────────────────────

    private static final float MARGIN = 40f;
    private static final float[] COLUMN_X = {MARGIN, 210f, 275f, 345f};
    private static final float RIGHT = PDRectangle.A4.getWidth() - MARGIN;

    byte[] toPdf(PaymentList list) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter pdf = new PdfWriter(document);
            pdf.text("Pagamentos aprovados — " + list.generatedOn().format(DATE), pdf.bold, 15, MARGIN);
            pdf.newLine(20);
            pdf.text(summary(list), pdf.regular, 10, MARGIN);
            pdf.textRight("Total a pagar: " + euros(list.total()), pdf.bold, 11, RIGHT);
            pdf.newLine(26);

            for (SupplierGroup group : list.suppliers()) {
                // Um fornecedor não começa no fundo da página: o cabeçalho com os
                // IBAN tem de ficar junto da primeira fatura.
                pdf.ensureSpace(30 + 14 * Math.max(group.accounts().size(), 1) + 30);
                pdf.band(18);
                pdf.text(group.name(), pdf.bold, 11, MARGIN + 4, 300);
                pdf.text(group.nif() == null ? "sem NIF" : "NIF " + group.nif(), pdf.regular, 10, 360);
                pdf.textRight(euros(group.total()), pdf.bold, 11, RIGHT - 4);
                pdf.newLine(18);

                if (group.accounts().isEmpty()) {
                    pdf.text("Sem IBAN na ficha do fornecedor", pdf.italic, 10, MARGIN + 4);
                    pdf.newLine(14);
                }
                for (BankAccount account : group.accounts()) {
                    pdf.text(accountLabel(account), pdf.regular, 10, MARGIN + 4, 120);
                    pdf.text(Iban.format(account.iban()), pdf.bold, 10, 170);
                    pdf.newLine(14);
                }
                pdf.newLine(4);

                for (int c = 0; c < COLUMN_X.length; c++) {
                    pdf.text(INVOICE_HEADERS[c], pdf.bold, 8, COLUMN_X[c]);
                }
                pdf.textRight(INVOICE_HEADERS[4], pdf.bold, 8, RIGHT);
                pdf.newLine(12);
                for (Line line : group.invoices()) {
                    pdf.ensureSpace(14);
                    pdf.text(line.number() == null ? "—" : line.number(), pdf.regular, 9, COLUMN_X[0], 165);
                    pdf.text(line.date() == null ? "—" : line.date().format(DATE), pdf.regular, 9, COLUMN_X[1]);
                    pdf.text(line.dueDate() == null ? "—" : line.dueDate().format(DATE), pdf.regular, 9, COLUMN_X[2]);
                    pdf.text(line.owner(), pdf.regular, 9, COLUMN_X[3], 130);
                    pdf.textRight(euros(line.amount()), pdf.regular, 9, RIGHT);
                    pdf.newLine(13);
                }
                pdf.newLine(14);
            }

            pdf.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    static String euros(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-PT"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        // O pt-PT do Java agrupa com espaço não separável (U+00A0), que a Helvetica
        // não tem no WinAnsi — fica um espaço normal.
        return format.format(value).replace(' ', ' ').replace(' ', ' ') + " €";
    }

    /** Escrita linha a linha, de cima para baixo, com mudança de página automática. */
    private static final class PdfWriter {
        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN - 10;
        }

        void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN) newPage();
        }

        void newLine(float height) throws IOException {
            y -= height;
            if (y < MARGIN) newPage();
        }

        /** Faixa de fundo do cabeçalho do fornecedor. */
        void band(float height) throws IOException {
            stream.setNonStrokingColor(0.85f, 0.88f, 0.95f);
            stream.addRect(MARGIN, y - 5, RIGHT - MARGIN, height);
            stream.fill();
            stream.setNonStrokingColor(0f, 0f, 0f);
        }

        void text(String value, PDType1Font font, float size, float x) throws IOException {
            text(value, font, size, x, Float.MAX_VALUE);
        }

        /** Escreve a partir de {@code x}, cortado com "…" se passar de {@code maxWidth}. */
        void text(String value, PDType1Font font, float size, float x, float maxWidth) throws IOException {
            String safe = fit(encodable(value, font), font, size, maxWidth);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(safe);
            stream.endText();
        }

        void textRight(String value, PDType1Font font, float size, float right) throws IOException {
            String safe = encodable(value, font);
            text(safe, font, size, right - width(safe, font, size));
        }

        void close() throws IOException {
            stream.close();
        }

        private static float width(String value, PDType1Font font, float size) throws IOException {
            return font.getStringWidth(value) / 1000f * size;
        }

        private static String fit(String value, PDType1Font font, float size, float maxWidth) throws IOException {
            if (maxWidth == Float.MAX_VALUE || width(value, font, size) <= maxWidth) return value;
            String cut = value;
            while (!cut.isEmpty() && width(cut + "…", font, size) > maxWidth) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "…";
        }

        /**
         * As fontes base do PDF só têm o WinAnsi (acentos portugueses e "€" sim,
         * o resto não): um carácter fora dele parava o documento inteiro, por
         * isso troca-se por "?".
         */
        private static String encodable(String value, PDType1Font font) {
            if (value == null) return "";
            StringBuilder out = new StringBuilder(value.length());
            value.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    out.append(ch);
                } catch (IOException | IllegalArgumentException e) {
                    out.append('?');
                }
            });
            return out.toString();
        }
    }
}
