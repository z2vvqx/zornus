package net.valoury.shared.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PostgresSchemaVerifier {

    private PostgresSchemaVerifier() {
    }

    public static boolean relationExists(Connection connection, String relationName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, relationName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    public static void requireRelations(Connection connection, String... relationNames) throws SQLException {
        for (String relationName : relationNames) {
            if (!relationExists(connection, relationName)) {
                throw new SQLException("Database schema is missing required relation " + relationName);
            }
        }
    }

    public static void requireColumns(
            Connection connection,
            String tableName,
            String... columnNames
    ) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = ?
                      AND column_name = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String columnName : columnNames) {
                statement.setString(1, tableName);
                statement.setString(2, columnName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    if (!resultSet.getBoolean(1)) {
                        throw new SQLException(
                                "Database schema is missing required column " + tableName + "." + columnName);
                    }
                }
            }
        }
    }

    public static boolean constraintExists(
            Connection connection,
            String tableName,
            String constraintName
    ) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE conrelid = to_regclass(?)
                      AND conname = ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    public static void requireConstraints(
            Connection connection,
            String tableName,
            String... constraintNames
    ) throws SQLException {
        for (String constraintName : constraintNames) {
            if (!constraintExists(connection, tableName, constraintName)) {
                throw new SQLException(
                        "Database schema is missing required constraint " + tableName + "." + constraintName);
            }
        }
    }
}
