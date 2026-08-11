# Phase 6 inventory, recipes, suppliers, and purchasing verification

## Scope and invariants

Phase 6 adds Flyway V8 and four ADMIN-only operational areas under the protected `/inventory` browser route. MySQL remains authoritative. Inventory balances are derived from immutable stock movements; clients never submit a current balance, actor, timestamp, movement sign, generated purchase-order number, supplier-price snapshot, or total.

Inventory uses exactly three canonical units: `GRAM`, `MILLILITER`, and `UNIT`. Quantities use `DECIMAL(14,3)`/Java `BigDecimal`, and supplier costs use `DECIMAL(12,4)`. There is no automatic unit conversion. Low stock means `onHand <= reorderThreshold`, including negative balances. Usage is allowed to make stock negative so kitchen work is not blocked by a stale digital count.

## Immutable ledger

`RECEIPT` and `ADJUSTMENT_IN` add stock. `USAGE`, `WASTE`, and `ADJUSTMENT_OUT` subtract stock. Every stored quantity is a positive magnitude. No movement update or delete endpoint exists; corrections require a compensating movement. Movement history includes its UTC occurrence timestamp, actor where applicable, safe reference, reason where appropriate, and receipt cost snapshot.

## Recipes and kitchen consumption

Each menu item has at most one soft-active recipe. Recipe and modifier-option ingredient lists reject duplicate inventory items, duplicate display orders, non-positive quantities, stale versions, and newly assigned inactive inventory items.

When a kitchen item moves `QUEUED → PREPARING`, the transaction reads the current active base recipe and selected modifier-option mappings, multiplies each quantity by the order-line quantity, aggregates shared inventory items, and writes one `USAGE` movement per kitchen-item/inventory-item pair. The deterministic source key is unique in MySQL, so retries cannot create duplicate usage. Recipe and modifier configuration locks protect each read from a concurrent replacement. The kitchen state change, movements, and audits either all commit or all roll back.

A missing active base recipe does not block preparation; configured modifier usage still applies. `PREPARING → READY`, repeated REST reads, real-time reconnects, and later recipe changes do not consume again or rewrite history. Cancelling after preparation does not restore stock automatically because food may already have been prepared; an administrator records an explicit compensating movement when operationally appropriate.

## Suppliers and purchasing

Suppliers and supplier-item prices are soft-active and versioned. A draft purchase order may update notes, add/change/remove lines, and change supplier only after clearing all lines. Adding a line snapshots the current inventory code, name, canonical unit, and active supplier relationship cost. Later supplier or inventory changes do not rewrite the snapshot.

Ordering requires an active supplier, at least one line, active inventory, and active supplier-item relationships. Supplier, lines, quantities, cost snapshots, and totals freeze after `ORDERED`. Partial receipts lock the purchase order and line, validate the request version and remaining quantity, append one immutable `RECEIPT`, and derive `PARTIALLY_RECEIVED` or `RECEIVED`. A partially received order may be cancelled without reversing received stock; a received order is terminal.

The real MySQL final-receipt regression launches two simultaneous authenticated HTTP requests with the same original version and remaining quantity. Exactly one returns 200 and one returns safe 409. The final line is fully received, the PO is `RECEIVED`, and exactly one matching receipt movement and audit event exist.

## API surface

- `/api/v1/inventory/items` supports create, list, safe filters/sorts, read, update, and soft activation.
- `/api/v1/inventory/items/{id}/movements` returns immutable chronological history.
- `/api/v1/inventory/movements` creates only manual adjustment-in, adjustment-out, or waste entries.
- `/api/v1/inventory/low-stock` returns the current derived low-stock set.
- `/api/v1/recipes` and `/api/v1/recipes/menu-items/{menuItemId}` manage recipe state and ordered ingredients.
- `/api/v1/recipes/modifier-options/{modifierOptionId}/ingredients` manages modifier usage requirements.
- `/api/v1/suppliers` manages suppliers, activation, and supplier-item pricing.
- `/api/v1/purchase-orders` manages drafts, lines, status transitions, and receipts.

All endpoints require a valid ADMIN Bearer token. Safe errors use the existing JSON shape: 400 for malformed/invalid input, 401 for missing authentication, 403 for insufficient authority, 404 for missing resources, and 409 for stale, locked, duplicate, inactive, invalid-transition, and receipt conflicts.

## Automated verification

From `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration-test verify
```

From `frontend/`:

```powershell
npm run test
npm run lint
npm run format:check
npm run build
```

From the repository root:

```powershell
git diff --check
```

The backend suite covers V8/fresh V1–V8 migration, authorization and safe JSON errors, normalized inventory/supplier uniqueness, signed ledger balances, low/negative stock, recipe validation and versions, modifier usage, exact-once kitchen consumption, price snapshots, exact totals, partial/full/cancelled PO behavior, failed-receipt rollback, terminal state, audits, and real MySQL receipt/kitchen concurrency. Existing Phase 5 concurrency tests are rerun by the integration profile.

Frontend tests exercise the protected route, authenticated API contract, versioned commands, negative/low-stock text, immutable history, manual movements, recipe and supplier search/filter behavior, receiving progress, receipt input, realtime inventory invalidation, loading/empty/error states, and query invalidation.

## Real browser checklist

Verify with the persistent MySQL development service without resetting its volume:

1. Log in with the local ignored development-admin credentials and open `/inventory`.
2. Create gram, milliliter, and unit inventory items; edit thresholds and activation.
3. Record adjustment-in, adjustment-out, and waste; verify derived, low, and negative balances plus immutable local-time history.
4. Configure an active menu recipe and modifier-option ingredients, including a shared inventory item.
5. Submit an order and start its kitchen item; verify exact aggregated usage, one-time consumption, and inventory refresh after realtime notification.
6. Move the item to READY, refetch/reconnect, change the recipe, and confirm historical usage is unchanged.
7. Create/edit/filter a supplier and configure active inventory pricing.
8. Create a draft PO, add/change/remove a line, order it, receive it partially, and receive the remainder.
9. Verify frozen labels/costs, exact totals, movement history, low-stock refresh, terminal read-only state, and safe invalid/stale/over-receipt feedback.
10. Verify desktop and 375px layouts have keyboard-operable controls, textual status, announced errors, and no page-level horizontal overflow or console errors/warnings.

## Current limitations

Phase 6 supports one logical restaurant and ADMIN only. It has no unit conversion, locations/bins, transfers, counts/stocktakes, lot or expiry tracking, lead times, forecasting, automatic replenishment, barcode scanning, invoice matching, supplier portal, tax, multi-currency, kitchen stock blocking, or automatic reversal after cancellation. The existing in-process kitchen notification broker remains best-effort; reconnect and events refetch authoritative REST state.
