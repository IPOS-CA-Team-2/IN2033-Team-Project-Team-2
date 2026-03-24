package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages the MySQL database connection for IPOS-CA.
 *
 * Usage:
 *   Connection conn = DatabaseManager.getConnection();
 *   // ... use conn ...
 *   conn.close();
 */
public class DatabaseManager {

    private static final String URL = "jdbc:mysql://localhost:3306/ipos_ca";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC driver not found. Add mysql-connector-j to lib/.", e);
        }
    }

    /**
     * Returns a new connection to the ipos_ca database.
     * Caller is responsible for closing it.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
