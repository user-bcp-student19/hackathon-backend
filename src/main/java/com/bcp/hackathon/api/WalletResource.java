package com.bcp.hackathon.api;

import com.bcp.hackathon.service.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class WalletResource {
    @Inject
    WalletService service;

    @GET
    @Path("health")
    public Response health() {
        return respond(service.health());
    }

    @GET
    @Path("scenarios")
    public Response scenarios() {
        return respond(service.scenarios());
    }

    @POST
    @Path("users/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerUser(Map<String, Object> body) {
        return respond(service.registerUser(body));
    }

    @POST
    @Path("kyc/verifications")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createKyc(Map<String, Object> body) {
        return respond(service.createKyc(body));
    }

    @GET
    @Path("kyc/verifications/{id}")
    public Response getKyc(@PathParam("id") String id) {
        return respond(service.getKyc(id));
    }

    @POST
    @Path("kyc/verifications/{id}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response decideKyc(@PathParam("id") String id, Map<String, Object> body) {
        return respond(service.decideKyc(id, body));
    }

    @GET
    @Path("wallets/{id}")
    public Response getWallet(@PathParam("id") String id) {
        return respond(service.getWallet(id));
    }

    @POST
    @Path("payments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createPayment(@HeaderParam("Idempotency-Key") String idempotencyKey,
                                  Map<String, Object> body) {
        return respond(service.createPayment(idempotencyKey, body));
    }

    @GET
    @Path("payments")
    public Response listPayments(@QueryParam("walletId") String walletId) {
        return respond(service.listPayments(walletId));
    }

    @GET
    @Path("payments/{id}")
    public Response getPayment(@PathParam("id") String id) {
        return respond(service.getPayment(id));
    }

    @GET
    @Path("payments/{id}/receipt")
    public Response receipt(@PathParam("id") String id) {
        return respond(service.receipt(id));
    }

    @GET
    @Path("provider/payments/{id}")
    public Response providerPayment(@PathParam("id") String id) {
        return respond(service.providerPayment(id));
    }

    @GET
    @Path("reversals/{id}")
    public Response reversal(@PathParam("id") String id) {
        return respond(service.reversal(id));
    }

    @POST
    @Path("payments/{id}/resolve")
    public Response resolvePayment(@PathParam("id") String id) {
        return respond(service.resolvePayment(id));
    }

    @POST
    @Path("payments/{id}/reversals")
    public Response requestReversal(@PathParam("id") String id) {
        return respond(service.requestReversal(id));
    }

    @POST
    @Path("qrs")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createQr(Map<String, Object> body) {
        return respond(service.createQr(body));
    }

    @POST
    @Path("qrs/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resolveQr(Map<String, Object> body) {
        return respond(service.resolveQr(body));
    }

    @POST
    @Path("callbacks/payments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response callback(Map<String, Object> body) {
        return respond(service.callback(body));
    }

    @GET
    @Path("control/events")
    public Response events() {
        return respond(service.events());
    }

    @GET
    @Path("ledger")
    public Response ledger() {
        return respond(service.ledger());
    }

    @GET
    @Path("support/cases")
    public Response supportCases() {
        return respond(service.supportCases());
    }

    @GET
    @Path("settlements")
    public Response settlements() {
        return respond(service.settlements());
    }

    @GET
    @Path("reconciliations/incidents")
    public Response incidents() {
        return respond(service.incidents());
    }

    @POST
    @Path("control/events/{id}/deliver")
    public Response deliverEvent(@PathParam("id") String id) {
        return respond(service.deliverEvent(id));
    }

    @POST
    @Path("control/provider/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response completeProvider(@PathParam("id") String id, Map<String, Object> body) {
        return respond(service.completeProvider(id, body));
    }

    @POST
    @Path("control/settlements")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response configureSettlement(Map<String, Object> body) {
        return respond(service.configureSettlement(body));
    }

    @POST
    @Path("reconciliations")
    public Response reconcile() {
        return respond(service.reconcile());
    }

    @POST
    @Path("control/reset")
    public Response reset() {
        return respond(service.reset());
    }

    @GET
    @Path("groups")
    public Response groups() {
        return respond(service.groups());
    }

    @GET
    @Path("usage")
    public Response usage() {
        return respond(service.usage());
    }

    @GET
    @Path("logs")
    public Response logs(@QueryParam("limit") String limit,
                         @QueryParam("before") String before,
                         @QueryParam("status") String status,
                         @QueryParam("method") String method,
                         @QueryParam("endpoint") String endpoint) {
        return respond(service.logs(Map.of(
                "limit", valueOrEmpty(limit),
                "before", valueOrEmpty(before),
                "status", valueOrEmpty(status),
                "method", valueOrEmpty(method),
                "endpoint", valueOrEmpty(endpoint))));
    }

    @GET
    @Path("usage/all")
    public Response usageAll() {
        return respond(service.usageAll());
    }

    private Response respond(ApiResult result) {
        return Response.status(result.status()).entity(result.body()).build();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
