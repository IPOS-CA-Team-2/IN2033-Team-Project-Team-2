package ui;

import exception.SaleException;
import model.*;
import repository.*;
import service.AccountService;
import service.SaleService;
import service.StockService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// sale screen for pharmacists — pick items, set payment, process sale, show receipt
// supports both walk-in cash customers and account holders
public class ProcessSaleUI extends JFrame {

    private final SaleService saleService;
    private final AccountService accountService;
    private final User currentUser;

    // stock catalogue table (left)
    private DefaultTableModel catalogueModel;
    private JTable catalogueTable;
    private JTextField searchField;

    // basket table (right)
    private DefaultTableModel basketModel;
    private JTable basketTable;
    private List<SaleLine> basketLines = new ArrayList<>();

    // account holder section
    private JTextField accountNumberField;
    private JLabel customerInfoLabel;
    private Customer selectedCustomer = null;  // null = walk-in customer

    // payment section
    private JRadioButton cashBtn, creditBtn, debitBtn;
    private JPanel cardPanel;
    private JTextField cardTypeField, firstFourField, lastFourField, expiryField;
    private JTextField discountField;

    private JLabel totalLabel;
    private List<StockItem> allStock = new ArrayList<>();

    public ProcessSaleUI(User currentUser) {
        this.currentUser = currentUser;
        CustomerRepositoryImpl customerRepo = new CustomerRepositoryImpl();
        this.accountService = new AccountService(customerRepo);
        this.saleService = new SaleService(
            new StockService(new StockRepositoryImpl()),
            new SaleRepositoryImpl(),
            accountService
        );

        setTitle("IPOS-CA — Process Sale");
        setSize(1150, 730);
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

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.add(buildAccountPanel(), BorderLayout.NORTH);

        JPanel splitPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        splitPanel.add(buildCataloguePanel());
        splitPanel.add(buildBasketPanel());
        panel.add(splitPanel, BorderLayout.CENTER);
        return panel;
    }

    // account holder lookup bar — optional, leave blank for walk-in customers
    private JPanel buildAccountPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Account Holder (leave blank for walk-in customer)"));

        accountNumberField = new JTextField(12);
        JButton lookupBtn = new JButton("Lookup");
        customerInfoLabel = new JLabel("No account selected — walk-in sale");
        customerInfoLabel.setFont(new Font("Arial", Font.ITALIC, 12));

