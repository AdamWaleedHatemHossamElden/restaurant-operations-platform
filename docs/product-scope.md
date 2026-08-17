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

## Implemented workflows

The product covers identity and administrator authorization; employee and shift coordination; table and reservation management; menu configuration; order capture and kitchen progression; inventory consumption and purchasing; confirmed payment recording and invoicing; operational reporting; real-time kitchen notifications; and traceable auditing.

## Implemented modules

The modular monolith is organized around authentication/users, tables, reservations, menu, orders, kitchen, inventory and purchasing, staff scheduling, payments/invoices, reports, real-time kitchen notifications, and audit behavior. Domain packages own their workflows and persistence boundaries inside one Spring Boot deployment.

## Current delivery boundary

The implemented product scope is feature-complete for portfolio purposes. It includes the Ember hospitality-operations interface, responsive authenticated workspaces, the operational modules listed above, production configuration and CI foundations, and an optional development-only showcase dataset. Public deployment is optional and currently deferred.

Reporting remains operational analytics, not formal accounting, profit/loss, tax or VAT reporting, payroll, labor cost, forecasting, AI insight, demand prediction, customer profiling, cross-location or multi-currency analytics, external BI, scheduled reporting, streaming analytics, a data warehouse, or fiscal reporting. Payments record confirmed settlements but do not process funds or collect card/bank credentials. Customer ordering, multi-restaurant tenancy, employee authentication/self-service, and provider integrations remain outside the current boundary.
