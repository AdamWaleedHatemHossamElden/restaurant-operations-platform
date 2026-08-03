import { z } from 'zod'

import { apiClient } from '../../lib/apiClient'

const healthResponseSchema = z.object({
  status: z.literal('UP'),
  service: z.string().min(1),
})

export type HealthResponse = z.infer<typeof healthResponseSchema>

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await apiClient.get<unknown>('/health')
  return healthResponseSchema.parse(response.data)
}
