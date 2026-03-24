package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// manages the sqlite connection and initialises all tables on first run
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

            // users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    role     TEXT NOT NULL CHECK(role IN ('Admin','Pharmacist','Manager')),
                    name     TEXT NOT NULL
                )
            """);

            stmt.execute("""
                INSERT OR IGNORE INTO users (username, password, role, name) VALUES
                    ('admin1',   'pass123', 'Admin',       'Alice'),
                    ('pharma1',  'pass456', 'Pharmacist',  'Bob'),
                    ('manager1', 'pass789', 'Manager',     'Carol')
            """);

            // stock table — bulk_cost is what we pay infopharma, markup_rate is our retail margin
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stock (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                TEXT NOT NULL,
                    quantity            INTEGER NOT NULL DEFAULT 0,
                    bulk_cost           REAL NOT NULL,
                    markup_rate         REAL NOT NULL DEFAULT 0.0,
                    vat_rate            REAL NOT NULL DEFAULT 0.0,
                    low_stock_threshold INTEGER NOT NULL DEFAULT 10
                )
            """);

            // sales table — one row per transaction
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id      INTEGER NOT NULL DEFAULT 0,
                    sale_date        TEXT NOT NULL,
                    discount_percent REAL NOT NULL DEFAULT 0.0,
                    payment_method   TEXT NOT NULL,
                    card_type        TEXT,
                    card_first_four  TEXT,
                    card_last_four   TEXT,
                    card_expiry      TEXT,
                    is_paid          INTEGER NOT NULL DEFAULT 0
                )
            """);

            // sale_lines table — one row per item in a sale
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sale_lines (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    sale_id    INTEGER NOT NULL,
                    item_id    INTEGER NOT NULL,
                    item_name  TEXT NOT NULL,
                    quantity   INTEGER NOT NULL,
                    unit_price REAL NOT NULL,
                    vat_rate   REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY (sale_id) REFERENCES sales(id)
                )
            """);

            // seed stock — bulk costs with 30% markup applied in the app to get retail price
            stmt.execute("""
                INSERT OR IGNORE INTO stock (id, name, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold) VALUES
                    (1, 'Paracetamol 500mg',  200, 1.92, 0.30, 0.00, 20),
                    (2, 'Ibuprofen 200mg',    150, 3.07, 0.30, 0.00, 15),
                    (3, 'Amoxicillin 250mg',    8, 6.92, 0.30, 0.00, 10),
                    (4, 'Cetirizine 10mg',      60, 3.45, 0.30, 0.00, 10),
                    (5, 'Omeprazole 20mg',       5, 5.38, 0.30, 0.00, 10)
            """);
        }
    }
}
