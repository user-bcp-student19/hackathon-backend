package com.bcp.hackathon.api;

import com.bcp.hackathon.security.AuthenticatedUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("auth/login")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @POST
    public Response login(@Context SecurityContext security) {
        return Response.ok(((AuthenticatedUser) security.getUserPrincipal()).user()).build();
    }
}
