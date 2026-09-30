import assert from 'node:assert/strict'
import { afterEach, mock, test } from 'node:test'
import { ApiError, apiRequest } from '../src/api/auth.ts'

afterEach(() => mock.restoreAll())
const token = () => Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fictional-token' })

test('offline before CSRF rejects without sending a write', async () => {
  const fetch = mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch') })
  await assert.rejects(apiRequest('/api/v1/members', 'POST', { name: 'Fictional' }), TypeError)
  assert.equal(fetch.mock.callCount(), 1)
  assert.equal(fetch.mock.calls[0].arguments[0], '/api/v1/auth/csrf')
})

test('lost write response is an unknown outcome, never a successful save or automatic retry', async () => {
  const fetch = mock.method(globalThis, 'fetch', async (path) => {
    if (path === '/api/v1/auth/csrf') return token()
    throw new TypeError('Connection lost')
  })
  await assert.rejects(apiRequest('/api/v1/points', 'POST', { requestId: 'fictional-id' }), TypeError)
  assert.equal(fetch.mock.callCount(), 2)
})

test('stale edits preserve the server conflict message', async () => {
  mock.method(globalThis, 'fetch', async (path) => path === '/api/v1/auth/csrf' ? token()
    : Response.json({ message: 'This record changed. Reload before editing.' }, { status: 409 }))
  await assert.rejects(apiRequest('/api/v1/members/fictional', 'PUT', { version: 0 }),
    error => error instanceof ApiError && error.status === 409 && error.message.includes('Reload'))
})

test('expired sessions and non-JSON proxy errors reject instead of reporting success', async () => {
  for (const status of [401, 403, 500, 502]) {
    mock.method(globalThis, 'fetch', async () => new Response('Backend unavailable', { status }))
    await assert.rejects(apiRequest('/api/v1/dashboard'), error => error instanceof ApiError && error.status === status)
    mock.restoreAll()
  }
})

test('successful writes send CSRF, session credentials and exactly one JSON mutation', async () => {
  const fetch = mock.method(globalThis, 'fetch', async (path) => path === '/api/v1/auth/csrf' ? token()
    : Response.json({ id: 'fictional-member' }, { status: 201 }))
  const result = await apiRequest('/api/v1/members', 'POST', { name: 'Fictional' })
  assert.equal(result.status, 201)
  assert.equal(fetch.mock.callCount(), 2)
  const [, options] = fetch.mock.calls[1].arguments
  assert.equal(options.credentials, 'same-origin')
  assert.equal(options.headers['X-CSRF-TOKEN'], 'fictional-token')
  assert.equal(options.body, JSON.stringify({ name: 'Fictional' }))
})
