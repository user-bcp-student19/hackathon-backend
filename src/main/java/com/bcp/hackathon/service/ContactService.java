package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.Contact;
import com.bcp.hackathon.api.ApiModels.ContactRequest;
import com.bcp.hackathon.store.ContactRepository;
import com.bcp.hackathon.store.ContactRepository.StoredContact;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class ContactService {
    @Inject
    ContactRepository contacts;

    @Inject
    WalletService wallets;

    public List<Contact> list(String ownerId) {
        return contacts.findByOwner(ownerId).stream()
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
        validation.required("wallet_id", body.walletId(), 23);
        validation.pattern("wallet_id", body.walletId(), FieldValidation.WALLET_ID,
                "Formato esperado: wallet_ seguido de 16 caracteres hexadecimales");
        validation.throwIfAny();

        if (!wallets.exists(body.walletId())) {
            throw new ApiException(422, new ApiError("WALLET_NOT_FOUND",
                    "El wallet_id no corresponde a una wallet existente"));
        }
        StoredContact contact = new StoredContact(
                UUID.randomUUID().toString(),
                ownerId,
                body.name(),
                body.phone(),
                body.walletId(),
                Instant.now().toString());
        contacts.insert(contact);
        return toContact(contact);
    }

    private static Contact toContact(StoredContact contact) {
        return new Contact(
                contact.id(),
                contact.name(),
                contact.phone(),
                contact.walletId(),
                contact.createdAt());
    }
}
