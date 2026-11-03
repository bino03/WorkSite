package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.backup.request.BackupRequestDTO;
import com.management.managementapi.enterprises.dto.backup.response.BackupPreviewDTO;
import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceDocumentsScope;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.service.attendance.AttendanceZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * O download à mão de obras e lotes, num só zip, com a data do backup no nome das
 * pastas. Desenho em notes/roadmap/backups.md.
 *
 * Não é {@code @Transactional}, de propósito: o {@link #write} corre enquanto a
 * resposta se escreve e seguraria uma ligação durante todo o streaming, num pool de
 * 5. Os livros geram-se um a um durante o streaming, cada um na sua transação, porque
 * todos à partida ficavam na heap ao mesmo tempo numa máquina de 1 GB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    static final String ERRORS_REPORT = "_ERROS.txt";
    static final String WITHOUT_LOT_FOLDER = "Sem lote";
    static final String COMPANY_FOLDER = "Despesas da empresa";
    static final String UNIDENTIFIED_FOLDER = "Faturas por identificar";
    /** No vault, a quarentena tem {@code Lançadas\} sem o nível {@code Faturas\}, ao contrário das outras pastas. */
    private static final String UNIDENTIFIED_DOCUMENTS = "Lançadas/";
    private static final String ZIP_NAME = "Backup Vilatro";
    private static final InvoiceDocumentsExportService.Plan NO_DOCUMENTS =
            new InvoiceDocumentsExportService.Plan(List.of(), 0, List.of());

    private final EnterpriseRepository enterpriseRepository;
    private final ConstructionBudgetItemService budgetService;
    private final BudgetExcelExportService excelExportService;
    private final InvoiceDocumentsExportService documentsExportService;
    private final UnassignedInvoicesWorkbookService unassignedWorkbookService;
    private final AttendanceZone zone;

    /** Uma pasta do zip. {@code folder} acaba em {@code /}; sem {@code workbook}, o Excel não foi pedido. */
    record Part(String folder, String workbookName, Supplier<BudgetExcelExportService.ExportFile> workbook,
                InvoiceDocumentsExportService.Plan documents, List<String> warnings) {}

    public record Backup(String fileName, List<Part> parts, List<String> warnings) {}

    // ── o que vai no zip ─────────────────────────────────────

    public BackupPreviewDTO preview(BackupRequestDTO request) {
        Backup backup = resolve(request);
        List<BackupPreviewDTO.Folder> folders = backup.parts().stream()
                .map(part -> new BackupPreviewDTO.Folder(
                        part.folder().substring(0, part.folder().length() - 1),
                        part.workbookName(),
                        part.documents().entries().size(),
                        part.documents().invoicesWithoutDocument(),
                        part.warnings()))
                .toList();
        int totalDocuments = folders.stream().mapToInt(BackupPreviewDTO.Folder::documentCount).sum();
        return new BackupPreviewDTO(backup.fileName(), folders, totalDocuments, backup.warnings());
    }

    /** Como a pré-visualização, mas recusa um download que daria um zip vazio. */
    public Backup plan(BackupRequestDTO request) {
        Backup backup = resolve(request);
        if (backup.parts().isEmpty()) {
            throw new BusinessException(ErrorCode.BACKUP_NOTHING_TO_DOWNLOAD);
        }
        return backup;
    }

    private Backup resolve(BackupRequestDTO request) {
        if (!request.includeWorkbooks() && !request.includeDocuments()) {
            throw new BusinessException(ErrorCode.BACKUP_NO_CONTENT_TYPE);
        }
        List<BackupRequestDTO.Selection> selections = selectionsOf(request);

        // Lisboa e não o servidor (UTC): à meia-noite e meia de verão ainda seria ontem
        String date = DateTimeFormatter.ISO_LOCAL_DATE.format(zone.today());
        Set<String> takenFolders = new HashSet<>();
        List<Part> parts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (BackupRequestDTO.Selection selection : selections) {
            Enterprise enterprise = enterpriseRepository.findById(selection.enterpriseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND));
            List<BudgetLotDTO> lots = budgetService.listLots(enterprise.getId());

            if (selection.budgetId() != null) {
                addChosenLot(enterprise, lots, selection.budgetId(), date, takenFolders, request, parts, warnings);
            } else if (lots.size() > 1) {
                addEveryLot(enterprise, lots, date, takenFolders, request, parts, warnings);
            } else {
                addWholeEnterprise(enterprise, lots, date, takenFolders, request, parts, warnings);
            }
        }

        if (request.all() || request.includeCompany()) {
            addWithoutEnterprise(ConstructionInvoice.Scope.COMPANY, COMPANY_FOLDER, InvoiceDocumentsExportService.FOLDER,
                    date, takenFolders, request, parts, warnings);
        }
        if (request.all() || request.includeUnidentified()) {
            addWithoutEnterprise(ConstructionInvoice.Scope.UNIDENTIFIED, UNIDENTIFIED_FOLDER, UNIDENTIFIED_DOCUMENTS,
                    date, takenFolders, request, parts, warnings);
        }

        return new Backup(zipName(parts, date), parts, warnings);
    }

    /** Despesas da empresa ou quarentena. Sem faturas não há pasta: o importador recusaria um livro vazio. */
    private void addWithoutEnterprise(ConstructionInvoice.Scope scope, String name, String documentsFolder, String date,
                                      Set<String> takenFolders, BackupRequestDTO request,
                                      List<Part> parts, List<String> warnings) {
        String folder = unique(name + " - " + date, takenFolders) + "/";
        InvoiceDocumentsExportService.Plan documents =
                documentsExportService.planWithoutEnterprise(scope, folder + documentsFolder);
        if (documents.isEmpty()) {
            warnings.add(name + ": sem faturas, fica de fora.");
            return;
        }
        String workbookName = scope == ConstructionInvoice.Scope.UNIDENTIFIED
                ? UnassignedInvoicesWorkbookService.UNIDENTIFIED_FILE_NAME
                : UnassignedInvoicesWorkbookService.COMPANY_FILE_NAME;
        addPart(folder, request, workbookName, () -> unassignedWorkbookService.export(scope), documents,
                new ArrayList<>(), parts, warnings);
    }

    private List<BackupRequestDTO.Selection> selectionsOf(BackupRequestDTO request) {
        if (request.all()) {
            return enterpriseRepository.findByIsActiveTrue().stream()
                    .filter(enterprise -> !Boolean.TRUE.equals(enterprise.getIsTest()))
                    .sorted(Comparator.comparing(Enterprise::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(enterprise -> new BackupRequestDTO.Selection(enterprise.getId(), null))
                    .toList();
        }
        List<BackupRequestDTO.Selection> requested = request.selections() == null ? List.of() : request.selections();
        if (requested.isEmpty() && !request.includeCompany() && !request.includeUnidentified()) {
            throw new BusinessException(ErrorCode.BACKUP_NO_SELECTION);
        }
        List<BackupRequestDTO.Selection> selections = requested.stream().distinct().toList();
        Set<UUID> wholeEnterprises = new HashSet<>();
        for (BackupRequestDTO.Selection selection : selections) {
            if (selection.budgetId() == null) {
                wholeEnterprises.add(selection.enterpriseId());
            }
        }
        boolean duplicates = selections.stream()
                .anyMatch(selection -> selection.budgetId() != null && wholeEnterprises.contains(selection.enterpriseId()));
        if (duplicates) {
            throw new BusinessException(ErrorCode.BACKUP_INVALID_SELECTION);
        }
        return selections;
    }

    /** Um lote escolhido à mão: uma pasta de topo só dele. */
    private void addChosenLot(Enterprise enterprise, List<BudgetLotDTO> lots, UUID budgetId, String date,
                              Set<String> takenFolders, BackupRequestDTO request,
                              List<Part> parts, List<String> warnings) {
        BudgetLotDTO lot = lots.stream()
                .filter(candidate -> candidate.id().equals(budgetId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BACKUP_INVALID_SELECTION));
        String folder = unique(enterpriseLabel(enterprise) + " - " + BudgetExcelExportService.safeName(lot.name())
                + " - " + date, takenFolders) + "/";

        List<String> partWarnings = new ArrayList<>();
        InvoiceDocumentsExportService.Plan withoutLot = documentsExportService.planWithoutLot(enterprise.getId(), "");
        if (!withoutLot.isEmpty()) {
            partWarnings.add("A obra tem faturas sem lote: só saem no backup da obra inteira.");
        }
        addLot(enterprise, lot, folder, request, partWarnings, parts, warnings);
    }

    /** Obra com vários lotes: uma pasta por lote, e nunca um Excel com os lotes todos. */
    private void addEveryLot(Enterprise enterprise, List<BudgetLotDTO> lots, String date,
                             Set<String> takenFolders, BackupRequestDTO request,
                             List<Part> parts, List<String> warnings) {
        String top = unique(enterpriseLabel(enterprise) + " - " + date, takenFolders) + "/";
        Set<String> takenLots = new HashSet<>();
        for (BudgetLotDTO lot : lots) {
            String folder = top + unique(BudgetExcelExportService.safeName(lot.name()), takenLots) + "/";
            addLot(enterprise, lot, folder, request, new ArrayList<>(), parts, warnings);
        }

        String folder = top + unique(WITHOUT_LOT_FOLDER, takenLots) + "/";
        UUID enterpriseId = enterprise.getId();
        InvoiceDocumentsExportService.Plan documents = documentsExportService.planWithoutLot(
                enterpriseId, folder + InvoiceDocumentsExportService.FOLDER);
        // sem faturas sem lote, esta pasta não é um esquecimento: não existe, e não se avisa
        if (!documents.isEmpty()) {
            addPart(folder, request, BudgetExcelExportService.withoutLotFileName(enterprise),
                    () -> excelExportService.exportWithoutLot(enterpriseId), documents, new ArrayList<>(), parts, warnings);
        }
    }

    private void addLot(Enterprise enterprise, BudgetLotDTO lot, String folder, BackupRequestDTO request,
                        List<String> partWarnings, List<Part> parts, List<String> warnings) {
        InvoiceDocumentsExportService.Plan documents = documentsExportService.plan(enterprise.getId(),
                InvoiceDocumentsScope.ASSOCIATED, lot.id(), folder + InvoiceDocumentsExportService.FOLDER);
        boolean hasBudget = lot.itemCount() > 0;
        if (!hasBudget && documents.isEmpty()) {
            warnings.add(displayName(folder) + ": sem orçamento nem faturas, fica de fora.");
            return;
        }
        UUID budgetId = lot.id();
        Set<BudgetExportSheet> sheets = sheetsFor(hasBudget);
        addPart(folder, request, BudgetExcelExportService.lotFileName(enterprise, lot.name()),
                () -> excelExportService.exportLot(budgetId, sheets), documents, partWarnings, parts, warnings);
    }

    /** Até um lote: a pasta é a da obra no vault, com um só livro. */
    private void addWholeEnterprise(Enterprise enterprise, List<BudgetLotDTO> lots, String date,
                                    Set<String> takenFolders, BackupRequestDTO request,
                                    List<Part> parts, List<String> warnings) {
        String folder = unique(enterpriseLabel(enterprise) + " - " + date, takenFolders) + "/";
        UUID enterpriseId = enterprise.getId();
        InvoiceDocumentsExportService.Plan documents = documentsExportService.plan(enterpriseId,
                InvoiceDocumentsScope.ALL, null, folder + InvoiceDocumentsExportService.FOLDER);
        boolean hasBudget = lots.stream().anyMatch(lot -> lot.itemCount() > 0);
        if (!hasBudget && documents.isEmpty()) {
            warnings.add(displayName(folder) + ": sem orçamento nem faturas, fica de fora.");
            return;
        }
        Set<BudgetExportSheet> sheets = sheetsFor(hasBudget);
        addPart(folder, request, BudgetExcelExportService.fileName(enterprise),
                () -> excelExportService.export(enterpriseId, sheets), documents, new ArrayList<>(), parts, warnings);
    }

    /** Aplica as duas caixas, Excel e Ficheiros. Uma pasta que ficaria vazia sai com aviso. */
    private void addPart(String folder, BackupRequestDTO request, String workbookName,
                         Supplier<BudgetExcelExportService.ExportFile> workbook,
                         InvoiceDocumentsExportService.Plan documents, List<String> partWarnings,
                         List<Part> parts, List<String> warnings) {
        InvoiceDocumentsExportService.Plan included = request.includeDocuments() ? documents : NO_DOCUMENTS;
        if (!request.includeWorkbooks() && included.entries().isEmpty()) {
            warnings.add(displayName(folder) + ": sem ficheiros, fica de fora.");
            return;
        }
        partWarnings.addAll(included.warnings());
        parts.add(new Part(folder,
                request.includeWorkbooks() ? workbookName : null,
                request.includeWorkbooks() ? workbook : null,
                included, partWarnings));
    }

    /** {@code BUDGET}/{@code COMPARISON} sem orçamento dariam {@code BUDGET_027}, e o backup inteiro rebentava. */
    private static Set<BudgetExportSheet> sheetsFor(boolean hasBudget) {
        return hasBudget
                ? EnumSet.of(BudgetExportSheet.BUDGET, BudgetExportSheet.EXPENSES, BudgetExportSheet.COMPARISON)
                : EnumSet.of(BudgetExportSheet.EXPENSES);
    }

    private static String enterpriseLabel(Enterprise enterprise) {
        return BudgetExcelExportService.testPrefix(enterprise) + BudgetExcelExportService.folderName(enterprise);
    }

    private static String displayName(String folder) {
        return folder.substring(0, folder.length() - 1);
    }

    /** {@code ZipOutputStream} rebenta com entradas repetidas, e o Windows não distingue maiúsculas. */
    private static String unique(String name, Set<String> taken) {
        String candidate = name;
        for (int n = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); n++) {
            candidate = name + " (" + n + ")";
        }
        return candidate;
    }

    private static String zipName(List<Part> parts, String date) {
        Set<String> tops = new HashSet<>();
        for (Part part : parts) {
            tops.add(part.folder().substring(0, part.folder().indexOf('/')));
        }
        return tops.size() == 1 ? tops.iterator().next() + ".zip" : ZIP_NAME + " - " + date + ".zip";
    }

    // ── o zip ─────────────────────────────────────────────────

    /**
     * Um Excel que falhe vai para o {@code _ERROS.txt} e o backup continua: os cabeçalhos
     * já seguiram, e um zip que declara a falta é melhor do que um cortado a meio. Uma
     * {@link IOException} a escrever propaga-se, porque é o browser a cancelar, e o
     * servidor tem de parar de descarregar do Storage.
     *
     * Compressão rápida: os PDFs quase não comprimem, e a CPU é partilhada com os pedidos
     * de toda a gente.
     */
    public void write(Backup backup, OutputStream out) throws IOException {
        List<String> errors = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.BEST_SPEED);
            for (Part part : backup.parts()) {
                writeWorkbook(zip, part, errors);
                documentsExportService.writeDocuments(zip, part.documents());
            }
            if (!errors.isEmpty()) {
                zip.putNextEntry(new ZipEntry(ERRORS_REPORT));
                zip.write(("O que falhou neste backup e não está no zip:\n"
                        + String.join("\n", errors) + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private void writeWorkbook(ZipOutputStream zip, Part part, List<String> errors) throws IOException {
        if (part.workbook() == null) {
            return;
        }
        BudgetExcelExportService.ExportFile file;
        try {
            file = part.workbook().get();
        } catch (RuntimeException e) {
            log.error("Backup: o Excel de \"{}\" não foi gerado: {}", part.folder(), e.getMessage(), e);
            errors.add(part.folder() + part.workbookName() + ": o Excel não foi gerado");
            return;
        }
        zip.putNextEntry(new ZipEntry(part.folder() + file.fileName()));
        zip.write(file.content());
        zip.closeEntry();
    }
}
