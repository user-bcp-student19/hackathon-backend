package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.RegisterRequest;
import com.bcp.hackathon.api.ApiModels.User;
import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserService {
    private static final String TYPE = "users";
    private static final int PASSWORD_MIN_CHARS = 8;
    private static final int PASSWORD_MAX_BYTES = 72;

    @Inject
    SqliteStore store;

    public synchronized User register(RegisterRequest request) {
        RegisterRequest body = Objects.requireNonNullElse(request, new RegisterRequest(null, null, null, null));
        FieldValidation validation = new FieldValidation();
        validation.required("name", body.name(), 120);
        validation.required("email", body.email(), 254);
        validation.pattern("email", body.email(), FieldValidation.EMAIL, "Formato de correo inválido");
        validation.required("phone", body.phone(), 16);
        validation.pattern("phone", body.phone(), FieldValidation.PHONE,
                "Debe tener 9 a 15 dígitos, con prefijo + opcional");
        validatePassword(validation, body.password());
        validation.throwIfAny();

        if (findRecordByEmail(body.email()).isPresent()) {
            throw new ApiException(409, new ApiError("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado"));
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", body.name());
        record.put("email", body.email());
        record.put("phone", body.phone());
        record.put("createdAt", Instant.now().toString());
        record.put("passwordHash", PasswordHasher.hash(body.password()));
        store.put(TYPE, id, record);
        return toUser(record);
    }

    public Optional<User> authenticate(String email, String password) {
        if (email == null || password == null
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return Optional.empty();
        }
        return findRecordByEmail(email)
                .filter(record -> PasswordHasher.matches(password, (String) record.get("passwordHash")))
                .map(UserService::toUser);
    }

    private static void validatePassword(FieldValidation validation, String password) {
        if (password == null || password.codePointCount(0, password.length()) < PASSWORD_MIN_CHARS) {
            validation.add("password", "Mínimo 8 caracteres");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            validation.add("password", "Máximo 72 bytes");
        }
    }

    private Optional<Map<String, Object>> findRecordByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return store.list(TYPE).stream()
                .filter(record -> email.equalsIgnoreCase((String) record.get("email")))
                .findFirst();
    }

    private static User toUser(Map<String, Object> record) {
        return new User(
                (String) record.get("id"),
                (String) record.get("name"),
                (String) record.get("email"),
                (String) record.get("phone"),
                (String) record.get("createdAt"));
    }
}
