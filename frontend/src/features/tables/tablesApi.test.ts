import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { apiClient, setApiAccessToken } from '../../lib/apiClient'
import { createTable, listTables, setTableActivation, updateTable } from './tablesApi'
import type { RestaurantTable } from './tableTypes'

const originalAdapter = apiClient.defaults.adapter
const table: RestaurantTable = {
  id: 1,
  tableNumber: 'T-01',
  displayName: 'Window',
  capacity: 4,
  section: 'Main',
  status: 'AVAILABLE',
  active: true,
  createdAt: '2026-08-04T10:00:00Z',
  updatedAt: '2026-08-04T10:00:00Z',
  version: 0,
}

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

describe('table API', () => {
  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
  })

  it('uses the protected client for filters and every table write', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    setApiAccessToken('memory-only-token')
    apiClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config, config.method === 'get' ? [table] : table)
    }

    await listTables({ active: true, tableNumber: 'T-0' })
    await createTable(table)
    await updateTable(table.id, { ...table, version: table.version })
    await setTableActivation(table)

    expect(requests.map(({ method, url }) => [method, url])).toEqual([
      ['get', '/tables'],
      ['post', '/tables'],
      ['put', '/tables/1'],
      ['patch', '/tables/1/activation'],
    ])
    expect(
      requests.every(
        (request) => request.headers.get('Authorization') === 'Bearer memory-only-token',
      ),
    ).toBe(true)
    expect(requests[0].params).toEqual({ active: true, tableNumber: 'T-0' })
    expect(JSON.parse(requests[3].data as string)).toEqual({ active: false, version: 0 })
  })
})
