import { useEffect, useState } from 'react'
import { Alert, Box, Button, CircularProgress, Stack, Typography } from '@mui/material'
import { getHealth, type HealthResponse } from '../api/health'

type ConnectionState =
  | { status: 'loading' }
  | { status: 'connected'; health: HealthResponse }
  | { status: 'error' }

export function ConnectionStatus() {
  const [attempt, setAttempt] = useState(0)
  const [connection, setConnection] = useState<ConnectionState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()

    getHealth(controller.signal)
      .then((health) => setConnection({ status: 'connected', health }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }

        setConnection({ status: 'error' })
      })

    return () => controller.abort()
  }, [attempt])

  const retry = () => {
    setConnection({ status: 'loading' })
    setAttempt((value) => value + 1)
  }

  if (connection.status === 'loading') {
    return (
      <Stack direction="row" spacing={1.5} role="status" sx={{ py: 1, alignItems: 'center' }}>
        <CircularProgress size={18} thickness={5} />
        <Typography variant="body2">Connecting to Apex...</Typography>
      </Stack>
    )
  }

  if (connection.status === 'error') {
    return (
      <Alert
        severity="error"
        action={(
          <Button color="inherit" size="small" onClick={retry}>
            Retry
          </Button>
        )}
        sx={{ alignItems: 'center' }}
      >
        <Typography variant="body2" sx={{ fontWeight: 700 }}>Apex is not connected</Typography>
        <Typography variant="caption">Start the backend, then try again.</Typography>
      </Alert>
    )
  }

  const { health } = connection

  return (
    <Alert severity="success" sx={{ alignItems: 'center' }}>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>{health.application} connected</Typography>
      <Box component="span" sx={{ display: 'block' }}>
        <Typography component="span" variant="caption">
          Application {health.status} · Database {health.database} · Schema v{health.schemaVersion}
        </Typography>
      </Box>
    </Alert>
  )
}
