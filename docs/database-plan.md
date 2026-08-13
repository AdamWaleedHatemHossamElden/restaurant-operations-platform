# Database plan

## Phase 1 schema

Flyway V1 creates only `roles`, `users`, `user_roles`, `restaurants`, and `audit_logs`. Tables use `BIGINT` generated identifiers where applicable, InnoDB, `utf8mb4`, explicit keys, and microsecond UTC-aware timestamp conventions. Password material is represented only by `password_hash`.

The development profile has a separate migration that seeds role names only. Production migrations contain no users or credentials.

## Implemented menu catalog (Flyway V5)

`menu_categories` owns normalized, uniquely named category records. `menu_items` references a category through a restrictive foreign key and stores a unique normalized code, `DECIMAL(12,2)` base price, independent active and sale-availability flags, display order, timestamps, and an optimistic-lock version.

Reusable `modifier_groups` store `SINGLE` or `MULTIPLE` selection rules. `modifier_options` belong to one group and have group-scoped unique names, non-negative `DECIMAL(12,2)` adjustments, display order, soft activation, and versions. `menu_item_modifier_groups` is an ordered many-to-many assignment with a composite primary key. All five tables use InnoDB, `utf8mb4`, checks, useful list indexes, and restrictive deletion behavior. No menu record is hard-deleted by the API.

## Implemented order management (Flyway V6)

`orders` stores a backend-generated unique order number, required restaurant-table reference, optional reservation reference, lifecycle status, optional notes, `DECIMAL(12,2)` subtotal and total, lifecycle timestamps, and an optimistic-lock version. Existing table, reservation, and menu records use restrictive foreign keys and are never deleted through an order operation.

`order_items` belongs to an order and records the source menu-item ID together with immutable code, name, base-price, unit-total, and line-total snapshots. `order_item_modifiers` records source modifier-group and option IDs plus immutable group-name, option-name, and price-adjustment snapshots in deterministic display order. Physically removing a draft line is allowed only while its order is `OPEN`; submitted content cannot be edited or deleted.

`order_status_history` stores every transition chronologically, including the initial `OPEN` state, previous and next states, change time, and actor. All four V6 tables use InnoDB, `utf8mb4`, checks, indexes for supported filters and ordering, and exact decimal money. Order-row write locks serialize item, metadata, and status mutations so aggregate totals and commercial immutability remain consistent.

## Implemented kitchen management (Flyway V7)

`kitchen_tickets` has a restrictive one-to-one relationship with submitted orders, a derived `QUEUED`, `PREPARING`, `READY`, or cancellation-owned `CANCELLED` status, lifecycle timestamps, and an optimistic version. `kitchen_ticket_items` has one restrictive, uniquely indexed relationship to each submitted `order_item` and stores only preparation status and timestamps; kitchen responses read immutable item/modifier labels and operational notes from the V6 snapshot tables instead of duplicating pricing or mutable menu data.

V7 uses InnoDB, `utf8mb4`, checks, restrictive foreign keys, and queue/filter indexes. It backfills tickets only for existing active `SUBMITTED` orders, preserving completed and cancelled history without manufacturing kitchen records. New submission creates the ticket and every item atomically. Mutations lock `orders`, then `kitchen_tickets`, then kitchen items so preparation, cancellation, and completion cannot form an inconsistent aggregate.

## Implemented inventory, recipes, suppliers, and purchasing (Flyway V8)

`inventory_items` defines normalized unique codes and names, one canonical unit, reorder thresholds, soft activation, timestamps, and optimistic versions. `stock_movements` is the sole stock authority: positive exact quantities are signed by movement type, and no update/delete API exists. Automatic usage carries a unique deterministic source key per kitchen item and inventory item.

`recipes` has at most one row per menu item. Ordered `recipe_ingredients` and `modifier_option_ingredients` use restrictive foreign keys, unique inventory-item assignments, exact positive quantities, and deterministic display order. Current configuration is read only when preparation starts; historical movements are snapshots and are never recalculated.

`suppliers` and `supplier_inventory_items` store soft-active vendors and exact current per-canonical-unit costs. `purchase_orders` owns its generated number, lifecycle, totals, timestamps, and optimistic version. `purchase_order_items` snapshots inventory labels, unit, quantity, cost, line total, and receipt progress. All V8 structures use InnoDB, `utf8mb4`, `DECIMAL`, checks, restrictive foreign keys, and indexes supporting the implemented filters and lock paths.

