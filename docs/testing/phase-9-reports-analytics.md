# Phase 9 reports and analytics testing

## Metric contract

Every report uses a required UTC `[from,to)` interval: `from` is included and `to` is excluded. The maximum interval is 366 days. The browser builds UTC instants from local calendar boundaries; SQL day, week, and month buckets are UTC. This single-restaurant phase does not configure a restaurant timezone.

- **Completed order value** is the sum of immutable `orders.total` for `COMPLETED` orders whose `completed_at` is in range. It excludes `OPEN`, `SUBMITTED`, and `CANCELLED` orders.
- **Payments received** is the sum of immutable successful payments whose `received_at` is in range. It is distinct from completed order value and is not accounting revenue.
- **Menu performance** groups completed-order line snapshots by source menu-item ID. Amounts and quantities remain historical; the deterministic displayed code/name is a representative stored snapshot and never comes from current menu pricing.
- **Reservations** use `start_at` membership and return status, planned-party, and table aggregates without guest PII.
- **Kitchen preparation duration** is `created_at` to `ready_at` for tickets that have a READY timestamp; unfinished tickets are excluded from the average.
- **Inventory** reports exact range movement per item and canonical unit. Current on-hand and low-stock flags are labeled current state; incompatible units are never summed.
- **Staff** uses shift `start_at` membership and counts the full planned duration of each non-cancelled shift as scheduled hours. Completed hours are planned durations for `COMPLETED` shifts, not attendance, payroll, or worked-time evidence.

## Automated verification

The normal Maven test lifecycle covers range validation, top bounds, safe errors, ADMIN authorization, operational-role denial, and CSV quoting/formula neutralization. The Testcontainers MySQL profile runs all report SQL against MySQL 8.4 with controlled boundary fixtures. It checks exact completed-order, payment, reconciliation, invoice, reservation, READY-duration, inventory-unit, and staff-hour results; verifies the exclusive `to` boundary; inspects CSV headers/content; and proves representative GETs leave core and audit row counts unchanged. Fresh migration verification checks Flyway V1–V11 and all three report indexes.

Frontend tests cover local half-open presets and custom ranges, authenticated Axios requests, report query parameters, protected routing/navigation, server-authoritative KPI and breakdown rendering, canonical inventory units, controlled loading/errors, retry, and safe blob downloads. The full quality gate is:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify

cd ..\frontend
npm run test
npm run lint
npm run format:check
npm run build

cd ..
git diff --check
```

## CSV security and boundaries

Sales, menu, payment, reservation, inventory, and staff exports reuse the same ADMIN-only range validation and bounded aggregate queries. Files are UTF-8 attachments with allowlisted filenames. Every cell is quoted, embedded quotes are doubled, and leading `=`, `+`, `-`, and `@` markers receive a literal apostrophe. No export contains guest contact data, employee contacts, raw payment references, authentication material, or supplier contacts.

## Manual verification

Real-browser verification uses the existing development administrator and persistent MySQL data without exposing environment values. It covers presets, custom range, each report tab, comparison against authoritative records, CSV downloads/content, desktop and 375px layouts, navigation, kitchen realtime, console cleanliness, and confirmation that authentication material is absent from browser storage and script-readable cookies.

## Limitations

Phase 9 does not provide formal accounting, profit/loss statements, tax or VAT reports, payroll, labor costs, forecasting, AI insight, demand prediction, customer profiles, cross-location or multi-currency analytics, external BI, scheduled email reports, streaming analytics, a warehouse, or fiscal reporting. Reports are query-time snapshots of committed operational data.
