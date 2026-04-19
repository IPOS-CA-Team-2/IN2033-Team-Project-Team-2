package repository;

import db.DatabaseManager;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

// reads and writes key/value config entries from the config table
public class ConfigRepository {

    public String get(String key) {
        String sql = "SELECT value FROM config WHERE key = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        }
        catch (SQLException ex) {
            System.err.println("error: " + ex.getMessage());
        }
        return "";
    }

    public boolean set(String key, String value) {
        String sql = "UPDATE config SET value = ? WHERE key = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.setString(2, key);
            stmt.executeUpdate();
            return true;
        }
        catch (SQLException ex) {
            System.err.println("error: " + ex.getMessage());
            return false;
        }
    }

    public Map<String, String> getAll() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT key, value FROM config";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
        }
        catch (SQLException ex) {
            System.err.println("error: " + ex.getMessage());
        }
        return map;
    }
}