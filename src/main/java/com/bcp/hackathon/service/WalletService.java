package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.Wallet;
import com.bcp.hackathon.store.WalletRepository;
import com.bcp.hackathon.store.WalletRepository.StoredWallet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WalletService {
    @Inject
    WalletRepository wallets;

    @Inject
    KycService kyc;

    public synchronized Wallet create(String userId) {
        String walletId = UUID.randomUUID().toString();
        StoredWallet wallet = new StoredWallet(walletId, userId, "active", Instant.now().toString());
        if (wallets.findByUser(userId).isPresent() || !wallets.insert(wallet)) {
            throw walletAlreadyExists();
        }
        return toWallet(wallet);
    }

    public Wallet getByUser(String userId) {
        return wallets.findByUser(userId).map(this::toWallet)
                .orElseThrow(() -> new ApiException(404,
                        new ApiError("WALLET_NOT_FOUND", "El usuario aún no tiene wallet")));
    }

    public boolean exists(String walletId) {
        return wallets.exists(walletId);
    }

    private static ApiException walletAlreadyExists() {
        return new ApiException(409, new ApiError("WALLET_ALREADY_EXISTS", "El usuario ya tiene wallet"));
    }

    private Wallet toWallet(StoredWallet wallet) {
        return new Wallet(
                wallet.walletId(),
                wallet.status(),
                kyc.isApproved(wallet.userId()),
                wallet.createdAt());
    }
}
