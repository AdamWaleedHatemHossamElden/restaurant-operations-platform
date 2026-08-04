# Roadmap

1. **Phase 1 — Foundation:** project structure, React/Spring Boot foundations, MySQL and Flyway, security defaults, health checks, test tooling, and architecture documentation.
2. **Phase 2 — Authentication and roles:** Phase 2A backend authentication is merged. **Phase 2B frontend authentication is implemented on this branch** with login validation, memory-only access tokens, HttpOnly-cookie session recovery, single-flight refresh, protected routes, safe logout, and an authenticated identity dashboard. Later authorization and user-management slices remain pending.
3. **Phase 3 — Restaurant tables and reservations:** table configuration, customers, reservations, availability rules, and host workflows.
4. **Phase 4 — Menu and order management:** categories, items, modifiers, order capture, pricing snapshots, and status history.
5. **Phase 5 — Kitchen display and real-time updates:** kitchen queues, authenticated WebSocket/STOMP events, preparation states, and reconnection behavior.
6. **Phase 6 — Inventory, recipes, and suppliers:** inventory ledger, recipe consumption, stock alerts, suppliers, and purchasing foundations.
7. **Phase 7 — Staff scheduling:** employee records, availability, shifts, scheduling constraints, and staff views.
8. **Phase 8 — Payments and invoices:** payment recording, invoice generation, reconciliation boundaries, and secure provider integration design.
9. **Phase 9 — Reports and analytics:** permission-aware operational reports, export design, and tested business metrics.
10. **Phase 10 — Testing, deployment, and portfolio presentation:** end-to-end testing, production hardening, CI/CD, observability, deployment, accessibility review, and portfolio narrative.

Each phase should ship tested vertical behavior without claiming later modules are complete.
