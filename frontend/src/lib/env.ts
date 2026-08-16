import { z } from 'zod'

const environmentSchema = z.object({
  VITE_API_BASE_URL: z.string().url('VITE_API_BASE_URL must be a valid URL'),
})

const parsedEnvironment = environmentSchema.safeParse({
  VITE_API_BASE_URL:
    import.meta.env.VITE_API_BASE_URL ??
    (import.meta.env.DEV ? 'http://localhost:8080/api/v1' : undefined),
})

if (!parsedEnvironment.success) {
  const details = parsedEnvironment.error.issues.map((issue) => issue.message).join('; ')
  throw new Error(`Invalid frontend environment configuration: ${details}`)
}

export const env = parsedEnvironment.data
