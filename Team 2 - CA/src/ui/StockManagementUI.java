package ui;

import app.AppContext;
import exception.StockException;
import model.StockItem;
import repository.StockRepositoryImpl;
import service.StockService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.sql.*;

// stock management screen — view all stock, update quantities, add/remove items
// accessible by pharmacist and admin from the dashboard
public class StockManagementUI extends JPanel {

    private final StockService stockService;
    private DefaultTableModel tableModel;
    private JTable stockTable;

    // column indices — COL_ID is hidden (internal use only)
    private static final int COL_ID          = 0;
    private static final int COL_ITEM_CODE   = 1;
    private static final int COL_NAME        = 2;
    private static final int COL_PKG_TYPE    = 3;
    private static final int COL_UNIT        = 4;
    private static final int COL_UPP         = 5;  // units per pack
    private static final int COL_BULK_COST   = 6;
    private static final int COL_QTY         = 7;
    private static final int COL_THRESHOLD   = 8;
    private static final int COL_RETAIL_PRICE = 9;
    private static final int COL_STATUS      = 10;

    public StockManagementUI() {
        this.stockService = new StockService(new StockRepositoryImpl());
        setLayout(new BorderLayout());
        setOpaque(false);

        add(topSection(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        loadStockData();

        // register for live stock refresh — when pu pushes an online sale via CaApiServer,
        // AppContext.notifyStockRefresh() calls loadStockData() on the EDT automatically
        AppContext.addStockRefreshListener(this::loadStockData);
    }

    private JPanel topSection() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel titleLabel = new JLabel("Stock Management");
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setFont(UITheme.FONT_BOLD);
        searchLabel.setForeground(Color.WHITE);

        JTextField searchField = new JTextField(18);
        UITheme.styleTextField(searchField);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate (javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void removeUpdate (javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(searchField.getText()); }
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
        stockTable.setRowSorter(sorter);

        if (query.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }

        String q = query.toLowerCase();
        int len = q.length();

        if      (q.startsWith("id: ")          && len > 4)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(4).trim(),  COL_ITEM_CODE));
        else if (q.startsWith("name: ")        && len > 6)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(),  COL_NAME));
        else if (q.startsWith("type: ")        && len > 6)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(),  COL_PKG_TYPE));
        else if (q.startsWith("unit: ")        && len > 6)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(),  COL_UNIT));
        else if (q.startsWith("quantity: ")    && len > 10) sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(10).trim(), COL_QTY));
        else if (q.startsWith("cost: ")        && len > 6)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(),  COL_BULK_COST));
        else if (q.startsWith("threshold: ")   && len > 11) sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(11).trim(), COL_THRESHOLD));
        else if (q.startsWith("price: ")       && len > 7)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(7).trim(),  COL_RETAIL_PRICE));
        else if (q.startsWith("status: ")      && len > 8)  sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(8).trim(),  COL_STATUS));
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q,
                COL_ITEM_CODE, COL_NAME, COL_PKG_TYPE, COL_UNIT, COL_QTY, COL_BULK_COST, COL_THRESHOLD, COL_RETAIL_PRICE, COL_STATUS));
    }

    // table showing all stock items — columns match the ipos spec catalogue
    private JPanel buildTablePanel() {
        String[] columns = {
            "ID",                   // 0 — hidden internal key
            "Item ID",              // 1 — spec item code e.g. 100 00001
            "Description",          // 2
            "Package Type",         // 3
            "Unit",                 // 4
            "Units in a Pack",      // 5
            "Package Cost (£)",     // 6
            "Availability (packs)", // 7
            "Stock Limit (packs)",  // 8
            "Retail Price (£)",     // 9 — calculated: bulk cost × (1 + markup)
            "Status"                // 10
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // all editing goes through buttons
            }
        };

        stockTable = new JTable(tableModel);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UITheme.styleTable(stockTable);

        // hide the internal id column — still in model for lookups
        stockTable.getColumnModel().getColumn(COL_ID).setMinWidth(0);
        stockTable.getColumnModel().getColumn(COL_ID).setMaxWidth(0);
        stockTable.getColumnModel().getColumn(COL_ID).setWidth(0);

        // highlight low stock rows in red
        stockTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                int modelRow = table.convertRowIndexToModel(row);
                String status = (String) tableModel.getValueAt(modelRow, COL_STATUS);
                if (!isSelected) {
                    if ("LOW STOCK".equals(status)) {
                        c.setBackground(new Color(255, 220, 220));
                        c.setForeground(new Color(180, 0, 0));
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                        c.setForeground(Color.BLACK);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(stockTable);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // action buttons at the bottom
    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton increaseBtn   = UITheme.primaryBtn("Increase Stock");
        JButton decreaseBtn   = UITheme.primaryBtn("Decrease Stock");
        JButton addItemBtn    = UITheme.successBtn("Add New Item");
        JButton removeItemBtn = UITheme.dangerBtn("Remove Item");
        JButton editBtn       = UITheme.primaryBtn("Edit Details");
        JButton refreshBtn    = UITheme.secondaryBtn("Refresh");

        increaseBtn.addActionListener(e -> handleAdjustStock(true));
        decreaseBtn.addActionListener(e -> handleAdjustStock(false));
        addItemBtn.addActionListener(e -> handleAddItem());
        removeItemBtn.addActionListener(e -> handleRemoveItem());
        editBtn.addActionListener(e -> handleEditItem());
        refreshBtn.addActionListener(e -> loadStockData());

        panel.add(increaseBtn);
        panel.add(decreaseBtn);
        panel.add(addItemBtn);
        panel.add(removeItemBtn);
        panel.add(editBtn);
        panel.add(refreshBtn);

        JLabel searchFilters = new JLabel(
            "Search commands: id, name, type, unit, quantity, cost, threshold, price, status  (e.g.  name: Aspirin)");
        searchFilters.setFont(UITheme.FONT_SMALL);
        searchFilters.setForeground(UITheme.SECONDARY);
        panel.add(searchFilters);

        return panel;
    }

    // pull fresh data from the db and repopulate the table
    private void loadStockData() {
        tableModel.setRowCount(0);
        List<StockItem> items = stockService.getAllStock();
        for (StockItem item : items) {
            String status = item.isLowStock() ? "LOW STOCK" : "OK";
            tableModel.addRow(new Object[]{
                item.getItemId(),                                       // hidden
                item.getItemCode(),                                     // Item ID
                item.getName(),                                         // Description
                item.getPackageType(),                                  // Package Type
                item.getUnit(),                                         // Unit
                item.getUnitsPerPack(),                                 // Units in a Pack
                String.format("%.2f", item.getBulkCost()),             // Package Cost
                item.getQuantity(),                                     // Availability
                item.getLowStockThreshold(),                            // Stock Limit
                String.format("%.2f", item.getUnitPrice()),            // Retail Price
                status
            });
        }
    }

    // edit dialog — all editable fields including new spec columns
    private void handleEditItem() {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a stock item first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = stockTable.convertRowIndexToModel(selectedRow);
        int itemId   = (int) tableModel.getValueAt(modelRow, COL_ID);
        String name  = (String) tableModel.getValueAt(modelRow, COL_NAME);

        JTextField itemCodeField   = new JTextField(tableModel.getValueAt(modelRow, COL_ITEM_CODE).toString());
        JTextField nameField       = new JTextField(name);
        JTextField pkgTypeField    = new JTextField(tableModel.getValueAt(modelRow, COL_PKG_TYPE).toString());
        JTextField unitField       = new JTextField(tableModel.getValueAt(modelRow, COL_UNIT).toString());
        JTextField uppField        = new JTextField(tableModel.getValueAt(modelRow, COL_UPP).toString());
        JTextField bulkCostField   = new JTextField(tableModel.getValueAt(modelRow, COL_BULK_COST).toString());
        JTextField thresholdField  = new JTextField(tableModel.getValueAt(modelRow, COL_THRESHOLD).toString());

        // retail price is read-only — calculated from bulk cost
        JTextField retailPriceField = new JTextField(tableModel.getValueAt(modelRow, COL_RETAIL_PRICE).toString());
        retailPriceField.setEditable(false);
        retailPriceField.setBackground(UITheme.ROW_ALT);

        for (JTextField f : new JTextField[]{itemCodeField, nameField, pkgTypeField, unitField,
                uppField, bulkCostField, thresholdField, retailPriceField}) {
            UITheme.styleTextField(f);
        }
        retailPriceField.setBackground(UITheme.ROW_ALT);

        Object[] fields = {
            "Item ID (code):",       itemCodeField,
            "Description:",          nameField,
            "Package Type:",         pkgTypeField,
            "Unit:",                 unitField,
            "Units in a Pack:",      uppField,
            "Package Cost (£):",     bulkCostField,
            "Stock Limit (packs):",  thresholdField,
            "Retail Price (£):",     retailPriceField
        };

        int result = JOptionPane.showConfirmDialog(this, fields,
                "Edit Stock Item — " + name, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String newCode    = itemCodeField.getText().trim();
            String newName    = nameField.getText().trim();
            String newPkgType = pkgTypeField.getText().trim();
            String newUnit    = unitField.getText().trim();
            int    newUpp     = Integer.parseInt(uppField.getText().trim());
            double newCost    = Double.parseDouble(bulkCostField.getText().trim());
            int    newThresh  = Integer.parseInt(thresholdField.getText().trim());

            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Description cannot be blank.", "Blank input", JOptionPane.ERROR_MESSAGE);
                return;
            }

            updateStockItem(itemId, newCode, newName, newPkgType, newUnit, newUpp, newCost, newThresh);
            loadStockData();
            JOptionPane.showMessageDialog(this, name + " updated successfully.", "Updated", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please check your inputs — numbers only for units per pack, cost, and threshold.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    // direct SQL update — updates all spec fields
    public void updateStockItem(int itemId, String itemCode, String name, String packageType,
                                String unit, int unitsPerPack, double bulkCost, int threshold) {
        String sql = "UPDATE stock SET item_code = ?, name = ?, package_type = ?, unit = ?, " +
                     "units_per_pack = ?, bulk_cost = ?, low_stock_threshold = ? WHERE id = ?";
        try (Connection conn = db.DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, itemCode);
            stmt.setString(2, name);
            stmt.setString(3, packageType);
            stmt.setString(4, unit);
            stmt.setInt(5, unitsPerPack);
            stmt.setDouble(6, bulkCost);
            stmt.setInt(7, threshold);
            stmt.setInt(8, itemId);
            stmt.executeUpdate();
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException("failed to update stock item: " + ex.getMessage());
        }
    }

    // handles both increase and decrease — prompts for amount
    private void handleAdjustStock(boolean isIncrease) {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a stock item first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = stockTable.convertRowIndexToModel(selectedRow);
        int itemId   = (int) tableModel.getValueAt(modelRow, COL_ID);
        String itemName = (String) tableModel.getValueAt(modelRow, COL_NAME);
        String action   = isIncrease ? "increase" : "decrease";

        String input = JOptionPane.showInputDialog(this,
            "Enter amount to " + action + " for: " + itemName,
            action.substring(0, 1).toUpperCase() + action.substring(1) + " Stock",
            JOptionPane.PLAIN_MESSAGE);

        if (input == null || input.isBlank()) return;

        try {
            int amount = Integer.parseInt(input.trim());
            if (isIncrease) {
                stockService.increaseStock(itemId, amount);
            } else {
                stockService.decreaseStock(itemId, amount);
            }
            loadStockData();

            // warn if now low stock after decrease
            if (!isIncrease) {
                StockItem updated = stockService.getStockItem(itemId);
                if (updated.isLowStock()) {
                    JOptionPane.showMessageDialog(this,
                        "Warning: " + itemName + " is now low on stock (" + updated.getQuantity() + " remaining).",
                        "Low Stock Warning",
                        JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid whole number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        } catch (StockException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Stock Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // dialog to add a brand new stock item — all spec fields included
    private void handleAddItem() {
        JTextField itemCodeField  = new JTextField();
        JTextField nameField      = new JTextField();
        JTextField pkgTypeField   = new JTextField("Box");
        JTextField unitField      = new JTextField("Caps");
        JTextField uppField       = new JTextField("20");
        JTextField qtyField       = new JTextField();
        JTextField bulkCostField  = new JTextField();
        JTextField thresholdField = new JTextField("10");

        Object[] fields = {
            "Item ID (code):",      itemCodeField,
            "Description:",         nameField,
            "Package Type:",        pkgTypeField,
            "Unit:",                unitField,
            "Units in a Pack:",     uppField,
            "Quantity (packs):",    qtyField,
            "Package Cost (£):",    bulkCostField,
            "Stock Limit (packs):", thresholdField
        };

        int result = JOptionPane.showConfirmDialog(this, fields, "Add New Stock Item", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String itemCode   = itemCodeField.getText().trim();
            String name       = nameField.getText().trim();
            String pkgType    = pkgTypeField.getText().trim();
            String unit       = unitField.getText().trim();
            int    upp        = Integer.parseInt(uppField.getText().trim());
            int    qty        = Integer.parseInt(qtyField.getText().trim());
            double bulkCost   = Double.parseDouble(bulkCostField.getText().trim());
            int    threshold  = Integer.parseInt(thresholdField.getText().trim());

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Description cannot be blank.", "Blank input", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 100% markup and 0% VAT per spec
            StockItem newItem = new StockItem(0, itemCode, name, pkgType, unit, upp, qty, bulkCost, 1.0, 0.0, threshold);
            stockService.addStockItem(newItem);
            loadStockData();
            JOptionPane.showMessageDialog(this, name + " added successfully.", "Item Added", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please check your inputs — numbers only for units per pack, quantity, cost, and threshold.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        } catch (StockException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Stock Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // remove selected item after confirmation
    private void handleRemoveItem() {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a stock item first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = stockTable.convertRowIndexToModel(selectedRow);
        int itemId   = (int) tableModel.getValueAt(modelRow, COL_ID);
        String itemName = (String) tableModel.getValueAt(modelRow, COL_NAME);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove \"" + itemName + "\" from stock? This cannot be undone.",
            "Confirm Remove",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            stockService.removeStockItem(itemId);
            loadStockData();
            JOptionPane.showMessageDialog(this, itemName + " removed.", "Item Removed", JOptionPane.INFORMATION_MESSAGE);
        } catch (StockException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Stock Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
