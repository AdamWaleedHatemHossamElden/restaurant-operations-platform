type RedirectState = {
  from?: {
    pathname?: string
    search?: string
    hash?: string
  }
}

export function safePostLoginTarget(state: unknown) {
  const redirectState = state as RedirectState | null
  const pathname = redirectState?.from?.pathname
  if (
    !pathname ||
    !pathname.startsWith('/') ||
    pathname.startsWith('//') ||
    pathname === '/login'
  ) {
    return '/dashboard'
  }
  return `${pathname}${redirectState?.from?.search ?? ''}${redirectState?.from?.hash ?? ''}`
}
