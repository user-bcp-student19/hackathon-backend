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

@ApplicationScoped
public class WalletService {
    @Inject
    WalletRepository wallets;

    @Inject
    BcpWalletClient bcpWallet;

    public synchronized Wallet create(String userId) {
        if (wallets.findByUser(userId).isPresent()) {
            throw walletAlreadyExists();
        }
        BcpWalletClient.ExternalWallet externalWallet = bcpWallet.createWalletForUser(userId);
        StoredWallet wallet = new StoredWallet(
                externalWallet.walletId(),
                userId,
                localStatus(externalWallet.status()),
                Instant.now().toString());
        if (!wallets.insert(wallet)) {
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

    private static String localStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "active";
            case "PENDING" -> "pending";
            case "REJECTED" -> "rejected";
            default -> throw new ApiException(502,
                    new ApiError("BCP_WALLET_UNAVAILABLE", "El proveedor externo devolvió un estado inválido"));
        };
    }

    private Wallet toWallet(StoredWallet wallet) {
        return new Wallet(
                wallet.walletId(),
                wallet.status(),
                true,
                wallet.createdAt());
    }
}
