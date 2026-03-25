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
            System.out.println("db ready: " + DB_PATH);
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

            // customers table — account holders with credit, discount, and status tracking
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                TEXT NOT NULL,
                    address             TEXT,
                    account_number      TEXT NOT NULL UNIQUE,
                    credit_limit        REAL NOT NULL DEFAULT 500.0,
                    current_balance     REAL NOT NULL DEFAULT 0.0,
                    monthly_spend       REAL NOT NULL DEFAULT 0.0,
                    discount_type       TEXT NOT NULL DEFAULT 'NONE',
                    fixed_discount_rate REAL NOT NULL DEFAULT 0.0,
                    status              TEXT NOT NULL DEFAULT 'NORMAL',
                    status_1st_reminder TEXT NOT NULL DEFAULT 'no_need',
                    status_2nd_reminder TEXT NOT NULL DEFAULT 'no_need',
                    date_1st_reminder   TEXT,
                    date_2nd_reminder   TEXT,
                    statement_date      TEXT
                )
            """);

            // seed sample account holders
            stmt.execute("""
                INSERT OR IGNORE INTO customers
                    (name, address, account_number, credit_limit, current_balance, monthly_spend,
                     discount_type, fixed_discount_rate, status, status_1st_reminder, status_2nd_reminder)
                VALUES
                    ('J. Smith',    '27 Sainsbury Close, Stratford, Essex EJ6 5TJ', 'CSM000123', 500.0,  0.0, 0.0, 'FIXED',    0.10, 'NORMAL', 'no_need', 'no_need'),
                    ('A. Johnson',  '14 Maple Avenue, London, SE1 4AB',             'CSM000124', 750.0,  0.0, 0.0, 'FLEXIBLE', 0.00, 'NORMAL', 'no_need', 'no_need'),
                    ('M. Patel',    '5 Green Lane, Birmingham, B2 7CD',             'CSM000125', 300.0,  0.0, 0.0, 'NONE',     0.00, 'NORMAL', 'no_need', 'no_need')
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

            // wholesale_orders — orders placed by merchant with infopharma (sa)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wholesale_orders (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_date        TEXT NOT NULL,
                    status            TEXT NOT NULL DEFAULT 'PENDING',
                    dispatch_date     TEXT,
                    courier           TEXT,
                    courier_ref       TEXT,
                    expected_delivery TEXT
                )
            """);

            // wholesale_order_lines — individual items within each wholesale order
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wholesale_order_lines (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id  INTEGER NOT NULL,
                    item_id   INTEGER NOT NULL,
                    item_name TEXT NOT NULL,
                    quantity  INTEGER NOT NULL,
                    unit_cost REAL NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES wholesale_orders(id)
                )
            """);

            // online_sales — records of sale events received from ipos-pu
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS online_sales (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    pu_order_id    TEXT NOT NULL,
                    received_date  TEXT NOT NULL,
                    customer_email TEXT,
                    fully_applied  INTEGER NOT NULL DEFAULT 1
                )
            """);

            // online_sale_items — line items within each incoming pu sale
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS online_sale_items (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    online_sale_id INTEGER NOT NULL,
                    item_id        INTEGER NOT NULL,
                    quantity       INTEGER NOT NULL,
                    FOREIGN KEY (online_sale_id) REFERENCES online_sales(id)
                )
            """);

            // seed stock items — prices are bulk cost, markup handled in StockItem.getUnitPrice()
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
