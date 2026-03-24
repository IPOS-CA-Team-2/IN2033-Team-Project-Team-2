package ui;

import app.Main;
import model.User;

import javax.swing.*;
import java.awt.*;

/**
 * Role-based dashboard shown after successful login.
 * Displays different menu options depending on whether the user is
 * an Admin, Pharmacist, or Manager.
 */
public class Dashboard extends JFrame {

    private final User currentUser;

    public Dashboard(User user) {
        this.currentUser = user;

        setTitle("IPOS-CA — " + user.getRole() + " Dashboard");
        setSize(Main.SCREEN_WIDTH, Main.SCREEN_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        header.setBackground(new Color(44, 62, 80));

        JLabel welcome = new JLabel("Welcome, " + user.getName());
        welcome.setFont(new Font("Arial", Font.BOLD, 18));
        welcome.setForeground(Color.WHITE);

        JLabel roleLabel = new JLabel(user.getRole() + " Dashboard");
        roleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        roleLabel.setForeground(new Color(189, 195, 199));
        roleLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> {
            dispose();
            Main.LoginScreen();
        });

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setOpaque(false);
        rightPanel.add(roleLabel);
        rightPanel.add(Box.createHorizontalStrut(15));
        rightPanel.add(logoutButton);

        header.add(welcome, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Role-specific menu panel
        JPanel menuPanel = buildMenuPanel();
        add(menuPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel buildMenuPanel() {
        switch (currentUser.getRole()) {
            case "Admin":
                return buildAdminMenu();
            case "Pharmacist":
                return buildPharmacistMenu();
            case "Manager":
                return buildManagerMenu();
            default:
                JPanel fallback = new JPanel();
                fallback.add(new JLabel("Unknown role."));
                return fallback;
        }
    }

    private JPanel buildAdminMenu() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 200, 60, 200));

        JButton manageUsers = new JButton("Manage Staff Users");
        manageUsers.setFont(new Font("Arial", Font.PLAIN, 16));
        manageUsers.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "User Management — coming soon");
            // TODO: open UserManagementUI
        });

        JButton viewStock = new JButton("View Stock");
        viewStock.setFont(new Font("Arial", Font.PLAIN, 16));
        viewStock.addActionListener(e -> new StockManagementUI());

        JButton viewReports = new JButton("View Reports");
        viewReports.setFont(new Font("Arial", Font.PLAIN, 16));
        viewReports.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Reports — coming soon");
            // TODO: open ReportsUI
        });

        panel.add(manageUsers);
        panel.add(viewStock);
        panel.add(viewReports);
        return panel;
    }

    private JPanel buildPharmacistMenu() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 200, 60, 200));

        JButton processSale = new JButton("Process Sale");
        processSale.setFont(new Font("Arial", Font.PLAIN, 16));
        processSale.addActionListener(e -> new ProcessSaleUI(currentUser));

        JButton maintainStock = new JButton("Maintain Local Stock");
        maintainStock.setFont(new Font("Arial", Font.PLAIN, 16));
        maintainStock.addActionListener(e -> new StockManagementUI());

        JButton checkAccounts = new JButton("Customer Accounts");
        checkAccounts.setFont(new Font("Arial", Font.PLAIN, 16));
        checkAccounts.addActionListener(e -> new CustomerAccountUI(currentUser));

        panel.add(processSale);
        panel.add(maintainStock);
        panel.add(checkAccounts);
        return panel;
    }

    private JPanel buildManagerMenu() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 200, 40, 200));

        JButton salesReport = new JButton("Sales / Turnover Report");
        salesReport.setFont(new Font("Arial", Font.PLAIN, 16));
        salesReport.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Sales Report — coming soon");
            // TODO: open ReportsUI
        });

        JButton stockReport = new JButton("Stock Availability Report");
        stockReport.setFont(new Font("Arial", Font.PLAIN, 16));
        stockReport.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Stock Report — coming soon");
            // TODO: open ReportsUI
        });

        JButton debtReport = new JButton("Aggregated Debt Report");
        debtReport.setFont(new Font("Arial", Font.PLAIN, 16));
        debtReport.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Debt Report — coming soon");
            // TODO: open ReportsUI
        });

        JButton customerAccounts = new JButton("Customer Accounts");
        customerAccounts.setFont(new Font("Arial", Font.PLAIN, 16));
        customerAccounts.addActionListener(e -> new CustomerAccountUI(currentUser));

        JButton placeOrder = new JButton("Place Wholesale Order");
        placeOrder.setFont(new Font("Arial", Font.PLAIN, 16));
        placeOrder.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Wholesale Order — coming soon");
            // TODO: open WholesaleOrderUI
        });

        panel.add(salesReport);
        panel.add(stockReport);
        panel.add(debtReport);
        panel.add(customerAccounts);
        panel.add(placeOrder);
        return panel;
    }
}
