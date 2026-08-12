# Restaurant Operations Platform

A modern full-stack restaurant operations platform. Phases 1 through 6 are merged into `main`; the current unmerged `phase-7-staff-scheduling` branch adds employees, date-specific availability, and conflict-safe weekly scheduling.

## Current status

- React + TypeScript frontend with a responsive login, startup session recovery, protected routing, authenticated dashboard, and health states
- Java 21 + Spring Boot modular-monolith backend foundation
- Versioned `/api/v1/health` endpoint and Actuator health endpoint
- Deny-by-default, stateless Spring Security configuration
- MySQL 8.4 local service and Flyway schema migrations
- Unit, MVC, frontend, and opt-in Testcontainers integration-test foundations
- Architecture, database, security, scope, and roadmap documentation
- BCrypt password storage, HS256 JWT access tokens, hashed rotating refresh tokens, and authentication audit events
- Backend login, refresh, logout, and current-user endpoints
- Development-only, environment-driven initial administrator bootstrap
- Required-claim JWT validation, timing-resistant login failure handling, and database-serialized refresh rotation
- Development OpenAPI Bearer authorization for the protected current-user endpoint
- Memory-only frontend access tokens, credentialed HttpOnly refresh-cookie requests, and single-flight 401 recovery
- ADMIN-only restaurant table CRUD, filtering, sorting, soft activation, optimistic locking, and audit events
- Responsive table-management workspace with validated create/edit forms and safe conflict handling
- ADMIN-only reservation creation, filtering, assignment, status workflow, optimistic locking, and audit events
- MySQL-serialized table availability checks with UTC storage and browser-local time display
- Responsive reservation agenda with live suitable-table loading and safe conflict recovery
- ADMIN-only menu category, item, modifier-group, modifier-option, and ordered assignment management
- Decimal-string EUR pricing, effective item availability, optimistic locking, and safe menu audit events
- Responsive protected menu workspace with categories, menu items, and modifiers sections
- ADMIN-only order creation, filtering, item capture, lifecycle transitions, and immutable status history
- Server-authoritative `BigDecimal` pricing snapshots and transactionally recalculated order totals
- Responsive order list and capture workspace with menu browsing, modifier selection, and conflict handling
- Transactional kitchen-ticket creation, item preparation, derived ticket status, cancellation, and READY-gated order completion
- ADMIN-authenticated STOMP notifications with after-commit publication and REST-authoritative recovery
- Responsive kitchen display with queue filters, snapshot instructions, conflict recovery, and order-detail status
- ADMIN-only inventory items, immutable stock ledger, low-stock alerts, recipes, modifier ingredients, suppliers, and purchase orders
- Atomic recipe consumption when kitchen preparation begins, with database-backed exactly-once usage and negative-stock visibility
- Exact supplier pricing snapshots, partial receiving, concurrency-safe final receipts, and a responsive protected inventory workspace
- ADMIN-only employee records, scheduling-domain operational roles, date-specific availability, and terminal shift lifecycle
- MySQL-serialized availability and shift overlap protection with a responsive protected weekly staff workspace

## Technology stack

Frontend: React 19, TypeScript, Vite, React Router, TanStack Query, Axios, React Hook Form, Zod, ESLint, Prettier, Vitest, React Testing Library, and jsdom.

Backend: Java 21, Spring Boot 4.1, Maven Wrapper, Spring MVC, Spring Data JPA, Spring Security, Bean Validation, MySQL Connector/J, Flyway, Spring WebSocket, Actuator, Springdoc OpenAPI, JUnit 5, Mockito, Spring Boot Test, and Testcontainers MySQL.

Infrastructure: Docker Compose and MySQL 8.4 using InnoDB, `utf8mb4`, UTC, a persistent named volume, and a health check.

## Project structure

```text
.
├── backend/                 Spring Boot API and migrations
├── frontend/                React/Vite client
├── docs/                    Product and technical plans
├── .editorconfig
├── .env.example
├── .gitignore
├── docker-compose.yml
├── LICENSE
└── README.md
```

## Requirements

- Java Development Kit 21
- Docker Desktop or a compatible Docker Engine with Compose
- Node.js 22.12 or newer (the current Vite template requirement)
- npm 10 or newer

A global Maven installation is not required; use the included wrapper.

## Environment setup

