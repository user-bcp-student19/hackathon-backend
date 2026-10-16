package com.bcp.hackathon.security;

import com.bcp.hackathon.api.ApiModels.ApiError;
import com.bcp.hackathon.api.ApiModels.User;
import com.bcp.hackathon.service.UserService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Optional;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class BasicAuthFilter implements ContainerRequestFilter {
    private static final String CHALLENGE = "Basic realm=\"hackathon-backend\"";

    @Inject
    UserService users;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (isPublic(requestContext)) {
            return;
        }
        Optional<User> user = credentials(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
                .flatMap(pair -> users.authenticate(pair[0], pair[1]));
        if (user.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError("UNAUTHORIZED", "No autenticado o credenciales incorrectas"))
                    .build());
            return;
        }
        SecurityContext original = requestContext.getSecurityContext();
        Principal principal = new AuthenticatedUser(user.get());
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return original.isSecure();
            }

            @Override
            public String getAuthenticationScheme() {
                return SecurityContext.BASIC_AUTH;
            }
        });
    }

    private static boolean isPublic(ContainerRequestContext requestContext) {
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return true;
        }
        String path = requestContext.getUriInfo().getPath().replaceAll("^/+|/+$", "");
        return "POST".equals(requestContext.getMethod()) && "users".equals(path);
    }

    private static Optional<String[]> credentials(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        return Optional.of(new String[]{decoded.substring(0, separator), decoded.substring(separator + 1)});
    }
}
