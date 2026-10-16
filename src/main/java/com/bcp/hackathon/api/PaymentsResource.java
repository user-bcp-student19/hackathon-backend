package com.bcp.hackathon.api;

import com.bcp.hackathon.api.ApiModels.PaymentRequest;
import com.bcp.hackathon.security.AuthenticatedUser;
import com.bcp.hackathon.service.PaymentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("payments")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PaymentsResource {
    @Inject
    PaymentService payments;

    @POST
    public Response create(@Context SecurityContext security,
                           @HeaderParam("Idempotency-Key") String idempotencyKey,
                           PaymentRequest body) {
        PaymentService.PaymentResult result = payments.create(
                AuthenticatedUser.idOf(security),
                idempotencyKey,
                body);
        return Response.status(result.replayed() ? Response.Status.OK : Response.Status.CREATED)
                .entity(result.response())
                .build();
    }
}
