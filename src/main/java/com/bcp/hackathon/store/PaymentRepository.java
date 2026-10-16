package com.bcp.hackathon.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@ApplicationScoped
public class PaymentRepository {
    @Inject
    SqliteStore store;

    public record StoredPayment(String ownerId, String idempotencyKey, String walletId, String recipient,
                                long amountCents, String currency, String scenario, String responseBody,
                                String createdAt) {
    }

    public Optional<StoredPayment> findByOwnerAndIdempotencyKey(String ownerId, String idempotencyKey) {
        return store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT owner_id, idempotency_key, wallet_id, recipient, amount_cents,
                           currency, scenario, response_body, created_at
                    FROM payments
                    WHERE owner_id = ? AND idempotency_key = ?
                    """)) {
                statement.setString(1, ownerId);
                statement.setString(2, idempotencyKey);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readPayment(resultSet));
                }
            }
        });
    }

    public void insert(StoredPayment payment) {
        store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payments (
                        owner_id, idempotency_key, wallet_id, recipient, amount_cents,
                        currency, scenario, response_body, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, payment.ownerId());
                statement.setString(2, payment.idempotencyKey());
                statement.setString(3, payment.walletId());
                statement.setString(4, payment.recipient());
                statement.setLong(5, payment.amountCents());
                statement.setString(6, payment.currency());
                statement.setString(7, payment.scenario());
                statement.setString(8, payment.responseBody());
                statement.setString(9, payment.createdAt());
                statement.executeUpdate();
                return null;
            }
        });
    }

    private static StoredPayment readPayment(ResultSet resultSet) throws SQLException {
        return new StoredPayment(
                resultSet.getString("owner_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("wallet_id"),
                resultSet.getString("recipient"),
                resultSet.getLong("amount_cents"),
                resultSet.getString("currency"),
                resultSet.getString("scenario"),
                resultSet.getString("response_body"),
                resultSet.getString("created_at"));
    }
}
