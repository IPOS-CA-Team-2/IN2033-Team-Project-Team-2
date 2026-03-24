package repository;

import db.DatabaseManager;
import model.StockItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// sqlite implementation of stock repository
public class StockRepositoryImpl implements StockRepository {

    // convert db row to stockitem object
    private StockItem mapRow(ResultSet rs) throws SQLException {
        return new StockItem(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getInt("quantity"),
                rs.getDouble("bulk_cost"),
                rs.getDouble("markup_rate"),
                rs.getDouble("vat_rate"),
                rs.getInt("low_stock_threshold")
        );
    }

    @Override
    public List<StockItem> findAll() {
        String sql = "SELECT id, name, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold FROM stock ORDER BY name";
        List<StockItem> items = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));

        } catch (SQLException e) {
            System.err.println("db error in findAll: " + e.getMessage());
            return Collections.emptyList();
        }

        return items;
    }

    @Override
    public StockItem findById(int itemId) {
        String sql = "SELECT id, name, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold FROM stock WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapRow(rs);

        } catch (SQLException e) {
            System.err.println("db error in findById: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean updateQuantity(int itemId, int newQuantity) {
        if (newQuantity < 0) {
            System.err.println("updateQuantity: quantity cannot be negative");
            return false;
        }

        String sql = "UPDATE stock SET quantity = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, newQuantity);
            stmt.setInt(2, itemId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error in updateQuantity: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<StockItem> findLowStock() {
        String sql = "SELECT id, name, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold " +
                     "FROM stock WHERE quantity <= low_stock_threshold ORDER BY quantity ASC";
        List<StockItem> items = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) items.add(mapRow(rs));

        } catch (SQLException e) {
            System.err.println("db error in findLowStock: " + e.getMessage());
            return Collections.emptyList();
        }

        return items;
    }

    @Override
    public boolean save(StockItem item) {
        if (item == null) return false;

        // insert or replace — handles both new items and updates
        String sql = "INSERT OR REPLACE INTO stock (id, name, quantity, bulk_cost, markup_rate, vat_rate, low_stock_threshold) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // id 0 means new item — let sqlite auto-generate the id
            if (item.getItemId() == 0) {
                stmt.setNull(1, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(1, item.getItemId());
            }
            stmt.setString(2, item.getName());
            stmt.setInt(3, item.getQuantity());
            stmt.setDouble(4, item.getBulkCost());
            stmt.setDouble(5, item.getMarkupRate());
            stmt.setDouble(6, item.getVatRate());
            stmt.setInt(7, item.getLowStockThreshold());

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error in save: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int itemId) {
        String sql = "DELETE FROM stock WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, itemId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("db error in delete: " + e.getMessage());
            return false;
        }
    }
}
