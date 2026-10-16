package com.bcp.hackathon.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class UserRepository {
    private static final String UNIQUE_VIOLATION = "UNIQUE constraint failed";

    @Inject
    SqliteStore store;

    public record StoredUser(String id, String name, String email, String phone,
                             String passwordHash, String createdAt) {
    }

    /**
     * @return {@code false} when the email is already registered (case-insensitive), {@code true} when inserted.
     */
    public boolean insert(StoredUser user) {
        return store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (id, name, email, phone, password_hash, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, user.id());
                statement.setString(2, user.name());
                statement.setString(3, user.email());
                statement.setString(4, user.phone());
                statement.setString(5, user.passwordHash());
                statement.setString(6, user.createdAt());
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains(UNIQUE_VIOLATION)) {
                    return false;
                }
                throw exception;
            }
        });
    }

    public Optional<StoredUser> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, email, phone, password_hash, created_at
                    FROM users WHERE email = ?
                    """)) {
                statement.setString(1, email);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new StoredUser(
                            resultSet.getString("id"),
                            resultSet.getString("name"),
                            resultSet.getString("email"),
                            resultSet.getString("phone"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("created_at")));
                }
            }
        });
    }
}
