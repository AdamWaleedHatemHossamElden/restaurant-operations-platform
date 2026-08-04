import { z } from 'zod'

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email address')
    .email('Enter a valid email address')
    .max(320, 'Email address is too long'),
  password: z.string().min(1, 'Enter your password').max(1024, 'Password is too long'),
})

export type LoginFormValues = z.infer<typeof loginSchema>
