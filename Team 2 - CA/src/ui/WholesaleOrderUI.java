package ui;

import integration.MockPuAdapter;
import integration.MockSaGateway;
import model.*;
import service.OnlineSaleService;
import service.StockService;
import service.WholesaleOrderService;
import repository.StockRepositoryImpl;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// wholesale order management screen
// pharmacists and managers can place orders with infopharma (sa),
// track order status, mark deliveries as received, and simulate pu online sales
public class WholesaleOrderUI extends JPanel {

    private final User                  currentUser;
    private final WholesaleOrderService orderService;
    private final MockPuAdapter         puAdapter;
    private final StockService          stockService;

    private DefaultTableModel tableModel;
    private JTable            orderTable;
    private JButton           deliveredBtn;
    private JButton           statusBtn;

    private static final int COL_ID       = 0;
    private static final int COL_DATE     = 1;
    private static final int COL_STATUS   = 2;
    private static final int COL_ITEMS    = 3;
    private static final int COL_TOTAL    = 4;
    private static final int COL_EXPECTED = 5;
    private static final int COL_COURIER  = 6;

    public WholesaleOrderUI(User user) {
        this.currentUser  = user;
        this.stockService = new StockService(new StockRepositoryImpl());
        this.orderService = new WholesaleOrderService(new MockSaGateway(), stockService);
        this.puAdapter    = new MockPuAdapter(new OnlineSaleService(stockService));

        setLayout(new BorderLayout());
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        loadOrders();
    }

    private JPanel buildHeader() {
        return UITheme.createHeaderPanel("Wholesale Orders — InfoPharma (SA)");
    }

    private JPanel buildTablePanel() {
        String[] cols = {"Order ID", "Date", "Status", "Items", "Total (£)", "Expected Delivery", "Courier"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        orderTable = new JTable(tableModel);
        orderTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        orderTable.getColumnModel().getColumn(COL_ID).setMaxWidth(70);
        orderTable.getColumnModel().getColumn(COL_ITEMS).setMaxWidth(60);
        UITheme.styleTable(orderTable);

        // colour rows by status
        orderTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String status = (String) table.getValueAt(row, COL_STATUS);
                    switch (status) {
                        case "DISPATCHED"      -> c.setBackground(new Color(212, 237, 218)); // light green
                        case "DELIVERED"       -> c.setBackground(new Color(209, 231, 221)); // teal-ish
                        case "BEING_PROCESSED" -> c.setBackground(new Color(255, 243, 205)); // yellow
                        default                -> c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                    }
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        // enable action buttons based on selected row status
        orderTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = orderTable.getSelectedRow();
            if (row == -1) {
                deliveredBtn.setEnabled(false);
                statusBtn.setEnabled(false);
                return;
            }
            String status = (String) tableModel.getValueAt(row, COL_STATUS);
            deliveredBtn.setEnabled("DISPATCHED".equals(status));
            statusBtn.setEnabled(!"DELIVERED".equals(status));
        });

        JScrollPane scroll = new JScrollPane(orderTable);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton placeBtn   = UITheme.successBtn("Place New Order");
        deliveredBtn       = UITheme.primaryBtn("Mark as Delivered");
        statusBtn          = UITheme.primaryBtn("Update Status");
        JButton puSimBtn   = UITheme.secondaryBtn("Simulate PU Online Sale");
        JButton refreshBtn = UITheme.secondaryBtn("Refresh");

        deliveredBtn.setEnabled(false);
        statusBtn.setEnabled(false);

        placeBtn.addActionListener(e -> handlePlaceOrder());
        deliveredBtn.addActionListener(e -> handleMarkDelivered());
        statusBtn.addActionListener(e -> handleUpdateStatus());
        puSimBtn.addActionListener(e -> handleSimulatePuSale());
        refreshBtn.addActionListener(e -> loadOrders());

        panel.add(placeBtn);
        panel.add(deliveredBtn);
        panel.add(statusBtn);
        panel.add(puSimBtn);
        panel.add(refreshBtn);

