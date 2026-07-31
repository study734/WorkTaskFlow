package com.teamproject.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("worktaskflow_migration")
                    .withUsername("worktaskflow")
                    .withPassword("worktaskflow");

    @Test
    void migratesFreshMySqlSchemaFromV1ThroughV34() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("34");
        assertThat(countSchemaObjects(
                "information_schema.tables",
                "table_name",
                List.of(
                        "reports",
                        "ai_weekly_report_revision",
                        "task_activity_events",
                        "weekly_objectives",
                        "task_weekly_objective_links")))
                .isEqualTo(5);
        assertThat(countColumns(
                "reports",
                List.of(
                        "ai_context_json",
                        "reference_index_json",
                        "evidence_json",
                        "editorial_json",
                        "publication_status",
                        "editor_version")))
                .isEqualTo(6);
        assertThat(countColumns(
                "tasks",
                List.of(
                        "blocker_type",
                        "blocker_next_action_type",
                        "blocker_review_date")))
                .isEqualTo(3);
    }

    private long countColumns(String table, List<String> columns) throws Exception {
        String placeholders = String.join(",", columns.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name IN (%s)
                """.formatted(placeholders);
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            for (int index = 0; index < columns.size(); index++) {
                statement.setString(index + 2, columns.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long countSchemaObjects(
            String informationSchemaTable, String nameColumn, List<String> names)
            throws Exception {
        String placeholders = String.join(",", names.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE table_schema = DATABASE()
                  AND %s IN (%s)
                """.formatted(informationSchemaTable, nameColumn, placeholders);
        try (Connection connection = MYSQL.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < names.size(); index++) {
                statement.setString(index + 1, names.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}
