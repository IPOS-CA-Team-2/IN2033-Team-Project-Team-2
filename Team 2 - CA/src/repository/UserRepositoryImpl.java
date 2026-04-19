package repository;

import db.DatabaseManager;
import model.Admin;
import model.Manager;
import model.Pharmacist;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// sqlite implementation of user repository
public class UserRepositoryImpl implements UserRepository {

    @Override
    public User findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String sql = "SELECT username, password, role, name FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String uname = rs.getString("username");
                String pass  = rs.getString("password");
                String role  = rs.getString("role");
                String name  = rs.getString("name");

                switch (role) {
                    case "Admin":
                        return new Admin(uname, pass, name);
                    case "Pharmacist":
                        return new Pharmacist(uname, pass, name);
                    case "Manager":
                        return new Manager(uname, pass, name);
                    default:
                        return null;
                }
            }

        } catch (SQLException e) {
            System.err.println("Database error in findByUsername: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean validateCredentials(String username, String password) {
        User user = this.findByUsername(username);
        return user != null && user.checkPassword(password);
    }
}
