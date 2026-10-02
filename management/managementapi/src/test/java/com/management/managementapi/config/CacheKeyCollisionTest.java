package com.management.managementapi.config;

import com.management.managementapi.enterprises.service.ConstructionBudgetItemService;
import com.management.managementapi.enterprises.service.EnterpriseService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dois métodos que partilham um cache não podem partilhar uma chave.
 *
 * O {@code SimpleKeyGenerator} do Spring constrói a chave só a partir dos
 * argumentos — o método não entra, e o nome do cache é o único namespace. Com
 * vários {@code @Cacheable} no mesmo cache, dois que recebam o mesmo argumento
 * caem na mesma entrada e o segundo lê o valor do primeiro, do tipo errado.
 *
 * Descoberto a 2026-10-01: um export estourava com
 * {@code ClassCastException: ArrayList cannot be cast to BudgetTreeDTO} porque
 * {@code listLots(enterpriseId)} e {@code getTree(enterpriseId)} dividiam a
 * chave. Ao procurar, apareceram mais duas no cache {@code enterprises} — e uma
 * delas não dava erro nenhum, só devolvia os DTOs errados.
 *
 * Cada caso abaixo é uma dessas colisões: prova-se primeiro que o gerador do
 * Spring as produz (senão o teste passava sem testar nada) e depois que o nosso
 * as separa.
 */
class CacheKeyCollisionTest {

    private final KeyGenerator ours = new CacheConfig().keyGenerator();
    private final KeyGenerator spring = new SimpleKeyGenerator();

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "O método " + type.getSimpleName() + "#" + name + " mudou de assinatura — "
                            + "este teste tem de acompanhar, senão deixa de vigiar a colisão.", e);
        }
    }

    /** As duas chaves que o Spring daria, e as duas que nós damos. */
    private void assertSeparates(Method a, Object[] argsA, Method b, Object[] argsB) {
        assertThat(spring.generate(null, a, argsA))
                .as("o gerador do Spring tem de colidir aqui, senão não há nada a testar")
                .isEqualTo(spring.generate(null, b, argsB));

        assertThat(ours.generate(null, a, argsA))
                .as("%s vs %s no mesmo cache", a.getName(), b.getName())
                .isNotEqualTo(ours.generate(null, b, argsB));
    }

    @Test
    @DisplayName("budgetTree: listLots e getTree, com o mesmo enterpriseId, ficam em chaves diferentes")
    void separatesListLotsFromGetTree() {
        UUID enterpriseId = UUID.randomUUID();
        assertSeparates(
                method(ConstructionBudgetItemService.class, "listLots", UUID.class), new Object[] { enterpriseId },
                method(ConstructionBudgetItemService.class, "getTree", UUID.class), new Object[] { enterpriseId });
    }

    @Test
    @DisplayName("budgetTree: getTree(enterpriseId) e getBudgetTree(budgetId) não se trocam com o mesmo UUID")
    void separatesEnterpriseTreeFromLotTree() {
        // hoje um enterpriseId e um budgetId nunca são o mesmo UUID, mas a separação
        // não deve depender disso — as duas devolvem BudgetTreeDTO, logo a troca
        // passava sem ClassCastException e dava a árvore errada
        UUID id = UUID.randomUUID();
        assertSeparates(
                method(ConstructionBudgetItemService.class, "getTree", UUID.class), new Object[] { id },
                method(ConstructionBudgetItemService.class, "getBudgetTree", UUID.class), new Object[] { id });
    }

    @Test
    @DisplayName("enterprises: findBasicById e findById, com o mesmo id, ficam em chaves diferentes")
    void separatesBasicFromFullEnterprise() {
        UUID id = UUID.randomUUID();
        assertSeparates(
                method(EnterpriseService.class, "findBasicById", UUID.class), new Object[] { id },
                method(EnterpriseService.class, "findById", UUID.class), new Object[] { id });
    }

    @Test
    @DisplayName("enterprises: findAllBasic e findAllActive, ambos sem argumentos, ficam em chaves diferentes")
    void separatesZeroArgEnterpriseLists() {
        // a pior das três: as duas devolvem List, o cast passa por erasure e o
        // cliente recebia em silêncio os DTOs do outro método
        assertSeparates(
                method(EnterpriseService.class, "findAllBasic"), new Object[0],
                method(EnterpriseService.class, "findAllActive"), new Object[0]);
    }

    @Test
    @DisplayName("A mesma chamada dá sempre a mesma chave — senão o cache nunca acertava")
    void sameCallGivesSameKey() {
        UUID id = UUID.randomUUID();
        Method getTree = method(ConstructionBudgetItemService.class, "getTree", UUID.class);

        assertThat(ours.generate(null, getTree, id)).isEqualTo(ours.generate(null, getTree, id));
        assertThat(ours.generate(null, getTree, id))
                .isEqualTo(CacheConfig.cacheKey(ConstructionBudgetItemService.class, "getTree", id));

        // e argumentos diferentes continuam a dar chaves diferentes
        assertThat(ours.generate(null, getTree, id))
                .isNotEqualTo(ours.generate(null, getTree, UUID.randomUUID()));
    }

    @Test
    @DisplayName("Um método sem argumentos tem chave própria e estável")
    void zeroArgKeyIsStable() {
        Method findAllBasic = method(EnterpriseService.class, "findAllBasic");

        assertThat(ours.generate(null, findAllBasic))
                .isEqualTo(ours.generate(null, findAllBasic))
                .isEqualTo(CacheConfig.cacheKey(EnterpriseService.class, "findAllBasic"));
    }
}
