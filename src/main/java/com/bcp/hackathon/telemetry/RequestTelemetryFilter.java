package com.bcp.hackathon.telemetry;

import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Provider
@ApplicationScoped
public class RequestTelemetryFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final int RETENTION_LIMIT = 10_000;
    private static final String START_TIME_PROPERTY = "hackathon.request.started";

    @Inject
    SqliteStore store;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!shouldTrack(requestContext)) return;
        requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    }

    @Override
    public synchronized void filter(ContainerRequestContext requestContext,
                                    ContainerResponseContext responseContext) throws IOException {
        Object startTime = requestContext.getProperty(START_TIME_PROPERTY);
        if (!(startTime instanceof Long startedAt)) return;

        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        long nextId = store.list("http_logs").stream()
                .mapToLong(record -> ((Number) record.get("id")).longValue())
                .max().orElse(0) + 1;
        String endpoint = "/" + requestContext.getUriInfo().getPath().replaceFirst("^/+", "");
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("id", nextId);
        log.put("groupId", 3);
        log.put("method", requestContext.getMethod());
        log.put("endpoint", endpoint);
        log.put("status", responseContext.getStatus());
        log.put("durationMs", durationMs);
        log.put("timestamp", Instant.now().toString());
        store.put("http_logs", Long.toString(nextId), log);

        var logs = store.list("http_logs");
        int excess = logs.size() - RETENTION_LIMIT;
        for (int index = 0; index < excess; index++) {
            store.delete("http_logs", String.valueOf(logs.get(index).get("id")));
        }
    }

    private boolean shouldTrack(ContainerRequestContext requestContext) {
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) return false;
        String path = requestContext.getUriInfo().getPath();
        return !path.equals("usage") && !path.equals("logs") && !path.equals("usage/all");
    }
}