1. Copy `.env.example` to `.env` and replace both example database passwords for local use.
2. Copy `frontend/.env.example` to `frontend/.env.local` if the API does not use the default `http://localhost:8080/api/v1` URL. Set `VITE_API_BASE_URL` to the API's versioned base URL; do not place secrets in any `VITE_` variable because Vite exposes them to browser code.
3. Set backend variables if their defaults do not match your local environment:
   - `MYSQL_HOST_PORT` (defaults to `3307` for the host; MySQL remains on `3306` inside the container)
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `SERVER_PORT`
   - `FRONTEND_ORIGIN`
   - `JWT_SECRET` (required; at least 32 UTF-8 bytes)
   - `JWT_ACCESS_TOKEN_TTL` (default `15m`)
   - `JWT_REFRESH_TOKEN_TTL` (default `7d`)
   - `AUTH_COOKIE_SECURE` and `AUTH_COOKIE_SAME_SITE`
   - optional dev-only `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, and `BOOTSTRAP_ADMIN_DISPLAY_NAME`

Do not commit real environment files.

The development JDBC URL for the Compose database is:

```text
jdbc:mysql://localhost:3307/restaurant_operations?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
```

## MySQL with Docker

```powershell
docker compose up -d mysql
docker compose ps
```

Stop the local container without deleting its named data volume:

```powershell
docker compose down
```

## Backend commands

From `backend/` on Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
$env:SPRING_PROFILES_ACTIVE='dev'
.\mvnw.cmd spring-boot:run
```

Run the Docker-backed Flyway integration smoke test separately:

```powershell
.\mvnw.cmd -Pintegration-test verify
```

The backend has development-only fallbacks, but explicit environment variables are preferred. When using a copied root `.env`, export the `DB_*` values into the backend process and keep `DB_PASSWORD` equal to `MYSQL_PASSWORD`.

## Frontend commands

From `frontend/`:

```powershell
npm install
npm run format:check
npm run lint
npm test
npm run build
npm run dev
```

The frontend starts at `http://localhost:5173`. Vite uses `VITE_API_BASE_URL` when present and otherwise calls `http://localhost:8080/api/v1`.

## Frontend authentication flow

On startup, the client shows a session-initialization screen while it calls `/auth/refresh` with credentials and `X-CSRF-Protection: 1`. If the backend refresh cookie is valid, the rotated response supplies a short-lived access token, `/auth/me` validates the current user, and the protected dashboard renders. If recovery fails, the in-memory session is cleared and protected routes redirect to `/login` while preserving the requested internal destination.

Login uses React Hook Form and Zod, sends credentials only to `/auth/login`, and shows the same generic error for invalid credentials, disabled users, malformed responses, unavailable services, and unexpected failures. The returned access token exists only in JavaScript memory. Axios attaches it as a Bearer credential to API requests; one shared refresh operation may retry simultaneous 401 responses once. A failed refresh clears authentication and cannot loop indefinitely.

The browser never writes access or refresh tokens to localStorage, sessionStorage, IndexedDB, or script-readable cookies. The backend alone creates, rotates, and clears the HttpOnly refresh cookie. Logout calls `/auth/logout` with the CSRF header and always clears local memory, even if the network request fails.

## Health and API documentation

- Application health: `GET http://localhost:8080/api/v1/health`
- Actuator health: `GET http://localhost:8080/actuator/health`
- Development Swagger UI: `http://localhost:8080/swagger-ui.html` when the `dev` profile is active
- Swagger Authorize uses the Bearer JWT returned by login; current-user and restaurant-management operations are marked as Bearer-protected
- Authentication: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, and `GET /api/v1/auth/me`
- Table management: `GET/POST /api/v1/tables`, `GET/PUT /api/v1/tables/{id}`, and `PATCH /api/v1/tables/{id}/activation`
- Reservation management: `GET/POST /api/v1/reservations`, `GET/PUT /api/v1/reservations/{id}`, `PATCH /api/v1/reservations/{id}/status`, and `GET /api/v1/reservations/availability`
- Menu management: category, item, modifier-group, modifier-option, activation, availability, and ordered-assignment operations below `/api/v1/menu`
- Order management: `GET/POST /api/v1/orders`, `GET/PUT /api/v1/orders/{id}`, item operations below `/api/v1/orders/{id}/items`, `PATCH /api/v1/orders/{id}/status`, and `GET /api/v1/orders/{id}/history`
- Kitchen management: `GET /api/v1/kitchen/tickets`, `GET /api/v1/kitchen/tickets/{id}`, `GET /api/v1/kitchen/orders/{orderId}`, and `PATCH /api/v1/kitchen/tickets/{ticketId}/items/{itemId}/status`
- Kitchen real-time endpoint: native STOMP over `/ws`, with ADMIN Bearer authentication in `CONNECT` and server notifications on `/topic/kitchen`
- Inventory: item and movement operations below `/api/v1/inventory`, recipe operations below `/api/v1/recipes`, supplier operations below `/api/v1/suppliers`, and purchasing below `/api/v1/purchase-orders`
- Staff scheduling: employee, availability, shift, activation, and lifecycle operations below `/api/v1/staff`

