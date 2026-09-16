package com.arthurpaiao.creditengine;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Runs in Maven verify, against a disposable PostgreSQL; never silently skips missing Docker. */
class DatabaseIT {
    @Test void migrationsSeedAndConstraintsWorkOnPostgres() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT base_rate, max_term_months, exchange_validity_hours FROM pricing_config WHERE id=1")) {
                    assertTrue(rows.next());
                    assertEquals(0, new java.math.BigDecimal("0.01").compareTo(rows.getBigDecimal(1)));
                    assertEquals(120, rows.getInt(2));
                    assertEquals(24, rows.getInt(3));
                }
                var duplicate = assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO assignors VALUES ('aebc49b1-1c4a-4f86-bf4e-f0224a9aa009', 'CED-001', 'Duplicado')"));
                assertEquals("23505", duplicate.getSQLState());
                var invalidRate = assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO exchange_rates VALUES ('aebc49b1-1c4a-4f86-bf4e-f0224a9aa010', 'USD/BRL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
                assertEquals("23514", invalidRate.getSQLState());
                connection.setAutoCommit(false);
                statement.executeUpdate("INSERT INTO assignors VALUES ('aebc49b1-1c4a-4f86-bf4e-f0224a9aa011', 'CED-ROLLBACK', 'Rollback')");
                connection.rollback();
                try (var rows = statement.executeQuery("SELECT count(*) FROM assignors WHERE code='CED-ROLLBACK'")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            }
        }
    }
}
