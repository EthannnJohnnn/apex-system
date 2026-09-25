import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, CircularProgress, Stack, TextField, Typography } from '@mui/material'
import { ApiError, authRequest, type President } from '../api/auth'
import { ConnectionStatus } from './ConnectionStatus'

export function PresidentAccess() {
  const [president, setPresident] = useState<President | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [notice, setNotice] = useState('')
  const [changing, setChanging] = useState(false)

  useEffect(() => {
    let active = true
    authRequest('me').then((response) => response.json()).then((user: President) => {
      if (active) setPresident(user)
    }).catch((error: unknown) => {
      if (active && !(error instanceof ApiError && error.status === 401))
        setMessage('Cannot reach Apex. Start the backend, then refresh this page.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  // Check on return to the tab, rather than polling and extending an idle session.
  useEffect(() => {
    if (!president) return
    const check = () => {
      if (document.visibilityState !== 'visible') return
      authRequest('me').catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) {
          setPresident(null)
          setChanging(false)
          setNotice('Your session expired. Please sign in again.')
        }
      })
    }
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', check)
    return () => {
      window.removeEventListener('focus', check)
      document.removeEventListener('visibilitychange', check)
    }
  }, [president])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    setBusy(true)
    setMessage('')
    setNotice('')
    try {
      if (president) {
        if (values.get('newPassword') !== values.get('confirmation')) {
          setMessage('New passwords do not match.')
          return
        }
        await authRequest('password', {
          currentPassword: values.get('currentPassword'),
          newPassword: values.get('newPassword'),
        })
        setPresident(null)
        setChanging(false)
        setNotice('Password changed. Sign in with your new password.')
      } else {
        await authRequest('login', new URLSearchParams({
          username: String(values.get('username')),
          password: String(values.get('password')),
        }))
        const response = await authRequest('me')
        setPresident(await response.json())
      }
      form.reset()
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setMessage(president ? 'Your session expired. Please sign in again.' : 'Incorrect username or password.')
        setPresident(null)
        setChanging(false)
      } else {
        setMessage(error instanceof ApiError ? error.message : 'Cannot reach Apex. Check that the backend is running.')
      }
    } finally { setBusy(false) }
  }

  async function logout() {
    setBusy(true)
    setMessage('')
    try {
      await authRequest('logout', {})
      setPresident(null)
      setChanging(false)
      setNotice('You have signed out.')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setPresident(null)
        setNotice('Your session expired. Please sign in again.')
      } else setMessage('Could not sign out. Check the connection and try again.')
    } finally { setBusy(false) }
  }

  if (loading) return <Box role="status"><CircularProgress size={24} /> Checking your session…</Box>

  return <Stack spacing={2.5}>
    <Box>
      <Typography variant="overline" color="primary">President workspace</Typography>
      <Typography component="h2" variant="h5" sx={{ fontWeight: 700 }}>
        {president ? 'Welcome, ' + president.username : 'Sign in to Apex'}
      </Typography>
    </Box>
    {notice && <Alert severity="info">{notice}</Alert>}
    {message && <Alert severity="error">{message}</Alert>}
    {(!president || changing) && <Box component="form" onSubmit={submit}>
      <Stack spacing={2}>
        {president ? <>
          <TextField label="Current password" name="currentPassword" type="password" autoComplete="current-password" required fullWidth />
          <TextField label="New password" name="newPassword" type="password" autoComplete="new-password" required fullWidth
            helperText="At least 12 characters; at most 72 UTF-8 bytes." slotProps={{ htmlInput: { minLength: 12 } }} />
          <TextField label="Confirm new password" name="confirmation" type="password" autoComplete="new-password" required fullWidth />
        </> : <>
          <TextField label="Username" name="username" autoComplete="username" required fullWidth />
          <TextField label="Password" name="password" type="password" autoComplete="current-password" required fullWidth />
        </>}
        <Button type="submit" variant="contained" disabled={busy}>
          {busy ? 'Please wait…' : president ? 'Save new password' : 'Sign in'}
        </Button>
      </Stack>
    </Box>}
    {president ? <>
      <Typography color="text.secondary">You are signed in. Member management is coming in Stage 9.</Typography>
      <Stack direction="row" spacing={1}>
        <Button disabled={busy} onClick={() => setChanging(!changing)}>{changing ? 'Cancel' : 'Change password'}</Button>
        <Button disabled={busy} onClick={logout}>Sign out</Button>
      </Stack>
      <ConnectionStatus />
    </> : <Typography variant="body2" color="text.secondary">
      Forgot your password? Use the account recovery command on the Apex laptop.
    </Typography>}
  </Stack>
}