## Restaurant table management

Authenticated administrators can open `/tables` to search by table number, filter by active state, operational status, and section, and create or edit table records. Deactivation is a soft state change: records and their audit history remain available and can be reactivated.

Table numbers are unique and normalized to uppercase. Every update and activation request includes the version returned by the latest read. A stale version returns HTTP 409 so a concurrent change is never silently overwritten. Write operations record `TABLE_CREATED`, `TABLE_UPDATED`, `TABLE_DEACTIVATED`, or `TABLE_REACTIVATED` without storing request bodies or sensitive session data.

## Reservation management

Authenticated administrators can open `/reservations` to create unassigned or table-assigned reservations, search and filter an agenda, edit guest and timing details, reassign tables, and apply the controlled pending, confirmed, seated, completed, cancelled, and no-show workflow.

Reservation instants are stored in UTC and converted at the browser boundary for local input and display. A table is suitable only when it is active, operationally available, large enough, and free of overlapping `CONFIRMED` or `SEATED` reservations. The write transaction locks the target table row before its final overlap check, so simultaneous assignments are serialized by MySQL rather than trusted to frontend state. `PENDING`, `COMPLETED`, `CANCELLED`, and `NO_SHOW` reservations do not block availability. Every edit and status request carries the latest optimistic-lock version.

## Menu management

Authenticated administrators can open `/menu` to manage normalized categories, uniquely coded menu items, reusable modifier groups, and their options. Records use soft activation and versioned updates. Item activation and `availableForSale` are independent; effective availability also requires an active category. Category deactivation therefore hides its items from effective sale without rewriting them.

Prices and adjustments travel through the API as decimal strings and are stored as `DECIMAL(12,2)`. The frontend formats them centrally in EUR without floating-point calculations. Modifier groups support `SINGLE` and `MULTIPLE` selection rules, ordered reusable assignments, and active-option validation. An unsafe assigned configuration returns HTTP 409 instead of becoming unusable.

## Order management

Authenticated administrators can open `/orders` to search, filter, sort, and create table-service orders, then use `/orders/{id}` for responsive order capture. Every order requires an active, operationally `AVAILABLE` table and may optionally reference a `SEATED` reservation assigned to that same table. Order workflows never mutate table or reservation status automatically.

The backend generates each unique order number and owns every monetary calculation. Adding an item snapshots its code, name, base price, selected modifier labels, and modifier adjustments into `DECIMAL(12,2)` fields. Later menu changes do not rewrite existing lines. Quantity-only and notes-only edits retain the stored snapshot; changing modifiers revalidates the current menu and refreshes the complete line snapshot. `subtotal` is the sum of line totals and Phase 4B defines `total = subtotal` because taxes, discounts, tips, service charges, and payments are not implemented.

New orders begin `OPEN`. Open orders can change metadata and items, and may transition to `SUBMITTED` or `CANCELLED`. Submitted orders are commercially immutable and may transition only to `COMPLETED` or `CANCELLED`; terminal orders cannot reopen. Phase 5 creates the kitchen aggregate in the submission transaction and requires its ticket to be `READY` before explicit completion. MySQL order-row locks serialize item and status mutations, while optimistic versions reject stale clients. Separate chronological status history and safe audit events record successful changes.

## Kitchen and real-time updates

Authenticated administrators can open `/kitchen` to view active tickets grouped as `QUEUED`, `PREPARING`, and `READY`, inspect immutable submitted item/modifier snapshots, and progress each item through `QUEUED → PREPARING → READY`. Ticket state is derived transactionally from its items; submitted-order cancellation marks the ticket `CANCELLED` without erasing preparation history. Cancelled tickets leave the active queue and reject further preparation.

All commands and reads use the REST API and MySQL remains authoritative. A single native STOMP client connects with the current memory-only Bearer access token and subscribes to `/topic/kitchen`. Small, safe notifications trigger React Query invalidation; initial load, every reconnect, and every event refetch REST state, so temporary delivery loss cannot permanently desynchronize the UI. Notifications publish only after a successful database commit, and delivery failure cannot roll back committed work. The client reconnects with a current token through the existing single-flight session recovery, stores no token, and disconnects on logout.

Kitchen item mutations follow the database lock order `orders → kitchen_tickets → kitchen_ticket_items`. The request ticket version is checked while those locks are held; invalid transitions, stale state, lock contention, cancellation races, and premature completion return safe HTTP 409 responses.

