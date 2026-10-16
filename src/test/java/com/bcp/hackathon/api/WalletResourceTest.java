package com.bcp.hackathon.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.bcp.hackathon.store.SqliteStore;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class WalletResourceTest {
    @Inject
    SqliteStore store;

    @BeforeEach
    void resetState() {
        given()
                .contentType(ContentType.JSON)
                .post("/control/reset")
                .then()
                .statusCode(200)
                .body("reset", equalTo(true));
    }

    @Test
    void registersUserAndStoresOnlySaltedPasswordHash() {
        String password = "demo-password-123";
        String userId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ada Lovelace","email":"ada@example.test","phone":"+51987654321","password":"%s"}
                        """.formatted(password))
                .post("/users/register")
                .then()
                .statusCode(201)
                .body("name", equalTo("Ada Lovelace"))
                .body("email", equalTo("ada@example.test"))
                .body("phone", equalTo("+51987654321"))
                .body("$", org.hamcrest.Matchers.not(hasKey("password")))
                .body("$", org.hamcrest.Matchers.not(hasKey("passwordHash")))
                .extract()
                .path("id");

        Map<String, Object> storedUser = store.get("users", userId);
        String passwordHash = (String) storedUser.get("passwordHash");
        assertTrue(passwordHash.startsWith("pbkdf2-sha256$600000$"));
        assertNotEquals(password, passwordHash);
        assertFalse(storedUser.containsKey("password"));
    }

    @Test
    void rejectsMissingFieldsAndDuplicateEmailOrPhone() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ada Lovelace","email":"ada@example.test","phone":"+51987654321"}
                        """)
                .post("/users/register")
                .then()
                .statusCode(400)
                .body("error", equalTo("INVALID_INPUT"));

        String registration = """
                {"name":"Ada Lovelace","email":"ada@example.test","phone":"+51987654321","password":"demo-password-123"}
                """;
        given()
                .contentType(ContentType.JSON)
                .body(registration)
                .post("/users/register")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ada Again","email":"ADA@example.test","phone":"+51987654322","password":"demo-password-456"}
                        """)
                .post("/users/register")
                .then()
                .statusCode(409)
                .body("error", equalTo("USER_ALREADY_EXISTS"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Ada Again","email":"other@example.test","phone":"51987654321","password":"demo-password-456"}
                        """)
                .post("/users/register")
                .then()
                .statusCode(409)
                .body("error", equalTo("USER_ALREADY_EXISTS"));
    }

    @Test
    void exposesHealthAndSeedWallet() {
        given()
                .get("/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("ok"))
                .body("storage", equalTo("sqlite"));

        given()
                .get("/wallets/wallet-demo")
                .then()
                .statusCode(200)
                .body("balance", equalTo(100000))
                .body("reserved", equalTo(0))
                .body("available", equalTo(100000));
    }

    @Test
    void paymentCanBeConfirmedIdempotentlyAndWritesBalancedLedger() {
        String paymentId = given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "payment-test-01")
                .body("""
                        {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":1500}
                        """)
                .post("/payments")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDING"))
                .extract()
                .path("id");

        String eventId = given()
                .get("/control/events")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .extract()
                .path("[0].id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":\"" + eventId + "\"}")
                .post("/callbacks/payments")
                .then()
                .statusCode(200)
                .body("duplicate", equalTo(false))
                .body("payment.status", equalTo("SUCCEEDED"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":\"" + eventId + "\"}")
                .post("/callbacks/payments")
                .then()
                .statusCode(200)
                .body("duplicate", equalTo(true));

        given()
                .get("/wallets/wallet-demo")
                .then()
                .statusCode(200)
                .body("balance", equalTo(98500))
                .body("reserved", equalTo(0));

        given()
                .get("/ledger")
                .then()
                .statusCode(200)
                .body("$", hasSize(4));

        given()
                .get("/payments/" + paymentId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCEEDED"));
    }

    @Test
    void kycManualReviewActivatesWalletOnlyAfterDecision() {
        String kycId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"documentType":"TEST","document":"TEST-001","scenario":"MANUAL_REVIEW"}
                        """)
                .post("/kyc/verifications")
                .then()
                .statusCode(201)
                .body("status", equalTo("PENDING"))
                .extract()
                .path("id");

        given()
                .get("/kyc/verifications/" + kycId)
                .then()
                .statusCode(200)
                .body("wallet_id", notNullValue());

        given()
                .contentType(ContentType.JSON)
                .body("{\"decision\":\"ACTIVE\"}")
                .post("/kyc/verifications/" + kycId + "/decision")
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentContent() {
        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "same-key")
                .body("""
                        {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":500}
                        """)
                .post("/payments")
                .then()
                .statusCode(202);

        given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "same-key")
                .body("""
                        {"walletId":"wallet-demo","recipient":"contact-demo","amountCents":600}
                        """)
                .post("/payments")
                .then()
                .statusCode(409)
                .body("error", equalTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void pendingPaymentCanBeResolvedAndReversedWithoutChangingFinalBalance() {
        String paymentId = given()
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", "payment-pending")
                .body("""
                        {"walletId":"wallet-demo","recipient":"merchant-demo","amountCents":3000,"scenario":"PENDING"}
                        """)
                .post("/payments")
                .then()
                .statusCode(202)
                .extract()
                .path("id");

        given()
                .post("/payments/" + paymentId + "/resolve")
                .then()
                .statusCode(202)
                .body("escalated", equalTo(true));

        String paymentEventId = given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"SUCCEEDED\"}")
                .post("/control/provider/" + paymentId + "/complete")
                .then()
                .statusCode(200)
                .extract()
                .path("eventId");

        given()
                .post("/control/events/" + paymentEventId + "/deliver")
                .then()
                .statusCode(200)
                .body("payment.status", equalTo("SUCCEEDED"));

        String reversalId = given()
                .post("/payments/" + paymentId + "/reversals")
                .then()
                .statusCode(202)
                .body("status", equalTo("PENDING"))
                .extract()
                .path("id");

        String reversalEventId = given()
                .get("/control/events")
                .then()
                .statusCode(200)
                .extract()
                .path("[1].id");

        given()
                .post("/control/events/" + reversalEventId + "/deliver")
                .then()
                .statusCode(200)
                .body("payment.status", equalTo("REVERSED"));

        given()
                .get("/reversals/" + reversalId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCEEDED"));

        given()
                .get("/wallets/wallet-demo")
                .then()
                .statusCode(200)
                .body("balance", equalTo(100000))
                .body("reserved", equalTo(0));

        given()
                .post("/reconciliations")
                .then()
                .statusCode(200)
                .body("status", equalTo("MATCHED"))
                .body("differences", hasSize(0));
    }

    @Test
    void servesTheGroupThreeOpenApiContract() {
        given()
                .get("/openapi.json")
                .then()
                .statusCode(200)
                .body("openapi", equalTo("3.0.3"))
                .body("paths", hasKey("/payments"));
    }

    @Test
    void recordsAndFiltersLocalRequestTelemetry() {
        given()
                .get("/health")
                .then()
                .statusCode(200);

        given()
                .get("/usage")
                .then()
                .statusCode(200)
                .body("groupId", equalTo(3))
                .body("totalRequests", greaterThan(0));

        given()
                .queryParam("method", "GET")
                .queryParam("endpoint", "/health")
                .get("/logs")
                .then()
                .statusCode(200)
                .body("logs", hasSize(greaterThan(0)));
    }
}
