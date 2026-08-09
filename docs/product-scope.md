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

Phases 1 through 4A are merged. Phase 4B adds ADMIN-only table-service order capture, optional seated-reservation traceability, immutable item and modifier pricing snapshots, exact totals, controlled order transitions, and business-visible status history.

Phase 4B does not include customer ordering, delivery, kitchen behavior, WebSocket order events, live occupancy, inventory consumption, payments, invoices, taxes, discounts, service charges, tips, split bills, refunds, image uploads, public menu publishing, or multi-currency behavior. Those workflows remain later phases.
