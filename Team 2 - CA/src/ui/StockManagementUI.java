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

    // column indices for the table
    private static final int COL_ID        = 0;
    private static final int COL_NAME      = 1;
    private static final int COL_QTY       = 2;
    private static final int COL_BULK_COST = 3;
    private static final int COL_MARKUP    = 4;
    private static final int COL_PRICE     = 5;
    private static final int COL_VAT       = 6;
    private static final int COL_THRESHOLD = 7;
    private static final int COL_STATUS    = 8;

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
        // adding a search bar at the very top to search for name of drug or by id
        // returns everything that matches
        // copied from StaffManagementUI

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));


        // adds management text on the left of the top bar
        // add a filler cause it looks empty without it
        JLabel titleLabel = new JLabel("Stock Management");
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
        stockTable.setRowSorter(sorter);

        if (query.isBlank()) {
            sorter.setRowFilter(null);
        }
        else {
            String searchQuery = query.toLowerCase(); // convert to lowercasse
            int queryLength = searchQuery.length();
            // if equals to anything on first  or second column
            if (query.toLowerCase().startsWith("id: ") && queryLength > 4) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(4).trim(), 0));
            }
            else if (query.toLowerCase().startsWith("name: ") && queryLength > 6) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(6).trim(), 1));
            }
            else if (query.toLowerCase().startsWith("quantity: ") && queryLength > 10) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(10).trim(), 2));
            }
            else if (query.toLowerCase().startsWith("cost: ") && queryLength > 6) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(6).trim(), 3));
            }
            else if (query.toLowerCase().startsWith("markup: ") && queryLength > 8) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(8).trim(), 4));
            }
            else if (query.toLowerCase().startsWith("price: ") && queryLength > 7) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(7).trim(), 5));
            }
            else if (query.toLowerCase().startsWith("vat: ") && queryLength > 5) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(5).trim(), 6));
            }
            else if (query.toLowerCase().startsWith("threshold: ") && queryLength > 11) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(11).trim(), 7));
            }
            else if (query.toLowerCase().startsWith("status: ") && queryLength >8) {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery.substring(8).trim(), 8));
            }
            else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchQuery, 0, 1, 2, 3,4,5,6, 7, 8));
            }
        }
    }



    // table showing all stock items
    private JPanel buildTablePanel() {
        String[] columns = {"ID", "Name", "Quantity", "Bulk Cost (£)", "Markup", "Unit Price (£)", "VAT Rate", "Low Stock Threshold", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false; // all editing goes through buttons
            }
        };

        stockTable = new JTable(tableModel);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        UITheme.styleTable(stockTable);

        // highlight low stock rows in red — alternating rows as base for normal items
        stockTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String status = (String) table.getValueAt(row, COL_STATUS);
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

        JButton increaseBtn  = UITheme.primaryBtn("Increase Stock");
        JButton decreaseBtn  = UITheme.primaryBtn("Decrease Stock");
        JButton addItemBtn   = UITheme.successBtn("Add New Item");
        JButton removeItemBtn = UITheme.dangerBtn("Remove Item");
        JButton editBtn = UITheme.primaryBtn("Edit Details"); // add button to edit vat and shit
        JButton refreshBtn   = UITheme.secondaryBtn("Refresh");

        increaseBtn.addActionListener(e -> handleAdjustStock(true));
        decreaseBtn.addActionListener(e -> handleAdjustStock(false));
        addItemBtn.addActionListener(e -> handleAddItem());
        removeItemBtn.addActionListener(e -> handleRemoveItem());
        editBtn.addActionListener(e -> editBtn());
        refreshBtn.addActionListener(e -> loadStockData());


        panel.add(decreaseBtn);
        panel.add(addItemBtn);
        panel.add(removeItemBtn);
        panel.add(editBtn); // add edit button
        panel.add(refreshBtn);

        JLabel searchFilters = new JLabel("command followed by  \": \"        |        Search commands: id, name, quantity, cost, markup, price, vat, threshold, status");
        searchFilters.setFont(UITheme.FONT_SMALL);
        searchFilters.setForeground(UITheme.SECONDARY);
        panel.add(searchFilters);

        return panel;
    }


    private void editBtn() {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow < 0) { // no row selected
            JOptionPane.showMessageDialog(this, "Please select a stock item first", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = stockTable.convertRowIndexToModel(selectedRow);
        int itemId = (int) tableModel.getValueAt(modelRow, COL_ID);
        String name = (String) tableModel.getValueAt(modelRow, COL_NAME);

        // pre-fill fields with current values
        JTextField nameField = new JTextField((String) tableModel.getValueAt(modelRow, COL_NAME));
        JTextField markupField = new JTextField(tableModel.getValueAt(modelRow, COL_MARKUP).toString().replace("%", ""));
        JTextField bulkCostField = new JTextField(tableModel.getValueAt(modelRow, COL_BULK_COST).toString());
        JTextField vatField = new JTextField(tableModel.getValueAt(modelRow, COL_VAT).toString().replace("%", ""));
        JTextField thresholdField = new JTextField(tableModel.getValueAt(modelRow, COL_THRESHOLD).toString());

        // unit price calculated from markup x bulk price

        JTextField unitPriceField = new JTextField(tableModel.getValueAt(modelRow, COL_PRICE).toString());
        unitPriceField.setEditable(false); // cannot be edited
        unitPriceField.setBackground(UITheme.ROW_ALT);

        UITheme.styleTextField(nameField);
        UITheme.styleTextField(markupField);
        UITheme.styleTextField(bulkCostField);
        UITheme.styleTextField(vatField);
        UITheme.styleTextField(thresholdField);
        UITheme.styleTextField(unitPriceField);

        Object[] fields = {
                "Item Name:", nameField,
                "Bulk Cost:", bulkCostField,
                "Markup Rate:", markupField,
                "Unit Price: (Bulk Cost + Markup)", unitPriceField,
                "VAT Rate:", vatField,
                "Low Stock Threshold:", thresholdField
        };

        int result = JOptionPane.showConfirmDialog(this, fields,
                "Edit Stock Item — " + name, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String newName = nameField.getText().trim();
            double bulkCost = Double.parseDouble(bulkCostField.getText().trim());
            double markup = Double.parseDouble(markupField.getText().trim()) / 100.0;
            double vat = Double.parseDouble(vatField.getText().trim()) / 100.0;
            int threshold = Integer.parseInt(thresholdField.getText().trim());

            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name cannot be blank", "Blank input", JOptionPane.ERROR_MESSAGE);
                return;
            }

            updateStockItem(itemId, newName, bulkCost, markup, vat, threshold);
            loadStockData();
            JOptionPane.showMessageDialog(this, name + " updated successfully", "Updated", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please check your inputs, numbers only for cost, markup, VAT and threshold", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void updateStockItem(int itemId, String name, double bulkCost, double markup, double vat, int threshold) {
        String sql = "UPDATE stock SET name = ?, bulk_cost = ?, markup_rate = ?, vat_rate = ?, low_stock_threshold = ? WHERE id = ?";
        try (Connection conn = db.DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setDouble(2, bulkCost);
            stmt.setDouble(3, markup);
            stmt.setDouble(4, vat);
            stmt.setInt(5, threshold);
            stmt.setInt(6, itemId);
            stmt.executeUpdate();
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException("failed to update stock item: " + ex.getMessage());
        }
    }




    // pull fresh data from the db and repopulate the table
    private void loadStockData() {
        tableModel.setRowCount(0);
        List<StockItem> items = stockService.getAllStock();
        for (StockItem item : items) {
            String status = item.isLowStock() ? "LOW STOCK" : "OK";
            tableModel.addRow(new Object[]{
                item.getItemId(),
                item.getName(),
                item.getQuantity(),
                String.format("%.2f", item.getBulkCost()),
                String.format("%.0f%%", item.getMarkupRate() * 100),
                String.format("%.2f", item.getUnitPrice()),
                String.format("%.0f%%", item.getVatRate() * 100),
                item.getLowStockThreshold(),
                status
            });
        }
    }

    // handles both increase and decrease — prompts for amount
    private void handleAdjustStock(boolean isIncrease) {
        int selectedRow = stockTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a stock item first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int itemId = (int) tableModel.getValueAt(selectedRow, COL_ID);
        String itemName = (String) tableModel.getValueAt(selectedRow, COL_NAME);
        String action = isIncrease ? "increase" : "decrease";

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

    // dialog to add a brand new stock item
    private void handleAddItem() {
        JTextField nameField      = new JTextField();
        JTextField qtyField       = new JTextField();
        JTextField bulkCostField  = new JTextField();
        JTextField markupField    = new JTextField("30");
        JTextField vatField       = new JTextField("0");
        JTextField thresholdField = new JTextField("10");

        Object[] fields = {
            "Item Name:", nameField,
            "Quantity:", qtyField,
            "Bulk Cost (£):", bulkCostField,
            "Markup Rate (%):", markupField,
            "VAT Rate (%):", vatField,
            "Low Stock Threshold:", thresholdField
        };

        int result = JOptionPane.showConfirmDialog(this, fields, "Add New Stock Item", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            String name     = nameField.getText().trim();
            int qty         = Integer.parseInt(qtyField.getText().trim());
            double bulkCost = Double.parseDouble(bulkCostField.getText().trim());
            double markup   = Double.parseDouble(markupField.getText().trim()) / 100.0;
            double vat      = Double.parseDouble(vatField.getText().trim()) / 100.0;
            int threshold   = Integer.parseInt(thresholdField.getText().trim());

            StockItem newItem = new StockItem(0, name, qty, bulkCost, markup, vat, threshold);
            stockService.addStockItem(newItem);
            loadStockData();
            JOptionPane.showMessageDialog(this, name + " added successfully.", "Item Added", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please check your inputs — numbers only for quantity, price, VAT and threshold.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
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

        int itemId = (int) tableModel.getValueAt(selectedRow, COL_ID);
        String itemName = (String) tableModel.getValueAt(selectedRow, COL_NAME);

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
