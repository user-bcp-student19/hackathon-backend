package com.bcp.hackathon.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class WalletRepository {
    private static final String UNIQUE_USER_VIOLATION = "UNIQUE constraint failed: wallets.user_id";

    @Inject
    SqliteStore store;

    public record StoredWallet(String walletId, String userId, String status, String createdAt) {
    }

    public boolean insert(StoredWallet wallet) {
        return store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO wallets (wallet_id, user_id, status, created_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setString(1, wallet.walletId());
                statement.setString(2, wallet.userId());
                statement.setString(3, wallet.status());
                statement.setString(4, wallet.createdAt());
                statement.executeUpdate();
                return true;
            } catch (SQLException exception) {
                if (exception.getMessage() != null
                        && exception.getMessage().contains(UNIQUE_USER_VIOLATION)) {
                    return false;
                }
                throw exception;
            }
        });
    }

    public Optional<StoredWallet> findByUser(String userId) {
        return find("SELECT wallet_id, user_id, status, created_at FROM wallets WHERE user_id = ?", userId);
    }

    public Optional<StoredWallet> findById(String walletId) {
        return find("SELECT wallet_id, user_id, status, created_at FROM wallets WHERE wallet_id = ?", walletId);
    }

    public boolean exists(String walletId) {
        return findById(walletId).isPresent();
    }

    private Optional<StoredWallet> find(String sql, String value) {
        return store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new StoredWallet(
                            resultSet.getString("wallet_id"),
                            resultSet.getString("user_id"),
                            resultSet.getString("status"),
                            resultSet.getString("created_at")));
                }
            }
        });
    }
}
