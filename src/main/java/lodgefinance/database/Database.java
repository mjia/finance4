package lodgefinance.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class Database {
    private final Path databasePath;

    public Database(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    public void initialize() throws IOException, SQLException {
        Files.createDirectories(databasePath.getParent());
        String schema;
        try (InputStream input = Database.class.getResourceAsStream("/database/schema.sql")) {
            if (input == null) {
                throw new IOException("Missing database/schema.sql resource.");
            }
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // This resource contains only the two simple CREATE TABLE statements.
                try (Statement statement = connection.createStatement()) {
                    for (String sql : schema.split(";")) {
                        if (!sql.isBlank()) {
                            statement.executeUpdate(sql);
                        }
                    }
                }
                checkSchema(connection);
                addInitialUsers(connection);
                if (countRows(connection, "TRANSACTIONS") == 0) {
                    addSampleTransactions(connection);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void checkSchema(Connection connection) throws SQLException {
        checkColumns(connection, "USERS",
                new String[]{"user_id", "username", "password"},
                new String[]{"INTEGER", "TEXT", "TEXT"});
        checkColumns(connection, "TRANSACTIONS",
                new String[]{"transaction_id", "date", "description", "type", "amount", "user_id"},
                new String[]{"INTEGER", "TEXT", "TEXT", "TEXT", "INTEGER", "INTEGER"});

        // Refuse an older schema instead of dropping or replacing existing tables.
        String[] requiredParts = {
                "autoincrement", "unique", "check(length(trim(username))>0)",
                "autoincrement", "check(length(trim(description))>0)",
                "check(typein('income','expense'))",
                "check(typeof(amount)='integer'andamount>0)",
                "foreignkey(user_id)referencesusers(user_id)"
        };
        String sql = "SELECT sql FROM sqlite_master WHERE type = 'table' AND name IN ('USERS', 'TRANSACTIONS')";
        String definitions = "";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                definitions += rows.getString("sql").toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
            }
        }
        for (String part : requiredParts) {
            if (!definitions.contains(part)) {
                throw new SQLException("Incompatible schema. Expected constraint: " + part);
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (rows.next()) {
                throw new SQLException("The existing database contains an invalid creator user ID.");
            }
        }
    }

    private void checkColumns(Connection connection, String table, String[] names, String[] types)
            throws SQLException {
        int index = 0;
        // Table names here are fixed by this class, never supplied by a web request.
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (index >= names.length || !names[index].equals(rows.getString("name"))
                        || !types[index].equalsIgnoreCase(rows.getString("type"))
                        || (index == 0 && rows.getInt("pk") != 1)
                        || (index > 0 && rows.getInt("notnull") != 1)) {
                    throw new SQLException("Incompatible " + table + " table. Compare it with schema.sql.");
                }
                index++;
            }
        }
        if (index != names.length) {
            throw new SQLException("Missing columns in " + table + ". Compare it with schema.sql.");
        }
    }

    private void addInitialUsers(Connection connection) throws SQLException {
        String[][] users = {
                {"treasurer", "LodgeDemo!2026"},
                {"lodgechief", "ChiefDemo!2026"},
                {"chinaclan", "AdviserDemo!2026"}
        };
        String sql = "INSERT INTO USERS (username, password) "
                + "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM USERS WHERE username = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String[] user : users) {
                statement.setString(1, user[0]);
                statement.setString(2, user[1]);
                statement.setString(3, user[0]);
                statement.executeUpdate();
            }
        }
    }

    private void addSampleTransactions(Connection connection) throws SQLException {
        Map<String, Integer> userIDs = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT user_id, username FROM USERS")) {
            while (rows.next()) {
                userIDs.put(rows.getString("username"), rows.getInt("user_id"));
            }
        }
        String[][] samples = {
                {"2026-11-14", "Membership dues", "Income", "25", "treasurer"},
                {"2026-11-13", "Event supplies", "Expense", "48", "treasurer"},
                {"2026-11-12", "Fall fellowship registration", "Income", "150", "treasurer"},
                {"2026-11-10", "Service day registration", "Income", "60", "lodgechief"},
                {"2026-11-11", "Service day supplies", "Expense", "75", "lodgechief"},
                {"2026-11-09", "Membership dues", "Income", "25", "lodgechief"},
                {"2026-11-08", "Lodge donation", "Income", "100", "chinaclan"},
                {"2026-11-07", "Newsletter printing", "Expense", "35", "chinaclan"},
                {"2026-11-06", "Fellowship refreshments contribution", "Income", "40", "chinaclan"}
        };
        String sql = "INSERT INTO TRANSACTIONS (date, description, type, amount, user_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String[] sample : samples) {
                statement.setString(1, sample[0]);
                statement.setString(2, sample[1]);
                statement.setString(3, sample[2]);
                statement.setInt(4, Integer.parseInt(sample[3]));
                statement.setInt(5, userIDs.get(sample[4]));
                statement.executeUpdate();
            }
        }
    }

    private int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
