# Phase 3B reservation-management verification

## Scope and data model

Phase 3B adds a single-restaurant reservation registry and management workspace. Flyway V4 creates `reservations` with a generated unique code, guest contact fields, party size, UTC start instant, supported duration, optional restaurant-table foreign key, workflow status, optional notes, timestamps, and an optimistic-lock version. Check constraints enforce party sizes from 1 through 100, durations from 15 through 480 minutes, and the six supported statuses. There is no delete endpoint.

## API and authorization

All reservation endpoints require a valid Bearer access token with the `ADMIN` role:

- `GET /api/v1/reservations` filters by start range, status, table, assigned state, guest name, and reservation code, with allowlisted sorting.
- `GET /api/v1/reservations/{id}` returns one reservation.
- `POST /api/v1/reservations` creates a pending assigned or unassigned reservation.
- `PUT /api/v1/reservations/{id}` edits non-terminal details and assignment using the latest version.
- `PATCH /api/v1/reservations/{id}/status` applies a valid transition using the latest version.
- `GET /api/v1/reservations/availability` lists suitable tables for a start instant, duration, and party size, optionally excluding the edited reservation.

Safe JSON errors use 400 for malformed or invalid requests, 401 for missing authentication, 403 for non-ADMIN access, 404 for missing reservations or tables, and 409 for availability, transition, contention, uniqueness, terminal-edit, or optimistic-lock conflicts.

## Availability and concurrency

Intervals use the half-open overlap rule `existing.start < requested.end AND existing.end > requested.start`; adjacent reservations therefore do not overlap. Only `CONFIRMED` and `SEATED` block availability. `PENDING`, `COMPLETED`, `CANCELLED`, and `NO_SHOW` are non-blocking.

The availability endpoint is an advisory current view. Every assignment, reassignment, or transition into a blocking state repeats validation inside its write transaction. The service locks the target `restaurant_tables` row with `PESSIMISTIC_WRITE`, then performs a locking overlap read before saving. Requests for one table are therefore ordered by MySQL, and the later contender sees the earlier commit. Each operation locks one target table, giving a consistent lock order and avoiding cross-table lock cycles. Expected lock contention is returned as safe HTTP 409 rather than 500.

A table must also exist, remain active, have operational status `AVAILABLE`, and have capacity at least equal to party size. Reservation operations never modify the table's operational status.

## Status transitions

Valid transitions are:

- `PENDING` to `CONFIRMED` or `CANCELLED`
- `CONFIRMED` to `SEATED`, `CANCELLED`, or `NO_SHOW`
- `SEATED` to `COMPLETED` or explicitly to `CANCELLED`

`COMPLETED`, `CANCELLED`, and `NO_SHOW` are terminal and cannot be reopened or edited. Invalid transitions return HTTP 409.

## Time behavior

The API accepts and returns ISO-8601 instants. Hibernate, JDBC, Jackson, and MySQL run in UTC. The frontend converts `datetime-local` browser input to an ISO UTC instant before sending and converts returned instants to browser-local values for forms and display. Changing the browser timezone changes presentation, not the stored instant.

## Audit behavior

Expected actions include `RESERVATION_CREATED`, `RESERVATION_UPDATED`, status-specific actions, `RESERVATION_TABLE_ASSIGNED`, and `RESERVATION_TABLE_CHANGED`. Audit rows retain actor, reservation identifier, optional table identifier, and truncated IP only. They exclude guest contact data, notes, request bodies, headers, tokens, and cookies.

## Automated verification

From `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

The MySQL 8.4 integration profile verifies Flyway V4, assigned and unassigned creation, validation, table suitability, overlap and adjacency, filtering, editing, reassignment, optimistic conflicts, every status path, persisted audits, and simultaneous overlapping confirmations. The concurrency test coordinates two real requests and proves exactly one blocking reservation remains.

From `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Frontend coverage includes protected routing, authenticated API calls, UTC/local conversion, states and filters, validated assigned and unassigned creation, availability loading, versioned editing and reassignment, status actions and confirmations, safe conflicts, and React Query invalidation.

## Manual browser checklist

1. Keep the persistent MySQL volume and apply Flyway V4 with the `dev` profile.
2. Sign in with the ignored local administrator credentials and open `/reservations`.
3. Create one unassigned reservation and one assigned reservation.
4. Confirm the assigned reservation, then verify an overlapping assignment is rejected and an adjacent one is allowed.
5. Verify insufficient, inactive, and out-of-service tables are absent from suitability results.
6. Edit and reassign a non-terminal reservation.
7. Complete `PENDING -> CONFIRMED -> SEATED -> COMPLETED` and separately test cancellation and no-show.
8. Exercise date, status, assignment, guest, code, and sorting controls.
9. Verify displayed time matches the browser timezone on desktop and at 375 px without horizontal overflow.
10. Confirm the browser console contains no errors, warnings, tokens, or guest request payloads.

## Current limitations

The feature assumes one logical restaurant and ADMIN-only operation. It does not provide customer CRM, notifications, occupancy automation, drag-and-drop calendars, orders, table-status automation, or multi-restaurant authorization.