## Staff scheduling (Flyway V9)

`employees` stores normalized unique codes, names, minimal optional contact data, a scheduling-domain default operational role, optional employment start date, soft activation, timestamps, and an optimistic version. It stores no password, salary, tax, bank, identity-document, or sensitive HR data and does not require an application user.

`employee_availability` stores removable date-specific UTC windows with `start_at < end_at`, optional bounded notes, restrictive employee ownership, timestamps, and optimistic versions. `shifts` stores the employee, independently assigned operational role, UTC range, `SCHEDULED`, `COMPLETED`, or `CANCELLED` state, bounded notes, lifecycle timestamps, and an optimistic version. V9 uses InnoDB, `utf8mb4`, checks, restrictive foreign keys, and indexes for employee/date, weekly schedule, role, and status access paths.

## Implemented payments and invoices (Flyway V10)

`payments` is an append-only ledger of confirmed `SUCCEEDED` settlements against an order. It stores exact positive EUR amounts, an allowlisted method, server-generated payment number, required unique idempotency key, optional unique external confirmation reference, actor, and receive time. It deliberately has no card-number, security-code, bank-credential, provider-payload, refund, or reversible-status fields. Order locking serializes balance validation before insert.

`payment_reconciliations` has a unique restrictive payment relationship and stores one immutable reconciliation timestamp, optional bounded reference, and actor. `invoices` has a unique restrictive order relationship and stores server-generated number, exact order totals, currency, issue time, and actor. Ordered `invoice_items` and `invoice_item_modifiers` copy the immutable commercial labels and prices from order snapshots. V10 uses InnoDB, `utf8mb4`, checks, restrictive foreign keys, and indexes for operational filters.

## Reporting access indexes (Flyway V11)

Phase 9 introduces no reporting tables and duplicates no authoritative values. V11 adds only three composite indexes for the report range scans not covered by V1–V10: completed orders by `status, completed_at`, kitchen tickets by `created_at, status`, and global stock movements by `occurred_at, movement_type, inventory_item_id`. Payments, invoices, reservations, and shifts already had suitable range indexes. All reports aggregate directly from committed operational records.

## Planned domain relationships

- **Users and roles:** users receive many roles through `user_roles`; identity credentials remain distinct from employee records.
- **Employees and shifts:** Phase 7 implements single-restaurant employee records without user-account links. Date-specific availability and shifts reference employees restrictively; operational roles are business data rather than authorization.
- **Customers:** customer contact and preference records support reservations without requiring a login account.
- **Restaurant tables:** physical tables belong to a restaurant, have capacity and availability state, and may participate in temporary combinations.
- **Reservations:** reservations link customers, restaurants, time ranges, party size, and table assignments. Status history should preserve lifecycle transitions.
- **Menu categories and items:** the Phase 4A single-restaurant catalog is implemented. Restaurant ownership, price history, tax rules, and currency configuration remain future extensions.
- **Orders and order items:** the Phase 4B single-restaurant table-service aggregate is implemented with optional seated-reservation traceability and immutable item/modifier pricing snapshots. Restaurant ownership, taxes, discounts, service charges, tips, and payment allocation remain future extensions.
- **Order status history:** immutable transitions capture previous state, new state, actor, and timestamp. Kitchen lifecycle is represented by ticket/item state, timestamps, and safe audit events rather than a second order-status history.
- **Inventory:** Phase 6 implements single-restaurant items with canonical units and ledger-derived balances; locations, conversions, and transfers remain future work.
- **Recipes and recipe ingredients:** Phase 6 connects menu items and modifier options to exact inventory usage quantities.
- **Suppliers:** Phase 6 provides suppliers and current supplier-item pricing; lead times and automated replenishment remain future work.
- **Stock movements:** Phase 6 implements immutable receipts, usage, waste, and adjustments. Transfers are not implemented.
- **Payments and invoices:** Phase 8 implements immutable confirmed-payment, reconciliation, and invoice-snapshot records without prohibited card data. Refunds, voids, chargebacks, taxes, discounts, and provider payloads remain future work.
- **Audit logs:** append-focused audit records identify actor, action, entity type, entity identifier, restaurant context, time, and structured details.

Future migrations will be added with the phase that owns the behavior. Indexes will follow verified access patterns; foreign keys will enforce ownership and deletion rules.
