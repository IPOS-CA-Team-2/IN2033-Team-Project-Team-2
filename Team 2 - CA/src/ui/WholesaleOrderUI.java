package ui;

import app.AppContext;
import integration.ISaGateway;
import integration.MockPuAdapter;
import model.*;
import service.WholesaleOrderService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// wholesale order management screen
// pharmacists and managers can place orders with infopharma (sa),
// track order status, mark deliveries as received, and simulate pu online sales
public class WholesaleOrderUI extends JPanel {

    private final User                  currentUser;
    private final WholesaleOrderService orderService;

    private DefaultTableModel tableModel;
    private JTable            orderTable;
    private JButton           deliveredBtn;
    private JButton           statusBtn;
    private JButton           invoiceBtn;
    private JButton           balanceBtn;

    private static final int COL_ID       = 0;
    private static final int COL_DATE     = 1;
    private static final int COL_STATUS   = 2;
    private static final int COL_ITEMS    = 3;
    private static final int COL_TOTAL    = 4;
    private static final int COL_EXPECTED = 5;
    private static final int COL_COURIER  = 6;

    public WholesaleOrderUI(User user) {
        this.currentUser  = user;
        // services come from AppContext — wired once in Main, shared across all screens
        this.orderService = AppContext.getOrderService();

        setLayout(new BorderLayout());
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        // register for live refresh — when sa pushes a status update via CaApiServer,
        // AppContext.notifyOrderRefresh() calls loadOrders() on the EDT automatically
        AppContext.addOrderRefreshListener(this::loadOrders);

        loadOrders();
    }

