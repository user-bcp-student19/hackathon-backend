package com.bcp.hackathon.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static String hash(char[] password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
                    .getEncoded();
            Base64.Encoder encoder = Base64.getEncoder();
            return "pbkdf2-sha256$" + ITERATIONS + "$"
                    + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot hash password", exception);
        } finally {
            keySpec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }
}
