# Phase 4B order-management verification

## Scope and architecture

Phase 4B adds Flyway V6 and an ADMIN-only `/api/v1/orders` vertical slice. An order is a transaction boundary containing metadata, ordered item snapshots, modifier snapshots, exact aggregate totals, optimistic versioning, lifecycle timestamps, and chronological status history. Orders are never hard-deleted. Draft item rows may be removed only while an order is `OPEN`; submitted history is immutable.

Each order requires an active restaurant table with operational status `AVAILABLE`. An optional reservation must be `SEATED`, assigned to a table, and match the order table. Order behavior never changes table or reservation status. This phase assumes one logical restaurant and intentionally excludes live occupancy.

## Pricing and editing contract

The server generates order numbers and is the sole pricing authority. At item creation it snapshots current item code, name, base price, modifier-group names, modifier-option names, and modifier adjustments. All money uses Java `BigDecimal`, MySQL `DECIMAL(12,2)`, and decimal-string JSON representation. A unit total is the base snapshot plus modifier-adjustment snapshots; a line total is unit total multiplied by quantity; subtotal is the sum of line totals; and Phase 4B defines total equal to subtotal.

Later menu changes never rewrite an existing snapshot. Quantity-only and notes-only edits preserve the complete stored snapshot and recalculate totals from it. Supplying new modifier selections revalidates the current effective menu configuration and refreshes the whole line snapshot, avoiding mixed old and new pricing. An unavailable item or invalid current modifier configuration returns a safe HTTP 409.

## Lifecycle and concurrency

New orders begin `OPEN` and receive an initial history row. Supported transitions are `OPEN` to `SUBMITTED` or `CANCELLED`, and `SUBMITTED` to `COMPLETED` or `CANCELLED`. Submission requires at least one item. Submitted orders are commercially frozen, and terminal orders cannot reopen.

Item mutations, metadata changes, and transitions acquire a database-backed pessimistic write lock on the order row, then validate the supplied version. Menu locks follow deterministic modifier-group ID order. This serialization prevents lost totals, duplicate transitions, and post-submission item mutation. Expected stale, lock, and state conflicts map to safe HTTP 409 responses rather than leaking SQL, Hibernate, or implementation details.

## API, authorization, audit, and history

- `GET/POST /api/v1/orders`
- `GET/PUT /api/v1/orders/{id}`
- `POST /api/v1/orders/{id}/items`
- `PUT/DELETE /api/v1/orders/{id}/items/{itemId}`
- `PATCH /api/v1/orders/{id}/status`
- `GET /api/v1/orders/{id}/history`

List filters cover status, table, reservation, order number, and created range; sorting uses an explicit allowlist. All endpoints require Bearer authentication and `ADMIN`. Successful changes persist `ORDER_CREATED`, `ORDER_UPDATED`, `ORDER_ITEM_ADDED`, `ORDER_ITEM_UPDATED`, `ORDER_ITEM_REMOVED`, `ORDER_SUBMITTED`, `ORDER_COMPLETED`, or `ORDER_CANCELLED` in the same transaction. Audit details contain safe identifiers and short summaries, never credentials, tokens, cookies, authorization headers, request bodies, or full notes. Status history is a separate business-visible record and is not inferred from timestamps or audit text.

## Automated verification

From `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

The integration profile starts fresh MySQL 8.4 containers, applies Flyway V1-V6, verifies the order schema, and exercises real persistence, snapshots, totals, lifecycle immutability, audit/history consistency, and a genuinely overlapping item-mutation-versus-submission race.

From `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Frontend coverage includes protected routes, navigation, list states and filters, creation, menu browsing, modifier selection, server-authoritative totals, draft edits and removal, lifecycle actions, conflict handling, history, local timestamp display, query invalidation, and continued use of the existing authenticated client without token persistence.

## Manual verification checklist

Run the existing persistent MySQL service without deleting its volume, start the backend with the `dev` profile and ignored root environment, and start Vite. Log in as the local development administrator. Verify plain and seated-reservation order creation; incompatible reservation handling; simple, `SINGLE`, and `MULTIPLE` modifier lines; required selections; exact totals; snapshot isolation after a menu-price change; quantity preservation; full repricing after modifier changes; item removal; submission immutability; completion and cancellation; status history; list search, filters, and sorting; safe stale conflicts; desktop and 375px layouts; absence of horizontal overflow; and a clean browser console.

## Current limitations and Phase 5 boundary

- One logical restaurant, ADMIN-only access, and EUR display.
- No hard order deletion or reopening of terminal orders.
- No taxes, discounts, promotions, service charges, tips, payments, invoices, split bills, or refunds.
- No customer-facing ordering, delivery, live occupancy, inventory consumption, or staff assignment.
- Kitchen queues, kitchen statuses, and authenticated real-time order events begin in Phase 5 and are not implied by Phase 4B submission.
