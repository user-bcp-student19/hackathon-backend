package com.bcp.hackathon.api;

import com.bcp.hackathon.security.AuthenticatedUser;
import com.bcp.hackathon.service.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("wallet")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {
    @Inject
    WalletService wallets;

    @GET
    public Response get(@Context SecurityContext security) {
        return Response.ok(wallets.getByUser(AuthenticatedUser.idOf(security))).build();
    }

    @POST
    public Response create(@Context SecurityContext security) {
        return Response.status(Response.Status.CREATED)
                .entity(wallets.create(AuthenticatedUser.idOf(security)))
                .build();
    }
}
