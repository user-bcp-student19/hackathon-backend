package com.bcp.hackathon.api;

import com.bcp.hackathon.api.ApiModels.KycRequest;
import com.bcp.hackathon.security.AuthenticatedUser;
import com.bcp.hackathon.service.KycService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("kyc/verifications")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class KycResource {
    @Inject
    KycService kyc;

    @POST
    public Response submit(@Context SecurityContext security, KycRequest body) {
        return Response.status(Response.Status.CREATED)
                .entity(kyc.submit(AuthenticatedUser.idOf(security), body))
                .build();
    }

    @GET
    public Response latest(@Context SecurityContext security) {
        return Response.ok(kyc.latest(AuthenticatedUser.idOf(security))).build();
    }
}
