package ui;

import app.AppContext;
import db.DatabaseManager;
import model.User;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

// screen for viewing and progressing online orders received from ipos-pu
// pharmacist or manager can move an order through: RECEIVED, READY_FOR_SHIPMENT, DISPATCHED, DELIVERED
// each status change is pushed back to PU so the member's order history stays up to date
public class OnlineOrdersUI extends JPanel {

    private DefaultTableModel tableModel;
    private JTable orderTable;
    private JLabel footerLabel;

    private static final int COL_DB_ID = 0;    // hidden, internal online_sales.id
    private static final int COL_ORDER_ID = 1;
    private static final int COL_DATE = 2;
    private static final int COL_EMAIL = 3;
    private static final int COL_ADDRESS = 4;
    private static final int COL_ITEMS = 5;
    private static final int COL_STATUS = 6;

    // status progression order
    private static final List<String> STATUS_ORDER =
        List.of("RECEIVED", "READY_FOR_SHIPMENT", "DISPATCHED", "DELIVERED");

    public OnlineOrdersUI(User user) {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(UITheme.createHeaderPanel("Online Orders (from PU Portal)"), BorderLayout.NORTH);
        topSection.add(buildControls(), BorderLayout.SOUTH);

        add(topSection,    BorderLayout.NORTH);
        add(buildTable(),  BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadOrders();
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setOpaque(false);

        JButton refreshBtn = UITheme.secondaryBtn("Refresh");
        refreshBtn.addActionListener(e -> loadOrders());

        JButton advanceBtn = UITheme.primaryBtn("Advance Status");
        advanceBtn.setToolTipText("Move selected order to the next status stage");
        advanceBtn.addActionListener(e -> handleAdvanceStatus());

        JLabel note = new JLabel("  Select an order and click Advance Status to progress it.");
        note.setFont(UITheme.FONT_SMALL);
        note.setForeground(UITheme.SUBTEXT);

        panel.add(refreshBtn);
        panel.add(advanceBtn);
        panel.add(note);
        return panel;
    }

    private JScrollPane buildTable() {
        String[] cols = {"#", "PU Order ID", "Date", "Customer Email", "Delivery Address", "Items", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        orderTable = new JTable(tableModel);
        UITheme.styleTable(orderTable);
        orderTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // hide internal DB id column
        orderTable.getColumnModel().getColumn(COL_DB_ID).setMinWidth(0);
        orderTable.getColumnModel().getColumn(COL_DB_ID).setMaxWidth(0);
        orderTable.getColumnModel().getColumn(COL_DB_ID).setWidth(0);

        orderTable.getColumnModel().getColumn(COL_ORDER_ID).setMaxWidth(130);
        orderTable.getColumnModel().getColumn(COL_DATE).setMaxWidth(100);
        orderTable.getColumnModel().getColumn(COL_ITEMS).setMaxWidth(80);
        orderTable.getColumnModel().getColumn(COL_STATUS).setMaxWidth(160);

        // colour status column by stage
        orderTable.getColumnModel().getColumn(COL_STATUS).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String status = value != null ? value.toString() : "";
                    c.setForeground(Color.BLACK);
                    switch (status) {
                        case "RECEIVED"          -> c.setBackground(new Color(220, 235, 255));
                        case "READY_FOR_SHIPMENT"-> c.setBackground(new Color(255, 243, 200));
                        case "DISPATCHED"        -> c.setBackground(new Color(200, 240, 255));
                        case "DELIVERED"         -> c.setBackground(new Color(200, 240, 200));
                        default                  -> c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                    }
                }
                return c;
            }
        });

        return new JScrollPane(orderTable);
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        footerLabel = new JLabel("  Loading…");
        footerLabel.setFont(UITheme.FONT_BOLD);
        footerLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        panel.add(footerLabel, BorderLayout.WEST);
        return panel;
    }

    // load all online_sales rows joined with item counts
    private void loadOrders() {
        tableModel.setRowCount(0);
        String sql = """
            SELECT os.id, os.pu_order_id, os.received_date, os.customer_email,
                   os.delivery_address, os.status,
                   COUNT(osi.id) AS item_count
            FROM online_sales os
            LEFT JOIN online_sale_items osi ON osi.online_sale_id = os.id
            GROUP BY os.id
            ORDER BY os.id DESC
        """;
        int total = 0;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("pu_order_id"),
                    rs.getString("received_date"),
                    rs.getString("customer_email") != null ? rs.getString("customer_email") : "—",
                    rs.getString("delivery_address") != null && !rs.getString("delivery_address").isBlank()
                        ? rs.getString("delivery_address") : "—",
                    rs.getInt("item_count") + " item(s)",
                    rs.getString("status")
                });
                total++;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Could not load online orders: " + e.getMessage(),
                "DB Error", JOptionPane.ERROR_MESSAGE);
        }
        footerLabel.setText("  " + total + " online order(s) on record");
    }

    // advance the selected order to the next status stage
    private void handleAdvanceStatus() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an order first.",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int dbId = (int) tableModel.getValueAt(row, COL_DB_ID);
        String puOrderId = (String) tableModel.getValueAt(row, COL_ORDER_ID);
        String current = (String) tableModel.getValueAt(row, COL_STATUS);

        if ("DELIVERED".equals(current)) {
            JOptionPane.showMessageDialog(this,
                "Order " + puOrderId + " is already marked DELIVERED.",
                "Already Complete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int idx  = STATUS_ORDER.indexOf(current);
        String next = (idx >= 0 && idx < STATUS_ORDER.size() - 1)
            ? STATUS_ORDER.get(idx + 1)
            : "DELIVERED";

        int confirm = JOptionPane.showConfirmDialog(this,
            "Advance order " + puOrderId + "\n\n"
            + "  From: " + formatStatus(current) + "\n"
            + "  To:   " + formatStatus(next),
            "Confirm Status Change", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // update locally
        String sql = "UPDATE online_sales SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, next);
            stmt.setInt(2, dbId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to update status: " + e.getMessage(),
                "DB Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // notify PU (non-fatal)
        try {
            if (AppContext.getPuAdapter() != null) {
                AppContext.getPuAdapter().notifyOrderStatusUpdate(puOrderId, next);
            }
        } catch (Exception e) {
            System.err.println("[OnlineOrdersUI] PU notification failed (non-fatal): " + e.getMessage());
        }

        loadOrders();
        JOptionPane.showMessageDialog(this,
            "Order " + puOrderId + " advanced to " + formatStatus(next) + ".",
            "Status Updated", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "RECEIVED"           -> "Received";
            case "READY_FOR_SHIPMENT" -> "Ready for Shipment";
            case "DISPATCHED"         -> "Dispatched";
            case "DELIVERED"          -> "Delivered";
            default                   -> status;
        };
    }
}
