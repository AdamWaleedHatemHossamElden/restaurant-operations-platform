# Phase 3A table-management verification

## Scope

Phase 3A introduces a single-restaurant table registry. It covers table creation, reads, edits, filtering, sorting, operational status, soft deactivation/reactivation, optimistic concurrency control, authorization, and audit logging. It does not introduce reservations, floor-plan coordinates, multi-restaurant tenancy, or hard deletion.

## API contract

All endpoints require a valid Bearer access token with the `ADMIN` role.

- `GET /api/v1/tables` lists tables. Optional filters are `active`, `section`, `status`, and `tableNumber`; `sortBy` and `direction` control sorting.
- `GET /api/v1/tables/{id}` returns one table or 404.
- `POST /api/v1/tables` creates an active table and returns 201.
- `PUT /api/v1/tables/{id}` replaces editable fields and requires the current `version`.
- `PATCH /api/v1/tables/{id}/activation` changes only the soft active state and requires the current `version`.

Duplicate table numbers and stale versions return the normal safe HTTP 409 JSON error. Validation failures return 400, missing records return 404, unauthenticated requests return 401, and non-ADMIN requests return 403. No error response contains request bodies, credentials, tokens, or stack traces.

## Automated verification

From `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

The normal suite covers entity behavior, DTO validation, safe error responses, ADMIN authorization, API documentation, service conflicts, soft activation, and audit dispatch. The integration profile runs MySQL 8.4 and verifies Flyway V3 plus the full create/filter/update/conflict/deactivate/reactivate lifecycle and persisted audit actions.

From `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Frontend tests cover protected routing, the authenticated Axios client, response validation, loading/empty/error/list states, filters, create validation, duplicate conflicts, versioned editing, deactivation confirmation, and reactivation.

## Manual browser checklist

1. Start the existing MySQL service without deleting its volume.
2. Export the ignored root `.env` variables without displaying them, select the `dev` profile, and start the backend.
3. Start the frontend and sign in with the local development administrator.
4. Open `/tables`; verify desktop and narrow responsive layouts.
5. Create a unique table, edit its name/capacity/section/status, deactivate it, filter inactive records, and reactivate it.
6. Attempt a duplicate table number and verify a safe conflict message.
7. Exercise number, status, active, and section filters.
8. Reload the page to verify authenticated session recovery and confirm the browser console has no errors or sensitive output.

Do not record credential values, access tokens, refresh cookies, or root `.env` contents in verification output.

## Persistence and audit checks

Flyway V3 creates `restaurant_tables` with a unique table number, positive-capacity and status constraints, timestamps, active state, and a JPA optimistic-lock version. The lifecycle never deletes a row. Expected audit actions are `TABLE_CREATED`, `TABLE_UPDATED`, `TABLE_DEACTIVATED`, and `TABLE_REACTIVATED`; audit details are intentionally empty for this slice.
