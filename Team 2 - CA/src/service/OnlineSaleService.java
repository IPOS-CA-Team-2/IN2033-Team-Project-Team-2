package service;

import db.DatabaseManager;
import exception.StockException;
import model.OnlineSale;
import model.OnlineSaleItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// handles incoming online sale events from ipos-pu
// deducts stock for each item and persists a record to online_sales
public class OnlineSaleService {

    private final StockService stockService;

    public OnlineSaleService(StockService stockService) {
        this.stockService = stockService;
    }

    // process an online sale — deducts stock and saves the record
    // returns true if all items were fully deducted
    public boolean processOnlineSale(OnlineSale sale) {
        if (sale == null || sale.getItems().isEmpty()) return false;

        boolean fullyApplied = true;

        for (OnlineSaleItem item : sale.getItems()) {
            try {
                stockService.decreaseStock(item.getItemId(), item.getQuantity());
            } catch (StockException e) {
                System.err.println("online sale " + sale.getPuOrderId()
                    + ": skipped item " + item.getItemId() + " — " + e.getMessage());
                fullyApplied = false;
            }
        }

        persistSale(sale, fullyApplied);
        return fullyApplied;
    }

    // saves the incoming sale to online_sales + online_sale_items tables
    private void persistSale(OnlineSale sale, boolean fullyApplied) {
        String saleSql = """
            INSERT OR IGNORE INTO online_sales
                (pu_order_id, received_date, customer_email, fully_applied, delivery_address, status)
            VALUES (?, ?, ?, ?, ?, 'RECEIVED')
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
                stmt.setString(5, sale.getDeliveryAddress());
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
            System.out.println("[OnlineSaleService] Persisted online sale " + sale.getPuOrderId()
                + " (fullyApplied=" + fullyApplied + ")");

        } catch (SQLException e) {
            System.err.println("[OnlineSaleService] DB error persisting online sale: " + e.getMessage());
        }
    }

    // returns a list of item ids that could not be fully applied (insufficient stock)
    public List<Integer> getSkippedItems(OnlineSale sale) {
        List<Integer> skipped = new ArrayList<>();
        for (OnlineSaleItem item : sale.getItems()) {
            try {
                stockService.getStockItem(item.getItemId());
            } catch (StockException ignored) {
                skipped.add(item.getItemId());
            }
        }
        return skipped;
    }
}
