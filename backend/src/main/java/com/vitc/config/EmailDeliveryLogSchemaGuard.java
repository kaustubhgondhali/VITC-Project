package com.vitc.config;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Keeps {@code email_delivery_logs.email_type} able to hold every {@link com.vitc.entity.enums.EmailType}.
 *
 * <p>database/email_delivery_logs_migration.sql declares the column VARCHAR(40), but where
 * Hibernate created the table itself ({@code ddl-auto=update}) MySQL got a native
 * {@code ENUM('COURSE_ADDED','STUDENT_CREDENTIALS')} - and {@code update} never alters an
 * existing column. Every email type added later is then rejected by MySQL, so its log row (and
 * with it the email) could never be written. This widens such a column to the VARCHAR(40) the
 * migration always intended, once, on startup. MySQL only; a no-op everywhere else.</p>
 */
@Slf4j
@Configuration
public class EmailDeliveryLogSchemaGuard {

    @Bean
    ApplicationRunner widenEmailTypeColumn(JdbcTemplate jdbc) {
        return args -> {
            try {
                String product = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
                String db = product == null ? "" : product.toLowerCase(Locale.ROOT);
                if (!db.contains("mysql") && !db.contains("mariadb")) {
                    return;
                }
                List<String> types = jdbc.queryForList(
                        "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                                + "AND TABLE_NAME = 'email_delivery_logs' AND COLUMN_NAME = 'email_type'",
                        String.class);
                if (types.isEmpty() || !"enum".equalsIgnoreCase(types.get(0))) {
                    return;
                }
                jdbc.execute("ALTER TABLE email_delivery_logs MODIFY COLUMN email_type VARCHAR(40) NOT NULL");
                log.info("Widened email_delivery_logs.email_type from ENUM to VARCHAR(40) so every email type can be logged");
            } catch (Exception ex) {
                log.warn("Could not check email_delivery_logs.email_type ({}). Run "
                        + "database/assignment_delivery_upgrade.sql, otherwise assignment emails cannot be logged.",
                        ex.getMessage());
            }
        };
    }
}
