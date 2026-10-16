---
name: quarkus-sqlite-openapi
description: 'Implement or change this project’s Quarkus REST endpoints, SQLite-backed behavior, and OpenAPI contract as one consistent backend feature.'
---

# Quarkus, SQLite, and OpenAPI Backend Work

Use this workflow when adding or changing a backend capability that crosses the HTTP API, persistence, or API contract.

## Procedure

1. **Trace the existing path.** Read the related REST resource, service, `SqliteStore`, configuration, OpenAPI definition, and tests. Identify the current request/response and error conventions before choosing where logic belongs.
2. **Implement in the established layers.** Keep HTTP handling in the Quarkus REST resource and business rules in the service. Reuse the CDI-managed SQLite store and `wallet.db.path` configuration; retain its prepared-statement, synchronization, and error-handling patterns. Avoid adding an ORM or a second storage mechanism unless the task explicitly calls for an architectural change.
3. **Keep the contract aligned.** For externally visible changes, update `src/main/resources/META-INF/resources/openapi.yaml` with the route, methods, schemas, status codes, and error responses. The project also configures the runtime OpenAPI endpoint at `/q/openapi`; preserve both contract surfaces as appropriate.
4. **Prove the behavior.** Add or adjust tests in `src/test/java` for success cases, validation, expected errors, and persistence behavior. Follow existing `@QuarkusTest` and Rest Assured conventions. Run the narrowest relevant Maven test target, then run the broader test suite or build if the change spans multiple layers.
5. **Check consistency.** Review the diff for mismatched JSON names, status codes, error shapes, stale OpenAPI entries, unparameterized SQL, and missing tests. Summarize changes and report the exact validation performed.

## Completion criteria

The changed behavior is covered by tests, the checked-in OpenAPI contract matches the externally visible API, persistence follows the existing SQLite approach, and relevant validation commands pass or are explicitly reported as blocked.
