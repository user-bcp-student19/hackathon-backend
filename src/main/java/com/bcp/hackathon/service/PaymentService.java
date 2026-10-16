package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.PaymentRequest;
import com.bcp.hackathon.service.BcpPaymentClient.PaymentCommand;
import com.bcp.hackathon.store.ContactRepository;
import com.bcp.hackathon.store.PaymentRepository;
import com.bcp.hackathon.store.PaymentRepository.StoredPayment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

@ApplicationScoped
public class PaymentService {
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    @Inject
    PaymentRepository payments;

    @Inject
    ContactRepository contacts;

    @Inject
    WalletService wallets;

    @Inject
    BcpPaymentClient bcpPayment;

    @Inject
    ObjectMapper objectMapper;

    public synchronized PaymentResult create(String ownerId, String idempotencyKey, PaymentRequest request) {
        PaymentRequest body = Objects.requireNonNullElse(request, new PaymentRequest(null, null, null, null));
        validate(idempotencyKey, body);

        var existing = payments.findByOwnerAndIdempotencyKey(ownerId, idempotencyKey);
        if (existing.isPresent()) {
            StoredPayment stored = existing.get();
            if (!sameRequest(stored, body)) {
                throw new ApiException(409,
                        new ApiError("IDEMPOTENCY_KEY_REUSED", "La clave ya fue usada con otros datos"));
            }
            return new PaymentResult(readResponse(stored.responseBody()), true);
        }

        var wallet = wallets.getByUser(ownerId);
        if (!wallet.paymentsEnabled()) {
            throw new ApiException(403,
                    new ApiError("PAYMENTS_NOT_ENABLED", "La wallet no está habilitada para realizar pagos"));
        }
        if (contacts.findByIdAndOwner(body.recipient(), ownerId).isEmpty()) {
            throw new ApiException(422,
                    new ApiError("CONTACT_NOT_FOUND", "El recipient no corresponde a un contacto del usuario"));
        }

        JsonNode response = bcpPayment.createPayment(
                idempotencyKey,
                new PaymentCommand(
                        wallet.walletId(),
                        body.recipient(),
                        body.amountCents(),
                        body.currency(),
                        body.scenario()));
        String responseBody = writeResponse(response);
        payments.insert(new StoredPayment(
                ownerId,
                idempotencyKey,
                wallet.walletId(),
                body.recipient(),
                body.amountCents(),
                body.currency(),
                body.scenario(),
                responseBody,
                Instant.now().toString()));
        return new PaymentResult(response, false);
    }

    private static void validate(String idempotencyKey, PaymentRequest body) {
        FieldValidation validation = new FieldValidation();
        validation.required("Idempotency-Key", idempotencyKey, 255);
        validation.required("recipient", body.recipient(), 36);
        if (body.amountCents() == null || body.amountCents() <= 0) {
            validation.add("amountCents", "Debe ser un número entero mayor que cero");
        }
        validation.required("currency", body.currency(), 3);
        if (body.currency() != null && !body.currency().isBlank()
                && !CURRENCY.matcher(body.currency()).matches()) {
            validation.add("currency", "Debe ser un código de moneda de tres letras mayúsculas");
        }
        validation.required("scenario", body.scenario(), 32);
        validation.throwIfAny();
    }

    private static boolean sameRequest(StoredPayment stored, PaymentRequest request) {
        return stored.recipient().equals(request.recipient())
                && stored.amountCents() == request.amountCents()
                && stored.currency().equals(request.currency())
                && stored.scenario().equals(request.scenario());
    }

    private JsonNode readResponse(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot decode stored payment response", exception);
        }
    }

    private String writeResponse(JsonNode response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode payment response for storage", exception);
        }
    }

    public record PaymentResult(JsonNode response, boolean replayed) {
    }
}
