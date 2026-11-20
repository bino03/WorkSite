package com.management.managementapi.security;

import static com.management.managementapi.security.PreAuthorizeEvaluator.allows;
import static com.management.managementapi.security.PreAuthorizeEvaluator.controllersIn;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoint;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoints;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.management.managementapi.controller.ActivityLogController;
import com.management.managementapi.controller.EmployeesController;
import com.management.managementapi.controller.LocationController;
import com.management.managementapi.controller.ProfileController;
import com.management.managementapi.controller.TaskController;
import com.management.managementapi.controller.attendance.ClockController;
import com.management.managementapi.controller.attendance.MyAttendanceController;
import com.management.managementapi.notifications.controller.NotificationController;

/**
 * O {@code EMPLOYEE} é a malta da obra (decisão de 2026-11-20): pica o ponto, vê as suas
 * tarefas, as notificações e o perfil — e nada do espaço Obras (obras, orçamentos, despesas,
 * faturas, fornecedores), nem a lista de funcionários, nem o histórico, nem as localizações.
 * Este teste é o que impede um endpoint novo de Obras de nascer aberto a um {@code EMPLOYEE}.
 */
class EmployeeAccessTest {

    private static final String EMPLOYEE = "EMPLOYEE";

    @Test
    void closesTheWholeWorksSpace() {
        List<Method> works = controllersIn("com.management.managementapi.enterprises.controller").stream()
                .flatMap(controller -> endpoints(controller).stream())
                .toList();

        // PROJECT é o scope que lhe estava aberto até 2026-11-20.
        assertThat(works).isNotEmpty()
                .noneMatch(method -> allows(EMPLOYEE, method, Map.of("scope", "PROJECT")));
    }

    @Test
    void closesEmployeesActivityAndLocations() {
        assertThat(endpoints(EmployeesController.class)).noneMatch(method -> allows(EMPLOYEE, method));
        assertThat(endpoints(ActivityLogController.class)).noneMatch(method -> allows(EMPLOYEE, method));
        assertThat(endpoints(LocationController.class)).noneMatch(method -> allows(EMPLOYEE, method));
        assertThat(allows(EMPLOYEE, endpoint(ProfileController.class, "getAllAgents"))).isFalse();
    }

    @Test
    void keepsClockingTasksAndNotifications() {
        assertThat(allows(EMPLOYEE, endpoint(ClockController.class, "preview"))).isTrue();
        assertThat(allows(EMPLOYEE, endpoint(ClockController.class, "clock"))).isTrue();
        assertThat(allows(EMPLOYEE, endpoint(ClockController.class, "myToday"))).isTrue();
        assertThat(allows(EMPLOYEE, endpoint(ClockController.class, "generate"))).isFalse();

        assertThat(allows(EMPLOYEE, endpoint(TaskController.class, "list"))).isTrue();
        assertThat(allows(EMPLOYEE, endpoint(TaskController.class, "updateStatus"))).isTrue();
        assertThat(endpoints(NotificationController.class)).allMatch(method -> allows(EMPLOYEE, method));
        // "As minhas férias": só leitura, sempre do próprio.
        assertThat(endpoints(MyAttendanceController.class)).isNotEmpty().allMatch(method -> allows(EMPLOYEE, method));
    }
}
