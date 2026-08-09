# Restaurant Operations Platform

A modern full-stack restaurant operations platform. Phases 1 through 4A are merged into `main`; the current `phase-4b-order-management` feature branch adds order capture, immutable pricing snapshots, exact totals, and order-status history.

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

New orders begin `OPEN`. Open orders can change metadata and items, and may transition to `SUBMITTED` or `CANCELLED`. Submitted orders are commercially immutable and may transition only to `COMPLETED` or `CANCELLED`; terminal orders cannot reopen. MySQL order-row locks serialize item and status mutations, while optimistic versions reject stale clients. Separate chronological status history and safe audit events record successful changes.

## Current limitations

There is no public registration, account recovery, user administration, customer CRM, multi-restaurant tenancy, automated notification, live occupancy workflow, kitchen workflow, inventory, staffing, payment, taxation, discounting, tipping, or reporting feature. The system assumes one logical restaurant and does not hard-delete tables, reservations, menu records, or orders. EUR is the single display currency until restaurant configuration and payment phases define currency ownership. Host, waiter, cashier, and manager roles remain pending; ADMIN is the only current application role. Authentication rate limiting, MFA, and production key management/rotation remain deferred hardening work.

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
- [Original project context](docs/original-project-context.md)

## Independent redesign

The idea has historical roots in a three-person second-year university team project. This repository is an independent redesign: no old source code is copied, and the architecture, schema, interface, security, and advanced workflows are being designed and implemented anew.
