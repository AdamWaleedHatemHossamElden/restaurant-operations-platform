# Phase 5 kitchen and real-time verification

## Scope and architecture

Phase 5 adds Flyway V7, an ADMIN-only `/api/v1/kitchen` REST slice, native STOMP notifications, a protected `/kitchen` display, and kitchen state on order details. MySQL and REST responses are authoritative. WebSocket messages are intentionally small invalidation signals rather than commands or a durable state replica.

Submitting an `OPEN` order creates exactly one ticket and one kitchen item per order item in the same transaction. Items progress only `QUEUED → PREPARING → READY`; the ticket is derived as `QUEUED`, `PREPARING`, or `READY`. Cancelling a submitted order transactionally marks the ticket `CANCELLED` without erasing item history. An order with a V7 ticket cannot be completed until that ticket is `READY`, and READY never auto-completes the order.

V7 uses InnoDB, `utf8mb4`, restrictive foreign keys, uniqueness for order/ticket and order-item/kitchen-item relationships, checks, queue indexes, UTC timestamps, and an optimistic ticket version. It backfills only active `SUBMITTED` development orders so completed or cancelled historical orders do not receive invented kitchen history. Kitchen views read V6 immutable item and modifier snapshot labels and do not duplicate pricing.

## Concurrency and errors

All lifecycle paths use the lock order `orders → kitchen_tickets → kitchen_ticket_items`. An item command first discovers its parent order without materializing stale entities, then acquires the order and ticket write locks, validates the submitted/cancelled state and request version, locks the item, applies one legal transition, and derives the aggregate while the locks remain held. Order cancellation and completion use the same prefix, preventing item updates after cancellation and completion before the final READY commit.

Malformed input is HTTP 400; missing resources are 404; invalid transitions, stale versions, expected lock/contention failures, cancellation races, and premature completion are safe 409 responses. Unauthenticated REST is 401 and authenticated non-ADMIN access is 403. Responses never expose SQL, ORM types, stack traces, credentials, tokens, cookies, or request bodies.

## WebSocket security and reliability

The browser opens native STOMP at `/ws` and sends the current access token only in the `CONNECT` Authorization header. The handshake returns no private data and is limited to the configured frontend origin. The existing JWT decoder validates the token and derives ADMIN authority. Anonymous, invalid, expired, or non-ADMIN connections cannot subscribe; only `/topic/kitchen` is allowed and every client `SEND` command is rejected.

Domain events are raised inside successful kitchen/order transactions and sent by a `TransactionalEventListener` in `AFTER_COMMIT`. A rollback therefore emits no success notification, while a delivery exception is contained after commit and cannot undo business state. There is no durable broker or outbox in Phase 5. Initial load, every event, and every reconnect invalidate/refetch REST data, which closes gaps after a temporary disconnect.

The frontend creates one connection per authenticated React context, obtains the latest memory-only token before each connection, reuses the existing single-flight refresh recovery when necessary, retries with a bounded delay, and disconnects on logout. Access tokens are not written to localStorage, sessionStorage, IndexedDB, URLs, or readable cookies.

## REST and frontend behavior

- `GET /api/v1/kitchen/tickets` supports allowlisted status, table, order-number, submitted-time, and sort filters.
- `GET /api/v1/kitchen/tickets/{id}` returns a DTO with order/table/reservation summaries and immutable snapshot instructions.
- `GET /api/v1/kitchen/orders/{orderId}` supplies the compact order-detail integration.
- `PATCH /api/v1/kitchen/tickets/{ticketId}/items/{itemId}/status` accepts only the next legal item status and current ticket version.
- `/kitchen` groups active work into QUEUED, PREPARING, and READY columns; cancelled history is filterable and read-only.
- `/orders/{id}` displays authoritative kitchen state and enables explicit completion only after READY.

The responsive display uses textual states, accessible action names, loading/empty/error/reconnection feedback, touch-friendly controls, and stacked 375px layouts without page-level horizontal overflow.

## Automated verification

Run from `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

Coverage includes V1–V7 migration and constraints, transactional ticket creation/cancellation/completion, item and aggregate lifecycle, timestamps, snapshot persistence after catalog renames, REST security, safe errors, audit correctness, actual overlapping MySQL item transitions, STOMP authentication/destination restrictions, and after-commit delivery isolation.

Run from `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Coverage includes route protection/navigation, queue/loading/empty/error states, snapshot display, item actions/conflicts, cancelled history, order completion gating, event/reconnect invalidation, single connection lifecycle, logout disconnect, and the existing memory-only session architecture.

Manual verification uses the ignored root `.env`, the persistent MySQL 8.4 service on port 3307, the development backend, and the Vite client. It covers login; no ticket before submission; multiple snapshot items/modifiers; real-time ticket creation and progression in two views; completion gating; cancellation; post-submission menu rename safety; network reconnect/REST recovery; anonymous STOMP rejection; desktop and 375px layouts; storage/cookie checks; clean console; and logout disconnection. Credentials, tokens, and cookie values must never be printed.

## Current limitations

Phase 5 uses the in-process Spring simple broker and best-effort notifications, not durable messaging. A reconnect refetch is the delivery recovery mechanism. It supports one logical restaurant and ADMIN only. It does not implement inventory or recipe deduction, suppliers, payments, taxes, discounts, tips, invoices, customer ordering, delivery, staff roles/scheduling, live occupancy automation, push/email/SMS, multiple locations, Kafka, RabbitMQ, Redis, event sourcing, or microservices.
