# Local showcase data

Phase 11B provides a deterministic, fictional dataset for local portfolio demonstrations of Cedar & Stone Kitchen. It exercises the existing product surface without adding restaurant functionality or weakening production configuration.

## Safety model

- The dataset is loaded only when the Spring `dev` profile is active **and** `DEMO_DATA_ENABLED=true`.
- The SQL resource lives under `db/dev`; it is not a Flyway migration and is not included in the production Flyway locations.
- Production keeps `DEMO_DATA_ENABLED` unset or `false` and loads only `classpath:db/migration`.
- A safe audit marker makes the initializer idempotent. Restarting the backend does not duplicate the dataset.
- All restaurant, guest, employee, supplier, contact, payment-reference, and order data is fictional. The script contains no credentials, authentication tokens, card data, or environment values.
- Timestamps are calculated relative to the initialization time so the operational dashboard, kitchen queue, schedule, reservations, and reports remain useful for visual capture.

## What is included

The dataset covers restaurant tables and reservations; a coherent Mediterranean menu with modifiers; ingredients, recipes, suppliers, stock movements, and purchase orders; employees, current-week availability, and shifts; historical and live orders; kitchen tickets; completed, partial, and unpaid settlement states; invoices and reconciliations; and enough recent history for every report.

Relationships use the application schema directly: kitchen usage follows recipe and modifier quantities, prices are immutable order snapshots, payments never exceed completed-order totals, invoices exist only for fully paid orders, blocking reservations do not overlap, and staff shifts remain inside date-specific availability.

## Start a local showcase

1. Keep local secrets in the ignored root `.env` file and start the existing MySQL service on `localhost:3307`.
2. Export the `.env` values into the current terminal without printing them.
3. Set `SPRING_PROFILES_ACTIVE=dev` and `DEMO_DATA_ENABLED=true`.
4. Start the backend from `backend` with the Maven wrapper.
5. Start the frontend from `frontend` with `npm run dev`.
6. Sign in with the existing local bootstrap administrator configured in `.env`.

Expected local URLs:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- API health: `http://localhost:8080/api/v1/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Rebuild the disposable showcase database

Reset only the Compose resources owned by this repository. Before removing anything, verify that the target container is `restaurant-operations-mysql`, that its data mount is the named volume `restaurant-operations-mysql-data`, and that the database contains no data that must be retained. Never target an unrelated MySQL container, the default MySQL port, or a broad Docker volume pattern.

After removing that exact project container and named volume, start the Compose MySQL service again and run the backend with the opt-in settings above. Flyway recreates V1–V11 (plus the dev role migration), the bootstrap administrator runs first, and the showcase initializer loads the dataset once.

## Visual-capture checklist

Use a 1440-pixel desktop viewport and verify Dashboard, Tables, Reservations, Menu, Orders, Kitchen, Inventory, Staff, Payments, and Reports. The Kitchen page should show a small live queue, Reports should show recent trend data, Inventory should include actionable low-stock items, and Staff should show the current week. Confirm the browser console is clean before capturing portfolio screenshots.
