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

    private JTabbedPane buildMenuPanel() {
        // changed to tabs
        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(UITheme.FONT_BOLD);
        tabs.setBackground(UITheme.LIGHT_BG);
        switch (currentUser.getRole()) {
            case "Admin":
                buildAdminMenu(tabs);
                break;
            case "Pharmacist":
                buildPharmacistMenu(tabs);
                break;
            case "Manager":
                buildManagerMenu(tabs);
                break;
        }
        return tabs;
    }

    // builds a centered column of menu buttons with consistent styling
    private void buildAdminMenu(JTabbedPane tabs) {
        tabs.addTab("Manage Staff Users", new StaffManagementUI());
        tabs.addTab("View Stock", new StockManagementUI());
        tabs.addTab("View Reports", new ReportsUI(currentUser, 0));
    }

    private void buildPharmacistMenu(JTabbedPane tabs) {
        // converted to tabs -done
        tabs.addTab("Process Sale", new ProcessSaleUI(currentUser));
        tabs.addTab("Maintain Local Stock", new StockManagementUI());
        tabs.addTab("Customer Accounts", new CustomerAccountUI(currentUser));
        tabs.addTab("Wholesale Orders", new WholesaleOrderUI(currentUser));

    }

    private void buildManagerMenu(JTabbedPane tabs) {
        // converted tyo tabs -done
        // converted to one singular tab for all 3 reports as there are sub tabs contained within
        tabs.addTab("View Reports", new ReportsUI(currentUser, 0));
        tabs.addTab("Customer Accounts", new CustomerAccountUI(currentUser));
        tabs.addTab("Place Wholesale Order", new WholesaleOrderUI(currentUser));

    }
}
