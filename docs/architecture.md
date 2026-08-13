# Architecture

## Modular monolith

The backend is one deployable Spring Boot application with business capabilities organized as explicit modules. This keeps transactions, local development, refactoring, and deployment straightforward while leaving room for disciplined boundaries. A module should own its domain logic and persistence access and expose intentional application interfaces rather than reaching into another module's internals.

Microservices are not appropriate at this stage: there is no demonstrated independent scaling need, large autonomous team structure, or operational maturity that would justify distributed transactions, network failure modes, duplicated infrastructure, and cross-service observability.

## Runtime boundaries

The React application is an independent TypeScript client. It communicates with Spring Boot through versioned JSON REST endpoints under `/api/v1`. Axios provides the HTTP boundary and TanStack Query owns server-state loading, caching, retry, and invalidation. Browser routes are separate from API routes.

Spring Boot owns validation, authorization, application workflows, and persistence. MySQL is the system of record. Hibernate validates entity mappings against the schema and never creates or updates the schema. Flyway is the only schema-change mechanism.

## API conventions

- Versioned resource paths begin with `/api/v1`.
- JSON is the default representation.
- UTC is used for persisted instants and serialized timestamps.
- Validation failures use a consistent API error body.
- OpenAPI and Swagger UI are available only in the development profile.
- Unimplemented and unauthenticated routes are denied by default.

## Real-time direction

Phase 5 uses native STOMP over `/ws` for server-to-client kitchen change notifications. The handshake is origin-restricted and carries no business data; the `CONNECT` frame uses the current memory-only Bearer token, and only authenticated ADMIN subscriptions to `/topic/kitchen` are accepted. REST/MySQL remains authoritative: notifications publish after commit and cause query invalidation, while initial load and reconnect refetch state. The in-process simple broker is deliberately best-effort and no client command destinations or durable messaging infrastructure are introduced.

## Inventory transaction boundary

Phase 6 keeps stock as an append-only ledger rather than a mutable balance. Inventory configuration, recipes, modifier ingredients, suppliers, and purchase orders remain separate aggregates in the modular monolith, while the kitchen service calls one inventory application service inside the existing `QUEUED → PREPARING` transaction. Recipe and modifier configuration rows are locked while their current ingredients are read; usage is aggregated and persisted before kitchen state changes. A database-unique deterministic source key is the final exactly-once authority.

Purchase receiving locks the purchase order before its line, validates the optimistic request version and remaining quantity, appends one receipt movement, and derives the new PO status in one transaction. Negative stock means usage writers do not lock or update a shared balance row, avoiding an unnecessary contention point.

## Staff scheduling transaction boundary

Phase 7 keeps employee identity separate from authentication. Operational roles are scheduling values and do not participate in Spring Security. Employee, availability, and shift commands serialize on the employee row. A scheduling write then locks or reads availability before locking and checking shifts, preserving the deterministic order `employee → availability → shifts`. Under MySQL `READ_COMMITTED`, a waiter sees current committed planning state after acquiring the employee lock, so simultaneous overlaps cannot both pass.

Availability windows and shifts use half-open UTC intervals. Browser-local inputs are converted only at the HTTP boundary. Existing shifts are intentionally not rewritten when availability changes; planned and historical shift state remains explicit and administrator-controlled.

## Payments and invoice transaction boundary

Phase 8 records confirmed settlements; it does not process or authorize funds. A payment command locks the order row, rechecks `COMPLETED`, resolves a required idempotency key, derives successful paid and outstanding totals with `BigDecimal`, and inserts only when the request remains within the outstanding balance. The order row is the common database-backed serialization point for competing payment and invoice commands. A unique idempotency key provides a final retry backstop; identical replay returns the original payment and mismatched replay conflicts.

Reconciliation locks its payment and has one immutable unique child. Invoice issuance locks the same order, requires exact full settlement, and copies existing item and modifier snapshots into a one-per-order invoice aggregate. Concurrent issuance therefore returns the same durable invoice without duplicate audit events. MySQL and REST remain authoritative; the React client holds only one submission idempotency key in component memory and never accepts card credentials.

## Testing strategy

Fast unit and MVC tests run with the normal Maven lifecycle and do not require Docker. The `integration-test` Maven profile runs Testcontainers MySQL tests for fresh Flyway migration and real locking/concurrency behavior. Frontend behavior is covered with Vitest, React Testing Library, and jsdom; stable vertical workflows are also verified manually in a real browser against the development services.
