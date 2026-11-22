package cn.planet;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PLANET_TEST_FRESH_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PLANET_TEST_LEGACY_URL", matches = ".+")
class SchemaMigrationTest {
    @Test
    void emptyDatabaseStartsAtVersionOneAndAddsParentAccounts() throws Exception {
        String url = System.getenv("PLANET_TEST_FRESH_URL");
        Flyway migrations = migrations(url, false);
        assertEquals(4, migrations.migrate().migrationsExecuted);
        assertEquals(0, migrations.migrate().migrationsExecuted);
        assertEquals(0, count(url, "activity"));
        assertEquals(0, count(url, "parent_account"));
    }

    @Test
    void existingDatabaseRequiresExplicitBaselineAndPreservesActivity() throws Exception {
        String url = System.getenv("PLANET_TEST_LEGACY_URL");
        try (var connection = DriverManager.getConnection(url, user(), password());
             var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE activity (id BIGINT PRIMARY KEY, agent INT NOT NULL,
                    title VARCHAR(150) NOT NULL, steps TEXT NOT NULL) DEFAULT CHARSET=utf8mb4
                """);
            statement.executeUpdate("""
                INSERT INTO activity VALUES (123, 0, '颜色捉迷藏', '["找两件不同颜色的玩具。"]')
                """);
        }
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> migrations(url, false).migrate());
        assertEquals(4, migrations(url, true).migrate().migrationsExecuted);
        assertEquals(0, migrations(url, false).migrate().migrationsExecuted);
        assertEquals(0, count(url, "parent_account"));
        try (var connection = DriverManager.getConnection(url, user(), password());
             var statement = connection.createStatement();
             var activity = statement.executeQuery("SELECT * FROM activity")) {
            assertTrue(activity.next());
            assertEquals(123, activity.getLong("id"));
            assertEquals(0, activity.getInt("agent"));
            assertEquals("颜色捉迷藏", activity.getString("title"));
            assertEquals("[\"找两件不同颜色的玩具。\"]", activity.getString("steps"));
            assertFalse(activity.next());
        }
    }

    private Flyway migrations(String url, boolean baseline) {
        return Flyway.configure().dataSource(url, user(), password())
            .baselineOnMigrate(baseline).baselineVersion("0").load();
    }

    private int count(String url, String table) throws Exception {
        try (var connection = DriverManager.getConnection(url, user(), password());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String user() { return System.getenv("DB_USER"); }
    private String password() { return System.getenv("DB_PASSWORD"); }
}
