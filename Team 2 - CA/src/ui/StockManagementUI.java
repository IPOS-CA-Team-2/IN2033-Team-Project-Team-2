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
        if (query == null || query.isBlank()) {
            sorter.setRowFilter(null);
        } else {
            // if equals to anything on first  or second column
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query, 0, 1));
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
        JButton refreshBtn   = UITheme.secondaryBtn("Refresh");

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
