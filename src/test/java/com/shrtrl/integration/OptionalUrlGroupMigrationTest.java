package com.shrtrl.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class OptionalUrlGroupMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void preservesMembershipAndAllowsUngroupedUrls() throws Exception {
        String schema = prepareSchema();
        try (Connection connection = connection(schema)) {
            sql(connection, "INSERT INTO group_url VALUES (1, 1)");
            migrate(schema, "4");
            assertEquals(1, number(connection, "SELECT groupid FROM urls WHERE urlid = 1"));
            assertEquals(1, number(connection, "SELECT count(*) FROM urls WHERE urlid = 2 AND groupid IS NULL"));
            sql(connection, "INSERT INTO urls (longurl, userid, created_date) "
                    + "VALUES ('https://ungrouped.example', '00000000-0000-0000-0000-000000000001', now())");
            assertEquals(2, number(connection, "SELECT count(*) FROM urls WHERE groupid IS NULL"));
            assertEquals(0, number(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = current_schema() AND table_name = 'group_url'"));
            migrate(schema, "4"); // Applied migrations are safe to check again.
        }
    }

    @Test
    void enforcesOwnershipAndDeletingGroupPreservesUrls() throws Exception {
        String schema = prepareSchema();
        migrate(schema, "4");
        try (Connection connection = connection(schema)) {
            sql(connection, "UPDATE urls SET groupid = 1 WHERE urlid = 1");
            SQLException failure = assertThrows(SQLException.class,
                    () -> sql(connection, "UPDATE urls SET groupid = 3 WHERE urlid = 1"));
            assertEquals("23503", failure.getSQLState());
            sql(connection, "DELETE FROM groups WHERE groupid = 1");
            assertEquals(1, number(connection, "SELECT count(*) FROM urls WHERE urlid = 1 "
                    + "AND groupid IS NULL AND userid = '00000000-0000-0000-0000-000000000001'"));
        }
    }

    @Test
    void rejectsMultipleMembershipsWithoutDiscardingThem() throws Exception {
        String schema = prepareSchema();
        try (Connection connection = connection(schema)) {
            sql(connection, "INSERT INTO group_url VALUES (1, 1), (2, 1)");
            assertThrows(FlywayException.class, () -> migrate(schema, "4"));
            assertEquals(2, number(connection, "SELECT count(*) FROM group_url"));
            assertEquals(0, number(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema = current_schema() AND table_name = 'urls' AND column_name = 'groupid'"));
        }
    }

    @Test
    void rejectsExistingCrossOwnerMembership() throws Exception {
        String schema = prepareSchema();
        try (Connection connection = connection(schema)) {
            sql(connection, "INSERT INTO group_url VALUES (3, 1)");
            assertThrows(FlywayException.class, () -> migrate(schema, "4"));
            assertEquals(1, number(connection, "SELECT count(*) FROM group_url"));
        }
    }

    private String prepareSchema() throws Exception {
        String schema = "url_group_" + UUID.randomUUID().toString().replace("-", "");
        migrate(schema, "3");
        try (Connection connection = connection(schema)) {
            sql(connection, "INSERT INTO users VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 'alice', 'alice@example.com', 'hash'),"
                    + "('00000000-0000-0000-0000-000000000002', 'bobby', 'bob@example.com', 'hash')");
            sql(connection, "INSERT INTO groups (groupid, groupname, userid) VALUES "
                    + "(1, 'workgroup', '00000000-0000-0000-0000-000000000001'),"
                    + "(2, 'research', '00000000-0000-0000-0000-000000000001'),"
                    + "(3, 'bobgroup', '00000000-0000-0000-0000-000000000002')");
            sql(connection, "INSERT INTO urls (longurl, userid, created_date) VALUES "
                    + "('https://first.example', '00000000-0000-0000-0000-000000000001', now()),"
                    + "('https://second.example', '00000000-0000-0000-0000-000000000001', now())");
        }
        return schema;
    }

    private void migrate(String schema, String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .schemas(schema).defaultSchema(schema).target(target).load().migrate();
    }

    private Connection connection(String schema) throws SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int number(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
