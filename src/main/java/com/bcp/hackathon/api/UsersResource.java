package com.bcp.hackathon.api;

import com.bcp.hackathon.api.ApiModels.RegisterRequest;
import com.bcp.hackathon.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("users")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UsersResource {
    @Inject
    UserService users;

    @POST
    public Response register(RegisterRequest body) {
        return Response.status(Response.Status.CREATED).entity(users.register(body)).build();
    }
}
