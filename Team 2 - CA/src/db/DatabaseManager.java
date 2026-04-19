package db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// manages the sqlite connection and sets up all tables on first run
public class DatabaseManager {

    // resolve db path regardless of working directory:
    //   running from Team 2 - CA/ (IntelliJ default): ipos_ca.db
    //   running from repo root (terminal): Team 2 - CA/ipos_ca.db
    private static final String DB_PATH = resolveDbPath();
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    private static String resolveDbPath() {
        // if src/ exists here we are already inside the module root
        if (new File("src").isDirectory()) {
            return "ipos_ca.db";
        }
        // if Team 2 - CA/ exists we are in the repo root
        File moduleDir = new File("Team 2 - CA");
        if (moduleDir.isDirectory()) {
            return "Team 2 - CA/ipos_ca.db";
        }
        // fallback, use working directory as-is
        return "ipos_ca.db";
    }

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            initialise();
            System.err.println("db ready: " + new File(DB_PATH).getAbsolutePath());
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
                    ('sysdba',     'masterkey',   'Admin',       'System Administrator'),
                    ('manager',    'Get_it_done', 'Manager',     'Director of Operations'),
                    ('accountant', 'Count_money', 'Pharmacist',  'Senior Accountant'),
                    ('clerk',      'Paperwork',   'Pharmacist',  'Accountant')
            """);

            // stock table (bulk_cost is what we pay infopharma, markup_rate is our retail margin)
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

            // customers table, stores account holders with credit, discount, and status tracking
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

            // migration: add last_payment_date to existing installations
            try { stmt.execute("ALTER TABLE customers ADD COLUMN last_payment_date TEXT"); }
            catch (SQLException ignored) { /* already present */ }

            // seed account holders from spec, full scenario state baked in so no UPDATE needed on restart
            // Eva:   £159.47 outstanding (Mar + Apr purchases, 3% discount applied), 1st reminder sent 15 Apr
            // Glynne: £0 balance (paid in full 29 Mar), last_payment_date recorded
            stmt.execute("""
                INSERT OR IGNORE INTO customers
                    (name, contact_name, phone, address, account_number, credit_limit, current_balance,
                     monthly_spend, discount_type, fixed_discount_rate, status,
                     status_1st_reminder, status_2nd_reminder,
                     date_1st_reminder, date_2nd_reminder, statement_date, last_payment_date)
                VALUES
                    ('Ms Eva Bauyer',     'Ms Eva Bauyer',      '0207 321 8001', '1, Liverpool street, London EC2V 8NS',
                     'ACC0001', 500.0, 159.47, 71.20, 'FIXED',    0.03, 'SUSPENDED',
                     'sent', 'no_need', '2026-04-15', NULL, '2026-03-31', '2026-02-28'),
                    ('Mr Glynne Morrison','Ms Glynne Morisson', '0207 321 8001', '1, Liverpool street, London EC2V 8NS',
                     'ACC0002', 500.0,   0.00,  0.00, 'FLEXIBLE', 0.00, 'NORMAL',
                     'no_need', 'no_need', NULL, NULL, NULL, '2026-03-29')
            """);

            // sales table, one row per transaction
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

            // sale_lines table, one row per item in a sale
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

            // wholesale_orders, orders placed by merchant with infopharma (SA)
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

            // wholesale_order_lines, individual items within each wholesale order
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

            // online_sales, records of sale events received from ipos-pu
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS online_sales (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    pu_order_id    TEXT NOT NULL,
                    received_date  TEXT NOT NULL,
                    customer_email TEXT,
                    fully_applied  INTEGER NOT NULL DEFAULT 1
                )
            """);

            // online_sale_items, line items within each incoming PU sale
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
            catch (SQLException ignored) { /* column already present, safe to skip */ }

            // migration: add online order lifecycle columns to existing installations
            try { stmt.execute("ALTER TABLE online_sales ADD COLUMN delivery_address TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE online_sales ADD COLUMN status TEXT NOT NULL DEFAULT 'RECEIVED'"); }
            catch (SQLException ignored) { /* already present */ }

            // migration: add new stock columns to existing installations
            try { stmt.execute("ALTER TABLE stock ADD COLUMN item_code TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN package_type TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN unit TEXT NOT NULL DEFAULT ''"); }
            catch (SQLException ignored) { /* already present */ }
            try { stmt.execute("ALTER TABLE stock ADD COLUMN units_per_pack INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) { /* already present */ }

            // seed stock, all 14 products from the ipos spec catalogue
            // ids 1-5 match sa's product ids for wholesale ordering integration
            // bulk_cost = package cost from spec, markup 30%, vat 0% (applied globally at sale)
            stmt.execute("""
                INSERT OR REPLACE INTO stock
                    (id, item_code, name, package_type, unit, units_per_pack, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold) VALUES
                    (1,  '100 00001', 'Paracetamol',          'Box',    'Caps', 20,  121, 0.10,  1.0, 0.00, 10),
                    (2,  '100 00002', 'Aspirin',              'Box',    'Caps', 20,  197, 0.50,  1.0, 0.00, 15),
                    (3,  '100 00003', 'Analgin',              'Box',    'Caps', 10,   16, 1.20,  1.0, 0.00, 10),
                    (4,  '100 00004', 'Celebrex, caps 100 mg','Box',    'Caps', 10,   37, 10.00, 1.0, 0.00, 10),
                    (5,  '100 00005', 'Celebrex, caps 200 mg','Box',    'Caps', 10,   34, 18.50, 1.0, 0.00,  5),
                    (6,  '100 00006', 'Retin-A Tretin, 30 g', 'Box',    'Caps', 20,   24, 25.00, 1.0, 0.00, 10),
                    (7,  '100 00007', 'Lipitor TB, 20 mg',    'Box',    'Caps', 30,    9, 15.50, 1.0, 0.00, 10),
                    (8,  '100 00008', 'Claritin CR, 60g',     'Box',    'Caps', 20,   20, 19.50, 1.0, 0.00, 10),
                    (9,  '200 00004', 'Iodine tincture',      'Bottle', 'Ml',  100,   33,  0.30, 1.0, 0.00, 10),
                    (10, '200 00005', 'Rhynol',               'Bottle', 'Ml',  200,   12,  2.50, 1.0, 0.00, 15),
                    (11, '300 00001', 'Ospen',                'Box',    'Caps', 20,   74, 10.50, 1.0, 0.00, 10),
                    (12, '300 00002', 'Amopen',               'Box',    'Caps', 30,   85, 15.00, 1.0, 0.00, 15),
                    (13, '400 00001', 'Vitamin C',            'Box',    'Caps', 30,   18,  1.20, 1.0, 0.00, 15),
                    (14, '400 00002', 'Vitamin B12',          'Box',    'Caps', 30,   37,  1.30, 1.0, 0.00, 15)
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

            // Scenario 10: Eva Bauyer account sale, 1 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, card_type, card_first_four, card_last_four, card_expiry, is_paid) VALUES (1, 1, '2026-03-01T00:00:00', 0.03, 'CREDIT_CARD', 'Visa', '4111', '1111', '12/28', 0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (1, 11, 'Ospen',       1, 21.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (1, 13, 'Vitamin C',   2,  2.40, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (1, 12, 'Amopen',      2, 30.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (1, 14, 'Vitamin B12', 2,  2.60, 0.0)");

            // Scenario 11a: cash sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (2, 0, '2026-03-03T00:00:00', 0.0, 'CASH', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (2, 2, 'Aspirin', 2, 1.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (2, 3, 'Analgin', 3, 2.40, 0.0)");

            // Scenario 11b: Visa credit card sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, card_type, card_first_four, card_last_four, card_expiry, is_paid) VALUES (3, 0, '2026-03-03T00:00:00', 0.0, 'CREDIT_CARD', 'Visa', '4111', '1111', '12/28', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (3, 4, 'Celebrex, caps 100 mg', 2, 20.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (3, 6, 'Retin-A Tretin, 30 g',  2, 50.00, 0.0)");

            // Scenario 11c: cash sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (4, 0, '2026-03-03T00:00:00', 0.0, 'CASH', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (4, 7, 'Lipitor TB, 20 mg', 1, 31.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (4, 8, 'Claritin CR, 60g',  1, 39.00, 0.0)");

            // Scenario 11d: cash sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (5, 0, '2026-03-03T00:00:00', 0.0, 'CASH', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (5, 5,  'Celebrex, caps 200 mg', 1, 37.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (5, 9,  'Iodine tincture',       2,  0.60, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (5, 10, 'Rhynol',                2,  5.00, 0.0)");

            // Scenario 11e: debit card sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, card_type, card_first_four, card_last_four, card_expiry, is_paid) VALUES (6, 0, '2026-03-03T00:00:00', 0.0, 'DEBIT_CARD', 'Visa', '4222', '2222', '11/27', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (6, 11, 'Ospen',     2, 21.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (6, 13, 'Vitamin C', 2,  2.40, 0.0)");

            // Scenario 11f: cash sale, 3 March 2026
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (7, 0, '2026-03-03T00:00:00', 0.0, 'CASH', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (7, 12, 'Amopen',     3, 30.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (7, 14, 'Vitamin B12',2,  2.60, 0.0)");

            // Scenario 12: Glynne Morrison account sale, 5 March 2026
            // paid in full on 29 March (scenario 14), is_paid=1 set directly, no UPDATE needed
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (8, 2, '2026-03-05T00:00:00', 0.0, 'CASH', 1)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (8, 2,  'Aspirin',               2,  1.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (8, 3,  'Analgin',               3,  2.40, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (8, 4,  'Celebrex, caps 100 mg', 2, 20.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (8, 6,  'Retin-A Tretin, 30 g',  2, 50.00, 0.0)");

            // Scenario 13: Eva Bauyer account sale, 1 April 2026
            // 3% discount applied: 73.40 × 0.97 = £71.20 charged to account
            stmt.execute("INSERT OR IGNORE INTO sales (id, customer_id, sale_date, discount_percent, payment_method, is_paid) VALUES (9, 1, '2026-04-01T00:00:00', 0.03, 'CASH', 0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (9, 11, 'Ospen',                1, 21.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (9, 3,  'Analgin',              3,  2.40, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (9, 4,  'Celebrex, caps 100 mg',2, 20.00, 0.0)");
            stmt.execute("INSERT OR IGNORE INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate) VALUES (9, 14, 'Vitamin B12',          2,  2.60, 0.0)");

        }
    }
}
