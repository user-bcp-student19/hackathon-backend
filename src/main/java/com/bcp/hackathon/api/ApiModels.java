package com.bcp.hackathon.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record ApiError(String code, String message) {
    }

    public record FieldError(String field, String message) {
    }

    public record ValidationError(String code, String message, List<FieldError> fields) {
    }

    public record RegisterRequest(String name, String email, String phone, String password) {
    }

    public record User(String id, String name, String email, String phone, String createdAt) {
    }

    public record Wallet(@JsonProperty("wallet_id") String walletId,
                         String status,
                         @JsonProperty("payments_enabled") boolean paymentsEnabled,
                         String createdAt) {
    }

    public record ContactRequest(String name, String phone, @JsonProperty("wallet_id") String walletId) {
    }

    public record Contact(String id, String name, String phone,
                          @JsonProperty("wallet_id") String walletId, String createdAt) {
    }

    public record PaymentRequest(String recipient, Long amountCents, String currency, String scenario) {
    }

    public record KycRequest(String documentType, String documentNumber, String fullName, String birthDate) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KycVerification(String id, String status, String message,
                                  String providerReference, String updatedAt) {
    }
}
