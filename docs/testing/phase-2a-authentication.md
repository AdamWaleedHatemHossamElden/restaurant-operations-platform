# Phase 2A backend authentication

## Architecture and security decisions

`AuthenticationController` exposes only `/api/v1/auth/login`, `/refresh`, `/logout`, and `/me`. `AuthenticationService` owns login, rotation, revocation, and current-user rules. Spring Security's resource-server support validates the HS256 signature, timestamps, `token_type=access`, and the required `sub`, `jti`, `token_type`, `roles`, `iat`, and `exp` claims. Access tokens contain only those authentication claims.

Login always performs one BCrypt comparison. Existing users are checked against their stored strength-12 hash; unknown users are checked against a fixed valid strength-12 dummy hash that is never persisted. Unknown email and wrong password therefore retain the same generic response, audit behavior, and password-verification work without relying on timing sleeps.

Access tokens default to 15 minutes to limit exposure and are not stored. Refresh tokens default to seven days, are generated from 32 secure random bytes, and only their SHA-256 hashes are stored. Every refresh revokes the old row and links a replacement in the same family. Presenting a revoked token records reuse and revokes the family's remaining active tokens. Logout is idempotent and revokes the presented token when found.

Refresh rotation uses a pessimistic MySQL lock on the owning user followed by a lock on the token row. Simultaneous requests with the same token are serialized: one may rotate successfully, while the loser observes reuse, is rejected, and revokes the replacement family. Clients must treat a rejected simultaneous refresh as a signed-out session and authenticate again.

The refresh cookie is HttpOnly and scoped to `/api/v1/auth`. Cookie-authenticated refresh/logout calls require `X-CSRF-Protection: 1`; this includes MVC-equivalent trailing-slash, matrix-parameter, and application-context-path forms. The firewall's semicolon exception is limited to POST requests that normalize to refresh or logout. Unrelated routes retain the default strict firewall and deny-by-default authorization; depending on servlet normalization, a rejected unrelated matrix path can surface as either an early 400 or the normal JSON 401 denial. Explicit credentialed CORS restricts browser calls and custom-header preflights to `FRONTEND_ORIGIN`. Use HTTPS with `AUTH_COOKIE_SECURE=true` in production. Local HTTP commonly uses `false` and `SameSite=Lax`.

If a user is disabled after login, refresh returns 403, creates no replacement, and revokes the active refresh-token family. The earlier successful login remains audited; the rejected request does not record `TOKEN_REFRESH` or reuse because neither event occurred.

The `dev` profile can create one initial administrator when `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are present. The password is BCrypt-hashed, the ADMIN role is assigned, an existing account is never overwritten, and omission disables bootstrap. Tests seed independent users.

## Configuration

- `JWT_SECRET`: required, secret, at least 32 UTF-8 bytes
- `JWT_ACCESS_TOKEN_TTL`: default `15m`
- `JWT_REFRESH_TOKEN_TTL`: default `7d`
- `AUTH_COOKIE_SECURE`: default `false`; must be `true` over production HTTPS
- `AUTH_COOKIE_SAME_SITE`: default `Lax`
- `FRONTEND_ORIGIN`: the one credentialed browser origin
- `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_DISPLAY_NAME`: optional, dev-only bootstrap

No real value belongs in tracked files.

## Automated coverage

Run from `backend`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

Unit/MVC coverage includes BCrypt strength and login timing-work equivalence, SHA-256 refresh hashing, normalization, expiry/rotation state, required JWT claims, 900-second expiry, expired/incorrectly signed/incorrect-type tokens, opaque refresh-token rejection as Bearer authentication, generic credential errors, malformed JSON, JSON 401/403 responses, and exact/trailing/matrix/context-path CSRF variants with missing, incorrect, and valid headers. CORS tests cover allowed and disallowed origins plus the custom-header preflight. Bootstrap tests cover dev-only profile selection, creation, duplicate prevention, non-overwrite behavior, missing configuration, and a missing enabled ADMIN role. Generated OpenAPI coverage verifies the Bearer scheme and that only `/me` requires it.

MySQL 8.4 Testcontainers coverage applies V1 and V2 from an empty schema and verifies persistence, login, last-login update, hashed credentials/tokens, rotation, reuse family revocation, simultaneous refresh serialization, disabled-user refresh revocation, logout, expiry, uniqueness, and safe persisted LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_REFRESH, REFRESH_TOKEN_REUSE_DETECTED, and LOGOUT audit records.

## Manual API checklist

1. Set safe local DB values plus `JWT_SECRET`, bootstrap email/password/display name, and `SPRING_PROFILES_ACTIVE=dev`; never paste them into source or shared shell history.
2. Run `docker compose up -d mysql`, then from `backend` run `.\mvnw.cmd spring-boot:run`.
3. Open `http://localhost:8080/swagger-ui.html`. Confirm the Authorize button is present and `GET /api/v1/health` returns 200.
4. POST `/api/v1/auth/login` with the bootstrap email/password. Expect 200, a short-lived `accessToken`, safe user fields, and an HttpOnly cookie; no password or refresh token appears in JSON.
5. Copy only the JSON access token into Swagger's Bearer authorization. GET `/api/v1/auth/me`; expect 200 and user/roles. Without it, expect JSON 401.
6. Repeat login with a wrong password; expect generic JSON 401.
7. POST `/api/v1/auth/refresh` with header `X-CSRF-Protection: 1` in a cookie-preserving client; expect 200 and a rotated cookie. POST `/logout` with the same header; expect 200 and cookie removal. A later refresh expects 401.
8. Inspect MySQL without selecting secret-bearing values: `SELECT installed_rank, version, description, success FROM flyway_schema_history;`, `SELECT id,email,enabled,last_login_at FROM users;`, `SELECT u.email,r.name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id;`, `SELECT id,user_id,family_id,created_at,expires_at,revoked_at FROM refresh_tokens;`, and `SELECT action,occurred_at FROM audit_logs ORDER BY id;`.
9. Stop the backend with Ctrl+C and run `docker compose down` from the project root. Do not use `down -v`.

When the persistent development database already contains the dev-only V1000 role seed and V2 was added later, the `dev` profile can report an out-of-order Flyway warning while validating that both are applied. V1000 lives only in `db/dev`; a production/default migration run uses only `db/migration` and applies V1 then V2 normally. This warning describes local development history, not the production migration design.

## Remaining limitations

This phase has no frontend login, registration, account recovery, MFA, rate limiting, user administration, restaurant scoping, or business modules. Production deployment still requires managed secrets, HTTPS, secure cookies, monitoring, and operational key rotation.
