package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.Contact;
import com.bcp.hackathon.api.ApiModels.ContactRequest;
import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class ContactService {
    private static final String TYPE = "contacts";

    @Inject
    SqliteStore store;

    @Inject
    WalletService wallets;

    public List<Contact> list(String ownerId) {
        return store.list(TYPE).stream()
                .filter(record -> ownerId.equals(record.get("ownerId")))
                .map(ContactService::toContact)
                .toList();
    }

    public synchronized Contact create(String ownerId, ContactRequest request) {
        ContactRequest body = Objects.requireNonNullElse(request, new ContactRequest(null, null, null));
        FieldValidation validation = new FieldValidation();
        validation.required("name", body.name(), 120);
        validation.required("phone", body.phone(), 16);
        validation.pattern("phone", body.phone(), FieldValidation.PHONE,
                "Debe tener 9 a 15 dígitos, con prefijo + opcional");
        validation.required("wallet_id", body.walletId(), 36);
        validation.pattern("wallet_id", body.walletId(), FieldValidation.UUID, "Formato UUID inválido");
        validation.throwIfAny();

        if (!wallets.exists(body.walletId())) {
            throw new ApiException(422, new ApiError("WALLET_NOT_FOUND",
                    "El wallet_id no corresponde a una wallet existente"));
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("ownerId", ownerId);
        record.put("name", body.name());
        record.put("phone", body.phone());
        record.put("walletId", body.walletId());
        record.put("createdAt", Instant.now().toString());
        store.put(TYPE, id, record);
        return toContact(record);
    }

    private static Contact toContact(Map<String, Object> record) {
        return new Contact(
                (String) record.get("id"),
                (String) record.get("name"),
                (String) record.get("phone"),
                (String) record.get("walletId"),
                (String) record.get("createdAt"));
    }
}
