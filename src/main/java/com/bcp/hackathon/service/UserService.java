package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.RegisterRequest;
import com.bcp.hackathon.api.ApiModels.User;
import com.bcp.hackathon.store.UserRepository;
import com.bcp.hackathon.store.UserRepository.StoredUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserService {
    private static final int PASSWORD_MIN_CHARS = 8;
    private static final int PASSWORD_MAX_BYTES = 72;

    @Inject
    UserRepository users;

    public User register(RegisterRequest request) {
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

        StoredUser stored = new StoredUser(
                UUID.randomUUID().toString(),
                body.name(),
                body.email(),
                body.phone(),
                PasswordHasher.hash(body.password()),
                Instant.now().toString());
        if (!users.insert(stored)) {
            throw new ApiException(409, new ApiError("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado"));
        }
        return toUser(stored);
    }

    public Optional<User> authenticate(String email, String password) {
        if (email == null || password == null
                || password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            return Optional.empty();
        }
        return users.findByEmail(email)
                .filter(user -> PasswordHasher.matches(password, user.passwordHash()))
                .map(UserService::toUser);
    }

    private static void validatePassword(FieldValidation validation, String password) {
        if (password == null || password.codePointCount(0, password.length()) < PASSWORD_MIN_CHARS) {
            validation.add("password", "Mínimo 8 caracteres");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            validation.add("password", "Máximo 72 bytes");
        }
    }

    private static User toUser(StoredUser user) {
        return new User(user.id(), user.name(), user.email(), user.phone(), user.createdAt());
    }
}
