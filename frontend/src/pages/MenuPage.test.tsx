import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as menuApi from '../features/menu/menuApi'
import type { MenuCategory, MenuItem, ModifierGroup } from '../features/menu/menuTypes'
import { MenuPage } from './MenuPage'

vi.mock('../features/menu/menuApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/menu/menuApi')>()
  return {
    ...original,
    listCategories: vi.fn(),
    listItems: vi.fn(),
    listGroups: vi.fn(),
    createCategory: vi.fn(),
    createItem: vi.fn(),
    createGroup: vi.fn(),
    createOption: vi.fn(),
    updateCategory: vi.fn(),
    updateItem: vi.fn(),
    updateGroup: vi.fn(),
    updateOption: vi.fn(),
    toggleCategory: vi.fn(),
    toggleItem: vi.fn(),
    toggleAvailability: vi.fn(),
    toggleGroup: vi.fn(),
    toggleOption: vi.fn(),
    assignGroups: vi.fn(),
  }
})

const category: MenuCategory = {
  id: 1,
  name: 'Drinks',
  description: 'Cold and hot drinks',
  displayOrder: 0,
  active: true,
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}
const item: MenuItem = {
  id: 2,
  category: { id: 1, name: 'Drinks', active: true },
  code: 'LATTE',
  name: 'Latte',
  description: 'Espresso and milk',
  basePrice: '4.20',
  displayOrder: 0,
  active: true,
  availableForSale: true,
  effectivelyAvailable: true,
  modifierGroups: [],
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}
const group: ModifierGroup = {
  id: 3,
  name: 'Milk choice',
  description: null,
  selectionType: 'SINGLE',
  minimumSelections: 1,
  maximumSelections: 1,
  displayOrder: 0,
  active: true,
  assignedItemCount: 1,
  options: [
    {
      id: 4,
      modifierGroupId: 3,
      name: 'Oat milk',
      priceAdjustment: '0.80',
      displayOrder: 0,
      active: true,
      createdAt: '2026-08-05T00:00:00Z',
      updatedAt: '2026-08-05T00:00:00Z',
      version: 0,
    },
  ],
  createdAt: '2026-08-05T00:00:00Z',
  updatedAt: '2026-08-05T00:00:00Z',
  version: 0,
}

function renderPage() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <MenuPage />
    </QueryClientProvider>,
  )
}

describe('menu page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(menuApi.listCategories).mockResolvedValue([category])
    vi.mocked(menuApi.listItems).mockResolvedValue([item])
    vi.mocked(menuApi.listGroups).mockResolvedValue([group])
  })

  it('shows category loading, content, empty, and safe error states', async () => {
    vi.mocked(menuApi.listCategories).mockReturnValueOnce(new Promise(() => undefined))
    const loading = render(
      <QueryClientProvider client={new QueryClient()}>
        <MenuPage />
      </QueryClientProvider>,
    )
    expect(screen.getByText('Loading categories…')).toBeInTheDocument()
    loading.unmount()

    vi.mocked(menuApi.listCategories).mockResolvedValueOnce([])
    renderPage()
    expect(
      await screen.findByRole('heading', { name: 'No categories match these filters.' }),
    ).toBeInTheDocument()

    vi.mocked(menuApi.listCategories).mockRejectedValueOnce(new Error('unavailable'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('categories could not be loaded')

    renderPage()
    expect(await screen.findByRole('heading', { name: 'Drinks' })).toBeInTheDocument()
  })

  it('creates a category through React Hook Form validation and invalidates menu queries', async () => {
    vi.mocked(menuApi.createCategory).mockResolvedValue({ ...category, id: 5, name: 'Desserts' })
    renderPage()
    await screen.findByRole('heading', { name: 'Drinks' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Add category' }))
    await user.click(screen.getByRole('button', { name: 'Create category' }))
    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Name'), 'Desserts')
    await user.click(screen.getByRole('button', { name: 'Create category' }))
    await waitFor(() =>
      expect(menuApi.createCategory).toHaveBeenCalledWith({
        name: 'Desserts',
        description: '',
        displayOrder: 0,
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('Desserts was saved.')
  })

  it('filters and renders menu items with decimal money and effective availability', async () => {
    renderPage()
    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: 'Menu items' }))
    expect(await screen.findByRole('heading', { name: 'Latte' })).toBeInTheDocument()
    expect(screen.getByText('€4.20')).toBeInTheDocument()
    expect(screen.getByText('Sellable')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Search items'), 'latte')
    await user.selectOptions(screen.getByLabelText('Category'), '1')
    await user.selectOptions(screen.getByLabelText('Sale availability'), 'true')
    await user.selectOptions(screen.getByLabelText('Sort by'), 'basePrice')
    await waitFor(() =>
      expect(menuApi.listItems).toHaveBeenLastCalledWith(
        expect.objectContaining({
          search: 'latte',
          categoryId: 1,
          availableForSale: true,
          sortBy: 'basePrice',
        }),
      ),
    )
  })

  it('validates modifier rules and supports ordered item assignments', async () => {
    vi.mocked(menuApi.assignGroups).mockResolvedValue({
      ...item,
      modifierGroups: [
        {
          modifierGroupId: 3,
          name: 'Milk choice',
          selectionType: 'SINGLE',
          minimumSelections: 1,
          maximumSelections: 1,
          displayOrder: 0,
          active: true,
        },
      ],
    })
    renderPage()
    const user = userEvent.setup()
    await user.click(screen.getByRole('tab', { name: 'Modifiers' }))
    expect(await screen.findByRole('heading', { name: 'Milk choice' })).toBeInTheDocument()
    expect(screen.getByText(/€0\.80/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Add group' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Name'), 'Invalid single')
    await user.clear(within(dialog).getByLabelText('Maximum selections'))
    await user.type(within(dialog).getByLabelText('Maximum selections'), '2')
    await user.click(within(dialog).getByRole('button', { name: 'Create group' }))
    expect(
      await within(dialog).findByText('Single selection requires a maximum of 1'),
    ).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Close menu form' }))
    await user.click(screen.getByRole('tab', { name: 'Menu items' }))
    await user.click(await screen.findByRole('button', { name: 'Modifiers' }))
    const assignments = screen.getByRole('dialog')
    await user.selectOptions(within(assignments).getByLabelText('Add active group'), '3')
    await user.click(within(assignments).getByRole('button', { name: 'Save assignments' }))
    await waitFor(() => expect(menuApi.assignGroups).toHaveBeenCalledWith(item, [3]))
  })
})
