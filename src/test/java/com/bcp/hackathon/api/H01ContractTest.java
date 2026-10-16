package com.bcp.hackathon.api;

import com.bcp.hackathon.BcpWalletMockResource;
import com.bcp.hackathon.store.ContactRepository;
import com.bcp.hackathon.store.SqliteStore;
import com.bcp.hackathon.store.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(BcpWalletMockResource.class)
class H01ContractTest {
    @Inject
    SqliteStore store;

    @Inject
    WalletRepository wallets;

    @Inject
    ContactRepository contacts;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        store.clearBusinessData();
    }

    @Test
    void registerReturns201WithoutPassword() {
        String email = uniqueEmail();
        given().contentType(ContentType.JSON)
                .body(registerBody(email, "password123"))
                .when().post("/users")
                .then().statusCode(201)
                .body("$", not(hasKey("password")))
                .body("email", equalTo(email));
    }

    @Test
    void duplicateEmailReturns409() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        given().contentType(ContentType.JSON)
                .body(registerBody(email, "password123"))
                .when().post("/users")
                .then().statusCode(409)
                .body("code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void duplicateEmailIsCaseInsensitive() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        given().contentType(ContentType.JSON)
                .body(registerBody(email.toUpperCase(), "password123"))
                .when().post("/users")
                .then().statusCode(409)
                .body("code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidRegisterReturns400WithFields() {
        given().contentType(ContentType.JSON)
                .body(registerBody("not-an-email", "short"))
                .when().post("/users")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("fields.field", hasItem("email"));
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        given().auth().preemptive().basic(email, "password123")
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("email", equalTo(email));
    }

    @Test
    void loginWithWrongPasswordReturns401WithChallenge() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        given().auth().preemptive().basic(email, "wrong-password")
                .when().post("/auth/login")
                .then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Basic realm=\"hackathon-backend\""))
                .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointWithoutCredentialsReturns401() {
        given().when().get("/wallet")
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void walletLifecycle() throws Exception {
        String email = uniqueEmail();
        registerUser(email, "password123");

        given().auth().preemptive().basic(email, "password123")
                .when().get("/wallet")
                .then().statusCode(404)
                .body("code", equalTo("WALLET_NOT_FOUND"));

        String walletId = given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .when().post("/wallet")
                .then().statusCode(201)
                .body("wallet_id", notNullValue())
                .body("status", equalTo("active"))
                .body("payments_enabled", equalTo(false))
                .extract().path("wallet_id");

        given().auth().preemptive().basic(email, "password123")
                .when().get("/wallet")
                .then().statusCode(200)
                .body("wallet_id", equalTo(walletId))
                .body("status", equalTo("active"))
                .body("payments_enabled", equalTo(false));

        WalletRepository.StoredWallet storedWallet = wallets.findById(walletId).orElseThrow();
        assertEquals("active", storedWallet.status());
        assertNotNull(storedWallet.userId());
        assertNotNull(storedWallet.createdAt());
        assertEquals(walletId, storedWallet.walletId());
        assertEquals(
                objectMapper.readTree("{\"documentType\":\"TEST\",\"document\":\"TEST-"
                        + wallets.findById(walletId).orElseThrow().userId()
                        + "\",\"scenario\":\"APPROVED\"}"),
                objectMapper.readTree(BcpWalletMockResource.lastRequestBody()));

        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .when().post("/wallet")
                .then().statusCode(409)
                .body("code", equalTo("WALLET_ALREADY_EXISTS"));
    }

    @Test
    void contactRequiresExistingWalletAndIsCreated() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        String walletId = createWallet(email);

        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body("{\"name\":\"Ana\",\"phone\":\"+51999999999\",\"wallet_id\":\""
                        + UUID.randomUUID() + "\"}")
                .when().post("/contacts")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("fields.field", hasItem("wallet_id"));

        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body("{\"name\":\"Ana\",\"phone\":\"+51999999999\",\"wallet_id\":\"wallet_0000000000000000\"}")
                .when().post("/contacts")
                .then().statusCode(422);

        String contactId = given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body("{\"name\":\"Ana\",\"phone\":\"+51999999999\",\"wallet_id\":\"" + walletId + "\"}")
                .when().post("/contacts")
                .then().statusCode(201)
                .body("wallet_id", equalTo(walletId))
                .extract().path("id");

        given().auth().preemptive().basic(email, "password123")
                .when().get("/contacts")
                .then().statusCode(200)
                .body("size()", equalTo(1));

        ContactRepository.StoredContact storedContact = contacts.findByOwner(
                        wallets.findById(walletId).orElseThrow().userId())
                .stream()
                .filter(contact -> contact.id().equals(contactId))
                .findFirst()
                .orElseThrow();
        assertEquals("Ana", storedContact.name());
        assertEquals("+51999999999", storedContact.phone());
        assertEquals(walletId, storedContact.walletId());
        assertNotNull(storedContact.createdAt());
    }

    @Test
    void kycApprovalEnablesPaymentsAndSecondSubmissionIsRejected() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        createWallet(email);

        given().auth().preemptive().basic(email, "password123")
                .when().get("/kyc/verifications")
                .then().statusCode(404)
                .body("code", equalTo("KYC_NOT_FOUND"));

        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body(kycBody("DNI"))
                .when().post("/kyc/verifications")
                .then().statusCode(201)
                .body("status", equalTo("approved"));

        given().auth().preemptive().basic(email, "password123")
                .when().get("/wallet")
                .then().statusCode(200)
                .body("payments_enabled", equalTo(true));

        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body(kycBody("DNI"))
                .when().post("/kyc/verifications")
                .then().statusCode(409)
                .body("code", equalTo("KYC_ALREADY_APPROVED"));
    }

    @Test
    void invalidDocumentTypeReturns400() {
        String email = uniqueEmail();
        registerUser(email, "password123");
        given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .body(kycBody("LICENSE"))
                .when().post("/kyc/verifications")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_FAILED"));
    }

    @Test
    void openApiContractIsServed() {
        given().when().get("/openapi.yaml")
                .then().statusCode(200)
                .body(containsString("H-01"));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerBody(String email, String password) {
        return "{\"name\":\"Ana Perez\",\"email\":\"" + email + "\",\"phone\":\"+51999999999\",\"password\":\""
                + password + "\"}";
    }

    private static String kycBody(String documentType) {
        return "{\"documentType\":\"" + documentType + "\",\"documentNumber\":\"12345678\","
                + "\"fullName\":\"Ana Perez\",\"birthDate\":\"1990-01-31\"}";
    }

    private static void registerUser(String email, String password) {
        given().contentType(ContentType.JSON).body(registerBody(email, password))
                .when().post("/users").then().statusCode(201);
    }

    private static String createWallet(String email) {
        return given().auth().preemptive().basic(email, "password123").contentType(ContentType.JSON)
                .when().post("/wallet").then().statusCode(201)
                .extract().path("wallet_id");
    }
}
