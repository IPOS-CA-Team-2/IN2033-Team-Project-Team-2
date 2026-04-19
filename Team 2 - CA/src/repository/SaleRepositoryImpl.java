package repository;

import db.DatabaseManager;
import model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// sqlite implementation of sale repository
public class SaleRepositoryImpl implements SaleRepository {

    @Override
    public int save(Sale sale) {
        if (sale == null) return -1;

        String saleSql = """
            INSERT INTO sales (customer_id, sale_date, discount_percent, payment_method,
                card_type, card_first_four, card_last_four, card_expiry, is_paid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String lineSql = """
            INSERT INTO sale_lines (sale_id, item_id, item_name, quantity, unit_price, vat_rate)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement saleStmt = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                saleStmt.setInt(1, sale.getCustomerId());
                saleStmt.setString(2, sale.getSaleDate().toString());
                saleStmt.setDouble(3, sale.getDiscountPercent());
                saleStmt.setString(4, sale.getPaymentMethod().name());

                // card details, null for cash payments
                CardDetails card = sale.getCardDetails();
                if (card != null) {
                    saleStmt.setString(5, card.getCardType());
                    saleStmt.setString(6, card.getFirstFourDigits());
                    saleStmt.setString(7, card.getLastFourDigits());
                    saleStmt.setString(8, card.getExpiryDate());
                } else {
                    saleStmt.setNull(5, Types.VARCHAR);
                    saleStmt.setNull(6, Types.VARCHAR);
                    saleStmt.setNull(7, Types.VARCHAR);
                    saleStmt.setNull(8, Types.VARCHAR);
                }

                // account sales start as unpaid, cash/card sales are immediately settled
                boolean isPaid = sale.getPaymentMethod() != PaymentMethod.DEBIT_CARD
                              && sale.getCustomerId() == 0
                              || sale.getPaymentMethod() == PaymentMethod.CASH;
                saleStmt.setInt(9, isPaid ? 1 : 0);

                saleStmt.executeUpdate();
                ResultSet keys = saleStmt.getGeneratedKeys();
                if (!keys.next()) { conn.rollback(); return -1; }

                int generatedId = keys.getInt(1);

                // save each line item
                try (PreparedStatement lineStmt = conn.prepareStatement(lineSql)) {
                    for (SaleLine line : sale.getLines()) {
                        lineStmt.setInt(1, generatedId);
                        lineStmt.setInt(2, line.getItemId());
                        lineStmt.setString(3, line.getItemName());
                        lineStmt.setInt(4, line.getQuantity());
                        lineStmt.setDouble(5, line.getUnitPrice());
                        lineStmt.setDouble(6, line.getVatRate());
                        lineStmt.addBatch();
                    }
                    lineStmt.executeBatch();
                }

                conn.commit();
                return generatedId;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("db error saving sale: " + e.getMessage());
                return -1;
            }

        } catch (SQLException e) {
            System.err.println("db error getting connection for save: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public Sale findById(int saleId) {
        String sql = "SELECT * FROM sales WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, saleId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapSale(conn, rs);

        } catch (SQLException e) {
            System.err.println("db error in findById: " + e.getMessage());
        }

        return null;
    }

    @Override
    public List<Sale> findAll() {
        String sql = "SELECT * FROM sales ORDER BY sale_date DESC";
        return querySales(sql);
    }

    @Override
    public List<Sale> findByCustomerId(int customerId) {
        String sql = "SELECT * FROM sales WHERE customer_id = ? ORDER BY sale_date DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, customerId);
            ResultSet rs = stmt.executeQuery();
            List<Sale> results = new ArrayList<>();
            while (rs.next()) results.add(mapSale(conn, rs));
            return results;

        } catch (SQLException e) {
            System.err.println("db error in findByCustomerId: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Sale> findByDateRange(LocalDate from, LocalDate to) {
        // sale_date stored as ISO datetime string so prefix comparison works
        String sql = "SELECT * FROM sales WHERE sale_date >= ? AND sale_date <= ? ORDER BY sale_date DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, from.atStartOfDay().toString());
            stmt.setString(2, to.atTime(23, 59, 59).toString());
            ResultSet rs = stmt.executeQuery();
            List<Sale> results = new ArrayList<>();
            while (rs.next()) results.add(mapSale(conn, rs));
            return results;

        } catch (SQLException e) {
            System.err.println("db error in findByDateRange: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Sale> findUnpaidAccountSales() {
        // account sales are those with a customer id > 0 that havent been paid
        String sql = "SELECT * FROM sales WHERE customer_id > 0 AND is_paid = 0 ORDER BY sale_date DESC";
        return querySales(sql);
    }

    @Override
    public boolean markAsPaid(int saleId) {
        String sql = "UPDATE sales SET is_paid = 1 WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, saleId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error in markAsPaid: " + e.getMessage());
            return false;
        }
    }

    // -- private helpers --

    // run a no-parameter query and return list of sales
    private List<Sale> querySales(String sql) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            List<Sale> results = new ArrayList<>();
            while (rs.next()) results.add(mapSale(conn, rs));
            return results;

        } catch (SQLException e) {
            System.err.println("db error in querySales: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // map a sales row + its lines into a Sale object
    private Sale mapSale(Connection conn, ResultSet rs) throws SQLException {
        int saleId = rs.getInt("id");
        List<SaleLine> lines = loadLines(conn, saleId);

        PaymentMethod method = PaymentMethod.valueOf(rs.getString("payment_method"));

        // rebuild card details if present
        CardDetails cardDetails = null;
        String cardType = rs.getString("card_type");
        if (cardType != null) {
            cardDetails = new CardDetails(
                cardType,
                rs.getString("card_first_four"),
                rs.getString("card_last_four"),
                rs.getString("card_expiry")
            );
        }

        return new Sale(
            saleId,
            rs.getInt("customer_id"),
            lines,
            LocalDateTime.parse(rs.getString("sale_date")),
            rs.getDouble("discount_percent"),
            method,
            cardDetails
        );
    }

    // load all sale lines for a given sale id
    private List<SaleLine> loadLines(Connection conn, int saleId) throws SQLException {
        String sql = "SELECT * FROM sale_lines WHERE sale_id = ?";
        List<SaleLine> lines = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, saleId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                lines.add(new SaleLine(
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getInt("quantity"),
                    rs.getDouble("unit_price"),
                    rs.getDouble("vat_rate")
                ));
            }
        }

        return lines;
    }
}
