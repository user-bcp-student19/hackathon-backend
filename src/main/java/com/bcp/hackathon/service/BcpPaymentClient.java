package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
public class BcpPaymentClient {
    private static final String PAYMENTS_PATH = "/groups/3/payments";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = Logger.getLogger(BcpPaymentClient.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "bcp.wallet.base-url", defaultValue = "https://bcp-wallet-mock.yefri.workers.dev")
    String baseUrl;

    @ConfigProperty(name = "bcp.payment.wallet-id", defaultValue = "wallet-demo")
    String providerWalletId;

    @ConfigProperty(name = "bcp.payment.contact-recipient", defaultValue = "contact-demo")
    String providerContactRecipient;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public JsonNode createPayment(String idempotencyKey, PaymentCommand payment) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + PAYMENTS_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "*/*")
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapperWritePayment(payment)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "BCP payment request was interrupted", exception);
            throw providerUnavailable();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "BCP payment request failed", exception);
            throw providerUnavailable();
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String requestId = response.headers().firstValue("X-Request-Id").orElse("not-provided");
            LOGGER.warning("BCP payment provider returned HTTP " + response.statusCode()
                    + " (code=" + providerErrorCode(response.body()) + ", requestId=" + requestId + ")");
            throw providerUnavailable();
        }

        try {
            JsonNode paymentResponse = objectMapper.readTree(response.body());
            if (paymentResponse == null || !paymentResponse.isObject()) {
                LOGGER.warning("BCP payment provider response is not a JSON object");
                throw providerUnavailable();
            }
            return paymentResponse;
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.WARNING, "Could not parse BCP payment response", exception);
            throw providerUnavailable();
        }
    }

    private static ApiException providerUnavailable() {
        return new ApiException(502,
                new ApiError("BCP_PAYMENT_UNAVAILABLE", "No fue posible procesar el pago con el proveedor externo"));
    }

    private String objectMapperWritePayment(PaymentCommand payment) {
        try {
            return objectMapper.writeValueAsString(new ProviderPaymentRequest(
                    providerWalletId,
                    providerContactRecipient,
                    payment.amountCents(),
                    payment.currency(),
                    payment.scenario()));
        } catch (JsonProcessingException exception) {
            LOGGER.log(Level.SEVERE, "Could not serialize BCP payment request", exception);
            throw providerUnavailable();
        }
    }

    private String providerErrorCode(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            JsonNode code = body == null ? null : body.get("code");
            return code != null && code.isTextual() ? code.asText() : "not-provided";
        } catch (JsonProcessingException exception) {
            return "not-provided";
        }
    }

    public record PaymentCommand(String walletId, String recipient, long amountCents, String currency,
                                 String scenario) {
    }

    private record ProviderPaymentRequest(String walletId, String recipient, long amountCents, String currency,
                                          String scenario) {
    }
}
