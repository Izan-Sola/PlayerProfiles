package com.ShinyShadow_.profileplugin.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SQLiteProfileStorage implements ProfileStorage {

    private final JavaPlugin plugin;
    private final String dbFileName;
    private Connection connection;

    private final Executor dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ProfilePlugin-DB");
        t.setDaemon(true);
        return t;
    });

    public SQLiteProfileStorage(JavaPlugin plugin, String dbFileName) {
        this.plugin = plugin;
        this.dbFileName = dbFileName;
    }

    @Override
    public void init() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), dbFileName);
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS profiles (\n" +
                        "    uuid TEXT NOT NULL,\n" +
                        "    field TEXT NOT NULL,\n" +
                        "    value TEXT NOT NULL,\n" +
                        "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                        "    PRIMARY KEY (uuid, field)\n" +
                        ")");
            }
            plugin.getLogger().info("SQLite profile storage initialized (" + dbFile.getName() + ").");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite storage: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Map<String, String>> getProfile(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            // Use LinkedHashMap to preserve insertion order
            Map<String, String> fields = new LinkedHashMap<>();
            // Order by created_at to get fields in the order they were set
            String sql = "SELECT field, value FROM profiles WHERE uuid = ? ORDER BY created_at ASC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        fields.put(rs.getString("field"), rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to read profile for " + uuid + ": " + e.getMessage());
            }
            return fields;
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> setField(UUID uuid, String field, String value) {
        return CompletableFuture.runAsync(() -> {
            // First check if the field exists
            String checkSql = "SELECT COUNT(*) FROM profiles WHERE uuid = ? AND field = ?";
            boolean exists = false;
            try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, field);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        exists = rs.getInt(1) > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check field existence: " + e.getMessage());
            }

            String sql;
            if (exists) {
                // Update existing field - keep the original created_at
                sql = "UPDATE profiles SET value = ? WHERE uuid = ? AND field = ?";
            } else {
                // Insert new field with current timestamp
                sql = "INSERT INTO profiles (uuid, field, value, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
            }

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (exists) {
                    ps.setString(1, value);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, field);
                } else {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, field);
                    ps.setString(3, value);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set field '" + field + "' for " + uuid + ": " + e.getMessage());
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> clearField(UUID uuid, String field) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM profiles WHERE uuid = ? AND field = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, field);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear field '" + field + "' for " + uuid + ": " + e.getMessage());
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> clearProfile(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM profiles WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clear profile for " + uuid + ": " + e.getMessage());
            }
        }, dbExecutor);
    }
}