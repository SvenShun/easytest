package com.testhub.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Keeps a few legacy MySQL ENUM columns compatible with new enum values.
 *
 * <p>Earlier releases created {@code plan_run_item.executor_type} and
 * {@code plan_case.executor_type_override} as MySQL ENUMs. Hibernate's
 * {@code ddl-auto=update} does not widen those, so adding {@code API} would fail
 * with "Data truncated". Convert them to VARCHAR on MySQL; other databases are
 * untouched.</p>
 */
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
        alter("ALTER TABLE plan_run_item MODIFY COLUMN executor_type VARCHAR(16) NULL");
        alter("ALTER TABLE plan_case MODIFY COLUMN executor_type_override VARCHAR(16) NULL");
        alter("ALTER TABLE plan_run_item MODIFY COLUMN error TEXT NULL");
        alter("ALTER TABLE plan_run_item MODIFY COLUMN summary TEXT NULL");
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
