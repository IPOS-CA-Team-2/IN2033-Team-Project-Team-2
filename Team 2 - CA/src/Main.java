import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Main extends JFrame {
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;

    public Main() {
        LoginScreen();
    }

    public static void main(String[] args) {
        runLoginTests();
        new Main();
    }

    // Temporary test — remove once Ishmael's UserRepository is connected
    private static void runLoginTests() {
        UserRepository testRepo = new UserRepository() {
            @Override
            public User findByUsername(String username) {
                if (username.equals("admin1")) {
                    return new Admin("admin1", "pass123", "Alice");
                }
                return null;
            }

            @Override
            public boolean validateCredentials(String username, String password) {
                User u = findByUsername(username);
                return u != null && u.checkPassword(password);
            }
        };

        LoginService loginService = new LoginService(testRepo);

        // Test 1 — valid credentials
        try {
            User user = loginService.login("admin1", "pass123");
            System.out.println("Login success: " + user);
        } catch (AuthException e) {
            System.out.println("FAIL: " + e.getMessage());
        }

        // Test 2 — wrong password
        try {
            User user = loginService.login("admin1", "wrongpass");
            System.out.println("FAIL — should not reach here");
        } catch (AuthException e) {
            System.out.println("Expected failure (" + e.getReason() + "): " + e.getMessage());
        }

        // Test 3 — blank username
        try {
            User user = loginService.login("", "pass123");
            System.out.println("FAIL — should not reach here");
        } catch (AuthException e) {
            System.out.println("Expected failure (" + e.getReason() + "): " + e.getMessage());
        }

        // Test 4 — unknown user
        try {
            User user = loginService.login("ghost", "pass123");
            System.out.println("FAIL — should not reach here");
        } catch (AuthException e) {
            System.out.println("Expected failure (" + e.getReason() + "): " + e.getMessage());
        }
    }

    public static void LoginScreen() {
        // Temporary stub — replace with Ishmael's real UserRepository once ready
        UserRepository testRepo = new UserRepository() {
            @Override
            public User findByUsername(String username) {
                if (username.equals("admin1")) return new Admin("admin1", "pass123", "Alice");
                if (username.equals("pharma1")) return new Pharmacist("pharma1", "pass456", "Bob");
                if (username.equals("manager1")) return new Manager("manager1", "pass789", "Carol");
                return null;
            }

            @Override
            public boolean validateCredentials(String username, String password) {
                User u = findByUsername(username);
                return u != null && u.checkPassword(password);
            }
        };

        LoginService loginService = new LoginService(testRepo);

        // Create frame
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

        // username and password labels
        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        // login confirmation button
        JButton loginButton = new JButton("Login");

        panel.add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(new JLabel());
        panel.add(loginButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());

                try {
                    User user = loginService.login(username, password);
                    // Login successful — close login screen and route to dashboard
                    frame.dispose();
                    JOptionPane.showMessageDialog(null,
                        "Welcome, " + user.getName() + "! (" + user.getRole() + ")",
                        "Login Successful",
                        JOptionPane.INFORMATION_MESSAGE);
                    // TODO: pass user to Dashboard (Asma's task)
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
// chang qi is not cool