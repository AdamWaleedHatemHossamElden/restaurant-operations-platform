# Product scope

## Product problem

Restaurant teams often coordinate reservations, table service, kitchen work, stock, staffing, and payments through disconnected tools or informal hand-offs. The Restaurant Operations Platform is intended to provide one coherent operational system while retaining clear module boundaries.

## Intended users

- Restaurant administrators and managers
- Hosts and reservation staff
- Waiters and cashiers
- Kitchen staff
- Inventory managers and purchasing staff
- Employees who need schedules and shift information

## Planned workflows

The product roadmap covers identity and role assignment; employee and shift coordination; table and reservation management; menu publishing; order capture and kitchen progression; inventory consumption and purchasing; payment recording; operational reporting; notifications; and traceable auditing.

## Planned modules

The modular monolith is planned around `auth`, `users`, `employees`, `tables`, `reservations`, `menu`, `orders`, `kitchen`, `inventory`, `suppliers`, `payments`, `reports`, `notifications`, and `audit`. A module will be added only when its phase is implemented; Phase 1 does not create empty classes for these future areas.

## Current delivery boundary

Phases 1 through 7 are merged. Phase 8 is current unmerged work on `phase-8-payments-invoices`. It adds ADMIN-only recording of already-confirmed EUR payments, derived settlement state, immutable one-time reconciliation, paid-order invoice snapshots, concurrency-safe idempotency and overpayment protection, audit, and responsive settlement views.

Phase 8 intentionally does not process funds or collect card/bank credentials. Payment-provider integrations, authorization and capture, refunds, voids, chargebacks, taxes, discounts, tips, configurable currencies, accounting exports, fiscal numbering, and jurisdiction-specific invoice compliance remain outside scope.
