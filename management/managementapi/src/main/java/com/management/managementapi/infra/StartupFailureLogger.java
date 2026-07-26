package com.management.managementapi.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class StartupFailureLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupFailureLogger.class);

    @EventListener
    public void onFailed(ApplicationFailedEvent event) {
        Throwable t = event.getException();
        log.error("=== A aplicação falhou ao arrancar ===");
        int depth = 0;
        while (t != null && depth++ < 20) {
            if (t instanceof SQLException) {
                SQLException se = (SQLException) t;
                log.error("{}: {} (SQLState={}, ErrorCode={})",
                        se.getClass().getSimpleName(), se.getMessage(), se.getSQLState(), se.getErrorCode());
            } else {
                log.error("{}: {}", t.getClass().getSimpleName(), t.getMessage());
            }
            t = t.getCause();
        }
        log.error("=== Fim da stack simplificada ===");
    }
}
