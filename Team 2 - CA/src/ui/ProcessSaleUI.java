package ui;

import exception.SaleException;
import model.*;
import repository.SaleRepositoryImpl;
import repository.StockRepositoryImpl;
import service.SaleService;
import service.StockService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// sale screen for pharmacists — pick items, set payment, process sale, show receipt
public class ProcessSaleUI extends JFrame {

    private final SaleService saleService;
    private final User currentUser;

    // stock catalogue table (left)
    private DefaultTableModel catalogueModel;
    private JTable catalogueTable;
    private JTextField searchField;

    // basket table (right)
    private DefaultTableModel basketModel;
    private JTable basketTable;
    private List<SaleLine> basketLines = new ArrayList<>();

    // payment section
    private JRadioButton cashBtn, creditBtn, debitBtn;
    private JPanel cardPanel;
    private JTextField cardTypeField, firstFourField, lastFourField, expiryField;
    private JTextField discountField;

    // totals label
    private JLabel totalLabel;

    private List<StockItem> allStock = new ArrayList<>();

    public ProcessSaleUI(User currentUser) {
        this.currentUser = currentUser;
        this.saleService = new SaleService(
            new StockService(new StockRepositoryImpl()),
            new SaleRepositoryImpl()
        );

        setTitle("IPOS-CA — Process Sale");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildPaymentPanel(), BorderLayout.SOUTH);

