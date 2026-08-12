# Phase 7 staff scheduling testing

## Scope

Phase 7 adds ADMIN-only employee records, date-specific availability, and weekly shifts below `/api/v1/staff` and `/staff`. Operational roles describe scheduled work only; they do not create authorities or user accounts.

## Employee model

Employee codes are trimmed, uppercased, format-bounded, and uniquely constrained in MySQL. First and last names are required; email, phone, and employment start date are optional. Records use soft activation and optimistic versions. Deactivation returns 409 while future `SCHEDULED` shifts exist. Completed and cancelled history remains, and no hard-delete API exists.

## Availability and time policy

Availability is an exact date-specific UTC interval. The browser converts `datetime-local` values to UTC and formats responses in local time. Windows require `start < end`; overlapping windows for one employee return 409, while adjacent half-open windows are allowed. New or edited scheduled shifts must fit fully inside one window. Editing or removing availability never cancels or rewrites existing shifts.

## Shift lifecycle and overlap

Shifts begin `SCHEDULED` and may become `COMPLETED` or `CANCELLED`. Both terminal states are read-only and cannot reopen. A non-cancelled shift blocks another interval when `existing.start < requested.end` and `existing.end > requested.start`; adjacent shifts therefore remain valid. Cancelled shifts no longer block replacement scheduling.

## Concurrency and audit

Writes use MySQL row locks in the order `employee → availability → shifts`. Two genuine simultaneous overlapping shift requests for one employee yield exactly one HTTP 201 and one safe HTTP 409, with one shift and one `SHIFT_CREATED` audit. A focused concurrent availability test likewise proves one success, one conflict, one window, and one success audit. Stale, overlap, unavailable, inactive, and invalid-transition failures persist no success audit.

Safe events cover employee create/update/activation, availability create/update/remove, and shift create/update/complete/cancel. Details contain identifiers and concise state only; contact values, notes, request bodies, authorization headers, credentials, tokens, and cookies are excluded.

## Automated verification

Run from `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

Run from `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

The MySQL suite verifies fresh Flyway V1–V9, database constraints, normalized uniqueness, lifecycle rules, stale writes, audits, and real concurrency. Frontend tests cover the protected route, authenticated client and version payloads, employee filters and forms, availability history, weekly schedule behavior, terminal read-only state, local/UTC conversion, and query invalidation.

Final verification passed with 70 backend unit/security/OpenAPI tests, 32 MySQL 8.4 integration tests, and 98 frontend tests. Maven test, verify, packaging, Checkstyle, ESLint, Prettier, the frontend production build, and `git diff --check` all passed. Fresh MySQL schemas applied Flyway V1–V9 successfully.

## Manual verification

Use the ignored root `.env`, persistent MySQL 8.4 service, real development administrator, backend, and frontend. Create Maria (`WAITER`) and Nikos (`KITCHEN`), add exact local availability, verify overlapping availability and out-of-window or overlapping shifts return safe conflicts, verify adjacent shifts, edit and transition shifts, cancel before deactivation, reactivate, and confirm desktop and 375px layouts. Existing Orders, Kitchen, Inventory, authentication, and real-time navigation must remain functional with no browser console errors.

The final real-browser pass completed that workflow. It confirmed safe availability and shift conflicts, adjacent replacement scheduling after cancellation, terminal read-only behavior, deactivation protection, inactive-employee rejection, reactivation, browser-local schedule display, a vertically stacked 375px week without page overflow, intact Orders/Kitchen/Inventory navigation, a connected kitchen realtime session, authentication recovery after reload, and zero browser console warnings or errors in the clean verification tab.

## Current limitations

Phase 7 has no payroll, salaries, tax or bank data, benefits, leave accounting, time clock, attendance, timesheets, overtime or labor-law engine, recurring availability, shift swaps, employee self-service, account provisioning, notifications, automatic scheduling, forecasts, or multi-location staffing.
