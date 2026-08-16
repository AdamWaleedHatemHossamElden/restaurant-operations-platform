# Phase 10 UI/UX redesign verification

## Scope

Phase 10 is a frontend-only product-design pass over every implemented workflow. It introduces the Ember hospitality-operations identity, a semantic design-token layer, Lucide iconography, grouped desktop navigation, an accessible mobile drawer, a server-backed operational dashboard, a polished login experience, and a shared visual contract for controls, forms, tables, cards, dialogs, tabs, status, and application states.

The redesign preserves all backend APIs, React Query keys, Axios authentication, protected routes, form schemas, mutations, concurrency behavior, and business rules. It introduces no migration, backend source change, or later-phase module.

## Responsive and accessibility behavior

The authenticated shell uses a persistent grouped sidebar on wide screens, a compact sidebar at intermediate widths, and a sticky top bar plus slide-in drawer on mobile. The drawer has an accessible trigger, moves focus to its close control, closes on Escape or successful route selection, returns focus on explicit dismissal, and prevents background scrolling while open. Page content, toolbars, cards, tables, dialog surfaces, and touch targets are checked at 375, 768, 1024, and 1440 pixel widths.

Keyboard focus is consistently visible. Navigation and landmarks are labelled, the main-content skip link is available, existing form labels and dialog semantics remain intact, reduced-motion preferences are respected, and status information keeps its textual labels rather than depending on color alone.

## Automated verification

Run from `frontend`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

The router suite covers all protected module routes, active navigation, logout, dashboard links, and mobile drawer selection and Escape behavior. Existing feature suites continue to exercise the real page components, dialogs, validation, mutations, query invalidation, loading, empty, error, and conflict paths. From the repository root, `git diff --check` verifies clean whitespace.

## Browser verification

Use the real development administrator and running Spring Boot/MySQL services. Verify login, dashboard recovery after refresh, navigation through every module, representative tabs and dialogs, order detail/capture, kitchen status, invoice display/print controls, report controls and CSV export, logout, and protected-route redirection. Repeat the route and overflow review at desktop and 375-pixel mobile widths and require a clean browser console.

Confirm that localStorage, sessionStorage, IndexedDB, and script-readable cookies contain no access or refresh token values. The access token remains in memory and the refresh credential remains in the backend-managed HttpOnly cookie exactly as before the redesign.

## Current boundary

Phase 10 does not add customer ordering, new operational modules, native mobile applications, offline support, localization, multi-tenant theming, user-selectable themes, or portfolio screenshots. Final deployment, production hardening, CI/CD, observability, and portfolio presentation remain Phase 11 work.
