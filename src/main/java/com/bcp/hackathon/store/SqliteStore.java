package com.bcp.hackathon.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SqliteStore {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "wallet.db.path")
    String databasePath;

    private Connection connection;

    @jakarta.annotation.PostConstruct
    void initialize() {
        try {
            Path path = Path.of(databasePath).toAbsolutePath();
            Files.createDirectories(path.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS records (
                            record_type TEXT NOT NULL,
                            record_id TEXT NOT NULL,
                            payload TEXT NOT NULL,
                            PRIMARY KEY (record_type, record_id)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS users (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                            phone TEXT NOT NULL,
                            password_hash TEXT NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS wallets (
                            wallet_id TEXT PRIMARY KEY,
                            user_id TEXT UNIQUE,
                            status TEXT NOT NULL,
                            created_at TEXT
                        )
                        """);
            }
            migrateWalletRecords();
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("Cannot initialize SQLite storage", exception);
        }
    }

    private void migrateWalletRecords() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT OR IGNORE INTO wallets (wallet_id, user_id, status, created_at)
                    SELECT json_extract(payload, '$.id'),
                           json_extract(payload, '$.userId'),
                           json_extract(payload, '$.status'),
                           json_extract(payload, '$.createdAt')
                    FROM records
                    WHERE record_type = 'wallets'
                      AND json_extract(payload, '$.id') IS NOT NULL
                      AND json_extract(payload, '$.status') IS NOT NULL
                    ORDER BY rowid
                    """);
        }
    }

    @FunctionalInterface
    public interface ConnectionWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    public synchronized <T> T withConnection(ConnectionWork<T> work) {
        try {
            return work.apply(connection);
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    public synchronized Map<String, Object> get(String type, String id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload FROM records WHERE record_type = ? AND record_id = ?")) {
            statement.setString(1, type);
            statement.setString(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? decode(resultSet.getString(1)) : null;
            }
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    public synchronized List<Map<String, Object>> list(String type) {
        List<Map<String, Object>> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload FROM records WHERE record_type = ? ORDER BY rowid")) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(decode(resultSet.getString(1)));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    public synchronized void put(String type, String id, Map<String, Object> record) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO records (record_type, record_id, payload) VALUES (?, ?, ?)
                ON CONFLICT(record_type, record_id) DO UPDATE SET payload = excluded.payload
                """)) {
            statement.setString(1, type);
            statement.setString(2, id);
            statement.setString(3, objectMapper.writeValueAsString(record));
            statement.executeUpdate();
        } catch (SQLException | IOException exception) {
            throw storageFailure(exception);
        }
    }

    public synchronized void delete(String type, String id) {
        execute("DELETE FROM records WHERE record_type = ? AND record_id = ?", type, id);
    }

    public synchronized void deleteType(String type) {
        execute("DELETE FROM records WHERE record_type = ?", type);
    }

    public synchronized void clear() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM records");
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    public synchronized void clearBusinessData() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM records WHERE record_type <> 'http_logs'");
            statement.executeUpdate("DELETE FROM users");
            statement.executeUpdate("DELETE FROM wallets");
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    private void execute(String sql, String... parameters) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    private Map<String, Object> decode(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot decode SQLite record", exception);
        }
    }

    private IllegalStateException storageFailure(Exception exception) {
        return new IllegalStateException("SQLite operation failed", exception);
    }

    @PreDestroy
    void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }
}
