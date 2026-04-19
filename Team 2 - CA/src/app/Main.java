package app;

import exception.AuthException;
import integration.CaApiServer;
import integration.HttpPuAdapter;
import integration.HttpSaGateway;
import integration.IPuStockUpdater;
import integration.ISaGateway;
import integration.MockPuAdapter;
import integration.MockSaGateway;
import model.*;
import service.OnlineSaleService;
import repository.*;
import service.*;
import ui.Dashboard;
import ui.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class Main extends JFrame {
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;

    public static void main(String[] args) {
        // use cross-platform L&F so flat colored buttons render correctly on all OS
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        // wire up shared services, one instance of each for the whole app
        boolean httpMode = Boolean.getBoolean("ipos.http");

        StockService stockSvc = new StockService(new StockRepositoryImpl());
        OnlineSaleService onlineSvc = new OnlineSaleService(stockSvc);

        ISaGateway gateway;
        IPuStockUpdater puAdapter;
        if (httpMode) {
            HttpSaGateway httpGateway = new HttpSaGateway();
            httpGateway.login();
            gateway = httpGateway;
            puAdapter = new HttpPuAdapter(onlineSvc);
            System.err.println("[Main] HTTP mode — connected to SA on port 8080, PU on port 8082");
        } else {
            gateway = new MockSaGateway();
            puAdapter = new MockPuAdapter(onlineSvc);
            System.err.println("[Main] Mock mode — SA and PU are simulated locally");
        }

        WholesaleOrderService orderSvc = new WholesaleOrderService(gateway, stockSvc);
        AppContext.init(orderSvc, onlineSvc, puAdapter, stockSvc);

        if (httpMode) {
            CaApiServer apiServer = new CaApiServer(
                orderSvc, puAdapter,
                AppContext::notifyOrderRefresh,
                AppContext::notifyStockRefresh
            );
            apiServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(apiServer::stop, "ca-api-shutdown"));
        }

        // run account status engine on startup
        new AccountService(new CustomerRepositoryImpl()).updateAccountStatuses();

        JFrame frame = new JFrame("IPOS-CA");
        frame.setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        Main.LoginScreen(frame);
    }

    public static void LoginScreen(JFrame frame) {
        LoginService loginService = new LoginService(new UserRepositoryImpl());

        frame.setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(UITheme.LOGIN_BG);

        // --- card: white panel sitting on the dark background ---
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setPreferredSize(new Dimension(400, 430));
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 205)));

        // thin accent strip at the top of the card
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
                showDashboard(frame, user);
            } catch (AuthException ex) {
                errorLabel.setText(ex.getMessage());
            }
        };

        loginButton.addActionListener(doLogin);
        usernameField.addActionListener(doLogin);
        passwordField.addActionListener(doLogin);
        frame.getRootPane().setDefaultButton(loginButton);

        // form panel with vertical layout for the login fields
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

    private static void showDashboard(JFrame frame, User user) {
        frame.setTitle("IPOS-CA — " + user.getRole() + " Dashboard");
        frame.getContentPane().removeAll();
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(new Dashboard(user), BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
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

        header.add(textStack, BorderLayout.WEST);
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
