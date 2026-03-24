package app;

import exception.AuthException;
import exception.SaleException;
import exception.StockException;
import model.*;
import repository.*;
import service.*;
import ui.Dashboard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class Main extends JFrame {
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;

    public Main() {
        LoginScreen();
    }

    public static void main(String[] args) {
        // run account status engine on startup
        new AccountService(new CustomerRepositoryImpl()).updateAccountStatuses();
        new Main();
    }

    // =====================================================================
    // TESTS — covers all layers built so far, remove call from main() after
    // =====================================================================
    private static void runAllTests() {
        System.out.println("\n========== IPOS-CA TEST RUN ==========\n");
        int[] s = {0, 0}; // [pass, fail]

        // --- 1. LoginService ---
        System.out.println("--- 1. LoginService ---");
        LoginService loginSvc = new LoginService(new UserRepositoryImpl());

        try {
            User u = loginSvc.login("admin1", "pass123");
            check(s, "Admin".equals(u.getRole()) && "Alice".equals(u.getName()),
                "valid admin login returns correct user");
        } catch (Exception e) { fail(s, "admin login threw exception: " + e.getMessage()); }

        try {
            User p = loginSvc.login("pharma1", "pass456");
            User m = loginSvc.login("manager1", "pass789");
            check(s, "Pharmacist".equals(p.getRole()) && "Manager".equals(m.getRole()),
                "pharmacist and manager logins return correct roles");
        } catch (Exception e) { fail(s, "pharma/manager login threw exception: " + e.getMessage()); }

        try {
            loginSvc.login("", "pass123");
            fail(s, "blank username should throw AuthException");
        } catch (AuthException e) {
            check(s, e.getReason() == AuthException.Reason.BLANK_INPUT, "blank username throws BLANK_INPUT");
        }

        try {
            loginSvc.login("admin1", "wrongpassword");
            fail(s, "wrong password should throw AuthException");
        } catch (AuthException e) {
            check(s, e.getReason() == AuthException.Reason.INVALID_CREDENTIALS, "wrong password throws INVALID_CREDENTIALS");
        }

        // --- 2. StockService ---
        System.out.println("\n--- 2. StockService ---");
        StockService stockSvc = new StockService(new StockRepositoryImpl());

        List<StockItem> allStock = stockSvc.getAllStock();
        check(s, !allStock.isEmpty(), "getAllStock returns " + allStock.size() + " items");

        try {
            StockItem item1 = stockSvc.getStockItem(1);
            check(s, "Paracetamol 500mg".equals(item1.getName()), "getStockItem(1) returns Paracetamol 500mg");
        } catch (StockException e) { fail(s, "getStockItem(1) threw exception: " + e.getMessage()); }

        try {
            stockSvc.getStockItem(-1);
            fail(s, "negative item id should throw StockException");
        } catch (StockException e) {
            check(s, e.getReason() == StockException.Reason.INVALID_ITEM_ID, "negative id throws INVALID_ITEM_ID");
        }

        try {
            stockSvc.getStockItem(9999);
            fail(s, "unknown item id should throw ITEM_NOT_FOUND");
        } catch (StockException e) {
            check(s, e.getReason() == StockException.Reason.ITEM_NOT_FOUND, "unknown id throws ITEM_NOT_FOUND");
        }

        try {
            int before = stockSvc.getStockItem(1).getQuantity();
            stockSvc.increaseStock(1, 10);
            int after = stockSvc.getStockItem(1).getQuantity();
            check(s, after == before + 10, "increaseStock adds correct amount (" + before + " → " + after + ")");
            stockSvc.decreaseStock(1, 10);
            int restored = stockSvc.getStockItem(1).getQuantity();
            check(s, restored == before, "decreaseStock restores quantity (" + after + " → " + restored + ")");
        } catch (StockException e) { fail(s, "increase/decrease threw exception: " + e.getMessage()); }

        try {
            stockSvc.decreaseStock(1, 99999);
            fail(s, "decrease beyond stock should throw INSUFFICIENT_STOCK");
        } catch (StockException e) {
            check(s, e.getReason() == StockException.Reason.INSUFFICIENT_STOCK, "over-decrease throws INSUFFICIENT_STOCK");
        }

        try {
            // amoxicillin (id=3) seeded at qty=8, threshold=10 — should be low stock
            StockItem item3 = stockSvc.getStockItem(3);
            check(s, item3.isLowStock(), "Amoxicillin 250mg flagged as low stock (qty=" + item3.getQuantity() + ")");
        } catch (StockException e) { fail(s, "getStockItem(3) threw exception: " + e.getMessage()); }

        List<StockItem> lowStock = stockSvc.getLowStock();
        check(s, !lowStock.isEmpty(), "getLowStock returns " + lowStock.size() + " low stock item(s)");

        // --- 3. SaleService ---
        System.out.println("\n--- 3. SaleService ---");
        SaleService saleSvc = new SaleService(stockSvc, new SaleRepositoryImpl());

        try {
            saleSvc.processSale(0, new java.util.ArrayList<>(), 0.0, PaymentMethod.CASH, null, "Bob");
            fail(s, "empty basket should throw EMPTY_SALE");
        } catch (SaleException e) {
            check(s, e.getReason() == SaleException.Reason.EMPTY_SALE, "empty basket throws EMPTY_SALE");
        }

        try {
            List<SaleLine> lines = Arrays.asList(new SaleLine(1, "Paracetamol 500mg", 99999, 2.50, 0.0));
            saleSvc.processSale(0, lines, 0.0, PaymentMethod.CASH, null, "Bob");
            fail(s, "over-quantity should throw INSUFFICIENT_STOCK");
        } catch (SaleException e) {
            check(s, e.getReason() == SaleException.Reason.INSUFFICIENT_STOCK, "over-quantity throws INSUFFICIENT_STOCK");
        }

        try {
            List<SaleLine> lines = Arrays.asList(new SaleLine(1, "Paracetamol 500mg", 1, 2.50, 0.0));
            saleSvc.processSale(0, lines, 0.0, PaymentMethod.CREDIT_CARD, null, "Bob");
            fail(s, "card payment without details should throw INVALID_PAYMENT");
        } catch (SaleException e) {
            check(s, e.getReason() == SaleException.Reason.INVALID_PAYMENT, "card payment without details throws INVALID_PAYMENT");
        }

        try {
            List<SaleLine> lines = Arrays.asList(new SaleLine(1, "Paracetamol 500mg", 1, 2.50, 0.0));
            Receipt r = saleSvc.processSale(0, lines, 0.0, PaymentMethod.CASH, null, "Bob");
            check(s, r != null && r.getReceiptNumber().startsWith("RCP-"),
                "valid cash sale returns receipt: " + (r != null ? r.getReceiptNumber() : "null"));
        } catch (SaleException e) { fail(s, "valid cash sale threw exception: " + e.getMessage()); }

        // --- 4. AccountService ---
        System.out.println("\n--- 4. AccountService ---");
        AccountService accountSvc = new AccountService(new CustomerRepositoryImpl());

        // in-memory customers — no db access needed for these checks
        Customer normalCustomer = new Customer("Test Normal", "1 Test St", "TST-N001", 500.0, DiscountType.FIXED, 0.10);
        Customer suspendedCustomer = new Customer(0, "Test Suspended", "2 Test St", "TST-S001",
            500.0, 0.0, 0.0, DiscountType.NONE, 0.0, AccountStatus.SUSPENDED,
            "due", "no_need", null, null, null);
        Customer defaultCustomer = new Customer(0, "Test Default", "3 Test St", "TST-D001",
            500.0, 0.0, 0.0, DiscountType.NONE, 0.0, AccountStatus.IN_DEFAULT,
            "sent", "due", null, null, null);

        check(s, accountSvc.canMakePurchase(normalCustomer, 50.0), "NORMAL account under limit can purchase");
        check(s, !accountSvc.canMakePurchase(normalCustomer, 600.0), "NORMAL account over credit limit is blocked");
        check(s, !accountSvc.canMakePurchase(suspendedCustomer, 1.0), "SUSPENDED account cannot purchase");
        check(s, !accountSvc.canMakePurchase(defaultCustomer, 1.0), "IN_DEFAULT account cannot purchase");

        double fixedDisc = accountSvc.calculatePointOfSaleDiscount(normalCustomer, 100.0);
        check(s, Math.abs(fixedDisc - 10.0) < 0.001, "fixed 10% discount on £100 = £10.00 (got £" + fixedDisc + ")");

        Customer flexCustomer = new Customer("Test Flex", "4 Test St", "TST-F001", 500.0, DiscountType.FLEXIBLE, 0.0);
        double flexPOS = accountSvc.calculatePointOfSaleDiscount(flexCustomer, 100.0);
        check(s, flexPOS == 0.0, "flexible discount = £0.00 at point of sale");

        // flexible month-end discount tiers: <£50=1%, £50-£100=3%, >£100=5%
        Customer flexLow  = new Customer(0, "Flex Low",  "", "TST-FL", 500, 0, 30.0,  DiscountType.FLEXIBLE, 0, AccountStatus.NORMAL, "no_need", "no_need", null, null, null);
        Customer flexMid  = new Customer(0, "Flex Mid",  "", "TST-FM", 500, 0, 75.0,  DiscountType.FLEXIBLE, 0, AccountStatus.NORMAL, "no_need", "no_need", null, null, null);
        Customer flexHigh = new Customer(0, "Flex High", "", "TST-FH", 500, 0, 150.0, DiscountType.FLEXIBLE, 0, AccountStatus.NORMAL, "no_need", "no_need", null, null, null);

        check(s, Math.abs(accountSvc.calculateFlexibleMonthEndDiscount(flexLow)  - 0.30) < 0.001, "flexible < £50 spend = 1% → £0.30");
        check(s, Math.abs(accountSvc.calculateFlexibleMonthEndDiscount(flexMid)  - 2.25) < 0.001, "flexible £50-£100 spend = 3% → £2.25");
        check(s, Math.abs(accountSvc.calculateFlexibleMonthEndDiscount(flexHigh) - 7.50) < 0.001, "flexible > £100 spend = 5% → £7.50");

        // --- 5. ReminderService ---
        System.out.println("\n--- 5. ReminderService ---");
        CustomerRepositoryImpl custRepo = new CustomerRepositoryImpl();
        ReminderService reminderSvc = new ReminderService(custRepo);

        // insert a temporary customer with 1st reminder due
        String testAcct = "TEST-REM-TMP";
        Customer tempCustomer = new Customer(0, "Test Reminder", "99 Test Lane", testAcct,
            500.0, 100.0, 80.0, DiscountType.NONE, 0.0, AccountStatus.SUSPENDED,
            "due", "no_need", null, null, LocalDate.now().minusDays(15));
        int tempId = custRepo.save(tempCustomer);

        if (tempId > 0) {
            // generate — should produce 1st reminder
            List<Reminder> round1 = reminderSvc.generateDueReminders();
            boolean got1st = round1.stream().anyMatch(r ->
                testAcct.equals(r.getAccountNumber()) && r.getType() == Reminder.Type.FIRST);
            check(s, got1st, "1st reminder generated for account with status_1stReminder='due'");

            // letter should include "Payment Reminder"
            Reminder letter = round1.stream().filter(r -> testAcct.equals(r.getAccountNumber())).findFirst().orElse(null);
            check(s, letter != null && letter.getLetterText().contains("Payment Reminder"),
                "reminder letter contains 'Payment Reminder' subject line");

            // customer record should be updated
            Customer updated = custRepo.findById(tempId);
            check(s, updated != null && "sent".equals(updated.getStatus1stReminder()),
                "status_1stReminder marked 'sent' after generation");
            check(s, updated != null && LocalDate.now().plusDays(15).equals(updated.getDate2ndReminder()),
                "date_2ndReminder scheduled for today + 15 days");

            // set 2nd reminder due but with future date — should NOT generate yet
            custRepo.updateStatus(tempId, AccountStatus.IN_DEFAULT, "sent", "due",
                null, LocalDate.now().plusDays(15), null);
            List<Reminder> round2 = reminderSvc.generateDueReminders();
            boolean premature2nd = round2.stream().anyMatch(r ->
                testAcct.equals(r.getAccountNumber()) && r.getType() == Reminder.Type.SECOND);
            check(s, !premature2nd, "2nd reminder NOT generated before scheduled date");

            // move date to past — should generate now
            custRepo.updateStatus(tempId, AccountStatus.IN_DEFAULT, "sent", "due",
                null, LocalDate.now().minusDays(1), null);
            List<Reminder> round3 = reminderSvc.generateDueReminders();
            boolean got2nd = round3.stream().anyMatch(r ->
                testAcct.equals(r.getAccountNumber()) && r.getType() == Reminder.Type.SECOND);
            check(s, got2nd, "2nd reminder generated once scheduled date has passed");

            // clean up
            custRepo.delete(tempId);
            System.out.println("INFO: temp test customer removed");
        } else {
            fail(s, "could not insert temp customer for reminder tests");
        }

        // --- Summary ---
        System.out.println("\n========== RESULTS: " + s[0] + " passed, " + s[1] + " failed ==========\n");
    }

    private static void check(int[] s, boolean condition, String description) {
        if (condition) { System.out.println("PASS: " + description); s[0]++; }
        else           { System.out.println("FAIL: " + description); s[1]++; }
    }

    private static void fail(int[] s, String description) {
        System.out.println("FAIL: " + description);
        s[1]++;
    }

    // =====================================================================

    public static void LoginScreen() {
        LoginService loginService = new LoginService(new UserRepositoryImpl());

        JFrame frame = new JFrame("IPOS-CA");
        frame.setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel companyName = new JLabel("IPOS-CA name i which forgot", SwingConstants.CENTER);
        companyName.setFont(new Font("Arial", Font.BOLD, 20));

        gbc.gridy = 0;
        gbc.insets = new Insets(50, 10, 50, 10);
        frame.add(companyName, gbc);

        JPanel panel = new JPanel(new GridLayout(3, 2, 15, 15));
        panel.setPreferredSize(new Dimension(350, 120));

        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("Login");

        panel.add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(new JLabel());
        panel.add(loginButton);

        frame.getRootPane().setDefaultButton(loginButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());

                try {
                    User user = loginService.login(username, password);
                    frame.dispose();
                    new Dashboard(user);
                } catch (AuthException ex) {
                    JOptionPane.showMessageDialog(frame,
                        ex.getMessage(),
                        "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        gbc.gridy = 1;
        frame.add(panel, gbc);
        frame.setVisible(true);
    }
}
// chang qi is cool
