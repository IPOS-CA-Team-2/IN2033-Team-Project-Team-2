package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection for IPOS-CA.
 * On first run, automatically creates all tables and seeds default users.
 */
public class DatabaseManager {

    private static final String DB_PATH = "ipos_ca.db";
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initialise();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise database.", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void initialise() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {

            // Create users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    role     TEXT NOT NULL CHECK(role IN ('Admin','Pharmacist','Manager')),
                    name     TEXT NOT NULL
                )
            """);

            // Seed default users if table is empty
            stmt.execute("""
                INSERT OR IGNORE INTO users (username, password, role, name) VALUES
                    ('admin1',   'pass123', 'Admin',       'Alice'),
                    ('pharma1',  'pass456', 'Pharmacist',  'Bob'),
                    ('manager1', 'pass789', 'Manager',     'Carol')
            """);
        }
    }
}
