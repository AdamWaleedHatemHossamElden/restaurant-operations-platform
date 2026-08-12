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

Phases 1 through 6 are merged. Phase 7 is current unmerged work on `phase-7-staff-scheduling`. It adds ADMIN-only employee records, scheduling-domain operational roles, date-specific availability, weekly shift planning, overlap protection, controlled completion/cancellation, audit, and responsive staff views.

Phase 7 intentionally keeps employees separate from authentication users. Operational roles do not grant authorities, and availability is date-specific rather than recurring. Payroll, salaries, tax or bank data, benefits, leave balances, time clocks, attendance, timesheets, overtime and labor-law rules, shift swaps, employee self-service, user-account provisioning, notifications, automatic scheduling, forecasts, and multi-location staffing remain outside scope.
