import { z } from 'zod'

export const currentUserSchema = z.object({
  id: z.number().int().positive(),
  email: z.string().email(),
  displayName: z.string().min(1),
  enabled: z.boolean(),
  roles: z.array(z.string().min(1)),
})

export const authSessionSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresIn: z.number().int().positive(),
  user: currentUserSchema,
})

export type CurrentUser = z.infer<typeof currentUserSchema>
export type AuthSession = z.infer<typeof authSessionSchema>

export type LoginCredentials = {
  email: string
  password: string
}
