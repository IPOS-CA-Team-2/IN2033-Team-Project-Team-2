package app;

import exception.AuthException;
import exception.SaleException;
import exception.StockException;
import integration.MockPuAdapter;
import integration.MockSaGateway;
import model.*;
import repository.*;
import service.*;
import ui.Dashboard;
import ui.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class Main extends JFrame {
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;

    public Main() {
        LoginScreen();
    }

    public static void main(String[] args) {
        // use cross-platform L&F so flat colored buttons render correctly on all OS
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

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

        // --- 6. WholesaleOrderService (SA integration) ---
        System.out.println("\n--- 6. WholesaleOrderService ---");
        StockService wsSvc = new StockService(new StockRepositoryImpl());
        WholesaleOrderService orderSvc = new WholesaleOrderService(new MockSaGateway(), wsSvc);

        List<OrderLine> lines = Arrays.asList(
            new OrderLine(1, "Paracetamol 500mg", 50, 1.92),
            new OrderLine(2, "Ibuprofen 200mg",   30, 3.07)
        );
        WholesaleOrder placed = orderSvc.placeOrder(lines);
        check(s, placed != null && placed.getOrderId() > 0,
            "placeOrder returns saved order with id=" + (placed != null ? placed.getOrderId() : "null"));
        check(s, placed != null && placed.getStatus() == OrderStatus.PENDING,
            "new order starts as PENDING");
        check(s, placed != null && Math.abs(placed.getTotalValue() - (50*1.92 + 30*3.07)) < 0.001,
            "order total value correct: £" + (placed != null ? String.format("%.2f", placed.getTotalValue()) : "?"));

        if (placed != null) {
            int oid = placed.getOrderId();

            orderSvc.simulateStatusUpdate(oid, OrderStatus.ACCEPTED, null, null, null, null);
            check(s, orderSvc.getOrder(oid).getStatus() == OrderStatus.ACCEPTED,
                "order status updated to ACCEPTED");

            LocalDate dispDate = LocalDate.now();
            orderSvc.simulateStatusUpdate(oid, OrderStatus.DISPATCHED, "DHL", "DHL123", dispDate, dispDate.plusDays(3));
            WholesaleOrder dispatched = orderSvc.getOrder(oid);
            check(s, dispatched != null && dispatched.getStatus() == OrderStatus.DISPATCHED,
                "order status updated to DISPATCHED");
            check(s, dispatched != null && "DHL".equals(dispatched.getCourier()),
                "courier info stored: " + (dispatched != null ? dispatched.getCourier() : "null"));

            try {
                int qtyBefore = wsSvc.getStockItem(1).getQuantity();
                orderSvc.markDelivered(oid);
                int qtyAfter = wsSvc.getStockItem(1).getQuantity();
                check(s, qtyAfter == qtyBefore + 50,
                    "markDelivered increases stock for line 1 (" + qtyBefore + " → " + qtyAfter + ")");
                check(s, orderSvc.getOrder(oid).getStatus() == OrderStatus.DELIVERED,
                    "order status updated to DELIVERED");
                // clean up
                wsSvc.decreaseStock(1, 50);
                wsSvc.decreaseStock(2, 30);
            } catch (StockException e) { fail(s, "markDelivered threw StockException: " + e.getMessage()); }
        }

        List<WholesaleOrder> history = orderSvc.getAllOrders();
        check(s, !history.isEmpty(), "getAllOrders returns order history (" + history.size() + " order(s))");

        // --- 7. OnlineSaleService + MockPuAdapter (PU integration) ---
        System.out.println("\n--- 7. OnlineSaleService + MockPuAdapter ---");
        StockService puSvc      = new StockService(new StockRepositoryImpl());
        OnlineSaleService onlineSvc = new OnlineSaleService(puSvc);
        MockPuAdapter puAdapter     = new MockPuAdapter(onlineSvc);

        try {
            int beforePara = puSvc.getStockItem(1).getQuantity();
            List<OnlineSaleItem> saleItems = Arrays.asList(
                new OnlineSaleItem(1, 5), new OnlineSaleItem(4, 3));
            OnlineSale sale = new OnlineSale("PU-TEST-001", LocalDate.now(), "buyer@test.com", saleItems);
            boolean fullApply = onlineSvc.processOnlineSale(sale);
            int afterPara = puSvc.getStockItem(1).getQuantity();
            check(s, fullApply, "valid online sale returns true (all items applied)");
            check(s, afterPara == beforePara - 5,
                "stock deducted for item 1 (" + beforePara + " → " + afterPara + ")");
            puSvc.increaseStock(1, 5);
            puSvc.increaseStock(4, 3);
        } catch (StockException e) { fail(s, "online sale stock check threw: " + e.getMessage()); }

        // partial sale — one item way over stock
        OnlineSale partialSale = new OnlineSale("PU-TEST-002", LocalDate.now(), null,
            Arrays.asList(new OnlineSaleItem(1, 1), new OnlineSaleItem(3, 99999)));
        boolean partialApply = onlineSvc.processOnlineSale(partialSale);
        check(s, !partialApply, "partial online sale returns false when one item exceeds stock");
        try { puSvc.increaseStock(1, 1); } catch (StockException ignored) {}

        try {
            int beforeCet = puSvc.getStockItem(4).getQuantity();
            boolean simResult = puAdapter.simulateSale(
                List.of(new OnlineSaleItem(4, 2)), "sim@test.com");
            int afterCet = puSvc.getStockItem(4).getQuantity();
            check(s, simResult, "MockPuAdapter.simulateSale applies stock deduction");
            check(s, afterCet == beforeCet - 2,
                "cetirizine deducted via adapter (" + beforeCet + " → " + afterCet + ")");
            puSvc.increaseStock(4, 2);
        } catch (StockException e) { fail(s, "adapter simulate threw: " + e.getMessage()); }

        // --- 8. ReportService ---
        System.out.println("\n--- 8. ReportService ---");
        ReportService reportSvc = new ReportService(
            new SaleRepositoryImpl(),
            new CustomerRepositoryImpl(),
            new StockService(new StockRepositoryImpl()),
            new WholesaleOrderRepositoryImpl()
        );

        // stock report — always current snapshot
        ReportService.StockReport stockRpt = reportSvc.generateStockReport();
        check(s, stockRpt != null && stockRpt.rows.size() == 5,
            "stock report contains 5 items (got " + (stockRpt != null ? stockRpt.rows.size() : "null") + ")");
        check(s, stockRpt != null && stockRpt.totalStockValue > 0,
            "stock report total value > 0 (got £" + (stockRpt != null ? String.format("%.2f", stockRpt.totalStockValue) : "?") + ")");
        check(s, stockRpt != null && stockRpt.rows.stream().anyMatch(r -> r.lowStock),
            "stock report flags at least one low-stock item");
        check(s, stockRpt != null && stockRpt.rows.stream().anyMatch(r -> "Amoxicillin 250mg".equals(r.name) && r.lowStock),
            "amoxicillin correctly flagged as low stock in report");

        // turnover report — broad range captures the test sale placed in section 3
        LocalDate rptFrom = LocalDate.now().minusDays(1);
        LocalDate rptTo   = LocalDate.now().plusDays(1);
        ReportService.TurnoverReport turnRpt = reportSvc.generateTurnoverReport(rptFrom, rptTo);
        check(s, turnRpt != null && turnRpt.saleCount >= 1,
            "turnover report finds at least 1 sale today (got " + (turnRpt != null ? turnRpt.saleCount : "null") + ")");
        check(s, turnRpt != null && turnRpt.totalRevenue > 0,
            "turnover report revenue > 0 (got £" + (turnRpt != null ? String.format("%.2f", turnRpt.totalRevenue) : "?") + ")");
        check(s, turnRpt != null && turnRpt.cashCount >= 1,
            "turnover report counts cash sale from section 3");
        check(s, turnRpt != null && turnRpt.from.equals(rptFrom) && turnRpt.to.equals(rptTo),
            "turnover report period dates correct");

        // wholesale orders in range — section 6 placed one today
        check(s, turnRpt != null && turnRpt.orderCount >= 1,
            "turnover report finds wholesale order placed today (got " + (turnRpt != null ? turnRpt.orderCount : "null") + ")");
        check(s, turnRpt != null && turnRpt.orderTotal > 0,
            "turnover report wholesale total > 0 (got £" + (turnRpt != null ? String.format("%.2f", turnRpt.orderTotal) : "?") + ")");

        // debt report — 3 seeded customers
        ReportService.DebtReport debtRpt = reportSvc.generateDebtReport();
        check(s, debtRpt != null && debtRpt.rows.size() == 3,
            "debt report contains 3 account holders (got " + (debtRpt != null ? debtRpt.rows.size() : "null") + ")");
        check(s, debtRpt != null && debtRpt.totalDebt >= 0,
            "debt report total debt is non-negative (got £" + (debtRpt != null ? String.format("%.2f", debtRpt.totalDebt) : "?") + ")");
        check(s, debtRpt != null && debtRpt.rows.stream().allMatch(r -> r.accountNumber != null && !r.accountNumber.isBlank()),
            "all debt rows have valid account numbers");
        check(s, debtRpt != null && debtRpt.rows.stream().allMatch(r -> r.utilisationPct >= 0),
            "all debt rows have non-negative utilisation %");

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
        frame.getContentPane().setBackground(UITheme.LOGIN_BG);

        // --- card: white panel sitting on the dark background ---
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(400, 430));
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 205)));

        // accent strip — 4px primary blue bar at the very top
        JPanel accentStrip = new JPanel();
        accentStrip.setBackground(UITheme.PRIMARY);
        accentStrip.setPreferredSize(new Dimension(400, 4));

        // card top section = accent strip (NORTH) + dark header (CENTER)
        JPanel cardTop = new JPanel(new BorderLayout());
        cardTop.setOpaque(false);
        cardTop.add(accentStrip, BorderLayout.NORTH);
        cardTop.add(buildLoginHeader(), BorderLayout.CENTER);

        card.add(cardTop, BorderLayout.NORTH);

        // --- form body ---
        JTextField usernameField   = new JTextField(20);
        JPasswordField passwordField = new JPasswordField(20);
        UITheme.styleTextField(usernameField);
        UITheme.styleTextField(passwordField);

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(UITheme.DANGER);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton loginButton = UITheme.primaryBtn("Sign In");
        loginButton.setFont(new Font("Arial", Font.BOLD, 13));
        loginButton.setPreferredSize(new Dimension(340, 42));
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        // action shared by button click and enter key
        ActionListener doLogin = e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            try {
                User user = loginService.login(username, password);
                frame.dispose();
                new Dashboard(user);
            } catch (AuthException ex) {
                errorLabel.setText(ex.getMessage());
            }
        };

        loginButton.addActionListener(doLogin);
        usernameField.addActionListener(doLogin);
        passwordField.addActionListener(doLogin);
        frame.getRootPane().setDefaultButton(loginButton);

        // form panel — BoxLayout stacks everything vertically
        JPanel form = new JPanel();
        form.setBackground(Color.WHITE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));

        JLabel userLabel = makeFormLabel("USERNAME");
        JLabel passLabel = makeFormLabel("PASSWORD");

        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(userLabel);
        form.add(Box.createVerticalStrut(5));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(14));
        form.add(passLabel);
        form.add(Box.createVerticalStrut(5));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(22));
        form.add(loginButton);
        form.add(Box.createVerticalStrut(8));
        form.add(errorLabel);

        card.add(form, BorderLayout.CENTER);

        // add card to center of dark frame
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        frame.add(card, gbc);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // dark header section inside the login card
    private static JPanel buildLoginHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));

        // left: system name + subtitle stacked vertically
        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("IPOS-CA");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Pharmacy Management System");
        subtitle.setFont(new Font("Arial", Font.PLAIN, 11));
        subtitle.setForeground(UITheme.SUBTEXT);

        textStack.add(title);
        textStack.add(Box.createVerticalStrut(4));
        textStack.add(subtitle);

        // right: Rx symbol as a decorative element
        JLabel rx = new JLabel("Rx");
        rx.setFont(new Font("Arial", Font.BOLD, 38));
        rx.setForeground(new Color(255, 255, 255, 45));
        rx.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        header.add(textStack, BorderLayout.WEST);
        header.add(rx, BorderLayout.EAST);
        return header;
    }

    // small uppercase label used above form fields in the login card
    private static JLabel makeFormLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_LABEL);
        label.setForeground(UITheme.SECONDARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
// chang qi is cool