        return panel;
    }

    // load all orders into the table
    private void loadOrders() {
        tableModel.setRowCount(0);
        for (WholesaleOrder order : orderService.getAllOrders()) {
            tableModel.addRow(new Object[]{
                order.getOrderId(),
                order.getOrderDate().toString(),
                order.getStatus().name(),
                order.getLines().size(),
                String.format("%.2f", order.getTotalValue()),
                order.getExpectedDelivery() != null ? order.getExpectedDelivery().toString() : "—",
                order.getCourier() != null ? order.getCourier() : "—"
            });
        }
    }

    // returns the order id of the selected row, or -1 if nothing selected
    private int getSelectedOrderId() {
        int row = orderTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select an order first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return (int) tableModel.getValueAt(row, COL_ID);
    }

    // open a dialog to build a new wholesale order from the stock catalogue
    private void handlePlaceOrder() {
        List<StockItem> stock = stockService.getAllStock();
        if (stock.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No stock items found.", "Empty Catalogue", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // build a table where user enters quantity to order for each item
        String[] cols = {"Item", "Bulk Cost (£)", "Current Qty", "Order Qty"};
        DefaultTableModel dm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 3; }
            @Override public Class<?> getColumnClass(int c) { return c == 3 ? Integer.class : Object.class; }
        };
        for (StockItem item : stock) {
            dm.addRow(new Object[]{
                item.getName(),
                String.format("%.2f", item.getBulkCost()),
                item.getQuantity(),
                0
            });
        }

        JTable orderForm = new JTable(dm);
        UITheme.styleTable(orderForm);
        orderForm.getColumnModel().getColumn(3).setPreferredWidth(80);
        JScrollPane scroll = new JScrollPane(orderForm);
        scroll.setPreferredSize(new Dimension(560, 200));

        JLabel hint = new JLabel("Enter quantities to order. Leave 0 to skip.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(UITheme.SECONDARY);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(hint, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, content, "Place Wholesale Order",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        // collect lines where qty > 0
        List<OrderLine> lines = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) {
            Object val = dm.getValueAt(i, 3);
            int qty = val instanceof Integer ? (Integer) val : 0;
            if (qty > 0) {
                StockItem item = stock.get(i);
                lines.add(new OrderLine(item.getItemId(), item.getName(), qty, item.getBulkCost()));
            }
        }

        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No items selected — order not placed.", "Empty Order", JOptionPane.WARNING_MESSAGE);
            return;
        }

        WholesaleOrder placed = orderService.placeOrder(lines);
        if (placed != null) {
            loadOrders();
            JOptionPane.showMessageDialog(this,
                "Order #" + placed.getOrderId() + " placed successfully.\n"
                + placed.getLines().size() + " line(s), total £" + String.format("%.2f", placed.getTotalValue()),
                "Order Placed", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to place order.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // mark a dispatched order as delivered and increase stock
    private void handleMarkDelivered() {
        int orderId = getSelectedOrderId();
        if (orderId == -1) return;

        WholesaleOrder order = orderService.getOrder(orderId);
        if (order == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
            "Mark Order #" + orderId + " as Delivered?\n"
            + "Stock will be increased for all " + order.getLines().size() + " item(s).",
            "Confirm Delivery", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        orderService.markDelivered(orderId);
        loadOrders();
        JOptionPane.showMessageDialog(this,
            "Order #" + orderId + " marked as delivered. Stock has been updated.",
            "Delivered", JOptionPane.INFORMATION_MESSAGE);
    }

    // simulate sa updating the order status (for demo)
    private void handleUpdateStatus() {
        int orderId = getSelectedOrderId();
        if (orderId == -1) return;

        WholesaleOrder order = orderService.getOrder(orderId);
        if (order == null) return;

        // only show statuses that are valid next states
        OrderStatus current = order.getStatus();
        OrderStatus[] options = OrderStatus.values();

        OrderStatus selected = (OrderStatus) JOptionPane.showInputDialog(
            this, "Select new status for Order #" + orderId + ":",
            "Update Order Status", JOptionPane.PLAIN_MESSAGE,
            null, options, current);

        if (selected == null || selected == current) return;

        // if dispatching, collect courier details
        String courier = null, courierRef = null;
        LocalDate dispatchDate = null, expectedDelivery = null;

        if (selected == OrderStatus.DISPATCHED) {
            JTextField courierField   = new JTextField();
            JTextField refField       = new JTextField();
            JTextField dispatchField  = new JTextField(LocalDate.now().toString());
            JTextField expectedField  = new JTextField(LocalDate.now().plusDays(3).toString());

            Object[] fields = {
                "Courier:", courierField,
                "Courier Ref:", refField,
                "Dispatch Date (YYYY-MM-DD):", dispatchField,
                "Expected Delivery (YYYY-MM-DD):", expectedField
            };

            if (JOptionPane.showConfirmDialog(this, fields, "Dispatch Details",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

            courier      = courierField.getText().trim();
            courierRef   = refField.getText().trim();
            try {
                dispatchDate     = LocalDate.parse(dispatchField.getText().trim());
                expectedDelivery = LocalDate.parse(expectedField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid date format. Use YYYY-MM-DD.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        orderService.simulateStatusUpdate(orderId, selected, courier, courierRef, dispatchDate, expectedDelivery);
        loadOrders();
    }

    // simulate an online sale coming in from ipos-pu and deducting stock
    private void handleSimulatePuSale() {
        List<StockItem> stock = stockService.getAllStock();
        if (stock.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No stock items available.", "Empty Stock", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String[] cols = {"Item", "Current Qty", "Deduct Qty"};
        DefaultTableModel dm = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
            @Override public Class<?> getColumnClass(int c) { return c == 2 ? Integer.class : Object.class; }
        };
        for (StockItem item : stock) {
            dm.addRow(new Object[]{item.getName(), item.getQuantity(), 0});
        }

        JTable puForm = new JTable(dm);
        UITheme.styleTable(puForm);
        JScrollPane scroll = new JScrollPane(puForm);
        scroll.setPreferredSize(new Dimension(420, 180));

        JTextField emailField = new JTextField("customer@example.com");
        JLabel hint = new JLabel("Simulates a completed IPOS-PU online sale deducting stock.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(UITheme.SECONDARY);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(hint, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        JPanel emailRow = new JPanel(new BorderLayout(6, 0));
        emailRow.setOpaque(false);
        emailRow.add(new JLabel("Customer Email:"), BorderLayout.WEST);
        emailRow.add(emailField, BorderLayout.CENTER);
        content.add(emailRow, BorderLayout.SOUTH);

        if (JOptionPane.showConfirmDialog(this, content, "Simulate PU Online Sale",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        List<OnlineSaleItem> items = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();

        for (int i = 0; i < stock.size(); i++) {
            Object val = dm.getValueAt(i, 2);
            int qty = val instanceof Integer ? (Integer) val : 0;
            if (qty > 0) {
                StockItem item = stock.get(i);
                if (qty > item.getQuantity()) {
                    skippedNames.add(item.getName() + " (requested " + qty + ", only " + item.getQuantity() + " available)");
                } else {
                    items.add(new OnlineSaleItem(item.getItemId(), qty));
                }
            }
        }

        if (items.isEmpty() && skippedNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No items selected.", "Empty Sale", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder result = new StringBuilder();

        if (!items.isEmpty()) {
            boolean ok = puAdapter.simulateSale(items, emailField.getText().trim());
            result.append(ok ? "All items deducted successfully." : "Sale partially applied.");
        }

        if (!skippedNames.isEmpty()) {
            result.append("\n\nSkipped (insufficient stock):\n");
            for (String s : skippedNames) result.append("• ").append(s).append("\n");
        }

        JOptionPane.showMessageDialog(this, result.toString().trim(),
            "PU Sale Applied", JOptionPane.INFORMATION_MESSAGE);
    }
}
