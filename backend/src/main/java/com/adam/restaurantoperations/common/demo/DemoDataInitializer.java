package com.adam.restaurantoperations.common.demo;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(100)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String SCRIPT = "db/dev/showcase-data.seed";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHOWCASE_DATA_SEEDED'") > 0) {
            LOGGER.info("Development showcase data already exists; initializer made no changes");
            return;
        }
        if (count("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id "
                + "JOIN roles r ON r.id = ur.role_id "
                + "WHERE u.enabled = TRUE AND r.enabled = TRUE AND r.name = 'ADMIN'") == 0) {
            throw new IllegalStateException(
                    "Development showcase data requires an enabled bootstrap administrator");
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCRIPT));
        LOGGER.info("Development showcase data created successfully");
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
