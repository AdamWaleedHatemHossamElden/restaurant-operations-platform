# Phase 11A production-readiness verification

## Implemented foundation

Phase 11A adds one GitHub Actions workflow with parallel frontend, backend, and MySQL/Testcontainers jobs; weekly Dependabot proposals for npm, Maven, and Actions; an explicit `prod` Spring profile; a multi-stage non-root backend image; production-safe frontend environment validation; a top-level render error boundary; route-level lazy loading; and provider-neutral operational documentation.

The production profile requires injected database URL, username, password, JWT secret, and exact frontend origin. It forces secure refresh cookies, restricts Flyway to authoritative migrations, disables Swagger, exposes only health with liveness/readiness probes, honors trusted forwarded headers, hides framework error detail, and avoids DEBUG security logging. The dev-only bootstrap component remains absent in production.

## Security and dependency decisions

CI uses official supported Actions, `npm ci`, Java 21, the Maven wrapper, dependency caches, read-only repository permissions, cancellation of superseded runs, and job timeouts. Tests use only committed test-specific placeholders and Testcontainers; CI requires no repository secrets. Dependabot does not auto-merge. An NVD-dependent Maven scanner is not added because an unauthenticated feed makes default public CI unreliable; Phase 11B should use the selected platform/GitHub security tooling.

Rate limiting is deferred to the trusted Phase 11B edge so forwarded-client identity can be configured correctly. Frontend host headers and a tested CSP likewise belong to the selected static host. Existing React Testing Library coverage includes keyboard/focus semantics; a blanket accessibility assertion dependency was not added because Phase 10's manual audit and targeted component tests are stronger than a false-positive-heavy global scan. A final automated/live accessibility audit remains in 11C.

## Verification record

- Backend: 80 unit/security tests passed under both `test` and `verify`; Checkstyle reported zero violations and packaging succeeded.
- MySQL: 40 Testcontainers integration tests passed against MySQL 8.4. A fresh production-profile database applied exactly V1–V11, did not apply V1000, and contained no development administrator.
- Frontend: a clean `npm ci` completed with zero reported vulnerabilities; 127 tests passed; ESLint, Prettier, TypeScript, and the production build passed. Focused URL tests prove that versioned HTTPS and localhost API URLs derive the correct `/ws` endpoint. Production builds reject a missing API URL and non-HTTP(S) protocols before bundling.
- Bundle: route-level lazy loading reduced the main JavaScript chunk from 699,361 bytes to approximately 532,010 bytes (about 24%). The remaining Vite 500 kB advisory is non-blocking and should be reconsidered only with measured user impact.
- Container: the backend image built successfully, ran as the non-root `application` user with injected production-profile configuration, became healthy, returned minimal `UP` liveness/readiness responses, retained Spring security headers, and did not expose Swagger or OpenAPI. The smoke test used an isolated disposable MySQL 8.4 container with no persistent volume.
- Repository: workflow/configuration YAML, dependency lock state, secret/artifact hygiene, and `git diff --check` were validated after implementation.

## Remaining work

Phase 11B chooses hosting, database, secret-management, TLS, DNS, monitoring, backup, image-scanning, and edge rate-limit services; provisions the initial administrator; configures exact CORS/SameSite and WebSocket proxying; deploys immutable artifacts; and validates the live system.

Phase 11C prepares realistic demo data without rewriting migrations, captures final responsive screenshots, performs final browser/accessibility regression, finishes the portfolio README/GitHub presentation and engineering narrative, and completes project sign-off.
