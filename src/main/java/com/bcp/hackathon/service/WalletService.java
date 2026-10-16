package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.Wallet;
import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WalletService {
    private static final String TYPE = "wallets";

    @Inject
    SqliteStore store;

    @Inject
    KycService kyc;

    public synchronized Wallet create(String userId) {
        if (findRecordByUser(userId).isPresent()) {
            throw new ApiException(409, new ApiError("WALLET_ALREADY_EXISTS", "El usuario ya tiene wallet"));
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("userId", userId);
        record.put("status", "active");
        record.put("createdAt", Instant.now().toString());
        store.put(TYPE, id, record);
        return toWallet(record);
    }

    public Wallet getByUser(String userId) {
        return findRecordByUser(userId).map(this::toWallet)
                .orElseThrow(() -> new ApiException(404,
                        new ApiError("WALLET_NOT_FOUND", "El usuario aún no tiene wallet")));
    }

    public boolean exists(String walletId) {
        return store.get(TYPE, walletId) != null;
    }

    private Optional<Map<String, Object>> findRecordByUser(String userId) {
        return store.list(TYPE).stream()
                .filter(record -> userId.equals(record.get("userId")))
                .findFirst();
    }

    private Wallet toWallet(Map<String, Object> record) {
        String userId = (String) record.get("userId");
        return new Wallet(
                (String) record.get("id"),
                (String) record.get("status"),
                kyc.isApproved(userId),
                (String) record.get("createdAt"));
    }
}
