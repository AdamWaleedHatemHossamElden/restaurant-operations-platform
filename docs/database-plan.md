# Database plan

## Phase 1 schema

Flyway V1 creates only `roles`, `users`, `user_roles`, `restaurants`, and `audit_logs`. Tables use `BIGINT` generated identifiers where applicable, InnoDB, `utf8mb4`, explicit keys, and microsecond UTC-aware timestamp conventions. Password material is represented only by `password_hash`.

The development profile has a separate migration that seeds role names only. Production migrations contain no users or credentials.

## Implemented menu catalog (Flyway V5)

`menu_categories` owns normalized, uniquely named category records. `menu_items` references a category through a restrictive foreign key and stores a unique normalized code, `DECIMAL(12,2)` base price, independent active and sale-availability flags, display order, timestamps, and an optimistic-lock version.

Reusable `modifier_groups` store `SINGLE` or `MULTIPLE` selection rules. `modifier_options` belong to one group and have group-scoped unique names, non-negative `DECIMAL(12,2)` adjustments, display order, soft activation, and versions. `menu_item_modifier_groups` is an ordered many-to-many assignment with a composite primary key. All five tables use InnoDB, `utf8mb4`, checks, useful list indexes, and restrictive deletion behavior. No menu record is hard-deleted by the API.

## Planned domain relationships

- **Users and roles:** users receive many roles through `user_roles`; identity credentials remain distinct from employee records.
- **Employees and shifts:** employees belong to a restaurant and optionally link to a user. Shifts assign employees to time ranges and operational roles.
- **Customers:** customer contact and preference records support reservations without requiring a login account.
- **Restaurant tables:** physical tables belong to a restaurant, have capacity and availability state, and may participate in temporary combinations.
- **Reservations:** reservations link customers, restaurants, time ranges, party size, and table assignments. Status history should preserve lifecycle transitions.
- **Menu categories and items:** the Phase 4A single-restaurant catalog is implemented. Restaurant ownership, price history, tax rules, and currency configuration remain future extensions.
- **Orders and order items:** an order belongs to a restaurant and service context; items snapshot names and prices so historical receipts remain accurate.
- **Order status history:** immutable transitions capture previous state, new state, actor, timestamp, and optional reason.
- **Inventory:** inventory items hold units and thresholds per location. Counts should be derived or reconciled from stock movements.
- **Recipes and recipe ingredients:** recipes connect menu items to inventory quantities and units, enabling consumption calculations.
- **Suppliers:** suppliers provide inventory items through purchase relationships with lead times and supplier-specific references.
- **Stock movements:** immutable receipts, usage, waste, transfers, and adjustments provide the inventory ledger.
- **Payments:** payment records reference orders and preserve amount, currency, method, status, and external reference without storing prohibited card data.
- **Audit logs:** append-focused audit records identify actor, action, entity type, entity identifier, restaurant context, time, and structured details.

Future migrations will be added with the phase that owns the behavior. Indexes will follow verified access patterns; foreign keys will enforce ownership and deletion rules.
