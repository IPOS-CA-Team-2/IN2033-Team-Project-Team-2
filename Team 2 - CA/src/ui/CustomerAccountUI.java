package ui;

import model.*;
import repository.CustomerRepositoryImpl;
import service.AccountService;
import service.ReminderService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

// account holder management screen
// pharmacists can view/add/edit accounts and record payments
// managers also get: generate reminders, restore in-default accounts
public class CustomerAccountUI extends JFrame {

    private final User currentUser;
    private final CustomerRepositoryImpl customerRepo;
    private final AccountService accountService;
    private final ReminderService reminderService;

    private DefaultTableModel tableModel;
    private JTable customerTable;
    private JButton restoreBtn;

    private static final int COL_ID      = 0;
    private static final int COL_ACCOUNT = 1;
    private static final int COL_NAME    = 2;
    private static final int COL_BALANCE = 3;
    private static final int COL_LIMIT   = 4;
    private static final int COL_STATUS  = 5;
    private static final int COL_REM1    = 6;
    private static final int COL_REM2    = 7;

    public CustomerAccountUI(User user) {
        this.currentUser = user;
        this.customerRepo = new CustomerRepositoryImpl();
        this.accountService = new AccountService(customerRepo);
        this.reminderService = new ReminderService(customerRepo);

        setTitle("IPOS-CA — Customer Accounts");
        setSize(980, 580);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        UITheme.applyFrameBackground(this);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);

        loadCustomerData(null);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // header with title left, search controls right
    private JPanel buildHeader() {
        JTextField searchField = new JTextField(14);
        JButton searchBtn = UITheme.secondaryBtn("Search");
        JButton clearBtn  = UITheme.secondaryBtn("Clear");

        searchBtn.addActionListener(e -> loadCustomerData(searchField.getText().trim()));
        clearBtn.addActionListener(e -> { searchField.setText(""); loadCustomerData(null); });
        searchField.addActionListener(e -> loadCustomerData(searchField.getText().trim()));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(Color.WHITE);
        searchLabel.setFont(UITheme.FONT_BODY);
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(clearBtn);

        return UITheme.createHeaderPanel("Customer Accounts", searchPanel);
    }

    // table of all account holders with status colour coding
    private JPanel buildTablePanel() {
        String[] columns = {"ID", "Account #", "Name", "Balance (£)", "Credit Limit (£)", "Status", "1st Reminder", "2nd Reminder"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        customerTable = new JTable(tableModel);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerTable.getColumnModel().getColumn(COL_ID).setMaxWidth(40);
        UITheme.styleTable(customerTable);

        // colour rows by account status — alternating rows as base for normal accounts
        customerTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    String status = (String) table.getValueAt(row, COL_STATUS);
                    if ("IN_DEFAULT".equals(status)) {
                        c.setBackground(new Color(255, 200, 200));
                    } else if ("SUSPENDED".equals(status)) {
                        c.setBackground(new Color(255, 243, 205));
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                    }
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        });

