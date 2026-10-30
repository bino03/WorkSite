package com.management.managementapi.security;

import org.mockito.Mockito;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Avalia os {@code @PreAuthorize} dos controllers como o Spring Security os avalia num pedido,
 * mas sem contexto Spring nem base de dados: só a expressão, uma authority e os argumentos.
 * Um teste com o contexto inteiro ligaria à BD real (ver docs/commands.md).
 */
final class PreAuthorizeEvaluator {

    private static final DefaultMethodSecurityExpressionHandler HANDLER = new DefaultMethodSecurityExpressionHandler();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private PreAuthorizeEvaluator() {
    }

    /** As regras como o {@code SecurityConfig} as monta: {@code ROLE_AUTHENTICATED} + {@code ROLE_<role>}. */
    static boolean allows(String role, Method method, Map<String, Object> namedArguments) {
        String expression = expressionOf(method)
                .orElseThrow(() -> new AssertionError(method + " não tem @PreAuthorize"));
        var authentication = new TestingAuthenticationToken("user", "n/a", "ROLE_AUTHENTICATED", "ROLE_" + role);
        // o Spring resolve o método pela classe do alvo — um mock basta, nunca é chamado
        Object target = Mockito.mock(method.getDeclaringClass());
        var invocation = new SimpleMethodInvocation(target, method, argumentsFor(method, namedArguments));
        EvaluationContext context = HANDLER.createEvaluationContext(() -> authentication, invocation);
        return ExpressionUtils.evaluateAsBoolean(HANDLER.getExpressionParser().parseExpression(expression), context);
    }

    static boolean allows(String role, Method method) {
        return allows(role, method, Map.of());
    }

    /** A anotação do método, ou a da classe quando o método não tem — a mesma precedência do Spring. */
    static Optional<String> expressionOf(Method method) {
        PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (onMethod != null) return Optional.of(onMethod.value());
        PreAuthorize onClass = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PreAuthorize.class);
        return Optional.ofNullable(onClass).map(PreAuthorize::value);
    }

    /** Os métodos que servem pedidos HTTP ({@code @GetMapping} e afins são meta-anotados com {@code @RequestMapping}). */
    static List<Method> endpoints(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();
    }

    static Method endpoint(Class<?> controller, String name) {
        List<Method> matches = endpoints(controller).stream().filter(method -> method.getName().equals(name)).toList();
        if (matches.size() != 1) {
            throw new AssertionError(controller.getSimpleName() + "." + name + ": esperava 1 endpoint, há " + matches.size());
        }
        return matches.get(0);
    }

    static List<Class<?>> controllersIn(String basePackage) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents(basePackage).stream()
                .<Class<?>>map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(), null))
                .toList();
    }

    private static Object[] argumentsFor(Method method, Map<String, Object> namedArguments) {
        String[] names = PARAMETER_NAMES.getParameterNames(method);
        Object[] arguments = new Object[method.getParameterCount()];
        if (names == null) return arguments;
        for (int index = 0; index < names.length; index++) {
            arguments[index] = namedArguments.get(names[index]);
        }
        return arguments;
    }
}
