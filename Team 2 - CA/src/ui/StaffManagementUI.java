package ui;

import db.DatabaseManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;

public class StaffManagementUI extends JPanel {

    private static final String[] COLUMNS = {"ID", "Name", "Username", "Password", "Role"};
    private DefaultTableModel tableModel;
    private JTable table;
    private boolean passwordsVisible = false; // password toggle defaulted to false

    public StaffManagementUI() {
        setLayout(new BorderLayout());
        setOpaque(false);


        add(topSection(), BorderLayout.NORTH);
        add(mainSection(), BorderLayout.CENTER);
        add(bottomSection(), BorderLayout.SOUTH);
        loadUsers();
        hidePassword(); // hide paswords by deauglt
    }

    private JPanel topSection() {
        // adding a search bar at the very top to search for specific users by their name, username or role
        // returns everything that matches

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));


        // adds management text on the left of the top bar
        // add a filler cause it looks empty without it
        JLabel titleLabel = new JLabel("Staff Management");
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);



        // adds search bar on the right of the top bar
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setFont(UITheme.FONT_BOLD);
        searchLabel.setForeground(Color.WHITE);



        JTextField searchField = new JTextField(18);
        UITheme.styleTextField(searchField);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate (javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }

            public void removeUpdate (javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterTable(searchField.getText());
            }
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);


        header.add(searchPanel, BorderLayout.EAST);
        header.add(titleLabel, BorderLayout.WEST);

        return header;
    }

    private void filterTable(String query) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        if (query == null || query.isBlank()) {
            sorter.setRowFilter(null);
            return;
        }

        String q = query.toLowerCase();
        int len = q.length();

        if (q.startsWith("name: ") && len > 6) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(), 1));
        } else if (q.startsWith("username: ") && len > 10) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(10).trim(), 2));
        } else if (q.startsWith("password: ") && len > 10) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(10).trim(), 3));
        } else if (q.startsWith("role: ") && len > 6) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q.substring(6).trim(), 4));
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + q, 1, 2, 4));
        }
    }

    private JPanel mainSection() {
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        }; // generate the tables for the main user display thingy

        table = new JTable(tableModel);
        UITheme.styleTable(table);

        // column displays for the things
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setWidth(0);
        table.getColumnModel().getColumn(1).setPreferredWidth(180); // name
        table.getColumnModel().getColumn(2).setPreferredWidth(160); // username
        table.getColumnModel().getColumn(3).setPreferredWidth(160); // password
        table.getColumnModel().getColumn(4).setPreferredWidth(130); // role


        // the alternative grey white rows, easier to see
        DefaultTableCellRenderer altRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                    c.setForeground(Color.BLACK);
                }
                return c;

            }
        };

        table.getColumnModel().getColumn(1).setCellRenderer(altRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(altRenderer);


        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);


        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        panel.add(scroll, BorderLayout.CENTER);


        return panel;

    }

    private JPanel bottomSection() {
        // add the add user and delete user buttons
        
        JButton addBtn = UITheme.successBtn("Add User");
        JButton removeBtn = UITheme.dangerBtn("Remove Selected");

        addBtn.addActionListener(e -> showAddUserDialog());
        removeBtn.addActionListener(e -> removeSelectedUser());


        // promote and demote buttons for staff
        JButton promoteBtn = UITheme.successBtn("Promote");
        JButton demoteBtn  = UITheme.dangerBtn("Demote");


        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);

        // true -> promote, false -> demote
        promoteBtn.addActionListener(e -> handleRoleChange(true));
        demoteBtn.addActionListener(e -> handleRoleChange(false));

        btnRow.add(demoteBtn);
        btnRow.add(promoteBtn);
        btnRow.add(removeBtn);
        btnRow.add(addBtn);

        JButton togglePassBtn = UITheme.secondaryBtn("Show Passwords");
        togglePassBtn.addActionListener(e -> togglePasswords(togglePassBtn));
        btnRow.add(togglePassBtn);

        JLabel searchFilters = new JLabel("Search commands: name, username, password, role (e.g. username: manager)");
        searchFilters.setFont(UITheme.FONT_SMALL);
        searchFilters.setForeground(UITheme.SECONDARY);
        btnRow.add(searchFilters);


        // live count label on the left
        JLabel countLabel = new JLabel();
        countLabel.setFont(UITheme.FONT_SMALL);
        countLabel.setForeground(UITheme.SECONDARY);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 16, 14, 16));
        footer.add(countLabel, BorderLayout.WEST);
        footer.add(btnRow, BorderLayout.WEST);
        return footer;
    }



    private void togglePasswords(JButton btn) {
        passwordsVisible = !passwordsVisible; // toggle password when clicked
        btn.setText(passwordsVisible ? "Hide Passwords" : "Show Passwords");

        if (passwordsVisible) {
            // if true show the actual passwords
            table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
                    if (!sel) {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                        c.setForeground(Color.BLACK);
                    }
                    return c;
                }
            });
        } else {
            hidePassword();
        }

        table.repaint();
    }

    public void hidePassword() {
        // put it in a seperate one cause it was causing issues and not triggering at the start for some reason idk why
        //check column three again which is the pasword one
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value != null ? "**************" : "", sel, focus, row, col);
                // fix color issue
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : UITheme.ROW_ALT);
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });
    }





    private void handleRoleChange(boolean promote) {
        // takes in a bool so.. , true -> promote, false -> demote

        // must select user by clicking first
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            showError("Please select a user first.");
            return;
        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        int id = (int) tableModel.getValueAt(modelRow, 0);
        String username = (String) tableModel.getValueAt(modelRow, 2);
        String role = (String) tableModel.getValueAt(modelRow, 4);

        // role order: pharma -> manager -> admin
        String newRole;
        if (promote) {
            switch (role) {
                case "Pharmacist":
                    newRole = "Manager";
                    break;
                case "Manager":
                    newRole = "Admin";
                    break;
                default:
                    JOptionPane.showMessageDialog(this,
                            username + " is already assigned the highest role",
                            "cannot promote further", JOptionPane.WARNING_MESSAGE);
                    return;
            }
        }
        else {
            switch (role) {
                case "Admin":
                    newRole = "Manager";
                    break;
                case "Manager":
                    newRole = "Pharmacist";
                    break;
                default:
                    JOptionPane.showMessageDialog(this,
                            username + " is already assigned the lowest role",
                            "cannot demote further", JOptionPane.WARNING_MESSAGE);
                    return;
            }
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "do you want to change " + username + "'s role from " + role + " to " + newRole + "?",
                "confirm", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) { // cancelled change exit out
            return;
        };

        // db change
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newRole);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            loadUsers();
        } catch (SQLException ex) {
            showError("error changing role: " + ex.getMessage());
        }
    }





    private void loadUsers() {
        tableModel.setRowCount(0);
        String sql = "SELECT id, name, username, password, role FROM users ORDER BY role, name";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                });
            }
        } catch (SQLException ex) {
            showError("Could not load users: " + ex.getMessage());
        }
    }

    private void showAddUserDialog() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Add New Staff User", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(380, 320);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationRelativeTo(this);

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.DARK_HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("New Staff Account");
        title.setFont(UITheme.FONT_TITLE);
        title.setForeground(Color.WHITE);
        header.add(title);
        dialog.add(header, BorderLayout.NORTH);

        // add user form needs name, pass, role
        JTextField nameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passField = new JPasswordField();
        JComboBox<String> roleBox = new JComboBox<>(new String[]{"Pharmacist", "Manager", "Admin"});

        UITheme.styleTextField(nameField);
        UITheme.styleTextField(usernameField);
        UITheme.styleTextField(passField);
        roleBox.setFont(UITheme.FONT_BODY);
        roleBox.setPreferredSize(new Dimension(200, 34));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UITheme.LIGHT_BG);
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(6, 0, 2, 10);
        lc.gridx = 0; lc.weightx = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(6, 0, 2, 0);
        fc.gridx = 1; fc.weightx = 1;

        Component[] fields = {nameField, usernameField, passField, roleBox};

        for (int i = 0; i < fields.length; i++) {
            lc.gridy = fc.gridy = i;
            JLabel lbl = new JLabel(new String[]{"Name", "Username", "Password", "Role"}[i] + ":");
            lbl.setFont(UITheme.FONT_BOLD);
            form.add(lbl, lc);
            form.add(fields[i], fc);
        }

        dialog.add(form, BorderLayout.CENTER);

        // buttons
        JButton saveBtn   = UITheme.successBtn("Save");
        JButton cancelBtn = UITheme.secondaryBtn("Cancel");

        cancelBtn.addActionListener(e -> dialog.dispose());
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            String role = (String) roleBox.getSelectedItem();

            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                showError("All fields are required.", dialog);
                return;
            }

            if (insertUser(name, username, password, role)) {
                dialog.dispose();
                loadUsers();
            }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnRow.setBackground(UITheme.LIGHT_BG);
        btnRow.add(cancelBtn);
        btnRow.add(saveBtn);
        dialog.add(btnRow, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }



    private void removeSelectedUser() {
        // remove the selected user selection will be done by clicking on it

        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            showError("No user has been selected");
            return;

        }

        int modelRow = table.convertRowIndexToModel(viewRow);
        int id = (int) tableModel.getValueAt(modelRow, 0);

        String username = (String) tableModel.getValueAt(modelRow, 2); // get username from table

        int confirm = JOptionPane.showConfirmDialog(this, "Remove user: " + username, "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            loadUsers();
        } catch (SQLException ex) {
            showError("Error removing user: " + ex.getMessage());
        }
    }

    private boolean insertUser(String name, String username, String password, String role) {
        String sql = "INSERT INTO users (name, username, password, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, username);
            stmt.setString(3, password);
            stmt.setString(4, role);
            stmt.executeUpdate();
            return true;

        } catch (SQLException ex) {
            if (ex.getMessage().contains("UNIQUE")) { // if username is already taken show error
                showError("Username " + username + " is already taken");
            } else { // db error
                showError("Error could not insert user: " + ex.getMessage());
            }

            return false;
        }

    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showError(String message, Component parent) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);

    }


}
