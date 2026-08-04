import axios from 'axios'

import { apiClient } from '../../lib/apiClient'
import {
  restaurantTableSchema,
  restaurantTablesSchema,
  type RestaurantTable,
  type TableFilters,
  type TableWriteRequest,
} from './tableTypes'

export async function listTables(filters: TableFilters): Promise<RestaurantTable[]> {
  const response = await apiClient.get<unknown>('/tables', { params: filters })
  return restaurantTablesSchema.parse(response.data)
}

export async function createTable(request: TableWriteRequest): Promise<RestaurantTable> {
  const response = await apiClient.post<unknown>('/tables', request)
  return restaurantTableSchema.parse(response.data)
}

export async function updateTable(
  id: number,
  request: TableWriteRequest & { version: number },
): Promise<RestaurantTable> {
  const response = await apiClient.put<unknown>(`/tables/${id}`, request)
  return restaurantTableSchema.parse(response.data)
}

export async function setTableActivation(
  table: Pick<RestaurantTable, 'id' | 'active' | 'version'>,
): Promise<RestaurantTable> {
  const response = await apiClient.patch<unknown>(`/tables/${table.id}/activation`, {
    active: !table.active,
    version: table.version,
  })
  return restaurantTableSchema.parse(response.data)
}

export function tableRequestError(error: unknown): string {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    return 'That table number or version conflicts with a newer record. Refresh and try again.'
  }
  return 'The table request could not be completed. Please try again.'
}
