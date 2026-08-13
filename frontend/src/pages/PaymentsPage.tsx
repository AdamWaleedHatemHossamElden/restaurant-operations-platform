import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { formatEur } from '../features/menu/money'
import { InvoiceDocument, ReconcilePaymentDialog } from '../features/payments/PaymentDialogs'
import {
  invoiceKeys,
  listInvoices,
  listPayments,
  paymentKeys,
  paymentRequestError,
  reconcilePayment,
} from '../features/payments/paymentsApi'
import type { Invoice, Payment, PaymentMethod } from '../features/payments/paymentTypes'

type WorkspaceTab = 'payments' | 'reconciliation' | 'invoices'

function localDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

export function PaymentsPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<WorkspaceTab>('payments')
  const [search, setSearch] = useState('')
  const [method, setMethod] = useState<PaymentMethod | ''>('')
  const [reconciled, setReconciled] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [selectedPayment, setSelectedPayment] = useState<Payment | null>(null)
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null)
  const [error, setError] = useState<string | null>(null)

  const paymentsQuery = useQuery({
    queryKey: [...paymentKeys.all, { search, method, reconciled, from, to }],
    queryFn: () =>
      listPayments({
        search: search || undefined,
        method: method || undefined,
        reconciled: reconciled === '' ? undefined : reconciled === 'true',
        receivedFrom: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
        receivedTo: to ? new Date(`${to}T23:59:59.999`).toISOString() : undefined,
        sortBy: 'receivedAt',
        direction: 'DESC',
      }),
  })
  const invoicesQuery = useQuery({
    queryKey: [...invoiceKeys.all, { search, from, to }],
    queryFn: () =>
      listInvoices({
        search: search || undefined,
        issuedFrom: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
        issuedTo: to ? new Date(`${to}T23:59:59.999`).toISOString() : undefined,
        sortBy: 'issuedAt',
        direction: 'DESC',
      }),
    enabled: tab === 'invoices',
  })
  const reconcileMutation = useMutation({
    mutationFn: ({ payment, reference }: { payment: Payment; reference: string | null }) =>
      reconcilePayment(payment.id, reference),
    onSuccess: async () => {
      setSelectedPayment(null)
      setError(null)
      await queryClient.invalidateQueries({ queryKey: paymentKeys.all })
    },
    onError: (requestError) => setError(paymentRequestError(requestError)),
  })

  const shownPayments = (paymentsQuery.data ?? []).filter((payment) =>
    tab === 'reconciliation' ? !payment.reconciliation : true,
  )
  const currentQuery = tab === 'invoices' ? invoicesQuery : paymentsQuery

  return (
    <div className="page payments-page">
      <section className="page-heading payments-heading">
        <div>
          <p className="eyebrow">Phase 8 · EUR settlement ledger</p>
          <h1>Payments & invoices</h1>
          <p>
            Record confirmed settlements, reconcile them once, and issue immutable paid-order
            invoices.
          </p>
        </div>
      </section>

      <div className="workspace-tabs" role="tablist" aria-label="Payment workspace">
        {(['payments', 'reconciliation', 'invoices'] as WorkspaceTab[]).map((value) => (
          <button
            key={value}
            role="tab"
            aria-selected={tab === value}
            className={tab === value ? 'is-active' : ''}
            onClick={() => {
              setTab(value)
              setSelectedInvoice(null)
            }}
          >
            {value === 'payments'
              ? 'Payments'
              : value === 'reconciliation'
                ? 'Reconciliation'
                : 'Invoices'}
          </button>
        ))}
      </div>

      <section className="payment-filters" aria-label="Payment filters">
        <div className="form-field">
          <label htmlFor="payment-search">Search</label>
          <input
            id="payment-search"
            type="search"
            placeholder="Payment, order, invoice, reference"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        {tab !== 'invoices' && (
          <div className="form-field">
            <label htmlFor="payment-method-filter">Method</label>
            <select
              id="payment-method-filter"
              value={method}
              onChange={(event) => setMethod(event.target.value as PaymentMethod | '')}
            >
              <option value="">All methods</option>
              <option value="CASH">Cash</option>
              <option value="CARD">Card</option>
              <option value="BANK_TRANSFER">Bank transfer</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        )}
        {tab === 'payments' && (
          <div className="form-field">
            <label htmlFor="reconciled-filter">Reconciliation</label>
            <select
              id="reconciled-filter"
              value={reconciled}
              onChange={(event) => setReconciled(event.target.value)}
            >
              <option value="">All</option>
              <option value="true">Reconciled</option>
              <option value="false">Pending</option>
            </select>
          </div>
        )}
        <div className="form-field">
          <label htmlFor="payment-from">From</label>
          <input
            id="payment-from"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="payment-to">To</label>
          <input
            id="payment-to"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </div>
      </section>

      {currentQuery.isPending && <div className="table-state">Loading records&hellip;</div>}
      {currentQuery.isError && (
        <div className="table-state table-state--error" role="alert">
          Payment records could not be loaded.
        </div>
      )}
      {tab !== 'invoices' &&
        !paymentsQuery.isPending &&
        !paymentsQuery.isError &&
        shownPayments.length === 0 && (
          <div className="table-state">
            <h2>No payment records match.</h2>
          </div>
        )}
      {tab !== 'invoices' && shownPayments.length > 0 && (
        <div className="data-table-wrap">
          <table className="data-table payment-table">
            <thead>
              <tr>
                <th>Payment</th>
                <th>Order</th>
                <th>Method</th>
                <th>Amount</th>
                <th>Received</th>
                <th>Reconciliation</th>
              </tr>
            </thead>
            <tbody>
              {shownPayments.map((payment) => (
                <tr key={payment.id}>
                  <td>
                    <strong>{payment.paymentNumber}</strong>
                    {payment.externalReference && <small>{payment.externalReference}</small>}
                  </td>
                  <td>
                    <Link to={`/orders/${payment.orderId}`}>{payment.orderNumber}</Link>
                  </td>
                  <td>{payment.method.replace('_', ' ')}</td>
                  <td>{formatEur(payment.amount)}</td>
                  <td>{localDateTime(payment.receivedAt)}</td>
                  <td>
                    {payment.reconciliation ? (
                      <span className="status-chip status-chip--positive">Reconciled</span>
                    ) : (
                      <button
                        className="button button--secondary"
                        type="button"
                        onClick={() => {
                          setError(null)
                          setSelectedPayment(payment)
                        }}
                      >
                        Reconcile
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'invoices' &&
        !invoicesQuery.isPending &&
        !invoicesQuery.isError &&
        (invoicesQuery.data ?? []).length === 0 && (
          <div className="table-state">
            <h2>No invoices match.</h2>
          </div>
        )}
      {tab === 'invoices' && !selectedInvoice && (invoicesQuery.data ?? []).length > 0 && (
        <div className="invoice-grid">
          {invoicesQuery.data?.map((invoice) => (
            <article className="invoice-card" key={invoice.id}>
              <div>
                <p className="eyebrow">{invoice.orderNumber}</p>
                <h2>{invoice.invoiceNumber}</h2>
                <time dateTime={invoice.issuedAt}>{localDateTime(invoice.issuedAt)}</time>
              </div>
              <strong>{formatEur(invoice.total)}</strong>
              <button
                className="button button--secondary"
                type="button"
                onClick={() => setSelectedInvoice(invoice)}
              >
                View invoice
              </button>
            </article>
          ))}
        </div>
      )}
      {tab === 'invoices' && selectedInvoice && (
        <section className="invoice-view">
          <div className="invoice-view__actions">
            <button
              className="button button--secondary"
              type="button"
              onClick={() => setSelectedInvoice(null)}
            >
              Back to invoices
            </button>
            <button className="button button--primary" type="button" onClick={() => window.print()}>
              Print invoice
            </button>
          </div>
          <InvoiceDocument invoice={selectedInvoice} />
        </section>
      )}

      {selectedPayment && (
        <ReconcilePaymentDialog
          payment={selectedPayment}
          pending={reconcileMutation.isPending}
          error={error}
          onClose={() => setSelectedPayment(null)}
          onSave={async (reference) => {
            try {
              await reconcileMutation.mutateAsync({ payment: selectedPayment, reference })
            } catch {
              /* safe error shown */
            }
          }}
        />
      )}
    </div>
  )
}
