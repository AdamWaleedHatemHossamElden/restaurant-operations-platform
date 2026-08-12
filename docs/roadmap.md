# Roadmap

1. **Phase 1 — Foundation:** project structure, React/Spring Boot foundations, MySQL and Flyway, security defaults, health checks, test tooling, and architecture documentation.
2. **Phase 2 — Authentication and roles:** backend and frontend authentication are merged, including rotating refresh sessions, memory-only access tokens, protected routes, and safe logout. Later user-management slices remain pending.
3. **Phase 3 — Restaurant tables and reservations:** Phase 3A table management and Phase 3B reservation management are merged into `main`, including UTC scheduling, live suitability, serialized overlap protection, optimistic locking, controlled transitions, audit events, and responsive workspaces.
4. **Phase 4 — Menu and order management:** Phase 4A menu configuration and Phase 4B order management are merged into `main`, including table-service order capture, immutable pricing snapshots, exact totals, controlled lifecycle transitions, and chronological status history.
5. **Phase 5 — Kitchen display and real-time updates:** merged, including transactional kitchen tickets, item preparation, derived queue state, READY-gated completion, authenticated WebSocket/STOMP notifications, and REST recovery after reconnect.
6. **Phase 6 — Inventory, recipes, suppliers, and purchasing:** merged into `main`, including the immutable stock ledger, low-stock visibility, kitchen recipe consumption, supplier pricing, purchase orders, partial receiving, and concurrency protection.
7. **Phase 7 — Staff scheduling:** current unmerged work on `phase-7-staff-scheduling`, including employee records, scheduling-domain operational roles, date-specific availability, conflict-safe shifts, controlled lifecycle, audit, and responsive weekly views.
8. **Phase 8 — Payments and invoices:** payment recording, invoice generation, reconciliation boundaries, and secure provider integration design.
9. **Phase 9 — Reports and analytics:** permission-aware operational reports, export design, and tested business metrics.
10. **Phase 10 — Testing, deployment, and portfolio presentation:** end-to-end testing, production hardening, CI/CD, observability, deployment, accessibility review, and portfolio narrative.

Each phase should ship tested vertical behavior without claiming later modules are complete.
