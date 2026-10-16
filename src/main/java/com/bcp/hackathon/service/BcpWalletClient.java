package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class BcpWalletClient {
    private static final String VERIFICATIONS_PATH = "/groups/3/kyc/verifications";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = Logger.getLogger(BcpWalletClient.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "bcp.wallet.base-url", defaultValue = "https://bcp-wallet-mock.yefri.workers.dev")
    String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ExternalWallet createWalletForUser(String userId) {
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(
                    new VerificationRequest("TEST", "TEST-" + userId, "APPROVED"));
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.SEVERE, "Could not serialize BCP wallet request", exception);
            throw providerUnavailable();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + VERIFICATIONS_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "BCP wallet request was interrupted", exception);
            throw providerUnavailable();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "BCP wallet request failed", exception);
            throw providerUnavailable();
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String requestId = response.headers().firstValue("X-Request-Id").orElse("not-provided");
            LOGGER.warning("BCP wallet provider returned HTTP " + response.statusCode()
                    + " (requestId=" + requestId + ")");
            throw providerUnavailable();
        }

        ExternalWallet wallet;
        try {
            wallet = objectMapper.readValue(response.body(), ExternalWallet.class);
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.WARNING, "Could not parse BCP wallet response", exception);
            throw providerUnavailable();
        }
        if (wallet.walletId() == null || wallet.walletId().isBlank() || wallet.status() == null) {
            LOGGER.warning("BCP wallet provider response is missing wallet_id or status");
            throw providerUnavailable();
        }
        return wallet;
    }

    private static ApiException providerUnavailable() {
        return new ApiException(502,
                new ApiError("BCP_WALLET_UNAVAILABLE", "No fue posible crear la wallet con el proveedor externo"));
    }

    private record VerificationRequest(String documentType, String document, String scenario) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalWallet(@JsonProperty("wallet_id") String walletId, String status) {
    }
}
