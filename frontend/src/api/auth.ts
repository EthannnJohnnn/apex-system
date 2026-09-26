export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export interface President { username: string; role: string }

export async function authRequest(path: string, body?: URLSearchParams | object): Promise<Response> {
  return apiRequest('/api/v1/auth/' + path, body === undefined ? 'GET' : 'POST', body)
}

export async function apiRequest(path: string, method = 'GET', body?: URLSearchParams | object): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    const tokenResponse = await fetch('/api/v1/auth/csrf', { cache: 'no-store' })
    if (!tokenResponse.ok) throw new ApiError(tokenResponse.status, 'Please refresh and try again.')
    const csrf = await tokenResponse.json()
    headers[csrf.headerName] = csrf.token
    headers['Content-Type'] = body instanceof URLSearchParams
      ? 'application/x-www-form-urlencoded' : 'application/json'
  }
  const response = await fetch(path, {
    method,
    credentials: 'same-origin',
    cache: 'no-store',
    headers,
    body: body === undefined ? undefined
      : body instanceof URLSearchParams ? body : JSON.stringify(body),
  })
  if (!response.ok) {
    const data = await response.json().catch(() => null)
    throw new ApiError(response.status, data?.message ?? (
      response.status === 403 ? 'Security check expired. Please try again.' : 'Request failed. Please try again.'))
  }
  return response
}
