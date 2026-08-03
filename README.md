# Restaurant Operations Platform

A new independent portfolio project establishing the technical base for a modern restaurant operations system. The current repository contains **Phase 1: Foundation** only; it does not claim to implement authentication or restaurant business workflows.

## Phase 1 status

- React + TypeScript frontend with routing, API client, server-state management, responsive styling, and health states
- Java 21 + Spring Boot modular-monolith backend foundation
- Versioned `/api/v1/health` endpoint and Actuator health endpoint
- Deny-by-default, stateless Spring Security configuration
- MySQL 8.4 local service and Flyway schema migrations
- Unit, MVC, frontend, and opt-in Testcontainers integration-test foundations
- Architecture, database, security, scope, and roadmap documentation

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
2. Copy `frontend/.env.example` to `frontend/.env.local` if the API does not use the default URL.
3. Set backend variables if their defaults do not match your local environment:
   - `MYSQL_HOST_PORT` (defaults to `3307` for the host; MySQL remains on `3306` inside the container)
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `SERVER_PORT`
   - `FRONTEND_ORIGIN`

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

## Health and API documentation

- Application health: `GET http://localhost:8080/api/v1/health`
- Actuator health: `GET http://localhost:8080/actuator/health`
- Development Swagger UI: `http://localhost:8080/swagger-ui.html` when the `dev` profile is active

## Current limitations

There are no login, user-management, restaurant-management, reservation, menu, ordering, kitchen, inventory, staffing, payment, notification, or reporting features. Spring WebSocket and form libraries are dependencies for later controlled phases, not evidence of implemented features. Production deployment and Playwright end-to-end tests are also deferred.

## Documentation

- [Product scope](docs/product-scope.md)
- [Architecture](docs/architecture.md)
- [Database plan](docs/database-plan.md)
- [Security plan](docs/security-plan.md)
- [Roadmap](docs/roadmap.md)
- [Original project context](docs/original-project-context.md)

## Independent redesign

The idea has historical roots in a three-person second-year university team project. This repository is an independent redesign: no old source code is copied, and the architecture, schema, interface, security, and advanced workflows are being designed and implemented anew.
