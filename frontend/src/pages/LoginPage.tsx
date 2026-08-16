import { zodResolver } from '@hookform/resolvers/zod'
import { ChefHat, CheckCircle2, LockKeyhole, ShieldCheck } from 'lucide-react'
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
            <ChefHat size={24} />
          </span>
          <span className="brand-copy">
            <strong>Ember</strong>
            <small>Restaurant operations</small>
          </span>
        </a>
        <div className="login-intro__message">
          <span className="login-kicker">
            <ShieldCheck size={16} /> Secure team access
          </span>
          <h1 id="login-title">Run every service with clarity.</h1>
          <p>
            One focused workspace for the dining room, kitchen, inventory, staff, payments, and the
            decisions that keep your restaurant moving.
          </p>
        </div>
        <div className="login-proof">
          <span>
            <CheckCircle2 size={16} /> Live operational visibility
          </span>
          <span>
            <CheckCircle2 size={16} /> Secure, role-protected access
          </span>
          <span>
            <CheckCircle2 size={16} /> Authoritative business records
          </span>
        </div>
        <p className="login-intro__note">Built for focused teams and dependable service.</p>
      </section>

      <section className="login-panel" aria-label="Sign in">
        <div className="login-card">
          <span className="login-card__icon" aria-hidden="true">
            <LockKeyhole size={22} />
          </span>
          <div className="login-card__heading">
            <p className="eyebrow">Welcome back</p>
            <h2>Sign in</h2>
            <p>Enter your administrator credentials to continue.</p>
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
                placeholder="you@restaurant.com"
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
                placeholder="Enter your password"
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
            <ShieldCheck size={15} /> Secure, memory-only session
          </p>
        </div>
      </section>
    </main>
  )
}
