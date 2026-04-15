package repository;

import db.DatabaseManager;
import model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// sqlite implementation of customer repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private Customer mapRow(ResultSet rs) throws SQLException {
        String d1 = rs.getString("date_1st_reminder");
        String d2 = rs.getString("date_2nd_reminder");
        String sd = rs.getString("statement_date");

        return new Customer(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("contact_name"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getString("account_number"),
            rs.getDouble("credit_limit"),
            rs.getDouble("current_balance"),
            rs.getDouble("monthly_spend"),
            DiscountType.valueOf(rs.getString("discount_type")),
            rs.getDouble("fixed_discount_rate"),
            AccountStatus.valueOf(rs.getString("status")),
            rs.getString("status_1st_reminder"),
            rs.getString("status_2nd_reminder"),
            d1 != null ? LocalDate.parse(d1) : null,
            d2 != null ? LocalDate.parse(d2) : null,
            sd != null ? LocalDate.parse(sd) : null
        );
    }

    @Override
    public List<Customer> findAll() {
        String sql = "SELECT * FROM customers ORDER BY name";
        List<Customer> list = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            System.err.println("db error in findAll customers: " + e.getMessage());
            return Collections.emptyList();
        }

        return list;
    }

    @Override
    public Customer findById(int customerId) {
        String sql = "SELECT * FROM customers WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, customerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapRow(rs);

        } catch (SQLException e) {
            System.err.println("db error in findById customer: " + e.getMessage());
        }

        return null;
    }

    @Override
    public Customer findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM customers WHERE account_number = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, accountNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapRow(rs);

        } catch (SQLException e) {
            System.err.println("db error in findByAccountNumber: " + e.getMessage());
        }

        return null;
    }

    @Override
    public int save(Customer customer) {
        String sql = """
            INSERT INTO customers (name, contact_name, phone, address, account_number, credit_limit,
                current_balance, monthly_spend, discount_type, fixed_discount_rate, status,
                status_1st_reminder, status_2nd_reminder, date_1st_reminder, date_2nd_reminder, statement_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setCustomerParams(stmt, customer);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;

        } catch (SQLException e) {
            System.err.println("db error saving customer: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean update(Customer customer) {
        String sql = """
            UPDATE customers SET name=?, contact_name=?, phone=?, address=?, account_number=?,
                credit_limit=?, current_balance=?, monthly_spend=?, discount_type=?,
                fixed_discount_rate=?, status=?, status_1st_reminder=?, status_2nd_reminder=?,
                date_1st_reminder=?, date_2nd_reminder=?, statement_date=?
            WHERE id=?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setCustomerParams(stmt, customer);
            stmt.setInt(17, customer.getCustomerId());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error updating customer: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean updateBalance(int customerId, double newBalance, double newMonthlySpend) {
        String sql = "UPDATE customers SET current_balance=?, monthly_spend=? WHERE id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, newBalance);
            stmt.setDouble(2, newMonthlySpend);
            stmt.setInt(3, customerId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error updating balance: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean updateStatus(int customerId, AccountStatus status,
                                String status1st, String status2nd,
                                LocalDate date1st, LocalDate date2nd, LocalDate statementDate) {
        String sql = """
            UPDATE customers SET status=?, status_1st_reminder=?, status_2nd_reminder=?,
                date_1st_reminder=?, date_2nd_reminder=?, statement_date=?
            WHERE id=?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setString(2, status1st);
            stmt.setString(3, status2nd);
            stmt.setString(4, date1st != null ? date1st.toString() : null);
            stmt.setString(5, date2nd != null ? date2nd.toString() : null);
            stmt.setString(6, statementDate != null ? statementDate.toString() : null);
            stmt.setInt(7, customerId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error updating status: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int customerId) {
        String sql = "DELETE FROM customers WHERE id=?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, customerId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error deleting customer: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String generateAccountNumber() {
        // get the highest existing ACC account number and increment it
        String sql = "SELECT account_number FROM customers WHERE account_number LIKE 'ACC%' ORDER BY account_number DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String last = rs.getString("account_number");
                // parse the numeric suffix (e.g. ACC0002 → 2)
                int next = Integer.parseInt(last.substring(3)) + 1;
                return String.format("ACC%04d", next);
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("db error generating account number: " + e.getMessage());
        }
        // no customers yet — start from 1
        return "ACC0001";
    }

    // helper — binds all customer fields to a prepared statement (16 params)
    private void setCustomerParams(PreparedStatement stmt, Customer c) throws SQLException {
        stmt.setString(1, c.getName());
        stmt.setString(2, c.getContactName());
        stmt.setString(3, c.getPhone());
        stmt.setString(4, c.getAddress());
        stmt.setString(5, c.getAccountNumber());
        stmt.setDouble(6, c.getCreditLimit());
        stmt.setDouble(7, c.getCurrentBalance());
        stmt.setDouble(8, c.getMonthlySpend());
        stmt.setString(9, c.getDiscountType().name());
        stmt.setDouble(10, c.getFixedDiscountRate());
        stmt.setString(11, c.getStatus().name());
        stmt.setString(12, c.getStatus1stReminder());
        stmt.setString(13, c.getStatus2ndReminder());
        stmt.setString(14, c.getDate1stReminder() != null ? c.getDate1stReminder().toString() : null);
        stmt.setString(15, c.getDate2ndReminder() != null ? c.getDate2ndReminder().toString() : null);
        stmt.setString(16, c.getStatementDate() != null ? c.getStatementDate().toString() : null);
    }
}
