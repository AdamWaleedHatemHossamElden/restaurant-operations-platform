# Phase 2B frontend authentication

## Architecture and security design

`AuthProvider` owns the browser authentication lifecycle and exposes the current user, authenticated and initializing states, login, logout, and explicit recovery behavior. The access token remains internal: provider operations send it to the API-client module, but context consumers cannot read it. No frontend authentication code writes tokens to localStorage, sessionStorage, IndexedDB, or `document.cookie`.

Two credentialed Axios instances separate lifecycle calls from ordinary API requests. `authenticationClient` calls login, refresh, logout, and current-user endpoints without passing through the 401 recovery interceptor. `apiClient` attaches the in-memory Bearer token and may retry an authenticated 401 once. Simultaneous 401 responses share a single recovery promise, so they cannot independently rotate the same refresh cookie. A retry marker prevents loops. If another request has already installed a newer access token, a stale 401 retries with that token without rotating again.

Refresh and logout include `X-CSRF-Protection: 1` and allow the backend-managed HttpOnly cookie through `withCredentials`. Authentication responses and current-user data are validated with Zod before entering application state. Malformed payloads, network failures, disabled users, rejected credentials, and unexpected server errors all produce a cleared session or the same generic login message; raw backend details are not rendered.

The route tree waits for startup recovery before deciding between protected content and login. A valid refresh cookie is rotated, then `/auth/me` verifies the identity. Failed recovery redirects protected requests to `/login` and preserves a safe internal path, query, and fragment. An authenticated visit to `/login` redirects to the dashboard. Every asynchronous authentication operation captures the current session generation before making a request and checks it before applying a result. Logout advances that generation, unregisters 401 recovery, and clears memory before calling the backend, so delayed refresh, `/auth/me`, login, or 401 results cannot restore authentication. Remote logout failure therefore still leaves the application signed out.

## Local configuration and startup

The frontend has one public setting:

- `VITE_API_BASE_URL`: versioned API base URL; defaults to `http://localhost:8080/api/v1`

Copy `frontend/.env.example` to an ignored `frontend/.env.local` only when overriding the fallback. Never put passwords, signing keys, tokens, or other secrets in a `VITE_` variable.

Start the backend with its `dev` profile and configured local environment, then run from `frontend`:

```powershell
npm install
npm run dev
```

Open `http://localhost:5173/login`. The backend CORS `FRONTEND_ORIGIN` must match this browser origin.

## Automated verification

Run from `frontend`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Coverage includes form validation, duplicate-submit prevention, successful and rejected login, safe error rendering, protected-route redirects, authenticated rendering, startup recovery and failure, destination preservation, redirecting authenticated users away from login, successful and failed-network logout, logout during refresh and `/auth/me`, delayed 401 rejection after logout, stale-result suppression, credentialed CSRF calls, malformed response rejection, Bearer attachment, one shared refresh for simultaneous 401 responses, one-retry enforcement, failed-refresh clearing, and absence of browser-storage persistence.

## Manual verification

1. Confirm MySQL is healthy, the backend is running on `http://localhost:8080` with the `dev` profile, and the frontend origin is allowed.
2. Start the frontend and open a protected path such as `http://localhost:5173/dashboard?view=manual`. With no session, confirm the initialization state appears before redirecting to login.
3. Submit empty and invalid fields; confirm accessible validation appears and no request is sent.
4. Sign in using local bootstrap credentials without copying them into source, logs, screenshots, or documentation. Confirm the dashboard shows display name, email, roles, and backend health.
5. In browser developer tools, confirm login returns 200, the access token is used only as an Authorization header, and the refresh cookie is HttpOnly. Do not copy or display either value.
6. Reload the page. Confirm refresh uses credentials plus `X-CSRF-Protection: 1`, `/auth/me` succeeds, and the dashboard appears without a login-page flash.
7. Confirm localStorage, sessionStorage, IndexedDB, and script-readable cookies contain no access or refresh token.
8. Sign out. Confirm logout includes the CSRF header, the backend clears the refresh cookie, login renders, and direct dashboard navigation redirects back to login.
9. Stop the backend and repeat sign-in or recovery. Confirm the UI remains generic and protected content is not shown.

## Scope and remaining limitations

Phase 2B provides frontend authentication and identity display only. It does not add public registration, account recovery, MFA, user administration, role-management screens, restaurant scoping, business modules, or later authorization rules. Production deployment still requires HTTPS, secure-cookie configuration, managed secrets, rate limiting, monitoring, and an end-to-end browser test environment.
