package com.bcp.hackathon.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ContactRepository {
    @Inject
    SqliteStore store;

    public record StoredContact(String id, String ownerId, String name, String phone,
                                String walletId, String createdAt) {
    }

    public void insert(StoredContact contact) {
        store.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contacts (id, owner_id, name, phone, wallet_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, contact.id());
                statement.setString(2, contact.ownerId());
                statement.setString(3, contact.name());
                statement.setString(4, contact.phone());
                statement.setString(5, contact.walletId());
                statement.setString(6, contact.createdAt());
                statement.executeUpdate();
                return null;
            }
        });
    }

    public List<StoredContact> findByOwner(String ownerId) {
        return store.withConnection(connection -> {
            List<StoredContact> contacts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, owner_id, name, phone, wallet_id, created_at
                    FROM contacts
                    WHERE owner_id = ?
                    ORDER BY rowid
                    """)) {
                statement.setString(1, ownerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        contacts.add(new StoredContact(
                                resultSet.getString("id"),
                                resultSet.getString("owner_id"),
                                resultSet.getString("name"),
                                resultSet.getString("phone"),
                                resultSet.getString("wallet_id"),
                                resultSet.getString("created_at")));
                    }
                }
            }
            return contacts;
        });
    }
}
