package com.bcp.hackathon.api;

import com.bcp.hackathon.api.ApiModels.ContactRequest;
import com.bcp.hackathon.security.AuthenticatedUser;
import com.bcp.hackathon.service.ContactService;
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

@Path("contacts")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContactsResource {
    @Inject
    ContactService contacts;

    @GET
    public Response list(@Context SecurityContext security) {
        return Response.ok(contacts.list(AuthenticatedUser.idOf(security))).build();
    }

    @POST
    public Response create(@Context SecurityContext security, ContactRequest body) {
        return Response.status(Response.Status.CREATED)
                .entity(contacts.create(AuthenticatedUser.idOf(security), body))
                .build();
    }
}
