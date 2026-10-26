package com.testhub.uiexec.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/** Widen legacy MySQL ENUM columns when new executor types are introduced. */
@Component
public class SchemaCompatibility implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibility.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SchemaCompatibility(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("mysql")) {
                return;
            }
        } catch (Exception e) {
            log.debug("schema compatibility check skipped: {}", e.getMessage());
            return;
        }
        alter("ALTER TABLE ue_task MODIFY COLUMN executor_type VARCHAR(32) NOT NULL");
        alter("ALTER TABLE ue_task MODIFY COLUMN steps_json LONGTEXT NULL");
    }

    private void alter(String sql) {
        try {
            jdbc.execute(sql);
            log.info("schema compatibility applied: {}", sql);
        } catch (Exception e) {
            log.debug("schema compatibility skipped ({}): {}", sql, e.getMessage());
        }
    }
}