        lookupBtn.addActionListener(e -> handleAccountLookup());
        accountNumberField.addActionListener(e -> handleAccountLookup()); // enter key

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            selectedCustomer = null;
            accountNumberField.setText("");
            customerInfoLabel.setText("No account selected — walk-in sale");
            customerInfoLabel.setForeground(Color.DARK_GRAY);
            discountField.setText("0");
            discountField.setEditable(true);
        });

        panel.add(new JLabel("Account No:"));
        panel.add(accountNumberField);
        panel.add(lookupBtn);
        panel.add(clearBtn);
        panel.add(customerInfoLabel);
        return panel;
    }

    private JPanel buildCataloguePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Stock Catalogue"));

        searchField = new JTextField();
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
        catalogueTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) handleAddToBasket();
            }
        });

        JButton addBtn = new JButton("Add to Sale →");
        addBtn.addActionListener(e -> handleAddToBasket());

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

        cardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        cardTypeField  = new JTextField(8);
        firstFourField = new JTextField(4);
        lastFourField  = new JTextField(4);
        expiryField    = new JTextField(5);
        cardPanel.add(new JLabel("Card Type:")); cardPanel.add(cardTypeField);
        cardPanel.add(new JLabel("First 4:"));  cardPanel.add(firstFourField);
        cardPanel.add(new JLabel("Last 4:"));   cardPanel.add(lastFourField);
        cardPanel.add(new JLabel("Expiry:"));   cardPanel.add(expiryField);
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

    // look up account holder by account number
    private void handleAccountLookup() {
        String accountNo = accountNumberField.getText().trim();
        if (accountNo.isEmpty()) return;

        Customer customer = accountService.getCustomerRepository().findByAccountNumber(accountNo) instanceof CustomerRepository
            ? null
            : ((CustomerRepositoryImpl) accountService.getCustomerRepository()).findByAccountNumber(accountNo);

        // direct lookup via repository
        CustomerRepositoryImpl repo = new CustomerRepositoryImpl();
        customer = repo.findByAccountNumber(accountNo);

        if (customer == null) {
            customerInfoLabel.setText("Account not found: " + accountNo);
            customerInfoLabel.setForeground(Color.RED);
            selectedCustomer = null;
            return;
        }

        selectedCustomer = customer;

        // show status — warn if suspended or blocked if in default
        String statusInfo = String.format("%s | Balance: £%.2f / £%.2f | Discount: %s",
            customer.getStatus(),
            customer.getCurrentBalance(),
            customer.getCreditLimit(),
            customer.getDiscountType() == DiscountType.FIXED
                ? String.format("Fixed %.0f%%", customer.getFixedDiscountRate() * 100)
                : customer.getDiscountType().toString()
        );

        customerInfoLabel.setText(customer.getName() + " (" + customer.getAccountNumber() + ") — " + statusInfo);

        if (customer.getStatus() == AccountStatus.IN_DEFAULT) {
            customerInfoLabel.setForeground(Color.RED);
        } else if (customer.getStatus() == AccountStatus.SUSPENDED) {
            customerInfoLabel.setForeground(new Color(200, 100, 0));
        } else {
            customerInfoLabel.setForeground(new Color(0, 120, 0));
        }

        // apply fixed discount automatically — lock the discount field
        if (customer.getDiscountType() == DiscountType.FIXED) {
            discountField.setText(String.format("%.0f", customer.getFixedDiscountRate() * 100));
            discountField.setEditable(false);
        } else {
            discountField.setText("0");
            discountField.setEditable(false); // flexible handled at month end
        }

        refreshBasket();
    }

    private void loadCatalogue() {
        allStock = new StockService(new StockRepositoryImpl()).getAllStock();
        populateCatalogue(allStock);
    }

    private void populateCatalogue(List<StockItem> items) {
        catalogueModel.setRowCount(0);
        for (StockItem item : items) {
            catalogueModel.addRow(new Object[]{
                item.getItemId(), item.getName(),
                item.getQuantity(), String.format("%.2f", item.getUnitPrice())
            });
        }
    }

    private void filterCatalogue() {
        String query = searchField.getText().toLowerCase().trim();
        if (query.isEmpty()) { populateCatalogue(allStock); return; }
        List<StockItem> filtered = new ArrayList<>();
        for (StockItem item : allStock)
            if (item.getName().toLowerCase().contains(query)) filtered.add(item);
        populateCatalogue(filtered);
    }

    private void handleAddToBasket() {
        int row = catalogueTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select an item first.", "No Selection", JOptionPane.WARNING_MESSAGE); return; }

        int itemId = (int) catalogueModel.getValueAt(row, 0);
        String itemName = (String) catalogueModel.getValueAt(row, 1);
        int inStock = (int) catalogueModel.getValueAt(row, 2);
        double unitPrice = Double.parseDouble(catalogueModel.getValueAt(row, 3).toString());
        StockItem stockItem = allStock.stream().filter(i -> i.getItemId() == itemId).findFirst().orElse(null);
        if (stockItem == null) return;

        String input = JOptionPane.showInputDialog(this, "Quantity for " + itemName + " (max " + inStock + "):", "1");
        if (input == null || input.isBlank()) return;

        try {
            int qty = Integer.parseInt(input.trim());
            if (qty <= 0 || qty > inStock) {
                JOptionPane.showMessageDialog(this, "Enter a quantity between 1 and " + inStock + ".", "Invalid Quantity", JOptionPane.ERROR_MESSAGE);
                return;
            }
            for (int i = 0; i < basketLines.size(); i++) {
                if (basketLines.get(i).getItemId() == itemId) {
                    int newQty = basketLines.get(i).getQuantity() + qty;
                    if (newQty > inStock) { JOptionPane.showMessageDialog(this, "Total quantity would exceed stock.", "Too Many", JOptionPane.WARNING_MESSAGE); return; }
                    basketLines.set(i, new SaleLine(itemId, itemName, newQty, unitPrice, stockItem.getVatRate()));
                    refreshBasket(); return;
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
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select an item to remove.", "No Selection", JOptionPane.WARNING_MESSAGE); return; }
        basketLines.remove(row);
        refreshBasket();
    }

    private void refreshBasket() {
        basketModel.setRowCount(0);
        double total = 0;
        double discountPct = 0;
        try { discountPct = Double.parseDouble(discountField.getText().trim()) / 100.0; } catch (NumberFormatException ignored) {}

        for (SaleLine line : basketLines) {
            double lineTotal = line.getLineTotalIncVat();
            basketModel.addRow(new Object[]{
                line.getItemName(), line.getQuantity(),
                String.format("%.2f", line.getUnitPrice()),
                String.format("%.2f", lineTotal)
            });
            total += lineTotal;
        }
        total = total - (total * discountPct);
        totalLabel.setText(String.format("Total: £%.2f", total));
    }

    private void handleProcessSale() {
        if (basketLines.isEmpty()) { JOptionPane.showMessageDialog(this, "Add items before processing.", "Empty Sale", JOptionPane.WARNING_MESSAGE); return; }

        double discountPercent = 0;
        try {
            discountPercent = Double.parseDouble(discountField.getText().trim()) / 100.0;
            if (discountPercent < 0 || discountPercent > 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Discount must be 0-100.", "Invalid Discount", JOptionPane.ERROR_MESSAGE); return;
        }

        PaymentMethod method;
        CardDetails cardDetails = null;
        if (cashBtn.isSelected()) {
            method = PaymentMethod.CASH;
        } else {
            method = creditBtn.isSelected() ? PaymentMethod.CREDIT_CARD : PaymentMethod.DEBIT_CARD;
            String type = cardTypeField.getText().trim();
            String first = firstFourField.getText().trim();
            String last = lastFourField.getText().trim();
            String expiry = expiryField.getText().trim();
            if (type.isEmpty() || first.length() != 4 || last.length() != 4 || expiry.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter all card details.", "Missing Card Details", JOptionPane.ERROR_MESSAGE); return;
            }
            try { cardDetails = new CardDetails(type, first, last, expiry); }
            catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid Card Details", JOptionPane.ERROR_MESSAGE); return; }
        }

        // account holders cannot pay cash per brief spec
        if (selectedCustomer != null && method == PaymentMethod.CASH) {
            JOptionPane.showMessageDialog(this, "Account holders must pay by card (credit or debit).", "Invalid Payment", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Receipt receipt;
            if (selectedCustomer != null) {
                receipt = saleService.processSaleForAccount(selectedCustomer, basketLines, method, cardDetails, currentUser.getName());
            } else {
                receipt = saleService.processSale(0, basketLines, discountPercent, method, cardDetails, currentUser.getName());
            }
            showReceipt(receipt);
            basketLines.clear();
            selectedCustomer = null;
            accountNumberField.setText("");
            customerInfoLabel.setText("No account selected — walk-in sale");
            customerInfoLabel.setForeground(Color.DARK_GRAY);
            discountField.setText("0");
            discountField.setEditable(true);
            refreshBasket();
            loadCatalogue();
        } catch (SaleException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Sale Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showReceipt(Receipt receipt) {
        JTextArea text = new JTextArea(receipt.format());
        text.setFont(new Font("Monospaced", Font.PLAIN, 12));
        text.setEditable(false);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(520, 420));
        JOptionPane.showMessageDialog(this, scroll, "Receipt — " + receipt.getReceiptNumber(), JOptionPane.PLAIN_MESSAGE);
    }
}
