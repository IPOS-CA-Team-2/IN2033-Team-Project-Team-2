package ui;

import app.Main;
import model.User;

import javax.swing.*;
import java.awt.*;

// role-based dashboard shown after login
// routes to different screens depending on Admin / Pharmacist / Manager
public class Dashboard extends JFrame {

    private final User currentUser;

    public Dashboard(User user) {
        this.currentUser = user;

        setTitle("IPOS-CA — " + user.getRole() + " Dashboard");
        setSize(Main.SCREEN_WIDTH, Main.SCREEN_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        UITheme.applyFrameBackground(this);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMenuPanel(), BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // dark header with welcome text left, role label + logout right
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel welcome = new JLabel("Welcome, " + currentUser.getName());
        welcome.setFont(new Font("Arial", Font.BOLD, 18));
        welcome.setForeground(Color.WHITE);

        JLabel roleLabel = new JLabel(currentUser.getRole() + " Dashboard");
        roleLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        roleLabel.setForeground(UITheme.SUBTEXT);

        JButton logoutButton = UITheme.secondaryBtn("Logout");
        logoutButton.addActionListener(e -> { dispose(); Main.LoginScreen(); });

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(roleLabel);
        rightPanel.add(Box.createHorizontalStrut(8));
        rightPanel.add(logoutButton);

        header.add(welcome, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMenuPanel() {
        switch (currentUser.getRole()) {
            case "Admin":      return buildAdminMenu();
            case "Pharmacist": return buildPharmacistMenu();
            case "Manager":    return buildManagerMenu();
            default:
                JPanel fallback = new JPanel();
                fallback.setOpaque(false);
                fallback.add(new JLabel("Unknown role."));
                return fallback;
        }
    }

    // builds a centered column of menu buttons with consistent styling
    private JPanel buildAdminMenu() {
        JButton manageUsers = UITheme.primaryBtn("Manage Staff Users");
        JButton viewStock   = UITheme.primaryBtn("View Stock");
        JButton viewReports = UITheme.primaryBtn("View Reports");

        manageUsers.addActionListener(e -> new StaffManagementUI());
        viewStock.addActionListener(e -> new StockManagementUI());
        viewReports.addActionListener(e -> JOptionPane.showMessageDialog(this, "Reports — coming soon"));

        return wrapMenuButtons(new JButton[]{manageUsers, viewStock, viewReports});
    }

    private JPanel buildPharmacistMenu() {
        JButton processSale    = UITheme.primaryBtn("Process Sale");
        JButton maintainStock  = UITheme.primaryBtn("Maintain Local Stock");
        JButton checkAccounts  = UITheme.primaryBtn("Customer Accounts");
        JButton wholesaleOrder = UITheme.primaryBtn("Wholesale Orders");

        processSale.addActionListener(e -> new ProcessSaleUI(currentUser));
        maintainStock.addActionListener(e -> new StockManagementUI());
        checkAccounts.addActionListener(e -> new CustomerAccountUI(currentUser));
        wholesaleOrder.addActionListener(e -> new WholesaleOrderUI(currentUser));

        return wrapMenuButtons(new JButton[]{processSale, maintainStock, checkAccounts, wholesaleOrder});
    }

    private JPanel buildManagerMenu() {
        JButton salesReport    = UITheme.primaryBtn("Sales / Turnover Report");
        JButton stockReport    = UITheme.primaryBtn("Stock Availability Report");
        JButton debtReport     = UITheme.primaryBtn("Aggregated Debt Report");
        JButton customerAccounts = UITheme.primaryBtn("Customer Accounts");
        JButton placeOrder     = UITheme.primaryBtn("Place Wholesale Order");

        salesReport.addActionListener(e -> new ReportsUI(currentUser, 0));
        stockReport.addActionListener(e -> new ReportsUI(currentUser, 1));
        debtReport.addActionListener(e -> new ReportsUI(currentUser, 2));
        customerAccounts.addActionListener(e -> new CustomerAccountUI(currentUser));
        placeOrder.addActionListener(e -> new WholesaleOrderUI(currentUser));

        return wrapMenuButtons(new JButton[]{salesReport, stockReport, debtReport, customerAccounts, placeOrder});
    }

    // wraps an array of buttons in a centered vertical panel with consistent sizing
    private JPanel wrapMenuButtons(JButton[] buttons) {
        JPanel inner = new JPanel(new GridLayout(buttons.length, 1, 0, 14));
        inner.setOpaque(false);

        for (JButton btn : buttons) {
            btn.setFont(new Font("Arial", Font.BOLD, 14));
            btn.setPreferredSize(new Dimension(260, 50));
            inner.add(btn);
        }

        JPanel centered = new JPanel(new GridBagLayout());
        centered.setOpaque(false);
        centered.add(inner, new GridBagConstraints());
        return centered;
    }
}