        loadCatalogue();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(44, 62, 80));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("Process Sale — " + currentUser.getName());
        title.setFont(new Font("Arial", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.WEST);
        return panel;
    }

    // left: stock catalogue, right: basket
    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        panel.add(buildCataloguePanel());
        panel.add(buildBasketPanel());
        return panel;
    }

    private JPanel buildCataloguePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Stock Catalogue"));

        // search bar
        searchField = new JTextField();
        searchField.setToolTipText("Search by name...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterCatalogue(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterCatalogue(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterCatalogue(); }
        });

        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        String[] cols = {"ID", "Name", "In Stock", "Unit Price (£)"};
        catalogueModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        catalogueTable = new JTable(catalogueModel);
        catalogueTable.setRowHeight(24);
        catalogueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton addBtn = new JButton("Add to Sale →");
        addBtn.addActionListener(e -> handleAddToBasket());
        // double-click also adds
        catalogueTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleAddToBasket();
            }
        });

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(catalogueTable), BorderLayout.CENTER);
        panel.add(addBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBasketPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Current Sale"));

        String[] cols = {"Item", "Qty", "Unit Price (£)", "Line Total (£)"};
        basketModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        basketTable = new JTable(basketModel);
        basketTable.setRowHeight(24);
        basketTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton removeBtn = new JButton("← Remove Item");
        removeBtn.addActionListener(e -> handleRemoveFromBasket());

        totalLabel = new JLabel("Total: £0.00");
        totalLabel.setFont(new Font("Arial", Font.BOLD, 14));
        totalLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(removeBtn, BorderLayout.WEST);
        bottomPanel.add(totalLabel, BorderLayout.EAST);

        panel.add(new JScrollPane(basketTable), BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPaymentPanel() {
        JPanel outer = new JPanel(new BorderLayout(10, 5));
        outer.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        // discount + payment method row
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));

        discountField = new JTextField("0", 5);
        topRow.add(new JLabel("Discount (%):"));
        topRow.add(discountField);

        topRow.add(new JSeparator(SwingConstants.VERTICAL));
        topRow.add(new JLabel("Payment:"));

        cashBtn   = new JRadioButton("Cash", true);
        creditBtn = new JRadioButton("Credit Card");
        debitBtn  = new JRadioButton("Debit Card");
        ButtonGroup group = new ButtonGroup();
        group.add(cashBtn); group.add(creditBtn); group.add(debitBtn);

        cashBtn.addActionListener(e -> cardPanel.setVisible(false));
        creditBtn.addActionListener(e -> cardPanel.setVisible(true));
        debitBtn.addActionListener(e -> cardPanel.setVisible(true));

        topRow.add(cashBtn); topRow.add(creditBtn); topRow.add(debitBtn);

        // card details row — hidden by default
        cardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        cardTypeField  = new JTextField(8);
        firstFourField = new JTextField(4);
        lastFourField  = new JTextField(4);
        expiryField    = new JTextField(5);

        cardPanel.add(new JLabel("Card Type:")); cardPanel.add(cardTypeField);
        cardPanel.add(new JLabel("First 4:")); cardPanel.add(firstFourField);
        cardPanel.add(new JLabel("Last 4:")); cardPanel.add(lastFourField);
        cardPanel.add(new JLabel("Expiry:")); cardPanel.add(expiryField);
        cardPanel.setVisible(false);

        JButton processBtn = new JButton("Process Sale");
        processBtn.setFont(new Font("Arial", Font.BOLD, 14));
        processBtn.setBackground(new Color(39, 174, 96));
        processBtn.setForeground(Color.WHITE);
        processBtn.addActionListener(e -> handleProcessSale());

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(topRow, BorderLayout.NORTH);
        leftPanel.add(cardPanel, BorderLayout.CENTER);

        outer.add(leftPanel, BorderLayout.CENTER);
        outer.add(processBtn, BorderLayout.EAST);
        return outer;
    }

    // load all stock into the catalogue table
    private void loadCatalogue() {
        StockService stockService = new StockService(new StockRepositoryImpl());
        allStock = stockService.getAllStock();
        populateCatalogue(allStock);
    }

    private void populateCatalogue(List<StockItem> items) {
        catalogueModel.setRowCount(0);
        for (StockItem item : items) {
            catalogueModel.addRow(new Object[]{
                item.getItemId(),
                item.getName(),
                item.getQuantity(),
                String.format("%.2f", item.getUnitPrice())
            });
        }
    }

    private void filterCatalogue() {
        String query = searchField.getText().toLowerCase().trim();
        if (query.isEmpty()) {
            populateCatalogue(allStock);
        } else {
            List<StockItem> filtered = new ArrayList<>();
            for (StockItem item : allStock) {
                if (item.getName().toLowerCase().contains(query)) filtered.add(item);
            }
            populateCatalogue(filtered);
        }
    }

    // prompt for quantity and add selected item to basket
    private void handleAddToBasket() {
        int row = catalogueTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an item first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int itemId = (int) catalogueModel.getValueAt(row, 0);
        String itemName = (String) catalogueModel.getValueAt(row, 1);
        int inStock = (int) catalogueModel.getValueAt(row, 2);
        double unitPrice = Double.parseDouble(catalogueModel.getValueAt(row, 3).toString());

        // find the stock item to get vat rate
        StockItem stockItem = allStock.stream().filter(i -> i.getItemId() == itemId).findFirst().orElse(null);
        if (stockItem == null) return;

        String input = JOptionPane.showInputDialog(this,
            "Quantity for " + itemName + " (max " + inStock + "):", "1");
        if (input == null || input.isBlank()) return;

        try {
            int qty = Integer.parseInt(input.trim());
            if (qty <= 0 || qty > inStock) {
                JOptionPane.showMessageDialog(this, "Enter a quantity between 1 and " + inStock + ".", "Invalid Quantity", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // check if already in basket — update quantity instead of adding duplicate
            for (int i = 0; i < basketLines.size(); i++) {
                if (basketLines.get(i).getItemId() == itemId) {
                    SaleLine existing = basketLines.get(i);
                    int newQty = existing.getQuantity() + qty;
                    if (newQty > inStock) {
                        JOptionPane.showMessageDialog(this, "Total quantity would exceed stock.", "Too Many", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    basketLines.set(i, new SaleLine(itemId, itemName, newQty, unitPrice, stockItem.getVatRate()));
                    refreshBasket();
                    return;
                }
            }

            basketLines.add(new SaleLine(itemId, itemName, qty, unitPrice, stockItem.getVatRate()));
            refreshBasket();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid whole number.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRemoveFromBasket() {
        int row = basketTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select an item to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        basketLines.remove(row);
        refreshBasket();
    }

    // rebuild basket table and recalculate total
    private void refreshBasket() {
        basketModel.setRowCount(0);
        double total = 0;
        for (SaleLine line : basketLines) {
            basketModel.addRow(new Object[]{
                line.getItemName(),
                line.getQuantity(),
                String.format("%.2f", line.getUnitPrice()),
                String.format("%.2f", line.getLineTotalIncVat())
            });
            total += line.getLineTotalIncVat();
        }

        // apply discount to displayed total
        try {
            double discount = Double.parseDouble(discountField.getText().trim()) / 100.0;
            total = total - (total * discount);
        } catch (NumberFormatException ignored) {}

        totalLabel.setText(String.format("Total: £%.2f", total));
    }

    // validate inputs, call SaleService, show receipt
    private void handleProcessSale() {
        if (basketLines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add items before processing.", "Empty Sale", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // parse discount
        double discountPercent = 0;
        try {
            discountPercent = Double.parseDouble(discountField.getText().trim()) / 100.0;
            if (discountPercent < 0 || discountPercent > 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Discount must be a number between 0 and 100.", "Invalid Discount", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // determine payment method and card details
        PaymentMethod method;
        CardDetails cardDetails = null;

        if (cashBtn.isSelected()) {
            method = PaymentMethod.CASH;
        } else {
            method = creditBtn.isSelected() ? PaymentMethod.CREDIT_CARD : PaymentMethod.DEBIT_CARD;
            String type  = cardTypeField.getText().trim();
            String first = firstFourField.getText().trim();
            String last  = lastFourField.getText().trim();
            String expiry= expiryField.getText().trim();

            if (type.isEmpty() || first.length() != 4 || last.length() != 4 || expiry.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter all card details (card type, first 4, last 4 digits, expiry).", "Missing Card Details", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                cardDetails = new CardDetails(type, first, last, expiry);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid Card Details", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // process the sale
        try {
            Receipt receipt = saleService.processSale(
                0, basketLines, discountPercent, method, cardDetails, currentUser.getName()
            );
            showReceipt(receipt);
            basketLines.clear();
            refreshBasket();
            loadCatalogue(); // refresh stock levels
        } catch (SaleException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Sale Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // display the receipt in a scrollable dialog
    private void showReceipt(Receipt receipt) {
        JTextArea text = new JTextArea(receipt.format());
        text.setFont(new Font("Monospaced", Font.PLAIN, 12));
        text.setEditable(false);

        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(500, 400));

        JOptionPane.showMessageDialog(this, scroll, "Receipt — " + receipt.getReceiptNumber(), JOptionPane.PLAIN_MESSAGE);
    }
}
