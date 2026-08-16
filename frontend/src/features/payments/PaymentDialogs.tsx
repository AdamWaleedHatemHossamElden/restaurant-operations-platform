import { useState, type FormEvent } from 'react'

import { Dialog } from '../../components/ui/Dialog'
import { formatEur } from '../menu/money'
import type { Invoice, Payment, PaymentInput, PaymentMethod } from './paymentTypes'

function DialogShell({
  title,
  children,
  onClose,
}: {
  title: string
  children: React.ReactNode
  onClose: () => void
}) {
  return (
    <Dialog
      className="dialog payment-dialog"
      labelledBy="payment-dialog-title"
      onClose={onClose}
      closeOnBackdrop
    >
      <div className="dialog__header">
        <h2 id="payment-dialog-title">{title}</h2>
        <button className="icon-button" type="button" aria-label="Close dialog" onClick={onClose}>
          &times;
        </button>
      </div>
      {children}
    </Dialog>
  )
}

export function RecordPaymentDialog({
  outstanding,
  pending,
  error,
  onClose,
  onSave,
}: {
  outstanding: string
  pending: boolean
  error: string | null
  onClose: () => void
  onSave: (input: PaymentInput) => Promise<void>
}) {
  const [amount, setAmount] = useState(outstanding)
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [reference, setReference] = useState('')
  const [validation, setValidation] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const numeric = Number(amount)
    if (!/^\d{1,10}(\.\d{1,2})?$/.test(amount) || numeric <= 0 || numeric > Number(outstanding)) {
      setValidation(`Enter an amount up to ${formatEur(outstanding)} with at most two decimals.`)
      return
    }
    if ((method === 'CARD' || method === 'BANK_TRANSFER') && !reference.trim()) {
      setValidation('An external reference is required for card and bank-transfer confirmations.')
      return
    }
    setValidation(null)
    await onSave({ amount, method, externalReference: reference.trim() || null })
  }
  return (
    <DialogShell title="Record confirmed payment" onClose={onClose}>
      <form onSubmit={submit}>
        <p className="field-hint">
          Only record money already confirmed outside this application. Outstanding:{' '}
          {formatEur(outstanding)}.
        </p>
        {(validation || error) && (
          <div className="form-alert" role="alert">
            {validation || error}
          </div>
        )}
        <div className="form-field">
          <label htmlFor="payment-amount">Amount (EUR)</label>
          <input
            id="payment-amount"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
        </div>
        <div className="form-field">
          <label htmlFor="payment-method">Method</label>
          <select
            id="payment-method"
            value={method}
            onChange={(event) => setMethod(event.target.value as PaymentMethod)}
          >
            <option value="CASH">Cash</option>
            <option value="CARD">Card (already confirmed)</option>
            <option value="BANK_TRANSFER">Bank transfer (already confirmed)</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="payment-reference">External reference (optional for cash/other)</label>
          <input
            id="payment-reference"
            maxLength={120}
            value={reference}
            onChange={(event) => setReference(event.target.value)}
            autoComplete="off"
          />
          <small>Confirmation reference only. Never enter card numbers or security codes.</small>
        </div>
        <div className="dialog__actions">
          <button className="button button--secondary" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="button button--primary" disabled={pending} type="submit">
            {pending ? 'Recording…' : 'Record payment'}
          </button>
        </div>
      </form>
    </DialogShell>
  )
}

export function ReconcilePaymentDialog({
  payment,
  pending,
  error,
  onClose,
  onSave,
}: {
  payment: Payment
  pending: boolean
  error: string | null
  onClose: () => void
  onSave: (reference: string | null) => Promise<void>
}) {
  const [reference, setReference] = useState('')
  return (
    <DialogShell title={`Reconcile ${payment.paymentNumber}`} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          void onSave(reference.trim() || null)
        }}
      >
        <p>
          Confirm this immutable successful-payment record against the cash drawer or external
          settlement report.
        </p>
        {error && (
          <div className="form-alert" role="alert">
            {error}
          </div>
        )}
        <div className="form-field">
          <label htmlFor="reconciliation-reference">Reconciliation reference (optional)</label>
          <input
            id="reconciliation-reference"
            maxLength={120}
            value={reference}
            onChange={(event) => setReference(event.target.value)}
          />
        </div>
        <div className="dialog__actions">
          <button className="button button--secondary" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="button button--primary" disabled={pending} type="submit">
            Confirm reconciliation
          </button>
        </div>
      </form>
    </DialogShell>
  )
}

export function InvoiceDocument({ invoice }: { invoice: Invoice }) {
  return (
    <article className="invoice-document">
      <header>
        <div>
          <p className="eyebrow">Tax-neutral receipt snapshot</p>
          <h2>{invoice.invoiceNumber}</h2>
        </div>
        <div>
          <strong>{invoice.orderNumber}</strong>
          <time dateTime={invoice.issuedAt}>{new Date(invoice.issuedAt).toLocaleString()}</time>
        </div>
      </header>
      <div className="invoice-lines">
        {invoice.items.map((item) => (
          <div className="invoice-line" key={item.id}>
            <div>
              <strong>
                {item.quantity}&times; {item.itemName}
              </strong>
              <small>
                {item.itemCode} · {formatEur(item.unitTotal)} each
              </small>
              {item.modifiers.map((modifier) => (
                <span key={modifier.id}>
                  {modifier.groupName}: {modifier.optionName} (+
                  {formatEur(modifier.priceAdjustment)})
                </span>
              ))}
            </div>
            <strong>{formatEur(item.lineTotal)}</strong>
          </div>
        ))}
      </div>
      <dl className="order-totals">
        <div>
          <dt>Subtotal</dt>
          <dd>{formatEur(invoice.subtotal)}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd>{formatEur(invoice.total)}</dd>
        </div>
        <div>
          <dt>Paid</dt>
          <dd>{formatEur(invoice.paidTotal)}</dd>
        </div>
      </dl>
      <footer>
        <span>Currency: EUR</span>
        <span>Immutable snapshot · no taxes, discounts, or tips</span>
      </footer>
    </article>
  )
}
