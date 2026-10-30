package com.management.managementapi.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.management.managementapi.security.PreAuthorizeEvaluator.allows;
import static com.management.managementapi.security.PreAuthorizeEvaluator.controllersIn;
import static com.management.managementapi.security.PreAuthorizeEvaluator.endpoints;
import static com.management.managementapi.security.PreAuthorizeEvaluator.expressionOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * No espaço Obras o ACCOUNTANT faz o que o ADMIN faz (docs/security.md → "Autorização").
 * Este teste é o que impede um endpoint novo de Obras de nascer só com {@code hasRole('ADMIN')}.
 */
class WorksEndpointsRoleGuardTest {

    private static final String WORKS_CONTROLLERS = "com.management.managementapi.enterprises.controller";

    @Test
    void everyWorksEndpointHasPreAuthorize() {
        List<String> unguarded = worksEndpoints().stream()
                .filter(method -> expressionOf(method).isEmpty())
                .map(WorksEndpointsRoleGuardTest::describe)
                .toList();

        assertThat(unguarded).isEmpty();
    }

    @Test
    void accountantIsAllowedWhereverAdminIs() {
        // scope=COMPANY é o caso mais restrito das listas por scope (#scope na expressão)
        Map<String, Object> arguments = Map.of("scope", "COMPANY");

        List<String> adminOnly = worksEndpoints().stream()
                .filter(method -> expressionOf(method).isPresent())
                .filter(method -> allows("ADMIN", method, arguments) && !allows("ACCOUNTANT", method, arguments))
                .map(WorksEndpointsRoleGuardTest::describe)
                .toList();

        assertThat(adminOnly).isEmpty();
    }

    @Test
    void scansTheWorksControllers() {
        assertThat(controllersIn(WORKS_CONTROLLERS)).hasSizeGreaterThanOrEqualTo(8);
    }

    private static List<Method> worksEndpoints() {
        return controllersIn(WORKS_CONTROLLERS).stream().flatMap(controller -> endpoints(controller).stream()).toList();
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
