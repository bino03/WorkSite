package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.backup.request.BackupRequestDTO;
import com.management.managementapi.enterprises.dto.backup.response.BackupPreviewDTO;
import com.management.managementapi.enterprises.service.BackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/** O download à mão de obras e lotes, num só zip. Só ADMIN: é um download em massa de todas as faturas. */
@RestController
@RequestMapping("/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @PostMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public BackupPreviewDTO preview(@Valid @RequestBody BackupRequestDTO request) {
        return backupService.preview(request);
    }

    /** A seleção resolve-se antes do {@code return}: é aí que um erro ainda sai como JSON. */
    @PostMapping("/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamingResponseBody> download(@Valid @RequestBody BackupRequestDTO request) {
        BackupService.Backup backup = backupService.plan(request);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(backup.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out -> backupService.write(backup, out));
    }
}
