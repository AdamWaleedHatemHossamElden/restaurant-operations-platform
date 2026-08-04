import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'

import { safePostLoginTarget } from '../features/auth/authNavigation'
import { useAuth } from '../features/auth/authContext'
import { loginSchema, type LoginFormValues } from '../features/auth/loginSchema'

const GENERIC_LOGIN_ERROR = 'Unable to sign in. Check your credentials and try again.'

export function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [authenticationError, setAuthenticationError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const submit = handleSubmit(async (values) => {
    setAuthenticationError(null)
    try {
      await auth.login(values)
      navigate(safePostLoginTarget(location.state), { replace: true })
    } catch {
      setAuthenticationError(GENERIC_LOGIN_ERROR)
    }
  })

  return (
    <main className="login-page">
      <section className="login-intro" aria-labelledby="login-title">
        <a className="brand brand--login" href="/login" aria-label="Restaurant Operations login">
          <span className="brand-mark" aria-hidden="true">
            RO
          </span>
          <span>Restaurant Operations</span>
        </a>
        <div>
          <p className="eyebrow">Secure operations access</p>
          <h1 id="login-title">Welcome back to service.</h1>
          <p>
            Sign in to reach your restaurant operations workspace. Your session is protected with
            short-lived access and securely rotated credentials.
          </p>
        </div>
        <p className="login-intro__note">Built for focused teams and dependable shifts.</p>
      </section>

      <section className="login-panel" aria-label="Sign in">
        <div className="login-card">
          <div className="login-card__heading">
            <p className="eyebrow">Account access</p>
            <h2>Sign in</h2>
            <p>Use the credentials provided by your administrator.</p>
          </div>

          {authenticationError && (
            <div className="form-alert" role="alert">
              {authenticationError}
            </div>
          )}

          <form className="login-form" onSubmit={submit} noValidate>
            <div className="form-field">
              <label htmlFor="email">Email address</label>
              <input
                id="email"
                type="email"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck="false"
                aria-invalid={errors.email ? 'true' : 'false'}
                aria-describedby={errors.email ? 'email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p className="field-error" id="email-error">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="form-field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                aria-invalid={errors.password ? 'true' : 'false'}
                aria-describedby={errors.password ? 'password-error' : undefined}
                {...register('password')}
              />
              {errors.password && (
                <p className="field-error" id="password-error">
                  {errors.password.message}
                </p>
              )}
            </div>

            <button
              className="button button--primary login-submit"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Signing in…' : 'Sign in securely'}
            </button>
          </form>

          <p className="login-card__security">
            Access tokens remain only in this page&rsquo;s memory.
          </p>
        </div>
      </section>
    </main>
  )
}
