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

            // Create stock table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stock (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                TEXT NOT NULL,
                    quantity            INTEGER NOT NULL DEFAULT 0,
                    unit_price          REAL NOT NULL,
                    vat_rate            REAL NOT NULL DEFAULT 0.20,
                    low_stock_threshold INTEGER NOT NULL DEFAULT 10
                )
            """);

            // Seed sample stock items if table is empty
            stmt.execute("""
                INSERT OR IGNORE INTO stock (id, name, quantity, unit_price, vat_rate, low_stock_threshold) VALUES
                    (1, 'Paracetamol 500mg',    200, 2.49, 0.00, 20),
                    (2, 'Ibuprofen 200mg',      150, 3.99, 0.00, 15),
                    (3, 'Amoxicillin 250mg',     8,  8.99, 0.00, 10),
                    (4, 'Cetirizine 10mg',       60, 4.49, 0.00, 10),
                    (5, 'Omeprazole 20mg',        5, 6.99, 0.00, 10)
            """);
        }
    }
}
