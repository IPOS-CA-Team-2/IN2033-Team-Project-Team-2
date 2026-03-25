package repository;

import db.DatabaseManager;
import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// sqlite implementation of wholesale order persistence
public class WholesaleOrderRepositoryImpl implements WholesaleOrderRepository {

    @Override
    public int save(WholesaleOrder order) {
        String orderSql = """
            INSERT INTO wholesale_orders (order_date, status, dispatch_date, courier, courier_ref, expected_delivery)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        String lineSql = """
            INSERT INTO wholesale_order_lines (order_id, item_id, item_name, quantity, unit_cost)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // insert the order header
            int orderId;
            try (PreparedStatement stmt = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, order.getOrderDate().toString());
                stmt.setString(2, order.getStatus().name());
                stmt.setString(3, order.getDispatchDate()     != null ? order.getDispatchDate().toString()     : null);
                stmt.setString(4, order.getCourier());
                stmt.setString(5, order.getCourierRef());
                stmt.setString(6, order.getExpectedDelivery() != null ? order.getExpectedDelivery().toString() : null);
                stmt.executeUpdate();
                ResultSet keys = stmt.getGeneratedKeys();
                orderId = keys.next() ? keys.getInt(1) : -1;
            }

            if (orderId == -1) { conn.rollback(); return -1; }

            // insert each line
            try (PreparedStatement lineStmt = conn.prepareStatement(lineSql)) {
                for (OrderLine line : order.getLines()) {
                    lineStmt.setInt(1, orderId);
                    lineStmt.setInt(2, line.getItemId());
                    lineStmt.setString(3, line.getItemName());
                    lineStmt.setInt(4, line.getQuantity());
                    lineStmt.setDouble(5, line.getUnitCost());
                    lineStmt.addBatch();
                }
                lineStmt.executeBatch();
            }

            conn.commit();
            return orderId;

        } catch (SQLException e) {
            System.err.println("db error saving wholesale order: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public WholesaleOrder findById(int orderId) {
        String sql = "SELECT * FROM wholesale_orders WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return mapRow(conn, rs);
        } catch (SQLException e) {
            System.err.println("db error finding wholesale order: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<WholesaleOrder> findAll() {
        String sql = "SELECT * FROM wholesale_orders ORDER BY order_date DESC, id DESC";
        List<WholesaleOrder> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(mapRow(conn, rs));
        } catch (SQLException e) {
            System.err.println("db error listing wholesale orders: " + e.getMessage());
            return Collections.emptyList();
        }
        return list;
    }

    @Override
    public boolean updateStatus(int orderId, OrderStatus status,
                                String courier, String courierRef,
                                LocalDate dispatchDate, LocalDate expectedDelivery) {
        String sql = """
            UPDATE wholesale_orders SET status=?, courier=?, courier_ref=?,
                dispatch_date=?, expected_delivery=? WHERE id=?
        """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setString(2, courier);
            stmt.setString(3, courierRef);
            stmt.setString(4, dispatchDate     != null ? dispatchDate.toString()     : null);
            stmt.setString(5, expectedDelivery != null ? expectedDelivery.toString() : null);
            stmt.setInt(6, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("db error updating order status: " + e.getMessage());
            return false;
        }
    }

    // maps a result set row + fetches associated lines for the given order
    private WholesaleOrder mapRow(Connection conn, ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String sd = rs.getString("dispatch_date");
        String ed = rs.getString("expected_delivery");

        return new WholesaleOrder(
            id,
            LocalDate.parse(rs.getString("order_date")),
            OrderStatus.valueOf(rs.getString("status")),
            loadLines(conn, id),
            sd != null ? LocalDate.parse(sd) : null,
            rs.getString("courier"),
            rs.getString("courier_ref"),
            ed != null ? LocalDate.parse(ed) : null
        );
    }

    // loads all lines for a given order id
    private List<OrderLine> loadLines(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT * FROM wholesale_order_lines WHERE order_id = ?";
        List<OrderLine> lines = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                lines.add(new OrderLine(
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getInt("quantity"),
                    rs.getDouble("unit_cost")
                ));
            }
        }
        return lines;
    }
}
