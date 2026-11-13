package com.management.managementapi.security;

import com.management.managementapi.enterprises.controller.BackupController;
import org.junit.jupiter.api.Test;

import static com.management.managementapi.security.PreAuthorizeEvaluator.allows;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoints;
import static org.assertj.core.api.Assertions.assertThat;

/** Os backups são um download em massa de todas as faturas: só o ADMIN (notes/roadmap/backups.md). */
class BackupAccessTest {

    @Test
    void onlyAdminCanPreviewOrDownload() {
        assertThat(endpoints(BackupController.class)).hasSize(2)
                .allMatch(method -> allows("ADMIN", method))
                .noneMatch(method -> allows("EMPLOYEE", method))
                .noneMatch(method -> allows("ACCOUNTANT", method));
    }
}
