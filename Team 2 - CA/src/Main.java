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
        new Main();
    }

    public static void LoginScreen() {
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
                String username = usernameField.getText(); // get username
                String password = new String(passwordField.getPassword()); // get password

                // get the username and password from db
                // if correct hide frame go to next one
                // if incorrect say password incorrect etc

            }
        });
        gbc.gridy = 1;
        frame.add(panel, gbc);
        frame.setVisible(true);
    }


}
