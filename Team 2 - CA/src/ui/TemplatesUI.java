package ui;

import repository.ConfigRepository;

import javax.swing.*;
import java.awt.*;

// screen for managers to edit pharmacy details and template text for reminders and receipts
public class TemplatesUI extends JPanel {

    private final ConfigRepository configRepo = new ConfigRepository();

    // pharmacy details fields
    private JTextField nameField;
    private JTextField addressField;
    private JTextField emailField;
    private JTextField phoneField;

    // template fields
    private JTextArea reminder1Area;
    private JTextArea reminder2Area;
    private JTextArea receiptFooterArea;

    public TemplatesUI() {
        setLayout(new BorderLayout());
        setOpaque(false);

        add(UITheme.createHeaderPanel("Templates & Pharmacy Details"), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);
        add(buildSaveButton(), BorderLayout.SOUTH);

        loadValues();
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.FONT_BOLD);
        tabs.addTab("Pharmacy Details", buildPharmacyPanel());
        tabs.addTab("1st Reminder Template", buildTemplatePanel("reminder1"));
        tabs.addTab("2nd Reminder Template", buildTemplatePanel("reminder2"));
        tabs.addTab("Receipt Footer", buildTemplatePanel("receipt_footer"));
        return tabs;
    }

    private JPanel buildPharmacyPanel() {
        nameField = new JTextField();
        addressField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();

        UITheme.styleTextField(nameField);
        UITheme.styleTextField(addressField);
        UITheme.styleTextField(emailField);
        UITheme.styleTextField(phoneField);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UITheme.LIGHT_BG);
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(8, 0, 4, 16);
        lc.gridx = 0;
        lc.weightx = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(8, 0, 4, 0);
        fc.gridx = 1;
        fc.weightx = 1;

        String[] labels = {"Pharmacy Name:", "Address:", "Email:", "Phone:"};
        JTextField[] fields = {nameField, addressField, emailField, phoneField};

        for (int i = 0; i < labels.length; i++) {
            lc.gridy = fc.gridy = i;
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(UITheme.FONT_BOLD);
            form.add(lbl, lc);
            form.add(fields[i], fc);
        }

        // hint about placeholders
        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = labels.length;
        hc.gridwidth = 2;
        hc.insets = new Insets(16, 0, 0, 0);
        hc.anchor = GridBagConstraints.WEST;
        JLabel hint = new JLabel("These details appear on all reminders and receipts.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(UITheme.SECONDARY);
        form.add(hint, hc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(form, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildTemplatePanel(String key) {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(12);

        switch (key) {
            case "reminder1":
                reminder1Area = area;
                break;
            case "reminder2":
                reminder2Area = area;
                break;
            case "receipt_footer":
                receiptFooterArea = area;
                break;
        }

        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hint.setOpaque(false);
        JLabel hintLabel = new JLabel("Available placeholders: {customer_name}  {account_no}  {invoice_no}  {amount}  {pharmacy_name}  {date}");
        hintLabel.setFont(UITheme.FONT_SMALL);
        hintLabel.setForeground(UITheme.SECONDARY);
        hint.add(hintLabel);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSaveButton() {
        JButton saveBtn = UITheme.successBtn("save changes");
        saveBtn.addActionListener(e -> saveValues());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 10));
        panel.setOpaque(false);
        panel.add(saveBtn);
        return panel;
    }

    private void loadValues() {
        nameField.setText(configRepo.get("pharmacy_name"));
        addressField.setText(configRepo.get("pharmacy_address"));
        emailField.setText(configRepo.get("pharmacy_email"));
        phoneField.setText(configRepo.get("pharmacy_phone"));
        reminder1Area.setText(configRepo.get("reminder1_template"));
        reminder2Area.setText(configRepo.get("reminder2_template"));
        receiptFooterArea.setText(configRepo.get("receipt_footer"));
    }

    private void saveValues() {
        configRepo.set("pharmacy_name", nameField.getText().trim());
        configRepo.set("pharmacy_address", addressField.getText().trim());
        configRepo.set("pharmacy_email", emailField.getText().trim());
        configRepo.set("pharmacy_phone", phoneField.getText().trim());
        configRepo.set("reminder1_template", reminder1Area.getText());
        configRepo.set("reminder2_template", reminder2Area.getText());
        configRepo.set("receipt_footer", receiptFooterArea.getText());

        JOptionPane.showMessageDialog(this, "changes saved successfully", "saved", JOptionPane.INFORMATION_MESSAGE);
    }
}
