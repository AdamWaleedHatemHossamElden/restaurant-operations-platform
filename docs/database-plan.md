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

## Kitchen management in progress (Flyway V7)

`kitchen_tickets` has a restrictive one-to-one relationship with submitted orders, a derived `QUEUED`, `PREPARING`, `READY`, or cancellation-owned `CANCELLED` status, lifecycle timestamps, and an optimistic version. `kitchen_ticket_items` has one restrictive, uniquely indexed relationship to each submitted `order_item` and stores only preparation status and timestamps; kitchen responses read immutable item/modifier labels and operational notes from the V6 snapshot tables instead of duplicating pricing or mutable menu data.

V7 uses InnoDB, `utf8mb4`, checks, restrictive foreign keys, and queue/filter indexes. It backfills tickets only for existing active `SUBMITTED` orders, preserving completed and cancelled history without manufacturing kitchen records. New submission creates the ticket and every item atomically. Mutations lock `orders`, then `kitchen_tickets`, then kitchen items so preparation, cancellation, and completion cannot form an inconsistent aggregate.

## Planned domain relationships

- **Users and roles:** users receive many roles through `user_roles`; identity credentials remain distinct from employee records.
- **Employees and shifts:** employees belong to a restaurant and optionally link to a user. Shifts assign employees to time ranges and operational roles.
- **Customers:** customer contact and preference records support reservations without requiring a login account.
- **Restaurant tables:** physical tables belong to a restaurant, have capacity and availability state, and may participate in temporary combinations.
- **Reservations:** reservations link customers, restaurants, time ranges, party size, and table assignments. Status history should preserve lifecycle transitions.
- **Menu categories and items:** the Phase 4A single-restaurant catalog is implemented. Restaurant ownership, price history, tax rules, and currency configuration remain future extensions.
- **Orders and order items:** the Phase 4B single-restaurant table-service aggregate is implemented with optional seated-reservation traceability and immutable item/modifier pricing snapshots. Restaurant ownership, taxes, discounts, service charges, tips, and payment allocation remain future extensions.
- **Order status history:** immutable transitions capture previous state, new state, actor, and timestamp. Kitchen lifecycle is represented by ticket/item state, timestamps, and safe audit events rather than a second order-status history.
- **Inventory:** inventory items hold units and thresholds per location. Counts should be derived or reconciled from stock movements.
- **Recipes and recipe ingredients:** recipes connect menu items to inventory quantities and units, enabling consumption calculations.
- **Suppliers:** suppliers provide inventory items through purchase relationships with lead times and supplier-specific references.
- **Stock movements:** immutable receipts, usage, waste, transfers, and adjustments provide the inventory ledger.
- **Payments:** payment records reference orders and preserve amount, currency, method, status, and external reference without storing prohibited card data.
- **Audit logs:** append-focused audit records identify actor, action, entity type, entity identifier, restaurant context, time, and structured details.

Future migrations will be added with the phase that owns the behavior. Indexes will follow verified access patterns; foreign keys will enforce ownership and deletion rules.
