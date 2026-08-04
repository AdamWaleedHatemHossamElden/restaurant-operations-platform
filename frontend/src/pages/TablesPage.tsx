import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { TableFormDialog } from '../features/tables/TableFormDialog'
import type { TableFormValues } from '../features/tables/tableSchema'
import {
  createTable,
  listTables,
  setTableActivation,
  tableRequestError,
  updateTable,
} from '../features/tables/tablesApi'
import type { RestaurantTable, TableFilters, TableStatus } from '../features/tables/tableTypes'

export function TablesPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [active, setActive] = useState('true')
  const [status, setStatus] = useState('')
  const [section, setSection] = useState('')
  const [editing, setEditing] = useState<RestaurantTable | null | undefined>(undefined)
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const filters = useMemo<TableFilters>(
    () => ({
      active: active === '' ? undefined : active === 'true',
      status: status ? (status as TableStatus) : undefined,
      section: section || undefined,
      tableNumber: search || undefined,
      sortBy: 'tableNumber',
      direction: 'ASC',
    }),
    [active, search, section, status],
  )
  const tablesQuery = useQuery({
    queryKey: ['tables', filters],
    queryFn: () => listTables(filters),
  })

  const refreshList = async () => queryClient.invalidateQueries({ queryKey: ['tables'] })
  const saveMutation = useMutation({
    mutationFn: async (values: TableFormValues) =>
      editing
        ? updateTable(editing.id, { ...values, version: editing.version })
        : createTable(values),
    onSuccess: async (table) => {
      setNotice(`${table.tableNumber} was ${editing ? 'updated' : 'created'} successfully.`)
      setEditing(undefined)
      setFormError(null)
      await refreshList()
    },
    onError: (error) => setFormError(tableRequestError(error)),
  })
  const activationMutation = useMutation({
    mutationFn: setTableActivation,
    onSuccess: async (table) => {
      setNotice(`${table.tableNumber} was ${table.active ? 'reactivated' : 'deactivated'}.`)
      await refreshList()
    },
    onError: (error) => setNotice(tableRequestError(error)),
  })

  const openCreate = () => {
    setFormError(null)
    setEditing(null)
  }
  const openEdit = (table: RestaurantTable) => {
    setFormError(null)
    setEditing(table)
  }
  const toggleActivation = (table: RestaurantTable) => {
    if (
      table.active &&
      !window.confirm(`Deactivate ${table.tableNumber}? Existing history is retained.`)
    ) {
      return
    }
    activationMutation.mutate(table)
  }

  return (
    <div className="page tables-page">
      <section className="tables-hero" aria-labelledby="tables-title">
        <div>
          <p className="eyebrow">Dining room setup</p>
          <h1 id="tables-title">Restaurant tables</h1>
          <p>Keep capacity, sections, availability, and active service records accurate.</p>
        </div>
        <button className="button button--primary" type="button" onClick={openCreate}>
          Add table
        </button>
      </section>

      {notice && (
        <div className="notice" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice(null)} aria-label="Dismiss notification">
            &times;
          </button>
        </div>
      )}

      <section className="table-filters" aria-label="Table filters">
        <div className="form-field table-filter--search">
          <label htmlFor="table-search">Search table number</label>
          <input
            id="table-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="T-01"
          />
        </div>
        <div className="form-field">
          <label htmlFor="active-filter">Record state</label>
          <select
            id="active-filter"
            value={active}
            onChange={(event) => setActive(event.target.value)}
          >
            <option value="true">Active</option>
            <option value="false">Inactive</option>
            <option value="">All records</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="status-filter">Status</label>
          <select
            id="status-filter"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="">Any status</option>
            <option value="AVAILABLE">Available</option>
            <option value="OUT_OF_SERVICE">Out of service</option>
          </select>
        </div>
        <div className="form-field">
          <label htmlFor="section-filter">Section</label>
          <input
            id="section-filter"
            value={section}
            onChange={(event) => setSection(event.target.value)}
            placeholder="Main dining"
          />
        </div>
      </section>

      {tablesQuery.isPending && <div className="table-state">Loading restaurant tables…</div>}
      {tablesQuery.isError && (
        <div className="table-state table-state--error" role="alert">
          <p>Restaurant tables could not be loaded.</p>
          <button
            className="button button--secondary"
            type="button"
            onClick={() => tablesQuery.refetch()}
          >
            Try again
          </button>
        </div>
      )}
      {tablesQuery.data?.length === 0 && (
        <div className="table-state">
          <h2>No tables match these filters.</h2>
          <p>Create a table or adjust the filters to see another part of the dining room.</p>
        </div>
      )}
      {tablesQuery.data && tablesQuery.data.length > 0 && (
        <div className="table-grid" aria-label="Restaurant table list">
          {tablesQuery.data.map((table) => (
            <article
              className={`table-card${table.active ? '' : ' table-card--inactive'}`}
              key={table.id}
            >
              <div className="table-card__heading">
                <div>
                  <p className="table-card__number">{table.tableNumber}</p>
                  <h2>{table.displayName}</h2>
                </div>
                <span className={`status-pill status-pill--${table.status.toLowerCase()}`}>
                  {table.status === 'AVAILABLE' ? 'Available' : 'Out of service'}
                </span>
              </div>
              <dl className="table-card__details">
                <div>
                  <dt>Capacity</dt>
                  <dd>{table.capacity} guests</dd>
                </div>
                <div>
                  <dt>Section</dt>
                  <dd>{table.section}</dd>
                </div>
                <div>
                  <dt>Record</dt>
                  <dd>{table.active ? 'Active' : 'Inactive'}</dd>
                </div>
              </dl>
              <div className="table-card__actions">
                <button
                  className="button button--secondary"
                  type="button"
                  onClick={() => openEdit(table)}
                >
                  Edit
                </button>
                <button
                  className={`button ${table.active ? 'button--danger' : 'button--secondary'}`}
                  type="button"
                  disabled={activationMutation.isPending}
                  onClick={() => toggleActivation(table)}
                >
                  {table.active ? 'Deactivate' : 'Reactivate'}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      {editing !== undefined && (
        <TableFormDialog
          table={editing}
          isSaving={saveMutation.isPending}
          error={formError}
          onClose={() => setEditing(undefined)}
          onSave={async (values) => {
            try {
              await saveMutation.mutateAsync(values)
            } catch {
              // The mutation's onError handler presents the safe user-facing message.
            }
          }}
        />
      )}
    </div>
  )
}