## Inventory, recipes, suppliers, and purchasing

Authenticated administrators can open `/inventory` for four integrated workspaces: Stock, Recipes, Suppliers, and Purchasing. Inventory items use one canonical unit (`GRAM`, `MILLILITER`, or `UNIT`), a reorder threshold, soft activation, and optimistic versions. Current on-hand is derived only from immutable positive-magnitude ledger entries; movement type determines the signed effect. Manual adjustments and waste append history rather than rewriting it, and negative stock remains visible and counts as low stock.

An active menu-item recipe and selected modifier-option ingredient mappings are read when a kitchen item first moves from `QUEUED` to `PREPARING`. Quantities are multiplied by the order-line quantity and aggregated into one `USAGE` movement per kitchen item and inventory item. The kitchen transition and usage entries commit atomically, while a database-unique source key prevents duplicate consumption. Missing recipes do not block preparation. Later recipe changes, cancellation, or reconnects do not alter or reverse historical usage.

Suppliers have soft activation and versioned item-price relationships. Draft purchase orders snapshot the current inventory code, name, canonical unit, and exact `DECIMAL(12,4)` supplier cost. Commercial data freezes after ordering. Receipts may be partial, append immutable `RECEIPT` movements, and advance the purchase order through `ORDERED`, `PARTIALLY_RECEIVED`, and `RECEIVED`. Purchase-order and line locks plus request versions ensure simultaneous final receipts produce one successful ledger change and one safe conflict.

## Employees and staff scheduling

Authenticated administrators can open `/staff` for employee records, exact availability windows, and a browser-local weekly schedule. Employee codes are normalized and unique, employee records use soft activation and optimistic versions, and operational roles such as `WAITER`, `KITCHEN`, or `MANAGER` describe scheduled work only. They never create Spring Security authorities or application accounts; every Phase 7 operation remains ADMIN-only.

Availability and shift timestamps are stored as UTC instants and converted at the browser boundary. New scheduled shifts must fit completely inside one date-specific availability window and cannot overlap another non-cancelled shift under half-open `[start, end)` rules. MySQL write-locks the employee before availability and shift checks, so concurrent contenders serialize as one success and one safe conflict. Availability changes preserve existing shifts. Employees with future scheduled shifts cannot be deactivated until those shifts are cancelled or completed. Shifts progress only from `SCHEDULED` to `COMPLETED` or `CANCELLED`; terminal shifts remain read-only.

## Current limitations

There is no public registration, account recovery, user administration, customer CRM, multi-restaurant tenancy, live occupancy automation, unit conversion, stock transfers, payroll, salary management, time clock, attendance, timesheets, recurring availability, shift swaps, employee self-service, account provisioning, labor-law engine, automatic scheduling, staffing notifications, payment, taxation, discounting, tipping, invoicing, durable external messaging, customer ordering, or reporting feature. The system assumes one logical restaurant and does not hard-delete operational history. Inventory consumption intentionally permits negative balances, and cancellation after preparation does not automatically restore stock. EUR is the single display currency until restaurant configuration and payment phases define currency ownership. ADMIN remains the only application authority; Phase 7 operational roles are scheduling data only. Authentication rate limiting, MFA, and production key management/rotation remain deferred hardening work.

## Documentation

- [Product scope](docs/product-scope.md)
- [Architecture](docs/architecture.md)
- [Database plan](docs/database-plan.md)
- [Security plan](docs/security-plan.md)
- [Roadmap](docs/roadmap.md)
- [Phase 2A authentication testing](docs/testing/phase-2a-authentication.md)
- [Phase 2B frontend authentication testing](docs/testing/phase-2b-frontend-authentication.md)
- [Phase 3A table-management testing](docs/testing/phase-3a-table-management.md)
- [Phase 3B reservation-management testing](docs/testing/phase-3b-reservation-management.md)
- [Phase 4A menu-management testing](docs/testing/phase-4a-menu-management.md)
- [Phase 4B order-management testing](docs/testing/phase-4b-order-management.md)
- [Phase 5 kitchen and real-time testing](docs/testing/phase-5-kitchen-realtime.md)
- [Phase 6 inventory, recipes, suppliers, and purchasing testing](docs/testing/phase-6-inventory-suppliers.md)
- [Phase 7 employees and staff scheduling testing](docs/testing/phase-7-staff-scheduling.md)
- [Original project context](docs/original-project-context.md)

## Independent redesign

The idea has historical roots in a three-person second-year university team project. This repository is an independent redesign: no old source code is copied, and the architecture, schema, interface, security, and advanced workflows are being designed and implemented anew.
