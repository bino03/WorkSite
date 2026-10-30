package com.management.managementapi.security;

import com.management.managementapi.controller.AdminAuthController;
import com.management.managementapi.controller.ActivityLogController;
import com.management.managementapi.controller.EmailProviderController;
import com.management.managementapi.controller.EmployeesController;
import com.management.managementapi.controller.TaskController;
import com.management.managementapi.enterprises.controller.ConstructionInvoiceController;
import com.management.managementapi.enterprises.controller.PaymentController;
import com.management.managementapi.notifications.controller.NotificationController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.management.managementapi.security.PreAuthorizeEvaluator.allows;
import static com.management.managementapi.security.PreAuthorizeEvaluator.controllersIn;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoint;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoints;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que a role da contabilidade abre e o que continua fechado (docs/security.md → "Autorização").
 */
class AccountantAccessTest {

    private static final String ACCOUNTANT = "ACCOUNTANT";

    @Test
    void opensCompanyInvoicesPaymentsAndApproval() {
        Method upload = endpoint(ConstructionInvoiceController.class, "upload");

        assertThat(allows(ACCOUNTANT, upload, Map.of("scope", "COMPANY"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(ConstructionInvoiceController.class, "setApprovedForPayment"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(PaymentController.class, "markAsPaid"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(PaymentController.class, "delete"))).isTrue();
    }

    @Test
    void employeeStillCannotUseCompanyScopeOrPayments() {
        Method upload = endpoint(ConstructionInvoiceController.class, "upload");

        assertThat(allows("EMPLOYEE", upload, Map.of("scope", "COMPANY"))).isFalse();
        assertThat(allows("EMPLOYEE", upload, Map.of("scope", "PROJECT"))).isTrue();
        assertThat(allows("EMPLOYEE", endpoint(PaymentController.class, "markAsPaid"))).isFalse();
    }

    @Test
    void opensTheSharedReadsOfTheWorksSpace() {
        assertThat(allows(ACCOUNTANT, endpoint(EmployeesController.class, "search"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(EmployeesController.class, "listAssignable"))).isTrue();
        assertThat(endpoints(NotificationController.class)).allMatch(method -> allows(ACCOUNTANT, method));
        assertThat(endpoints(ActivityLogController.class)).allMatch(method -> allows(ACCOUNTANT, method));
    }

    @Test
    void tasksAsAnEmployee() {
        assertThat(allows(ACCOUNTANT, endpoint(TaskController.class, "list"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(TaskController.class, "updateStatus"))).isTrue();
        assertThat(allows(ACCOUNTANT, endpoint(TaskController.class, "create"))).isFalse();
        assertThat(allows(ACCOUNTANT, endpoint(TaskController.class, "delete"))).isFalse();
    }

    @Test
    void closesTheTeamSpace() {
        List<Method> attendance = controllersIn("com.management.managementapi.controller.attendance").stream()
                .flatMap(controller -> endpoints(controller).stream())
                .toList();

        assertThat(attendance).isNotEmpty().noneMatch(method -> allows(ACCOUNTANT, method));
    }

    @Test
    void closesSettingsAccountsAndInvites() {
        assertThat(endpoints(EmailProviderController.class)).noneMatch(method -> allows(ACCOUNTANT, method));
        assertThat(endpoints(AdminAuthController.class)).noneMatch(method -> allows(ACCOUNTANT, method));
        assertThat(allows(ACCOUNTANT, endpoint(EmployeesController.class, "patchRole"))).isFalse();
        assertThat(allows(ACCOUNTANT, endpoint(EmployeesController.class, "blockProfile"))).isFalse();
    }
}
