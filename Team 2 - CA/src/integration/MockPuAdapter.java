package integration;

import db.DatabaseManager;
import model.OnlineSale;
import model.OnlineSaleItem;
import service.OnlineSaleService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// mock implementation of IPuStockUpdater
// simulates ipos-pu notifying ipos-ca about a completed online sale
// also exposes simulateSale() for demo use without a live pu connection
// to integrate with real pu: call applyOnlineSale() from pu's system or wrap in an http endpoint
public class MockPuAdapter implements IPuStockUpdater {

    private final OnlineSaleService onlineSaleService;

    public MockPuAdapter(OnlineSaleService onlineSaleService) {
        this.onlineSaleService = onlineSaleService;
    }

    @Override
    public boolean applyOnlineSale(OnlineSale sale) {
        boolean result = onlineSaleService.processOnlineSale(sale);
        // persist a record of the online sale event for audit/reporting
        logOnlineSale(sale, result);
        return result;
    }

    // convenience method for demo — generates a fake pu order id and wraps the items
    public boolean simulateSale(List<OnlineSaleItem> items, String customerEmail) {
        String fakeOrderId = "PU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OnlineSale sale = new OnlineSale(fakeOrderId, LocalDate.now(), customerEmail, items);
        return applyOnlineSale(sale);
    }

    // log the incoming sale to the online_sales and online_sale_items tables
    private void logOnlineSale(OnlineSale sale, boolean fullyApplied) {
        String saleSql = """
            INSERT INTO online_sales (pu_order_id, received_date, customer_email, fully_applied)
            VALUES (?, ?, ?, ?)
        """;
        String itemSql = """
            INSERT INTO online_sale_items (online_sale_id, item_id, quantity)
            VALUES (?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            int saleId;
            try (PreparedStatement stmt = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, sale.getPuOrderId());
                stmt.setString(2, sale.getReceivedDate().toString());
                stmt.setString(3, sale.getCustomerEmail());
                stmt.setInt(4, fullyApplied ? 1 : 0);
                stmt.executeUpdate();
                ResultSet keys = stmt.getGeneratedKeys();
                saleId = keys.next() ? keys.getInt(1) : -1;
            }

            if (saleId > 0) {
                try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                    for (OnlineSaleItem item : sale.getItems()) {
                        itemStmt.setInt(1, saleId);
                        itemStmt.setInt(2, item.getItemId());
                        itemStmt.setInt(3, item.getQuantity());
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
            }

            conn.commit();

        } catch (SQLException e) {
            System.err.println("db error logging online sale: " + e.getMessage());
        }
    }
}
