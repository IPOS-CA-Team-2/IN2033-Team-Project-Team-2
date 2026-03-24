package ui;

import exception.StockException;
import model.StockItem;
import repository.StockRepositoryImpl;
import service.StockService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

// stock management screen — view all stock, update quantities, add/remove items
// accessible by pharmacist and admin from the dashboard
public class StockManagementUI extends JFrame {

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

        setTitle("IPOS-CA — Stock Management");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        loadStockData();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // top bar with title and low stock warning count
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        header.setBackground(new Color(44, 62, 80));

        JLabel title = new JLabel("Stock Management");
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        header.add(title, BorderLayout.WEST);
        return header;
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
        stockTable.setRowHeight(25);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        // highlight low stock rows in red
        stockTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                String status = (String) table.getValueAt(row, COL_STATUS);
                if (!isSelected) {
                    c.setBackground("LOW STOCK".equals(status) ? new Color(255, 220, 220) : Color.WHITE);
                    c.setForeground("LOW STOCK".equals(status) ? new Color(180, 0, 0) : Color.BLACK);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(stockTable);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // action buttons at the bottom
    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton increaseBtn = new JButton("Increase Stock");
        JButton decreaseBtn = new JButton("Decrease Stock");
        JButton addItemBtn = new JButton("Add New Item");
        JButton removeItemBtn = new JButton("Remove Item");
        JButton refreshBtn = new JButton("Refresh");

        increaseBtn.addActionListener(e -> handleAdjustStock(true));
        decreaseBtn.addActionListener(e -> handleAdjustStock(false));
        addItemBtn.addActionListener(e -> handleAddItem());
        removeItemBtn.addActionListener(e -> handleRemoveItem());
        refreshBtn.addActionListener(e -> loadStockData());

        panel.add(increaseBtn);
        panel.add(decreaseBtn);
        panel.add(addItemBtn);
        panel.add(removeItemBtn);
        panel.add(refreshBtn);
        return panel;
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
