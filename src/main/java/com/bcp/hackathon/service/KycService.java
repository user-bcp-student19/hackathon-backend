package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.KycRequest;
import com.bcp.hackathon.api.ApiModels.KycVerification;
import com.bcp.hackathon.service.KycProvider.ProviderResult;
import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class KycService {
    private static final String TYPE = "kyc_verifications";
    private static final Set<String> DOCUMENT_TYPES = Set.of("DNI", "CE", "PASSPORT");

    @Inject
    SqliteStore store;

    @Inject
    KycProvider provider;

    public synchronized KycVerification submit(String userId, KycRequest request) {
        KycRequest body = Objects.requireNonNullElse(request, new KycRequest(null, null, null, null));
        FieldValidation validation = new FieldValidation();
        if (body.documentType() == null || !DOCUMENT_TYPES.contains(body.documentType())) {
            validation.add("documentType", "Debe ser DNI, CE o PASSPORT");
        }
        validation.required("documentNumber", body.documentNumber(), 20);
        validation.required("fullName", body.fullName(), 120);
        validation.required("birthDate", body.birthDate(), 10);
        if (body.birthDate() != null && !body.birthDate().isBlank() && !isIsoDate(body.birthDate())) {
            validation.add("birthDate", "Formato de fecha esperado: YYYY-MM-DD");
        }
        validation.throwIfAny();

        if (isApproved(userId)) {
            throw new ApiException(409,
                    new ApiError("KYC_ALREADY_APPROVED", "El usuario ya tiene una verificación aprobada"));
        }
        ProviderResult result = provider.verify(body);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", UUID.randomUUID().toString());
        record.put("userId", userId);
        record.put("status", result.status());
        record.put("message", result.message());
        record.put("providerReference", result.providerReference());
        record.put("updatedAt", Instant.now().toString());
        store.put(TYPE, (String) record.get("id"), record);
        return toVerification(record);
    }

    public KycVerification latest(String userId) {
        return findLatest(userId).map(KycService::toVerification)
                .orElseThrow(() -> new ApiException(404,
                        new ApiError("KYC_NOT_FOUND", "No existe verificación para el usuario")));
    }

    public boolean isApproved(String userId) {
        return findLatest(userId).map(record -> "approved".equals(record.get("status"))).orElse(false);
    }

    private Optional<Map<String, Object>> findLatest(String userId) {
        List<Map<String, Object>> records = store.list(TYPE).stream()
                .filter(record -> userId.equals(record.get("userId")))
                .toList();
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(records.size() - 1));
    }

    private static boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static KycVerification toVerification(Map<String, Object> record) {
        return new KycVerification(
                (String) record.get("id"),
                (String) record.get("status"),
                (String) record.get("message"),
                (String) record.get("providerReference"),
                (String) record.get("updatedAt"));
    }
}
