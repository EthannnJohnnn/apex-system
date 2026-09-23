export interface HealthResponse {
  status: string
  application: string
  database: string
  schemaVersion: number
}

function isHealthResponse(value: unknown): value is HealthResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const health = value as Record<string, unknown>

  return typeof health.status === 'string'
    && typeof health.application === 'string'
    && typeof health.database === 'string'
    && typeof health.schemaVersion === 'number'
}

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch('/api/v1/health', {
    headers: { Accept: 'application/json' },
    signal,
  })

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`)
  }

  const data: unknown = await response.json()

  if (!isHealthResponse(data)) {
    throw new Error('The backend returned an unexpected health response')
  }

  return data
}