    private JPanel buildHeader() {
        // adds search bar on the right of the top bar
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));


        // adds management text on the left of the top bar
        // add a filler cause it looks empty without it
        JLabel titleLabel = new JLabel("Wholesale Orders — InfoPharma (SA)");
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);



        // adds search bar on the right of the top bar
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setFont(UITheme.FONT_BOLD);
        searchLabel.setForeground(Color.WHITE);



        JTextField searchField = new JTextField(18);
        UITheme.styleTextField(searchField);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate (javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }

            public void removeUpdate (javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);


        header.add(searchPanel, BorderLayout.EAST);
        header.add(titleLabel, BorderLayout.WEST);

        return header;

    }

    private void filterTable(String query) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        orderTable.setRowSorter(sorter);

        if (query == null || query.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }

        String q = query.toLowerCase();
        int len = q.length();

        if (q.startsWith("orderid: ") && len > 9) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(9).trim(), COL_ID));
        } else if (q.startsWith("date: ") && len > 6) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(), COL_DATE));
        } else if (q.startsWith("status: ") && len > 8) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(8).trim(), COL_STATUS));
        } else if (q.startsWith("items: ") && len > 7) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(7).trim(), COL_ITEMS));
        } else if (q.startsWith("total: ") && len > 7) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(7).trim(), COL_TOTAL));
        } else if (q.startsWith("delivery: ") && len > 10) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(10).trim(), COL_EXPECTED));
        } else if (q.startsWith("courier: ") && len > 9) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(9).trim(), COL_COURIER));
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q, COL_DATE, COL_STATUS, COL_COURIER));
        }
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
                        case "DISPATCHED"      -> c.setBackground(new Color(212, 237, 218));
                        case "DELIVERED"       -> c.setBackground(new Color(209, 231, 221));
                        case "BEING_PROCESSED" -> c.setBackground(new Color(255, 243, 205));
                        case "CANCELLED"       -> c.setBackground(new Color(248, 215, 218));
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
                invoiceBtn.setEnabled(false);
                return;
            }
            String status = (String) tableModel.getValueAt(row, COL_STATUS);
            int orderId = (int) tableModel.getValueAt(row, COL_ID);
            WholesaleOrder order = orderService.getOrder(orderId);
            deliveredBtn.setEnabled("DISPATCHED".equals(status));
            statusBtn.setEnabled(!"DELIVERED".equals(status) && !"CANCELLED".equals(status));
            invoiceBtn.setEnabled(order != null && order.getSaOrderId() > 0);
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
        invoiceBtn         = UITheme.secondaryBtn("View Invoice");
        balanceBtn         = UITheme.secondaryBtn("Check Balance");
        JButton puSimBtn   = UITheme.secondaryBtn("Simulate PU Online Sale");
        JButton refreshBtn = UITheme.secondaryBtn("Refresh");

        deliveredBtn.setEnabled(false);
        statusBtn.setEnabled(false);
        invoiceBtn.setEnabled(false);

        placeBtn.addActionListener(e -> handlePlaceOrder(placeBtn));
        deliveredBtn.addActionListener(e -> handleMarkDelivered());
        statusBtn.addActionListener(e -> handleUpdateStatus());
        invoiceBtn.addActionListener(e -> handleViewInvoice());
        balanceBtn.addActionListener(e -> handleCheckBalance());
        puSimBtn.addActionListener(e -> handleSimulatePuSale());
        refreshBtn.addActionListener(e -> loadOrders());

        JLabel searchFilters = new JLabel("Search commands: orderid, date, status, items, total, delivery, courier (e.g. date: 2026-03-02)");
        searchFilters.setFont(UITheme.FONT_SMALL);
        searchFilters.setForeground(UITheme.SECONDARY);

        panel.add(placeBtn);
        panel.add(deliveredBtn);
        panel.add(statusBtn);
        panel.add(invoiceBtn);
        panel.add(balanceBtn);
        panel.add(puSimBtn);
        panel.add(refreshBtn);
        panel.add(searchFilters);

        return panel;
    }

    // load all orders into the table — public so AppContext refresh listeners can call it
    public void loadOrders() {
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

    private int getSelectedOrderId() {
        int row = orderTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select an order first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return (int) tableModel.getValueAt(row, COL_ID);
    }

    // wrap the HTTP submission in a SwingWorker so it doesn't block the EDT
    // the Place Order button is disabled while the call is in flight
    private void handlePlaceOrder(JButton placeBtn) {
        List<StockItem> stock = AppContext.getStockService().getAllStock();
        if (stock.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No stock items found.", "Empty Catalogue", JOptionPane.WARNING_MESSAGE);
            return;
        }

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

        final List<OrderLine> finalLines = lines;
        placeBtn.setEnabled(false);

        new SwingWorker<WholesaleOrder, Void>() {
            @Override
            protected WholesaleOrder doInBackground() {
                return orderService.placeOrder(finalLines);
            }

            @Override
            protected void done() {
                placeBtn.setEnabled(true);
                try {
                    WholesaleOrder placed = get();
                    loadOrders();
                    if (placed != null) {
                        String saInfo = placed.getSaOrderId() > 0
                            ? "\nSA confirmed — SA Order ID: " + placed.getSaOrderId()
                            : "\n(SA offline — saved locally only)";
                        JOptionPane.showMessageDialog(WholesaleOrderUI.this,
                            "Order #" + placed.getOrderId() + " placed successfully.\n"
                            + placed.getLines().size() + " line(s), total £"
                            + String.format("%.2f", placed.getTotalValue()) + saInfo,
                            "Order Placed", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(WholesaleOrderUI.this,
                            "Failed to place order.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(WholesaleOrderUI.this,
                        "Order error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

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

    private void handleUpdateStatus() {
        int orderId = getSelectedOrderId();
        if (orderId == -1) return;

        WholesaleOrder order = orderService.getOrder(orderId);
        if (order == null) return;

        OrderStatus current = order.getStatus();
        OrderStatus[] options = OrderStatus.values();

        OrderStatus selected = (OrderStatus) JOptionPane.showInputDialog(
            this, "Select new status for Order #" + orderId + ":",
            "Update Order Status", JOptionPane.PLAIN_MESSAGE,
            null, options, current);

        if (selected == null || selected == current) return;

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

    // fetch invoice from SA for the selected order and display it in a scrollable dialog
    // runs on a SwingWorker so the HTTP call never blocks the EDT
    private void handleViewInvoice() {
        int orderId = getSelectedOrderId();
        if (orderId == -1) return;
        WholesaleOrder order = orderService.getOrder(orderId);
        if (order == null || order.getSaOrderId() <= 0) {
            JOptionPane.showMessageDialog(this,
                "This order has no SA Order ID — invoice unavailable.\n"
                + "(SA must have received and confirmed the order first.)",
                "No Invoice", JOptionPane.WARNING_MESSAGE);
            return;
        }

        invoiceBtn.setEnabled(false);
        int saId = order.getSaOrderId();

        new SwingWorker<java.util.Map<String, Object>, Void>() {
            @Override
            protected java.util.Map<String, Object> doInBackground() {
                return orderService.getInvoice(saId);
            }

            @Override
            protected void done() {
                invoiceBtn.setEnabled(true);
                java.util.Map<String, Object> inv;
                try { inv = get(); } catch (Exception ex) { inv = null; }

                if (inv == null) {
                    JOptionPane.showMessageDialog(WholesaleOrderUI.this,
                        "Could not retrieve invoice — SA may be offline or no invoice exists yet.",
                        "Invoice Unavailable", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // build invoice dialog
                JPanel dialog = new JPanel(new BorderLayout(0, 10));
                dialog.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

                // header fields
                JPanel meta = new JPanel(new java.awt.GridLayout(0, 2, 8, 4));
                meta.setOpaque(false);
                String[] metaKeys = {"Invoice #", "Issued", "Due Date"};
                String[] metaVals = {
                    String.valueOf(inv.getOrDefault("invoiceNumber", "—")),
                    String.valueOf(inv.getOrDefault("issuedAt",      "—")),
                    String.valueOf(inv.getOrDefault("dueDate",       "—"))
                };
                for (int i = 0; i < metaKeys.length; i++) {
                    JLabel k = new JLabel(metaKeys[i] + ":"); k.setFont(UITheme.FONT_BOLD);
                    JLabel v = new JLabel(metaVals[i]);
                    meta.add(k); meta.add(v);
                }

                // overdue warning
                String dueDateStr = String.valueOf(inv.getOrDefault("dueDate", ""));
                if (!dueDateStr.equals("—") && !dueDateStr.isEmpty()) {
                    try {
                        LocalDate due = LocalDate.parse(dueDateStr);
                        long daysOver = java.time.temporal.ChronoUnit.DAYS.between(due, LocalDate.now());
                        if (daysOver > 30) {
                            JLabel warn = new JLabel("⚠  Overdue by " + daysOver + " days — payment required immediately");
                            warn.setForeground(new Color(200, 0, 0));
                            warn.setFont(UITheme.FONT_BOLD);
                            meta.add(warn); meta.add(new JLabel(""));
                        } else if (daysOver > 0) {
                            JLabel warn = new JLabel("⚠  Overdue by " + daysOver + " day(s)");
                            warn.setForeground(new Color(200, 120, 0));
                            warn.setFont(UITheme.FONT_BOLD);
                            meta.add(warn); meta.add(new JLabel(""));
                        }
                    } catch (Exception ignored) { }
                }

                // line items table
                String[] lineCols = {"Description", "Qty", "Unit Price (£)", "Line Total (£)"};
                DefaultTableModel lm = new DefaultTableModel(lineCols, 0) {
                    @Override public boolean isCellEditable(int r, int c) { return false; }
                };
                Object rawLines = inv.get("lines");
                if (rawLines instanceof java.util.List<?> lineList) {
                    for (Object lineObj : lineList) {
                        if (lineObj instanceof java.util.Map<?,?>) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> line = (Map<String, Object>) lineObj;
                            lm.addRow(new Object[]{
                                line.getOrDefault("description", ""),
                                line.getOrDefault("quantity",    0),
                                String.format("%.2f", ((Number) line.getOrDefault("unitPrice",  0.0)).doubleValue()),
                                String.format("%.2f", ((Number) line.getOrDefault("lineTotal",  0.0)).doubleValue())
                            });
                        }
                    }
                }
                JTable lineTable = new JTable(lm);
                UITheme.styleTable(lineTable);
                JScrollPane lineScroll = new JScrollPane(lineTable);
                lineScroll.setPreferredSize(new Dimension(520, 140));

                // totals panel
                JPanel totals = new JPanel(new java.awt.GridLayout(0, 2, 8, 4));
                totals.setOpaque(false);
                double gross    = ((Number) inv.getOrDefault("grossTotal",            0.0)).doubleValue();
                double fixed    = ((Number) inv.getOrDefault("fixedDiscountAmount",   0.0)).doubleValue();
                double flexible = ((Number) inv.getOrDefault("flexibleCreditApplied", 0.0)).doubleValue();
                double total    = ((Number) inv.getOrDefault("totalDue",              0.0)).doubleValue();
                String[][] totRows = {
                    {"Gross Total:",           String.format("£%.2f", gross)},
                    {"Fixed Discount:",        String.format("−£%.2f", fixed)},
                    {"Flexible Credit:",       String.format("−£%.2f", flexible)},
                    {"Total Due:",             String.format("£%.2f", total)}
                };
                for (String[] row : totRows) {
                    JLabel k = new JLabel(row[0]); k.setFont(UITheme.FONT_BOLD);
                    JLabel v = new JLabel(row[1]);
                    if (row[0].equals("Total Due:")) { v.setFont(UITheme.FONT_BOLD); v.setForeground(new Color(0, 100, 0)); }
                    totals.add(k); totals.add(v);
                }

                JPanel north = new JPanel(new BorderLayout(0, 8));
                north.setOpaque(false);
                north.add(meta, BorderLayout.NORTH);
                north.add(lineScroll, BorderLayout.CENTER);

                dialog.add(north, BorderLayout.CENTER);
                dialog.add(totals, BorderLayout.SOUTH);

                JOptionPane.showMessageDialog(WholesaleOrderUI.this, dialog,
                    "Invoice — SA Order #" + saId, JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }

    // query outstanding balance from SA and display in a dialog with colour-coded warning
    private void handleCheckBalance() {
        balanceBtn.setEnabled(false);
        new SwingWorker<java.util.Map<String, Object>, Void>() {
            @Override
            protected java.util.Map<String, Object> doInBackground() {
                return orderService.getOutstandingBalance();
            }

            @Override
            protected void done() {
                balanceBtn.setEnabled(true);
                java.util.Map<String, Object> bal;
                try { bal = get(); } catch (Exception ex) { bal = null; }

                if (bal == null) {
                    JOptionPane.showMessageDialog(WholesaleOrderUI.this,
                        "Could not retrieve balance — SA may be offline.",
                        "Balance Unavailable", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                double total    = ((Number) bal.getOrDefault("outstandingTotal",    0.0)).doubleValue();
                String currency = String.valueOf(bal.getOrDefault("currency",        "GBP"));
                Object dueDateObj = bal.get("oldestUnpaidDueDate");
                long   daysElapsed = ((Number) bal.getOrDefault("daysElapsedSinceDue", 0L)).longValue();

                JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 8, 6));
                panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

                JLabel totalKey = new JLabel("Outstanding Balance:"); totalKey.setFont(UITheme.FONT_BOLD);
                JLabel totalVal = new JLabel(String.format("%s %.2f", currency, total));
                totalVal.setFont(UITheme.FONT_BOLD);
                if (total > 0) totalVal.setForeground(new Color(180, 0, 0));
                else           totalVal.setForeground(new Color(0, 130, 0));

                panel.add(totalKey); panel.add(totalVal);

                if (dueDateObj != null && !dueDateObj.toString().isEmpty()) {
                    JLabel dueKey = new JLabel("Oldest Unpaid Due:"); dueKey.setFont(UITheme.FONT_BOLD);
                    JLabel dueVal = new JLabel(dueDateObj.toString());
                    panel.add(dueKey); panel.add(dueVal);

                    JLabel daysKey = new JLabel("Days Since Due:"); daysKey.setFont(UITheme.FONT_BOLD);
                    JLabel daysVal = new JLabel(daysElapsed + " day(s)");
                    if (daysElapsed > 30) {
                        daysVal.setForeground(new Color(200, 0, 0));
                        daysVal.setFont(UITheme.FONT_BOLD);
                        panel.add(daysKey); panel.add(daysVal);
                        JLabel warn = new JLabel("⚠  Account at risk — contact SA immediately");
                        warn.setForeground(new Color(200, 0, 0));
                        warn.setFont(UITheme.FONT_BOLD);
                        panel.add(warn); panel.add(new JLabel(""));
                    } else if (daysElapsed > 0) {
                        daysVal.setForeground(new Color(200, 120, 0));
                        panel.add(daysKey); panel.add(daysVal);
                        JLabel warn = new JLabel("⚠  Payment overdue — settle soon to avoid suspension");
                        warn.setForeground(new Color(200, 120, 0));
                        panel.add(warn); panel.add(new JLabel(""));
                    } else {
                        panel.add(daysKey); panel.add(daysVal);
                    }
                }

                JOptionPane.showMessageDialog(WholesaleOrderUI.this, panel,
                    "Outstanding Balance — InfoPharma SA", JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();
    }

    private void handleSimulatePuSale() {
        List<StockItem> stock = AppContext.getStockService().getAllStock();
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

        List<OnlineSaleItem> items        = new ArrayList<>();
        List<String>         skippedNames = new ArrayList<>();

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
            if (!(AppContext.getPuAdapter() instanceof MockPuAdapter)) {
                JOptionPane.showMessageDialog(this,
                    "Simulate PU Sale is only available in mock mode.\n"
                    + "Running in live HTTP mode — trigger a real PU order instead.",
                    "Mock Only", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            boolean ok = ((MockPuAdapter) AppContext.getPuAdapter())
                .simulateSale(items, emailField.getText().trim());
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
