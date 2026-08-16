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

Phases 1 through 9 are merged. Phase 10 is current work on `phase-10-ui-ux-redesign`. It redesigns the frontend presentation layer around the Ember hospitality-operations identity, a shared token system, responsive application navigation, an operational dashboard, and consistent interaction patterns without changing the established business APIs or workflows.

Phase 9 is operational analytics, not formal accounting, profit/loss, tax or VAT reporting, payroll, labor cost, forecasting, AI insight, demand prediction, customer profiling, cross-location or multi-currency analytics, external BI, scheduled email reporting, streaming analytics, a data warehouse, or fiscal reporting. Phase 8 also intentionally does not process funds or collect card/bank credentials. Phase 10 changes product presentation and usability only; it does not add new business modules, API semantics, or backend persistence.
