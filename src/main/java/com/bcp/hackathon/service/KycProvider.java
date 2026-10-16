package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiModels.KycRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

// Stub: el proveedor KYC real está pendiente (ADR-001). Reemplazar por el cliente del proveedor.
@ApplicationScoped
public class KycProvider {
    public ProviderResult verify(KycRequest request) {
        return new ProviderResult("approved", null, "stub-" + UUID.randomUUID());
    }

    public record ProviderResult(String status, String message, String providerReference) {
    }
}
