package com.bcp.hackathon.security;

import com.bcp.hackathon.api.ApiModels.User;

import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

public record AuthenticatedUser(User user) implements Principal {
    @Override
    public String getName() {
        return user.id();
    }

    public static String idOf(SecurityContext security) {
        return security.getUserPrincipal().getName();
    }
}
