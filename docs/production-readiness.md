# Production-readiness runbook

## Scope and status

Phase 11A prepares a provider-neutral deployment foundation. It does not create infrastructure, DNS, TLS certificates, production data, or GitHub deployment secrets. Phase 11B must choose a host and execute every validation below before the application is described as deployed.

## Required runtime configuration

Start the backend with `SPRING_PROFILES_ACTIVE=prod`. Inject `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `FRONTEND_ORIGIN`; none has a production fallback. `JWT_SECRET` must be independently generated, at least 32 random bytes, stored in the provider's secret manager, and rotatable through a controlled session-invalidating deployment. `SERVER_PORT`, token TTLs, and `AUTH_COOKIE_SAME_SITE` are optional deployment settings. The prod profile always sets the refresh cookie `Secure=true`.

Build the frontend with the public `VITE_API_BASE_URL`, including `/api/v1`. `VITE_` values are browser-visible configuration and must never contain passwords, signing keys, provider tokens, or database credentials. HTTPS API origins automatically produce WSS kitchen connections.

## Database and migrations

Use a deployment-provided MySQL 8.4-compatible account with only the schema/data privileges needed by the application and Flyway. Do not reuse Compose credentials. Production Flyway loads only `classpath:db/migration`, so V1–V11 and future authoritative migrations run while `db/dev/V1000` cannot. Never edit an applied migration. Back up the database before migration-bearing releases and verify restore procedures periodically.

## Administrator provisioning

`DevelopmentAdminBootstrap` is active only in the `dev` profile; production ignores bootstrap variables and never creates or overwrites an administrator. The authoritative migrations intentionally create empty `roles` and `users` tables, so a fresh production database needs this controlled one-time procedure:

1. Generate a strong random password in a password manager. On a trusted operator workstation, generate its BCrypt cost-12 hash with an interactive tool that does not accept the plaintext on the command line. For example, `htpasswd -nBC 12 admin` prompts without echo; retain only the hash after the first colon.
2. Open a private, interactive MySQL session through the provider's controlled administrative connection. Do not use a command-line `--execute` argument, shell history, a migration, or a committed script for the email, display name, or hash.
3. Run the transaction below after replacing the three `replace_...` values inside that private session. The role insert is required because the development-only V1000 role seed never runs in production.

```sql
START TRANSACTION;

INSERT INTO roles (name, description, enabled)
VALUES ('ADMIN', 'Platform administration', TRUE)
ON DUPLICATE KEY UPDATE description = 'Platform administration', enabled = TRUE;

SET @admin_email = LOWER(TRIM('replace_with_admin_email'));
SET @admin_display_name = TRIM('replace_with_display_name');
SET @admin_password_hash = 'replace_with_cost_12_bcrypt_hash';

INSERT INTO users (email, password_hash, display_name, enabled)
VALUES (@admin_email, @admin_password_hash, @admin_display_name, TRUE);

SET @admin_user_id = LAST_INSERT_ID();

INSERT INTO user_roles (user_id, role_id)
SELECT @admin_user_id, id
FROM roles
WHERE name = 'ADMIN' AND enabled = TRUE;

SELECT COUNT(*) AS valid_admin_assignment
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
WHERE u.id = @admin_user_id
  AND u.enabled = TRUE
  AND r.name = 'ADMIN'
  AND r.enabled = TRUE;
```

The final query must return exactly `1`. Run `COMMIT;` only then; otherwise run `ROLLBACK;` and investigate. Clear the session variables and close the elevated database session immediately afterward. Validate login through the normal HTTPS endpoint, store the plaintext only in the intended password manager, and record the provisioning action in the operator audit log without including the email, password, or hash. This procedure never enables the dev profile, creates a public registration path, or places credentials in source.

## TLS, cookies, CORS, and proxying

Terminate TLS at the selected trusted proxy/load balancer and redirect HTTP to HTTPS. Forward standard headers only from that trusted hop; Spring's framework forwarded-header strategy then reconstructs the original scheme and host. Configure `FRONTEND_ORIGIN` as one exact origin—never `*` with credentials. Keep `SameSite=Lax` for a same-site topology. A genuinely cross-site deployment requires `SameSite=None`, `Secure=true`, credentialed CORS, and end-to-end refresh/logout testing.

Proxy native WebSocket upgrades on `/ws` without stripping STOMP `CONNECT` authorization. Restrict the handshake to `FRONTEND_ORIGIN`; do not put access tokens in URLs. Route `/api`, `/actuator/health`, and `/ws` to the backend, and configure the static frontend host to fall back unknown application routes to `index.html`.

## Health, observability, and security controls

Use `/actuator/health/liveness` for process restart decisions and `/actuator/health/readiness` for traffic admission. Details remain hidden. Do not expose `env`, `beans`, `configprops`, `heapdump`, or thread dumps publicly. Swagger is disabled in production. Keep INFO application logging and WARN Spring Security logging; centralize logs with secret redaction and suitable retention.

Spring Security retains its default frame, MIME/content-type, cache, and HTTPS-aware HSTS protections for backend responses. Configure the frontend host's HSTS, frame, content-type, referrer, and carefully tested CSP headers in Phase 11B. Apply generic authentication abuse limits at the trusted edge, not from untrusted forwarded headers. Run dependency updates through reviewed pull requests; do not auto-merge them.

## Build, backup, rollback, and validation

CI must pass frontend `npm ci`, formatting, lint, tests, and build; backend Maven verification; and the MySQL/Testcontainers profile. Build the backend image from `backend/`, identify it by immutable commit SHA, scan it with the chosen provider's tooling, and inject secrets only at runtime. Build frontend assets with the target public API URL and publish immutable assets through the chosen static host/CDN.

Before release, record a restorable database backup and previous artifact identifiers. Application rollback redeploys those artifacts; schema rollback restores the tested backup rather than editing Flyway history. Prefer forward-compatible migrations.

- Confirm TLS, trusted forwarded headers, exact CORS origin, secure cookies, and SameSite behavior.
- Confirm login, refresh rotation, logout, protected REST, and WSS reconnect.
- Confirm liveness/readiness and that sensitive Actuator/Swagger endpoints are unavailable.
- Confirm V1–V11 schema migrations and absence of development fixture V1000.
- Confirm no secrets exist in images, frontend bundles, logs, or repository files.
- Confirm backup ownership, restoration, monitoring, rate limits, and least-privilege DB access.
- Run the complete E2E workflow before directing portfolio traffic to the deployment.
