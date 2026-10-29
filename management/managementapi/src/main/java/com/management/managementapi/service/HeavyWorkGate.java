package com.management.managementapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Uma operação pesada em memória de cada vez: leitura de QR das faturas, miniaturas,
 * import/export de Excel. Em produção o backend corre numa máquina de 1 GB
 * (Fly.io — ver docs/operations.md → "Memória"), com a heap perto do pico do arranque;
 * cada uma destas sozinha cabe, várias em simultâneo podem não caber e o processo é
 * morto. Com o portão os picos não se somam — o segundo pedido espera uns segundos em
 * vez de derrubar a app.
 *
 * <p>Reentrante: uma operação que chame outra (ex. um export que gere o Excel e depois
 * leia algo pesado) não se bloqueia a si própria. Justo: quem chegou primeiro passa
 * primeiro. Se a espera passar de {@link #MAX_WAIT_MINUTES}, avança na mesma — perder
 * o pedido seria pior do que o risco de memória, e um bloqueio eterno só esconderia
 * uma operação presa.
 *
 * <p>Fica de fora o zip da pasta da obra ({@code InvoiceDocumentsExportService.writeZip}):
 * escreve em streaming, documento a documento, e pode demorar minutos — prender o
 * portão esse tempo todo pararia o carregamento de faturas sem ganho de memória.
 */
@Slf4j
@Component
public class HeavyWorkGate {

    private static final long MAX_WAIT_MINUTES = 5;

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T run(String what, Supplier<T> work) {
        boolean acquired = lock.tryLock();
        if (!acquired) {
            log.info("[heavy-work] '{}' à espera de outra operação pesada terminar", what);
            try {
                acquired = lock.tryLock(MAX_WAIT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!acquired) {
                log.warn("[heavy-work] '{}' esperou mais de {} min — avança sem o portão",
                        what, MAX_WAIT_MINUTES);
            }
        }
        try {
            return work.get();
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
