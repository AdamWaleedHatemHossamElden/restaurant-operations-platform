# Phase 4A menu-management verification

## Scope and architecture

Phase 4A adds Flyway V5 and a coherent ADMIN-only `/api/v1/menu` vertical slice. Categories, items, modifier groups, and modifier options are soft-activated aggregate records with optimistic-lock versions. Ordered item-to-group assignments use a restrictive composite-key join table. The implementation does not create orders, carts, kitchen tickets, inventory deductions, payments, public ordering, or image storage.

Prices use `BigDecimal` and MySQL `DECIMAL(12,2)`. JSON responses expose monetary values as decimal strings. The current single-restaurant frontend formats those strings in EUR through one utility and does not perform floating-point price calculations.

## Availability and modifier rules

An item is effectively available only when its category is active, the item is active, and `availableForSale` is true. Deactivating a category does not rewrite item flags; reactivation restores the effective result implied by each item.

Modifier groups support `SINGLE` and `MULTIPLE`. Minimum is non-negative, maximum is 1 through 20, minimum cannot exceed maximum, and `SINGLE` has a maximum of 1. Newly assigned groups must be active and have enough active options to meet both their minimum and configured maximum. Once actively assigned, changes that would reduce active options below those rules return HTTP 409. Deactivating a group preserves its historical assignments while making it non-selectable.

## API and security checks

- Verify unauthenticated menu requests return safe 401 responses and non-ADMIN requests return safe 403 responses.
- Verify validation, malformed JSON, missing records, duplicates, stale versions, and unusable modifier configurations return safe 400, 404, or 409 responses without stack traces or persistence details.
- Verify category names, item codes, group names, and option names are normalized before uniqueness checks.
- Verify filters and allowlisted sorting for categories, items, and modifier groups.
- Verify category/item activation, item sale availability, effective availability, ordered assignments, option activation, and optimistic-lock conflicts.
- Verify audit actions contain concise identifiers only and exclude credentials, tokens, cookies, and request bodies.

## Automated verification

From `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

The integration profile starts fresh MySQL 8.4 containers, applies Flyway V1–V5, verifies the five menu tables and constraints, and exercises real persistence for lifecycle, normalization, conflicts, effective availability, reusable modifiers, ordered assignments, unsafe option changes, and audit events.

From `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

Frontend tests cover the protected route, navigation/API integration, loading/empty/error states, category creation and validation, item filtering and decimal display, modifier rules, ordered assignment, React Query invalidation, and continued use of the authenticated Axios client.

## Manual verification checklist

Run the persistent MySQL service without deleting its volume, start the backend with the `dev` profile and ignored root environment, and start Vite. Log in as the local development administrator. Exercise category create/edit/reorder/activation; item create/edit/activation/sale flags; effective availability under an inactive category; `SINGLE` and `MULTIPLE` groups; free and paid options; ordered assignment/reordering/removal; unsafe option deactivation; duplicate and stale-version conflicts; filters/sorts; and desktop plus 375px layouts. Confirm no horizontal overflow and no browser console errors or warnings.

## Current limitations

- One logical restaurant and ADMIN-only management.
- EUR is the fixed display currency pending restaurant configuration and payment phases.
- No hard deletion or menu image upload.
- No orders, pricing snapshots, carts, kitchen, inventory, payment, or customer-facing behavior.