        // enable restore button only when an IN_DEFAULT row is selected
        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && restoreBtn != null) {
                int row = customerTable.getSelectedRow();
                restoreBtn.setEnabled(row != -1 && "IN_DEFAULT".equals(tableModel.getValueAt(row, COL_STATUS)));
            }
        });

        JScrollPane scrollPane = new JScrollPane(customerTable);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // action buttons — manager gets extra restore and generate reminders
    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        panel.setBackground(UITheme.LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton addBtn     = UITheme.successBtn("Add Account Holder");
        JButton editBtn    = UITheme.primaryBtn("Edit Details");
        JButton paymentBtn = UITheme.primaryBtn("Record Payment");
        JButton refreshBtn = UITheme.secondaryBtn("Refresh");

        addBtn.addActionListener(e -> handleAddCustomer());
        editBtn.addActionListener(e -> handleEditCustomer());
        paymentBtn.addActionListener(e -> handleRecordPayment());
        refreshBtn.addActionListener(e -> loadCustomerData(null));

        panel.add(addBtn);
        panel.add(editBtn);
        panel.add(paymentBtn);
        panel.add(refreshBtn);

        if ("Manager".equals(currentUser.getRole())) {
            JButton generateBtn = UITheme.primaryBtn("Generate Reminders");
            restoreBtn = UITheme.dangerBtn("Restore Account");
            restoreBtn.setEnabled(false);

            generateBtn.addActionListener(e -> handleGenerateReminders());
            restoreBtn.addActionListener(e -> handleRestoreAccount());

            panel.add(generateBtn);
            panel.add(restoreBtn);
        }

        return panel;
    }

    // loads all customers or filters by name / account number (case insensitive)
    private void loadCustomerData(String filter) {
        tableModel.setRowCount(0);
        List<Customer> customers = customerRepo.findAll();

        for (Customer c : customers) {
            if (filter != null && !filter.isEmpty()) {
                boolean matchName  = c.getName().toLowerCase().contains(filter.toLowerCase());
                boolean matchAcct  = c.getAccountNumber().toLowerCase().contains(filter.toLowerCase());
                if (!matchName && !matchAcct) continue;
            }
            tableModel.addRow(new Object[]{
                c.getCustomerId(),
                c.getAccountNumber(),
                c.getName(),
                String.format("%.2f", c.getCurrentBalance()),
                String.format("%.2f", c.getCreditLimit()),
                c.getStatus().name(),
                c.getStatus1stReminder(),
                c.getStatus2ndReminder()
            });
        }
    }

    // returns the Customer object for the selected table row, or null + warning if none
    private Customer getSelectedCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select an account holder first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int id = (int) tableModel.getValueAt(row, COL_ID);
        return customerRepo.findById(id);
    }

    // dialog to add a new account holder — account number is auto-generated
    private void handleAddCustomer() {
        // generate the next account number before showing the form
        String generatedAcctNum = customerRepo.generateAccountNumber();

        JTextField nameField    = new JTextField();
        JTextField addressField = new JTextField();
        JTextField limitField   = new JTextField("500.00");
        JComboBox<DiscountType> discountCombo = new JComboBox<>(DiscountType.values());
        JTextField rateField    = new JTextField("0");
        rateField.setEnabled(false);

        discountCombo.addActionListener(e ->
            rateField.setEnabled(discountCombo.getSelectedItem() == DiscountType.FIXED));

        // show the auto-generated number as a read-only label so staff can note it down
        JLabel acctLabel = new JLabel(generatedAcctNum);
        acctLabel.setFont(new Font("Arial", Font.BOLD, 13));
        acctLabel.setForeground(UITheme.PRIMARY);

        Object[] fields = {
            "Account Number (auto):", acctLabel,
            "Name:", nameField,
            "Address:", addressField,
            "Credit Limit (£):", limitField,
            "Discount Type:", discountCombo,
            "Fixed Discount Rate (%):", rateField
        };

        if (JOptionPane.showConfirmDialog(this, fields, "Add Account Holder", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        try {
            DiscountType dtype = (DiscountType) discountCombo.getSelectedItem();
            double rate = dtype == DiscountType.FIXED ? Double.parseDouble(rateField.getText().trim()) / 100.0 : 0.0;
            Customer newCustomer = new Customer(
                nameField.getText().trim(),
                addressField.getText().trim(),
                generatedAcctNum,
                Double.parseDouble(limitField.getText().trim()),
                dtype, rate
            );
            int id = customerRepo.save(newCustomer);
            if (id > 0) {
                loadCustomerData(null);
                JOptionPane.showMessageDialog(this,
                    "Account holder added.\nAccount number: " + generatedAcctNum,
                    "Added", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to create account.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number in credit limit or rate field.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    // dialog to edit an existing account holder's details
    private void handleEditCustomer() {
        Customer c = getSelectedCustomer();
        if (c == null) return;

        JTextField nameField    = new JTextField(c.getName());
        JTextField addressField = new JTextField(c.getAddress() != null ? c.getAddress() : "");
        JTextField limitField   = new JTextField(String.format("%.2f", c.getCreditLimit()));
        JComboBox<DiscountType> discountCombo = new JComboBox<>(DiscountType.values());
        discountCombo.setSelectedItem(c.getDiscountType());
        JTextField rateField = new JTextField(String.format("%.0f", c.getFixedDiscountRate() * 100));
        rateField.setEnabled(c.getDiscountType() == DiscountType.FIXED);

        discountCombo.addActionListener(e ->
            rateField.setEnabled(discountCombo.getSelectedItem() == DiscountType.FIXED));

        Object[] fields = {
            "Name:", nameField,
            "Address:", addressField,
            "Credit Limit (£):", limitField,
            "Discount Type:", discountCombo,
            "Fixed Discount Rate (%):", rateField
        };

        if (JOptionPane.showConfirmDialog(this, fields, "Edit Account Holder", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        try {
            DiscountType dtype = (DiscountType) discountCombo.getSelectedItem();
            double rate = dtype == DiscountType.FIXED ? Double.parseDouble(rateField.getText().trim()) / 100.0 : 0.0;
            Customer updated = new Customer(
                c.getCustomerId(), nameField.getText().trim(), addressField.getText().trim(),
                c.getAccountNumber(), Double.parseDouble(limitField.getText().trim()),
                c.getCurrentBalance(), c.getMonthlySpend(),
                dtype, rate, c.getStatus(),
                c.getStatus1stReminder(), c.getStatus2ndReminder(),
                c.getDate1stReminder(), c.getDate2ndReminder(), c.getStatementDate()
            );
            if (customerRepo.update(updated)) {
                loadCustomerData(null);
                JOptionPane.showMessageDialog(this, "Account holder updated.", "Updated", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Update failed.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number in credit limit or rate field.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    // prompt for payment amount and apply it to the selected account
    private void handleRecordPayment() {
        Customer c = getSelectedCustomer();
        if (c == null) return;

        String input = JOptionPane.showInputDialog(this,
            "Account: " + c.getAccountNumber() + " — " + c.getName() +
            "\nCurrent balance: £" + String.format("%.2f", c.getCurrentBalance()) +
            "\n\nEnter payment amount (£):",
            "Record Payment", JOptionPane.PLAIN_MESSAGE);

        if (input == null || input.isBlank()) return;

        try {
            double amount = Double.parseDouble(input.trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Payment must be greater than zero.", "Invalid", JOptionPane.WARNING_MESSAGE);
                return;
            }
            accountService.processPayment(c, amount);
            loadCustomerData(null);
            JOptionPane.showMessageDialog(this,
                "Payment of £" + String.format("%.2f", amount) + " recorded for " + c.getName() + ".",
                "Payment Recorded", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid amount.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
        }
    }

    // generate all due reminders, show summary, offer to view letters (manager only)
    private void handleGenerateReminders() {
        List<Reminder> reminders = reminderService.generateDueReminders();

        if (reminders.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No reminders are currently due.", "Generate Reminders", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append(reminders.size()).append(" reminder(s) generated:\n\n");
        for (Reminder r : reminders) {
            summary.append("• ").append(r.getType() == Reminder.Type.FIRST ? "1st" : "2nd")
                   .append(" reminder — ").append(r.getCustomerName())
                   .append(" (").append(r.getAccountNumber()).append(")\n");
        }
        summary.append("\nWould you like to view the letter(s)?");

        int view = JOptionPane.showConfirmDialog(this, summary.toString(), "Reminders Generated",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

        if (view == JOptionPane.YES_OPTION) {
            for (Reminder r : reminders) {
                JTextArea area = new JTextArea(r.getLetterText());
                area.setFont(new Font("Monospaced", Font.PLAIN, 12));
                area.setEditable(false);
                area.setColumns(60);
                area.setRows(25);
                JOptionPane.showMessageDialog(this, new JScrollPane(area),
                    (r.getType() == Reminder.Type.FIRST ? "1st" : "2nd") + " Reminder — " + r.getCustomerName(),
                    JOptionPane.PLAIN_MESSAGE);
            }
        }

        loadCustomerData(null);
    }

    // restore a selected IN_DEFAULT account to normal after manager confirmation
    private void handleRestoreAccount() {
        Customer c = getSelectedCustomer();
        if (c == null) return;

        if (c.getStatus() != AccountStatus.IN_DEFAULT) {
            JOptionPane.showMessageDialog(this, "Only In Default accounts can be restored.", "Not Applicable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Restore " + c.getName() + " (" + c.getAccountNumber() + ") to Normal status?\n" +
            "Outstanding balance of £" + String.format("%.2f", c.getCurrentBalance()) + " will remain.",
            "Confirm Restore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        if (accountService.restoreToNormal(c)) {
            loadCustomerData(null);
            JOptionPane.showMessageDialog(this,
                c.getName() + "'s account has been restored to Normal.",
                "Account Restored", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to restore account.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
