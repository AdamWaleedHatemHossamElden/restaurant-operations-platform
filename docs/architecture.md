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

## Testing strategy

Fast unit and MVC tests run with the normal Maven lifecycle and do not require Docker. The `integration-test` Maven profile runs Testcontainers MySQL tests for fresh Flyway migration and real locking/concurrency behavior. Frontend behavior is covered with Vitest, React Testing Library, and jsdom; stable vertical workflows are also verified manually in a real browser against the development services.
