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
                    item_code           TEXT NOT NULL DEFAULT '',
                    name                TEXT NOT NULL,
                    package_type        TEXT NOT NULL DEFAULT '',
                    unit                TEXT NOT NULL DEFAULT '',
                    units_per_pack      INTEGER NOT NULL DEFAULT 0,
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
                    contact_name        TEXT NOT NULL DEFAULT '',
                    phone               TEXT NOT NULL DEFAULT '',
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

            // migration: add contact_name and phone to existing installations
            try { stmt.execute("ALTER TABLE customers ADD COLUMN contact_name TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE customers ADD COLUMN phone TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }

            // seed account holders from spec
            stmt.execute("""
                INSERT OR IGNORE INTO customers
                    (name, contact_name, phone, address, account_number, credit_limit, current_balance,
                     monthly_spend, discount_type, fixed_discount_rate, status, status_1st_reminder, status_2nd_reminder)
                VALUES
                    ('Ms Eva Bauyer',    'Ms Eva Bauyer',    '0207 321 8001', '1, Liverpool street, London EC2V 8NS', 'ACC0001', 500.0, 0.0, 0.0, 'FIXED',    0.03, 'NORMAL', 'no_need', 'no_need'),
                    ('Mr Glynne Morrison','Ms Glynne Morisson','0207 321 8001','1, Liverpool street, London EC2V 8NS', 'ACC0002', 500.0, 0.0, 0.0, 'FLEXIBLE', 0.00, 'NORMAL', 'no_need', 'no_need')
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

            // migration: add sa_order_id column to existing installations
            // wrapped in try-catch because sqlite throws if the column already exists
            try { stmt.execute("ALTER TABLE wholesale_orders ADD COLUMN sa_order_id INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) { /* column already present — safe to skip */ }

            // migration: add new stock columns to existing installations
            try { stmt.execute("ALTER TABLE stock ADD COLUMN item_code TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN package_type TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN unit TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN units_per_pack INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) { /* already present */ }

            // seed stock — all 14 products from the ipos spec catalogue
            // ids 1-5 match sa's product ids for wholesale ordering integration
            // bulk_cost = package cost from spec, markup 30%, vat 0% (applied globally at sale)
            stmt.execute("""
                INSERT OR REPLACE INTO stock
                    (id, item_code, name, package_type, unit, units_per_pack, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold) VALUES
                    (1,  '100 00001', 'Paracetamol',          'Box',    'Caps', 20,  121, 0.10,  1.0, 0.00, 10),
                    (2,  '100 00002', 'Aspirin',              'Box',    'Caps', 20,  201, 0.50,  1.0, 0.00, 15),
                    (3,  '100 00003', 'Analgin',              'Box',    'Caps', 10,   25, 1.20,  1.0, 0.00, 10),
                    (4,  '100 00004', 'Celebrex, caps 100 mg','Box',    'Caps', 10,   43, 10.00, 1.0, 0.00, 10),
                    (5,  '100 00005', 'Celebrex, caps 200 mg','Box',    'Caps', 10,   35, 18.50, 1.0, 0.00,  5),
                    (6,  '100 00006', 'Retin-A Tretin, 30 g', 'Box',    'Caps', 20,   28, 25.00, 1.0, 0.00, 10),
                    (7,  '100 00007', 'Lipitor TB, 20 mg',    'Box',    'Caps', 30,   10, 15.50, 1.0, 0.00, 10),
                    (8,  '100 00008', 'Claritin CR, 60g',     'Box',    'Caps', 20,   21, 19.50, 1.0, 0.00, 10),
                    (9,  '200 00004', 'Iodine tincture',      'Bottle', 'Ml',  100,   35,  0.30, 1.0, 0.00, 10),
                    (10, '200 00005', 'Rhynol',               'Bottle', 'Ml',  200,   14,  2.50, 1.0, 0.00, 15),
                    (11, '300 00001', 'Ospen',                'Box',    'Caps', 20,   78, 10.50, 1.0, 0.00, 10),
                    (12, '300 00002', 'Amopen',               'Box',    'Caps', 30,   90, 15.00, 1.0, 0.00, 15),
                    (13, '400 00001', 'Vitamin C',            'Box',    'Caps', 30,   22,  1.20, 1.0, 0.00, 15),
                    (14, '400 00002', 'Vitamin B12',          'Box',    'Caps', 30,   43,  1.30, 1.0, 0.00, 15)
            """);

            // database for the templates
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS config (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL)
            """);

            // add defaults
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('pharmacy_name', 'Big Pharma')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('pharmacy_address', '117 Stratfield Road, Borehamwood, Hertfordshire, United Kingdom wd6 1ud')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('pharmacy_email', 'support@bigpharma.co.uk')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('pharmacy_phone', '07427380800')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('reminder1_template', 'Dear {customer_name},\n\nREMINDER\nAccount: {account_no} | Amount: £{amount}\n\nAccording to our records we have not received your payment. Please remit by {payment_due}.\n\nYours sincerely,\n{pharmacy_name}')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('reminder2_template', 'Dear {customer_name},\n\nSECOND REMINDER\nAccount: {account_no} | Amount: £{amount}\n\nDespite our previous reminder this balance remains unpaid. Please remit by {payment_due}.\n\nYours sincerely,\n{pharmacy_name}')");
            stmt.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('receipt_footer', 'Thank you for your purchase')");

        }
    }
}
