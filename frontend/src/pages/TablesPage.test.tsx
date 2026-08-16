import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createTable,
  listTables,
  setTableActivation,
  updateTable,
} from '../features/tables/tablesApi'
import type { RestaurantTable } from '../features/tables/tableTypes'
import { TablesPage } from './TablesPage'

vi.mock('../features/tables/tablesApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/tables/tablesApi')>()
  return {
    ...original,
    createTable: vi.fn(),
    listTables: vi.fn(),
    setTableActivation: vi.fn(),
    updateTable: vi.fn(),
  }
})

const mockedCreate = vi.mocked(createTable)
const mockedList = vi.mocked(listTables)
const mockedActivation = vi.mocked(setTableActivation)
const mockedUpdate = vi.mocked(updateTable)

const table: RestaurantTable = {
  id: 1,
  tableNumber: 'T-01',
  displayName: 'Window table',
  capacity: 4,
  section: 'Main',
  status: 'AVAILABLE',
  active: true,
  createdAt: '2026-08-04T10:00:00Z',
  updatedAt: '2026-08-04T10:00:00Z',
  version: 0,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <TablesPage />
    </QueryClientProvider>,
  )
}

describe('tables page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue([table])
  })

  it('renders loading, empty, error, and populated states accurately', async () => {
    mockedList.mockReturnValueOnce(new Promise(() => undefined))
    const loading = render(
      <QueryClientProvider client={new QueryClient()}>
        <TablesPage />
      </QueryClientProvider>,
    )
    expect(screen.getByText('Loading restaurant tables…')).toBeInTheDocument()
    loading.unmount()

    mockedList.mockResolvedValueOnce([])
    renderPage()
    expect(
      await screen.findByRole('heading', { name: 'No tables match these filters.' }),
    ).toBeInTheDocument()

    mockedList.mockRejectedValueOnce(new Error('Unavailable'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Restaurant tables could not be loaded.',
    )

    renderPage()
    expect(await screen.findByRole('heading', { name: 'Window table' })).toBeInTheDocument()
    expect(screen.getByText('4 guests')).toBeInTheDocument()
  })

  it('sends search and filter selections to the list API', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Window table' })
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Search table number'), 'T-0')
    await user.selectOptions(screen.getByLabelText('Record state'), '')
    await user.selectOptions(screen.getByLabelText('Status'), 'OUT_OF_SERVICE')
    await user.type(screen.getByLabelText('Section'), 'Patio')

    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith(
        expect.objectContaining({
          active: undefined,
          tableNumber: 'T-0',
          status: 'OUT_OF_SERVICE',
          section: 'Patio',
        }),
      ),
    )
  })

  it('validates creation then submits a valid table', async () => {
    mockedCreate.mockResolvedValue({ ...table, id: 2, tableNumber: 'P-02' })
    renderPage()
    await screen.findByRole('heading', { name: 'Window table' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add table' }))
    const dialog = screen.getByRole('dialog')
    await user.clear(screen.getByLabelText('Capacity'))
    await user.click(screen.getByRole('button', { name: 'Create table' }))
    expect(await screen.findByText('Table number is required')).toBeInTheDocument()
    expect(screen.getByText('Display name is required')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Table number'), 'P-02')
    await user.type(within(dialog).getByLabelText('Display name'), 'Patio two')
    await user.type(within(dialog).getByLabelText('Capacity'), '6')
    await user.type(within(dialog).getByLabelText('Section'), 'Patio')
    await user.click(screen.getByRole('button', { name: 'Create table' }))

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith({
        tableNumber: 'P-02',
        displayName: 'Patio two',
        capacity: 6,
        section: 'Patio',
        status: 'AVAILABLE',
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('P-02 was created successfully.')
  })

  it('shows duplicate conflicts without exposing backend details', async () => {
    const config = {} as InternalAxiosRequestConfig
    const response = { status: 409, data: {}, headers: {}, config } as AxiosResponse
    mockedCreate.mockRejectedValue(new AxiosError('Conflict', '409', config, undefined, response))
    renderPage()
    await screen.findByRole('heading', { name: 'Window table' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add table' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Table number'), 'T-01')
    await user.type(within(dialog).getByLabelText('Display name'), 'Duplicate')
    await user.clear(within(dialog).getByLabelText('Capacity'))
    await user.type(within(dialog).getByLabelText('Capacity'), '2')
    await user.type(within(dialog).getByLabelText('Section'), 'Main')
    await user.click(screen.getByRole('button', { name: 'Create table' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('conflicts with a newer record')
  })

  it('edits with the current version and deactivates after confirmation', async () => {
    mockedUpdate.mockResolvedValue({ ...table, displayName: 'Garden table', version: 1 })
    mockedActivation.mockResolvedValue({ ...table, active: false, version: 1 })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()
    await screen.findByRole('heading', { name: 'Window table' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByLabelText('Display name'))
    await user.type(screen.getByLabelText('Display name'), 'Garden table')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(1, expect.objectContaining({ version: 0 })),
    )

    await user.click(screen.getByRole('button', { name: 'Deactivate' }))
    expect(window.confirm).toHaveBeenCalled()
    await waitFor(() => expect(mockedActivation.mock.calls[0]?.[0]).toEqual(table))
    expect(await screen.findByRole('status')).toHaveTextContent('T-01 was deactivated.')
  })

  it('reactivates inactive records without a destructive confirmation', async () => {
    mockedList.mockResolvedValue([{ ...table, active: false }])
    mockedActivation.mockResolvedValue({ ...table, active: true, version: 1 })
    const confirm = vi.spyOn(window, 'confirm')
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Reactivate' }))

    await waitFor(() => expect(mockedActivation).toHaveBeenCalled())
    expect(confirm).not.toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('T-01 was reactivated.')
  })

  it('moves focus into the table dialog and restores it after Escape', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Window table' })
    const user = userEvent.setup()
    const trigger = screen.getByRole('button', { name: 'Add table' })

    await user.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Create a table' })).toHaveAttribute(
      'aria-modal',
      'true',
    )
    expect(screen.getByLabelText('Table number')).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
