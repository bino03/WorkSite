package com.management.managementapi.controller.attendance;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.attendance.response.AttendanceMonthReportDTO;
import com.management.managementapi.service.attendance.AttendanceExcelExportService;
import com.management.managementapi.service.attendance.AttendanceExcelExportService.ExportFile;
import com.management.managementapi.service.attendance.AttendanceReportService;

import lombok.RequiredArgsConstructor;

/**
 * o fecho do mês: por funcionário e por obra, e o mesmo num {@code .xlsx} com os
 * registos de onde saiu (picagens, incluindo as anuladas, e correções). Só leitura —
 * tudo é derivado e gerado a pedido.
 */
@RestController
@RequestMapping("/attendance/reports")
@RequiredArgsConstructor
public class AttendanceReportController {

    private final AttendanceReportService reportService;
    private final AttendanceExcelExportService exportService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/month")
    public ResponseEntity<AttendanceMonthReportDTO> month(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(reportService.forMonth(month));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/month/export")
    public ResponseEntity<byte[]> exportMonth(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        ExportFile file = exportService.exportMonth(month);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(AttendanceExcelExportService.CONTENT_TYPE))
                .body(file.content());
    }
}
