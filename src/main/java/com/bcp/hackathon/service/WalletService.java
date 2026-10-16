package com.bcp.hackathon.service;

import com.bcp.hackathon.api.ApiResult;
import com.bcp.hackathon.store.SqliteStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class WalletService {
    private static final int INITIAL_BALANCE = 100_000;
    private static final int MAX_AMOUNT_CENTS = 100_000_000;
    private static final String DEMO_WALLET = "wallet-demo";

    @Inject
    SqliteStore store;

    @jakarta.annotation.PostConstruct
    void seed() {
        if (store.get("wallets", DEMO_WALLET) == null) {
            reset();
        }
    }

    public ApiResult health() {
        return ApiResult.ok(Map.of(
                "status", "ok",
                "storage", "sqlite",
                "environment", "local",
                "service", "hackathon-backend",
                "groups", "3",
                "openapi", "/openapi.json"));
    }

    public ApiResult scenarios() {
        return ApiResult.ok(Map.of(
                "kyc", List.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "UNAVAILABLE"),
                "payments", List.of("APPROVED", "REJECTED", "PENDING", "DELAYED_APPROVAL",
                        "TIMEOUT_AFTER_PROCESSING", "PROVIDER_ERROR"),
                "settlements", List.of("MISSING", "AMOUNT", "DUPLICATE", "ORPHAN", "CLEAR")));
    }

    public synchronized ApiResult registerUser(Map<String, Object> body) {
        String name = text(body, "name");
        String email = text(body, "email");
        String phone = text(body, "phone");
        String password = text(body, "password");
        if (!validText(name, 200) || !validText(email, 254)
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || !validText(phone, 16) || !phone.matches("^\\+?[0-9]{8,15}$")
                || !validText(password, 256)) {
            return ApiResult.error(400, "INVALID_INPUT");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = phone.replaceAll("\\D", "");
        for (Map<String, Object> existing : store.list("users")) {
            if (normalizedEmail.equals(text(existing, "normalizedEmail"))
                    || normalizedPhone.equals(text(existing, "normalizedPhone"))) {
                return ApiResult.error(409, "USER_ALREADY_EXISTS");
            }
        }

        String userId = id("user");
        Map<String, Object> user = map(
                "id", userId,
                "name", name.trim(),
                "email", normalizedEmail,
                "phone", phone,
                "normalizedEmail", normalizedEmail,
                "normalizedPhone", normalizedPhone,
                "passwordHash", PasswordHasher.hash(password.toCharArray()),
                "createdAt", Instant.now().toString());
        store.put("users", userId, user);

        return ApiResult.created(map(
                "id", userId,
                "name", text(user, "name"),
                "email", text(user, "email"),
                "phone", text(user, "phone"),
                "createdAt", text(user, "createdAt")));
    }

    public synchronized ApiResult createKyc(Map<String, Object> body) {
        String documentType = text(body, "documentType");
        String document = text(body, "document");
        String scenario = text(body, "scenario", "APPROVED");
        if (!"TEST".equals(documentType) || !validText(document, 200) || !document.startsWith("TEST-")
                || !List.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "UNAVAILABLE").contains(scenario)) {
            return ApiResult.error(400, "INVALID_INPUT");
        }
        if ("UNAVAILABLE".equals(scenario)) {
            return ApiResult.error(503, "KYC_UNAVAILABLE");
        }
        String verificationId = id("kyc");
        String walletId = id("wallet");
        String status = switch (scenario) {
            case "APPROVED" -> "ACTIVE";
            case "MANUAL_REVIEW" -> "PENDING";
            default -> "REJECTED";
        };
        Map<String, Object> wallet = wallet(walletId, status, 0, 0);
        Map<String, Object> verification = map(
                "id", verificationId, "wallet_id", walletId, "scenario", scenario, "status", status);
        store.put("wallets", walletId, wallet);
        store.put("kyc", verificationId, verification);
        if ("MANUAL_REVIEW".equals(scenario)) {
            addCase(verificationId, "KYC_REVIEW");
        }
        return ApiResult.created(verification);
    }

    public ApiResult getKyc(String id) {
        return found(store.get("kyc", id));
    }

    public synchronized ApiResult decideKyc(String id, Map<String, Object> body) {
        Map<String, Object> verification = store.get("kyc", id);
        if (verification == null) return ApiResult.error(404, "NOT_FOUND");
        String decision = text(body, "decision");
        if (!List.of("ACTIVE", "REJECTED").contains(decision)) return ApiResult.error(400, "INVALID_DECISION");
        String current = text(verification, "status");
        if (!"PENDING".equals(current)) {
            return decision.equals(current) ? ApiResult.ok(verification)
                    : ApiResult.error(409, "DECISION_CONFLICT");
        }
        verification.put("status", decision);
        store.put("kyc", id, verification);
        Map<String, Object> wallet = store.get("wallets", text(verification, "wallet_id"));
        wallet.put("status", decision);
        store.put("wallets", text(verification, "wallet_id"), wallet);
        resolveCases(id, "KYC_REVIEW");
        return ApiResult.ok(verification);
    }

    public ApiResult getWallet(String id) {
        return found(store.get("wallets", id));
    }

    public synchronized ApiResult createPayment(String idempotencyKey, Map<String, Object> body) {
        String walletId = text(body, "walletId");
        String recipient = text(body, "recipient");
        Integer amount = integer(body, "amountCents");
        String currency = text(body, "currency", "PEN");
        String scenario = text(body, "scenario", "APPROVED");
        if (!validText(idempotencyKey, 200) || !validText(walletId, 200)
                || !List.of("contact-demo", "merchant-demo").contains(recipient)
                || amount == null || amount < 1 || amount > MAX_AMOUNT_CENTS
                || !"PEN".equals(currency)
                || !List.of("APPROVED", "REJECTED", "PENDING", "DELAYED_APPROVAL",
                        "TIMEOUT_AFTER_PROCESSING", "PROVIDER_ERROR").contains(scenario)) {
            return ApiResult.error(400, "INVALID_INPUT");
        }
        Map<String, Object> wallet = store.get("wallets", walletId);
        if (wallet == null) return ApiResult.error(404, "NOT_FOUND");
        String idemId = walletId + "|" + idempotencyKey;
        String fingerprint = walletId + "|" + recipient + "|" + amount + "|" + currency
                + "|" + text(body, "qrId") + "|" + scenario;
        Map<String, Object> existingIdem = store.get("idempotency", idemId);
        if (existingIdem != null) {
            if (!fingerprint.equals(text(existingIdem, "fingerprint"))) {
                return ApiResult.error(409, "IDEMPOTENCY_CONFLICT");
            }
            return ApiResult.ok(store.get("payments", text(existingIdem, "paymentId")));
        }
        if ("PROVIDER_ERROR".equals(scenario)) return ApiResult.error(503, "PROVIDER_ERROR");
        if (!"ACTIVE".equals(text(wallet, "status"))) return ApiResult.error(409, "WALLET_NOT_ACTIVE");

        String qrId = text(body, "qrId");
        if (qrId != null && !qrId.isBlank()) {
            Map<String, Object> qr = store.get("qrs", qrId);
            if (qr == null) return ApiResult.error(404, "NOT_FOUND");
            if (Instant.parse(text(qr, "expiresAt")).isBefore(Instant.now())) return ApiResult.error(410, "QR_EXPIRED");
            if (!"merchant-demo".equals(recipient)) return ApiResult.error(409, "QR_PAYMENT_MISMATCH");
            Integer fixedAmount = integer(qr, "amountCents");
            if (fixedAmount != null && !fixedAmount.equals(amount)) return ApiResult.error(409, "QR_PAYMENT_MISMATCH");
        }
        if (available(wallet) < amount) return ApiResult.error(409, "INSUFFICIENT_FUNDS");

        String paymentId = id("pay");
        Map<String, Object> payment = map(
                "id", paymentId, "wallet_id", walletId, "recipient", recipient, "amount", amount,
                "status", "PENDING", "scenario", scenario, "idem", idempotencyKey, "fingerprint", fingerprint);
        wallet.put("reserved", integer(wallet, "reserved") + amount);
        wallet.put("available", integer(wallet, "balance") - integer(wallet, "reserved"));
        store.put("wallets", walletId, wallet);
        store.put("payments", paymentId, payment);
        store.put("provider", paymentId, provider(paymentId, amount, providerInitialStatus(scenario)));
        store.put("idempotency", idemId, map("fingerprint", fingerprint, "paymentId", paymentId));

        if ("TIMEOUT_AFTER_PROCESSING".equals(scenario)) {
            payment.put("scenario", scenario);
            store.put("provider", paymentId, provider(paymentId, amount, "SUCCEEDED"));
            createEvent(paymentId, "SUCCEEDED");
            return new ApiResult(504, map("error", "TIMEOUT_AFTER_PROCESSING", "processed", true,
                    "paymentId", paymentId, "status", "PENDING"));
        }
        if ("APPROVED".equals(scenario) || "REJECTED".equals(scenario)) {
            createEvent(paymentId, scenario.equals("APPROVED") ? "SUCCEEDED" : "REJECTED");
        }
        return ApiResult.accepted(payment);
    }

    public ApiResult listPayments(String walletId) {
        String filter = walletId == null || walletId.isBlank() ? DEMO_WALLET : walletId;
        return ApiResult.ok(store.list("payments").stream()
                .filter(payment -> filter.equals(text(payment, "wallet_id"))).toList());
    }

    public ApiResult getPayment(String id) {
        return found(store.get("payments", id));
    }

    public ApiResult receipt(String id) {
        Map<String, Object> payment = store.get("payments", id);
        if (payment == null) return ApiResult.error(404, "NOT_FOUND");
        String status = text(payment, "status");
        if (!List.of("SUCCEEDED", "REVERSED").contains(status)) return ApiResult.error(409, "RECEIPT_NOT_READY");
        return ApiResult.ok(map(
                "receiptId", "receipt-" + id, "paymentId", id,
                "amountCents", integer(payment, "amount"), "currency", "PEN",
                "recipient", text(payment, "recipient"), "status", status));
    }

    public ApiResult providerPayment(String id) {
        return found(store.get("provider", id));
    }

    public ApiResult reversal(String id) {
        return found(store.get("reversals", id));
    }

    public synchronized ApiResult resolvePayment(String id) {
        Map<String, Object> payment = store.get("payments", id);
        if (payment == null) return ApiResult.error(404, "NOT_FOUND");
        Map<String, Object> provider = store.get("provider", id);
        String providerStatus = text(provider, "status");
        if ("PENDING".equals(providerStatus)) {
            addCase(id, "UNKNOWN_PAYMENT_RESULT");
            return ApiResult.accepted(map("status", "PENDING", "escalated", true));
        }
        String target = "REVERSED".equals(providerStatus) ? "REVERSED" : providerStatus;
        applyOutcome(payment, target);
        return ApiResult.ok(store.get("payments", id));
    }

    public synchronized ApiResult requestReversal(String id) {
        Map<String, Object> payment = store.get("payments", id);
        if (payment == null) return ApiResult.error(404, "NOT_FOUND");
        Map<String, Object> existing = findReversalForPayment(id);
        if (existing != null) {
            return "SUCCEEDED".equals(text(existing, "status"))
                    ? ApiResult.ok(existing) : ApiResult.accepted(existing);
        }
        if (!"SUCCEEDED".equals(text(payment, "status"))) return ApiResult.error(409, "PAYMENT_NOT_REVERSIBLE");
        String reversalId = id("rev");
        Map<String, Object> reversal = map("id", reversalId, "payment_id", id, "status", "PENDING");
        store.put("reversals", reversalId, reversal);
        store.put("provider", id, provider(id, integer(payment, "amount"), "REVERSED"));
        createEvent(id, "REVERSED");
        return ApiResult.accepted(reversal);
    }

    public synchronized ApiResult createQr(Map<String, Object> body) {
        String merchantId = text(body, "merchantId", "merchant-demo");
        Integer amount = integer(body, "amountCents");
        Integer ttl = integer(body, "expiresInSeconds");
        if (!"merchant-demo".equals(merchantId)) return ApiResult.error(404, "MERCHANT_NOT_FOUND");
        if (amount != null && (amount < 1 || amount > MAX_AMOUNT_CENTS)) {
            return ApiResult.error(400, "INVALID_INPUT");
        }
        if (ttl != null && (ttl < 0 || ttl > 86_400)) {
            return ApiResult.error(400, "INVALID_TTL");
        }
        int expirationSeconds = ttl == null ? 300 : ttl;
        String qrId = id("qr");
        Map<String, Object> qr = map(
                "id", qrId, "payload", "qrmock:" + qrId, "amountCents", amount,
                "currency", "PEN", "merchantId", merchantId, "merchantName", "Demo Merchant",
                "expiresAt", Instant.now().plusSeconds(expirationSeconds).toString());
        store.put("qrs", qrId, qr);
        return ApiResult.created(map("id", qrId, "payload", text(qr, "payload"),
                "amountCents", amount, "currency", "PEN"));
    }

    public ApiResult resolveQr(Map<String, Object> body) {
        String payload = text(body, "payload");
        if (payload == null || !payload.startsWith("qrmock:") || payload.length() <= "qrmock:".length()) {
            return ApiResult.error(400, "INVALID_QR");
        }
        String qrId = payload.substring("qrmock:".length());
        Map<String, Object> qr = store.get("qrs", qrId);
        if (qr == null) return ApiResult.error(404, "NOT_FOUND");
        if (Instant.parse(text(qr, "expiresAt")).isBefore(Instant.now())) return ApiResult.error(410, "QR_EXPIRED");
        return ApiResult.ok(map("merchantId", text(qr, "merchantId"), "merchantName", text(qr, "merchantName"),
                "amountCents", integer(qr, "amountCents"), "currency", "PEN"));
    }

    public synchronized ApiResult callback(Map<String, Object> body) {
        String eventId = text(body, "eventId");
        if (!validText(eventId, 200)) return ApiResult.error(400, "INVALID_INPUT");
        return deliverEvent(eventId);
    }

    public ApiResult events() {
        return ApiResult.ok(store.list("events"));
    }

    public ApiResult ledger() {
        return ApiResult.ok(store.list("ledger"));
    }

    public ApiResult supportCases() {
        return ApiResult.ok(store.list("cases"));
    }

    public ApiResult settlements() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> payment : store.list("provider")) {
            String id = text(payment, "id");
            Map<String, Object> adjustment = store.get("adjustments", id);
            if (adjustment == null || !"MISSING".equals(text(adjustment, "mode"))) {
                rows.add(settlementRow(payment, adjustment));
            }
            if (adjustment != null && "DUPLICATE".equals(text(adjustment, "mode"))) {
                rows.add(settlementRow(payment, adjustment));
            }
        }
        for (Map<String, Object> adjustment : store.list("adjustments")) {
            if ("ORPHAN".equals(text(adjustment, "mode"))) {
                rows.add(map("id", text(adjustment, "paymentId"), "amount", integer(adjustment, "amountCents"),
                        "status", "SUCCEEDED"));
            }
        }
        return ApiResult.ok(rows);
    }

    public ApiResult incidents() {
        return ApiResult.ok(store.list("incidents"));
    }

    public synchronized ApiResult deliverEvent(String eventId) {
        Map<String, Object> event = store.get("events", eventId);
        if (event == null) return ApiResult.error(404, "NOT_FOUND");
        if (integer(event, "delivered") == 1) return ApiResult.ok(map("duplicate", true));
        Map<String, Object> payment = store.get("payments", text(event, "payment_id"));
        if (payment == null) return ApiResult.error(404, "NOT_FOUND");
        applyOutcome(payment, text(event, "target"));
        event.put("delivered", 1);
        store.put("events", eventId, event);
        return ApiResult.ok(map("duplicate", false,
                "payment", store.get("payments", text(event, "payment_id"))));
    }

    public synchronized ApiResult completeProvider(String id, Map<String, Object> body) {
        Map<String, Object> provider = store.get("provider", id);
        if (provider == null) return ApiResult.error(404, "NOT_FOUND");
        String status = text(body, "status", "SUCCEEDED");
        if (!List.of("SUCCEEDED", "REJECTED").contains(status)) return ApiResult.error(400, "INVALID_STATUS");
        if (!"PENDING".equals(text(provider, "status"))) return ApiResult.error(409, "PROVIDER_ALREADY_FINAL");
        provider.put("status", status);
        store.put("provider", id, provider);
        String eventId = createEvent(id, status);
        return ApiResult.ok(map("eventId", eventId));
    }

    public synchronized ApiResult configureSettlement(Map<String, Object> body) {
        String paymentId = text(body, "paymentId");
        String mode = text(body, "mode");
        Integer amount = integer(body, "amountCents");
        if (!validText(paymentId, 200) || !List.of("MISSING", "AMOUNT", "DUPLICATE", "ORPHAN", "CLEAR").contains(mode)) {
            return ApiResult.error(400, "INVALID_MODE");
        }
        if ("CLEAR".equals(mode)) {
            store.delete("adjustments", paymentId);
            return ApiResult.ok(map("configured", true));
        }
        if (("AMOUNT".equals(mode) || "ORPHAN".equals(mode)) && (amount == null || amount < 1 || amount > MAX_AMOUNT_CENTS)) {
            return ApiResult.error(400, "INVALID_INPUT");
        }
        if ("ORPHAN".equals(mode)) {
            if (store.get("provider", paymentId) != null) return ApiResult.error(409, "PROVIDER_ALREADY_EXISTS");
        } else if (store.get("payments", paymentId) == null) {
            return ApiResult.error(404, "NOT_FOUND");
        }
        store.put("adjustments", paymentId, map("paymentId", paymentId, "mode", mode, "amountCents", amount));
        return ApiResult.ok(map("configured", true));
    }

    public synchronized ApiResult reconcile() {
        List<Map<String, Object>> report = castList(settlements().body());
        Map<String, Integer> reportCounts = new LinkedHashMap<>();
        Map<String, Map<String, Object>> reportById = new LinkedHashMap<>();
        for (Map<String, Object> row : report) {
            String id = text(row, "id");
            reportCounts.merge(id, 1, Integer::sum);
            reportById.putIfAbsent(id, row);
        }
        List<Map<String, Object>> differences = new ArrayList<>();
        for (Map<String, Object> payment : store.list("payments")) {
            String id = text(payment, "id");
            Map<String, Object> reportRow = reportById.get(id);
            if (reportRow == null) addIncident(differences, id, "MISSING_PROVIDER");
            else {
                if (reportCounts.getOrDefault(id, 0) > 1) addIncident(differences, id, "DUPLICATE_PROVIDER");
                if (!integer(payment, "amount").equals(integer(reportRow, "amount"))) addIncident(differences, id, "AMOUNT_MISMATCH");
                if (!text(payment, "status").equals(text(reportRow, "status"))) addIncident(differences, id, "STATUS_MISMATCH");
            }
            reconcileLedger(payment, differences);
        }
        for (Map<String, Object> row : report) {
            String id = text(row, "id");
            if (store.get("payments", id) == null) addIncident(differences, id, "MISSING_WALLET");
        }
        for (Map<String, Object> wallet : store.list("wallets")) {
            int expectedBalance = DEMO_WALLET.equals(text(wallet, "id")) ? INITIAL_BALANCE : 0;
            for (Map<String, Object> payment : store.list("payments")) {
                if (!text(wallet, "id").equals(text(payment, "wallet_id"))) continue;
                if ("SUCCEEDED".equals(text(payment, "status"))) {
                    expectedBalance -= integer(payment, "amount");
                } else if ("REVERSED".equals(text(payment, "status"))) {
                    expectedBalance -= integer(payment, "amount");
                    expectedBalance += integer(payment, "amount");
                }
            }
            if (expectedBalance != integer(wallet, "balance")) {
                addIncident(differences, text(wallet, "id"), "BALANCE_MISMATCH");
            }
        }
        for (Map<String, Object> incident : store.list("incidents")) {
            if (!differences.stream().anyMatch(current -> sameIncident(current, incident))) {
                incident.put("status", "RESOLVED");
                store.put("incidents", text(incident, "id"), incident);
            }
        }
        differences.forEach(incident -> {
            incident.put("status", "OPEN");
            store.put("incidents", text(incident, "id"), incident);
        });
        return ApiResult.ok(map("status", differences.isEmpty() ? "MATCHED" : "DIFFERENCES",
                "differences", differences));
    }

    public synchronized ApiResult reset() {
        store.clearBusinessData();
        store.put("wallets", DEMO_WALLET, wallet(DEMO_WALLET, "ACTIVE", INITIAL_BALANCE, 0));
        addLedger("opening", "FUNDING", "funding", -INITIAL_BALANCE);
        addLedger("opening", "FUNDING", DEMO_WALLET, INITIAL_BALANCE);
        return ApiResult.ok(map("reset", true));
    }

    public ApiResult groups() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (int group = 1; group <= 8; group++) {
            String base = "https://bcp-wallet-mock.yefri.workers.dev/groups/" + group;
            groups.add(map("groupId", group, "name", "Grupo " + group,
                    "baseUrl", base, "openapiUrl", base + "/openapi.json"));
        }
        return ApiResult.ok(groups);
    }

    public ApiResult usage() {
        return ApiResult.ok(usageSummary());
    }

    public ApiResult logs(Map<String, String> filters) {
        Integer limit = parseFilter(filters.get("limit"), 100);
        Integer before = parseFilter(filters.get("before"), null);
        Integer status = parseFilter(filters.get("status"), null);
        if (limit == null || limit < 1 || limit > 500
                || (filters.get("before") != null && !filters.get("before").isBlank()
                    && (before == null || before < 1))
                || (filters.get("status") != null && !filters.get("status").isBlank()
                    && (status == null || status < 100 || status > 599))) {
            return ApiResult.error(400, "INVALID_LOG_FILTER");
        }
        String method = filters.get("method");
        String endpoint = filters.get("endpoint");
        List<Map<String, Object>> matching = store.list("http_logs").stream()
                .filter(log -> before == null || integer(log, "id") < before)
                .filter(log -> status == null || status.equals(integer(log, "status")))
                .filter(log -> method == null || method.isBlank()
                        || method.equalsIgnoreCase(text(log, "method")))
                .filter(log -> endpoint == null || endpoint.isBlank()
                        || endpoint.equals(text(log, "endpoint")))
                .sorted((left, right) -> Integer.compare(integer(right, "id"), integer(left, "id")))
                .toList();
        List<Map<String, Object>> page = matching.stream().limit(limit).toList();
        Integer nextBefore = matching.size() > limit
                ? integer(page.get(page.size() - 1), "id") : null;
        return ApiResult.ok(map("groupId", 3, "logs", page, "nextBefore", nextBefore,
                "retentionLimit", 10_000));
    }

    public ApiResult usageAll() {
        Map<String, Object> summary = usageSummary();
        return ApiResult.ok(map("totalRequests", summary.get("totalRequests"),
                "errors", summary.get("errors"), "groups", List.of(summary)));
    }

    private void applyOutcome(Map<String, Object> payment, String target) {
        String paymentId = text(payment, "id");
        String current = text(payment, "status");
        if ("SUCCEEDED".equals(target)) {
            if ("REVERSED".equals(current)) return;
            if (!"PENDING".equals(current)) return;
            String walletId = text(payment, "wallet_id");
            Map<String, Object> wallet = store.get("wallets", walletId);
            int amount = integer(payment, "amount");
            wallet.put("reserved", Math.max(0, integer(wallet, "reserved") - amount));
            wallet.put("balance", integer(wallet, "balance") - amount);
            recalculateAvailable(wallet);
            store.put("wallets", walletId, wallet);
            payment.put("status", "SUCCEEDED");
            store.put("payments", paymentId, payment);
            addLedger(paymentId, "PAYMENT", walletId, -amount);
            addLedger(paymentId, "PAYMENT", "clearing", amount);
            resolveCases(paymentId, "UNKNOWN_PAYMENT_RESULT");
        } else if ("REJECTED".equals(target)) {
            if (!"PENDING".equals(current)) return;
            Map<String, Object> wallet = store.get("wallets", text(payment, "wallet_id"));
            wallet.put("reserved", Math.max(0, integer(wallet, "reserved") - integer(payment, "amount")));
            recalculateAvailable(wallet);
            store.put("wallets", text(payment, "wallet_id"), wallet);
            payment.put("status", "REJECTED");
            store.put("payments", paymentId, payment);
            resolveCases(paymentId, "UNKNOWN_PAYMENT_RESULT");
        } else if ("REVERSED".equals(target)) {
            Map<String, Object> reversal = findReversalForPayment(paymentId);
            if (reversal == null || !"PENDING".equals(text(reversal, "status"))) return;
            Map<String, Object> wallet = store.get("wallets", text(payment, "wallet_id"));
            int amount = integer(payment, "amount");
            wallet.put("balance", integer(wallet, "balance") + amount);
            recalculateAvailable(wallet);
            store.put("wallets", text(payment, "wallet_id"), wallet);
            reversal.put("status", "SUCCEEDED");
            store.put("reversals", text(reversal, "id"), reversal);
            payment.put("status", "REVERSED");
            store.put("payments", paymentId, payment);
            addLedger(paymentId, "REVERSAL", text(payment, "wallet_id"), amount);
            addLedger(paymentId, "REVERSAL", "clearing", -amount);
        }
    }

    private void reconcileLedger(Map<String, Object> payment, List<Map<String, Object>> differences) {
        String paymentId = text(payment, "id");
        List<Map<String, Object>> entries = store.list("ledger").stream()
                .filter(entry -> paymentId.equals(text(entry, "operation"))).toList();
        String status = text(payment, "status");
        long paymentEntries = entries.stream().filter(entry -> "PAYMENT".equals(text(entry, "kind"))).count();
        long reversalEntries = entries.stream().filter(entry -> "REVERSAL".equals(text(entry, "kind"))).count();
        boolean hasPayment = List.of("SUCCEEDED", "REVERSED").contains(status);
        boolean hasReversal = "REVERSED".equals(status);
        if ((hasPayment && paymentEntries != 2) || (!hasPayment && paymentEntries != 0)
                || (hasReversal && reversalEntries != 2) || (!hasReversal && reversalEntries != 0)) {
            addIncident(differences, paymentId, "LEDGER_MISMATCH");
        }
        int sum = entries.stream().mapToInt(entry -> integer(entry, "amount")).sum();
        if (sum != 0) addIncident(differences, paymentId, "LEDGER_UNBALANCED");
    }

    private void addIncident(List<Map<String, Object>> differences, String resource, String reason) {
        Map<String, Object> existing = store.list("incidents").stream()
                .filter(incident -> resource.equals(text(incident, "payment_id"))
                        && reason.equals(text(incident, "reason")))
                .findFirst().orElse(null);
        String incidentId = existing == null ? id("incident") : text(existing, "id");
        Map<String, Object> incident = map("id", incidentId, "payment_id", resource,
                "reason", reason, "status", "OPEN");
        if (differences.stream().noneMatch(current -> sameIncident(current, incident))) {
            differences.add(incident);
        }
    }

    private boolean sameIncident(Map<String, Object> first, Map<String, Object> second) {
        return text(first, "payment_id").equals(text(second, "payment_id"))
                && text(first, "reason").equals(text(second, "reason"));
    }

    private Map<String, Object> findReversalForPayment(String paymentId) {
        return store.list("reversals").stream()
                .filter(item -> paymentId.equals(text(item, "payment_id")))
                .findFirst().orElse(null);
    }

    private String createEvent(String paymentId, String target) {
        String eventId = id("evt");
        store.put("events", eventId, map(
                "id", eventId, "payment_id", paymentId, "target", target, "delivered", 0));
        return eventId;
    }

    private void addCase(String resource, String reason) {
        boolean exists = store.list("cases").stream()
                .anyMatch(item -> resource.equals(text(item, "resource"))
                        && reason.equals(text(item, "reason")) && "OPEN".equals(text(item, "status")));
        if (!exists) {
            String caseId = id("case");
            store.put("cases", caseId, map("id", caseId, "resource", resource,
                    "reason", reason, "status", "OPEN"));
        }
    }

    private void resolveCases(String resource, String reason) {
        for (Map<String, Object> supportCase : store.list("cases")) {
            if (resource.equals(text(supportCase, "resource"))
                    && reason.equals(text(supportCase, "reason"))) {
                supportCase.put("status", "RESOLVED");
                store.put("cases", text(supportCase, "id"), supportCase);
            }
        }
    }

    private void addLedger(String operation, String kind, String account, int amount) {
        String ledgerId = id("ledger");
        store.put("ledger", ledgerId, map(
                "id", store.list("ledger").size() + 1, "operation", operation,
                "kind", kind, "account", account, "amount", amount));
    }

    private Map<String, Object> settlementRow(Map<String, Object> provider, Map<String, Object> adjustment) {
        Integer amount = integer(provider, "amount");
        if (adjustment != null && "AMOUNT".equals(text(adjustment, "mode"))) {
            amount = integer(adjustment, "amountCents");
        }
        return map("id", text(provider, "id"), "amount", amount, "status", text(provider, "status"));
    }

    private String providerInitialStatus(String scenario) {
        if ("APPROVED".equals(scenario) || "TIMEOUT_AFTER_PROCESSING".equals(scenario)) return "SUCCEEDED";
        if ("REJECTED".equals(scenario)) return "REJECTED";
        return "PENDING";
    }

    private Map<String, Object> provider(String id, int amount, String status) {
        return map("id", id, "amount", amount, "status", status);
    }

    private Map<String, Object> wallet(String id, String status, int balance, int reserved) {
        return map("id", id, "status", status, "balance", balance, "reserved", reserved,
                "available", balance - reserved, "currency", "PEN");
    }

    private void recalculateAvailable(Map<String, Object> wallet) {
        wallet.put("available", integer(wallet, "balance") - integer(wallet, "reserved"));
    }

    private int available(Map<String, Object> wallet) {
        return integer(wallet, "balance") - integer(wallet, "reserved");
    }

    private Map<String, Object> usageSummary() {
        List<Map<String, Object>> logs = store.list("http_logs");
        Map<String, List<Map<String, Object>>> byEndpoint = new LinkedHashMap<>();
        for (Map<String, Object> log : logs) {
            String key = text(log, "method") + "|" + text(log, "endpoint");
            byEndpoint.computeIfAbsent(key, unused -> new ArrayList<>()).add(log);
        }
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (List<Map<String, Object>> endpointLogs : byEndpoint.values()) {
            Map<String, Object> first = endpointLogs.getFirst();
            long errors = endpointLogs.stream().filter(log -> integer(log, "status") >= 400).count();
            double averageDuration = endpointLogs.stream()
                    .mapToLong(log -> ((Number) log.get("durationMs")).longValue())
                    .average().orElse(0);
            endpoints.add(map("method", text(first, "method"), "endpoint", text(first, "endpoint"),
                    "totalRequests", endpointLogs.size(), "errors", errors,
                    "avgDurationMs", averageDuration));
        }
        long errors = logs.stream().filter(log -> integer(log, "status") >= 400).count();
        double averageDuration = logs.stream()
                .mapToLong(log -> ((Number) log.get("durationMs")).longValue())
                .average().orElse(0);
        return map("groupId", 3, "totalRequests", logs.size(), "errors", errors,
                "avgDurationMs", averageDuration, "endpoints", endpoints);
    }

    private Integer parseFilter(String value, Integer defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ApiResult found(Map<String, Object> record) {
        return record == null ? ApiResult.error(404, "NOT_FOUND") : ApiResult.ok(record);
    }

    private boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String text(Map<String, Object> values, String key, String defaultValue) {
        String value = text(values, key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Integer integer(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
