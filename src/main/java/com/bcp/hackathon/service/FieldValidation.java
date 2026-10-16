package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiException;
import com.bcp.hackathon.api.ApiModels.FieldError;
import com.bcp.hackathon.api.ApiModels.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class FieldValidation {
    static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{9,15}$");
    static final Pattern WALLET_ID = Pattern.compile("^wallet_[0-9a-f]{16}$");

    private final List<FieldError> errors = new ArrayList<>();

    void add(String field, String message) {
        errors.add(new FieldError(field, message));
    }

    void required(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            add(field, "Campo obligatorio");
        } else if (value.codePointCount(0, value.length()) > maxLength) {
            add(field, "Máximo " + maxLength + " caracteres");
        }
    }

    void pattern(String field, String value, Pattern pattern, String message) {
        if (value != null && !value.isBlank() && !pattern.matcher(value).matches()) {
            add(field, message);
        }
    }

    void throwIfAny() {
        if (!errors.isEmpty()) {
            throw new ApiException(400,
                    new ValidationError("VALIDATION_FAILED", "Datos inválidos", List.copyOf(errors)));
        }
    }
}
