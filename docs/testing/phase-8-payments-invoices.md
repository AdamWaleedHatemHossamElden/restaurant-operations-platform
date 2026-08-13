# Phase 8 payments, reconciliation, and invoices testing

## Automated coverage

- Backend unit/MVC coverage verifies ADMIN-only payment and invoice routes, OpenAPI exposure, safe error boundaries, the three-field payment DTO, and the absence of raw card-data fields.
- MySQL 8.4 integration coverage verifies Flyway V1–V10, split and partial settlement, stable idempotent replay, mismatched replay rejection, external-reference uniqueness, one-time reconciliation, immutable invoice snapshots, and invalid order/payment states.
- Coordinated concurrent tests submit competing final payments, identical-key retries, and invoice issuance against real MySQL. They assert no overpayment, one durable record and audit event, exact derived totals, and one invoice snapshot.
- Frontend tests verify authenticated API calls, the `Idempotency-Key` header, business-only payment payloads, payment filtering, reconciliation, invoice detail/print controls, order-detail settlement, safe states, and the absence of card-entry fields.

## Manual workflow

Use the ignored root `.env` only to start the development services and authenticate. Never print its values, access tokens, refresh cookies, payment keys, or complete cookie contents.

1. Complete an order and confirm its payment summary is `UNPAID`.
2. Record a partial confirmed payment and verify the server derives `PARTIALLY_PAID` and the exact outstanding EUR amount.
3. Record the remaining amount using another method and verify `PAID`; retry the same request with the same idempotency key and confirm no duplicate record or audit event.
4. Attempt overpayment and payment against a non-completed order; verify safe conflict responses.
5. Reconcile one payment, repeat the request, and verify one immutable reconciliation.
6. Issue the invoice, repeat issuance, and verify one invoice whose item/modifier labels and prices equal the order snapshots.
7. Verify `/payments` search/filter tabs, order links, print view, desktop layout, and 375px layout with zero console errors or warnings.

## Boundaries

Phase 8 records already-confirmed `CASH`, `CARD`, `BANK_TRANSFER`, or `OTHER` settlements in EUR. It does not process funds, accept raw card or bank credentials, integrate with a provider, or implement authorization/capture, refunds, voids, chargebacks, taxes, discounts, tips, multi-currency, or jurisdiction-specific fiscal invoicing.
