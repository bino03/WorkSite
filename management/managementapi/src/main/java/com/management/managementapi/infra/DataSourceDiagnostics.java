package com.management.managementapi.infra;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Component
public class DataSourceDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceDiagnostics.class);

    private final DataSource dataSource;
    private final DataSourceProperties props;
    private final JdbcTemplate jdbc;

    public DataSourceDiagnostics(DataSource dataSource, DataSourceProperties props, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.props = props;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void printProps() {
        // Mostra o que o Spring *efetivamente* está a usar (sem password)
        log.info("===[Datasource props efetivas]===");
        log.info("URL     : {}", nullSafe(props.getUrl()));
        log.info("User    : {}", nullSafe(props.getUsername()));
        // NÃO imprimir password por segurança
        log.info("Driver  : {}", nullSafe(props.getDriverClassName()));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("===[Teste de ligação à BD]===");
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            log.info("Conectado ✅  DB={} v{}.{}  Driver={} {}",
                    md.getDatabaseProductName(),
                    md.getDatabaseMajorVersion(),
                    md.getDatabaseMinorVersion(),
                    md.getDriverName(),
                    md.getDriverVersion());

            String dbUser  = jdbc.queryForObject("select current_user", String.class);
            String dbName  = jdbc.queryForObject("select current_database()", String.class);
            String addr    = jdbc.queryForObject("select inet_server_addr()::text", String.class);
            Integer port   = jdbc.queryForObject("select inet_server_port()", Integer.class);
            log.info("Sessão   : user='{}' db='{}' host='{}' port={}", dbUser, dbName, addr, port);
        } catch (SQLException e) {
            log.error("Falha ao obter ligação ❌  SQLState={} ErrorCode={} Msg={}",
                    e.getSQLState(), e.getErrorCode(), e.getMessage());
            // Desenrola toda a causa
            Throwable cause = e.getCause();
            while (cause != null) {
                log.error("Caused by: {}: {}", cause.getClass().getName(), cause.getMessage());
                cause = cause.getCause();
            }
            // Re-lança para que o boot falhe (e não esconda o problema)
            throw e;
        }
    }

    private String nullSafe(String s) {
        return (s == null || s.isBlank()) ? "<vazio>" : s;
    }
}
