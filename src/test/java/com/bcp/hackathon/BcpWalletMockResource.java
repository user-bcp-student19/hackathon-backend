package com.bcp.hackathon;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BcpWalletMockResource implements QuarkusTestResourceLifecycleManager {
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/groups/3/kyc/verifications", exchange -> {
                LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                ObjectMapper mapper = JsonMapper.builder().build();
                ObjectNode response = mapper.createObjectNode()
                        .put("id", UUID.randomUUID().toString())
                        .put("wallet_id", UUID.randomUUID().toString())
                        .put("scenario", "APPROVED")
                        .put("status", "ACTIVE");
                byte[] body = mapper.writeValueAsBytes(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return Map.of("bcp.wallet.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start BCP wallet mock server", exception);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public static String lastRequestBody() {
        return LAST_REQUEST_BODY.get();
    }
}
