package com.bcp.hackathon.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

final class PasswordHasher {
    private static final int COST = 12;

    private PasswordHasher() {
    }

    static String hash(String password) {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    static boolean matches(String password, String hash) {
        return BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray()).verified;
    }
}
