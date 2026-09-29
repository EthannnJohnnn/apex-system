import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'

interface Settings { organizationName: string; meetingPresent: number; meetingLate: number; eventPresent: number; eventLate: number; version: number }
interface Term { id: string; name: string; startDate: string; endDate: string; status: 'DRAFT' | 'ACTIVE' | 'CLOSED'; version: number }
interface History extends Omit<Term, 'version'> { action: string; reason: string; changedBy: string; changedAt: string; termVersion: number }
type Editor = { term?: Term; correction: boolean }
const pointFields = [['meetingPresent', 'Meeting: present'], ['meetingLate', 'Meeting: late'], ['eventPresent', 'Event: present'], ['eventLate', 'Event: late']] as const

export function Organization({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [terms, setTerms] = useState<Term[]>([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [editor, setEditor] = useState<Editor | null>(null)
  const [confirmation, setConfirmation] = useState<{ term: Term; action: 'activate' | 'close' } | null>(null)
  const [history, setHistory] = useState<{ name: string; entries: History[] } | null>(null)
  const [formError, setFormError] = useState('')

  function failure(err: unknown) {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return err instanceof ApiError ? err.message : 'Cannot reach Apex. Your change was not confirmed. Reload before retrying.'
  }
  async function reload() {
    setError(''); setNotice(''); setBusy(true)
    try {
      const [s, t] = await Promise.all([apiRequest('/api/v1/settings').then(r => r.json()), apiRequest('/api/v1/terms').then(r => r.json())])
      setSettings(s); setTerms(t)
    } catch (err) { setError(failure(err)) }
    finally { setBusy(false) }
  }
  useEffect(() => {
    let live = true
    Promise.all([apiRequest('/api/v1/settings').then(r => r.json()), apiRequest('/api/v1/terms').then(r => r.json())])
      .then(([s, t]) => { if (live) { setSettings(s); setTerms(t) } })
      .catch((err: unknown) => {
        if (!live) return
        if (err instanceof ApiError && err.status === 401) onSessionExpired()
        else setError('Could not load organization settings. Check the backend and reload.')
      })
    return () => { live = false }
  }, [onSessionExpired])

  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!settings) return
    setBusy(true); setError(''); setNotice('')
    const data = new FormData(event.currentTarget)
    try {
      const body = { ...settings, organizationName: String(data.get('organizationName')) }
      for (const [key] of pointFields) body[key] = Number(data.get(key))
      setSettings(await (await apiRequest('/api/v1/settings', 'PUT', body)).json())
      setNotice('Organization settings saved.')
    } catch (err) { setError(failure(err)) }
    finally { setBusy(false) }
  }
  async function saveTerm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor) return
    const data = new FormData(event.currentTarget)
    setBusy(true); setFormError(''); setNotice('')
    try {
      const term = editor.term
      const url = '/api/v1/terms' + (term ? '/' + term.id + (editor.correction ? '/corrections' : '') : '')
      const saved: Term = await (await apiRequest(url, !term || editor.correction ? 'POST' : 'PUT', {
        name: data.get('name'), startDate: data.get('startDate'), endDate: data.get('endDate'),
        version: term?.version, reason: data.get('reason'),
      })).json()
      setTerms(old => term ? old.map(t => t.id === saved.id ? saved : t) : [saved, ...old])
      setEditor(null); setNotice(editor.correction ? 'Correction saved. The term remains closed.' : 'Term saved.')
    } catch (err) { setFormError(failure(err)) }
    finally { setBusy(false) }
  }
  async function transition() {
    if (!confirmation) return
    setBusy(true); setFormError(''); setNotice('')
    try {
      const { term, action } = confirmation
      const saved: Term = await (await apiRequest(`/api/v1/terms/${term.id}/${action}`, 'POST', { version: term.version })).json()
      setTerms(old => old.map(t => t.id === saved.id ? saved : t))
      setConfirmation(null); setNotice(action === 'activate' ? 'Term activated. Previous terms are preserved.' : 'Term closed and preserved in history.')
    } catch (err) { setFormError(failure(err)) }
    finally { setBusy(false) }
  }
  async function showHistory(term: Term) {
    setBusy(true); setError('')
    try { setHistory({ name: term.name, entries: await (await apiRequest(`/api/v1/terms/${term.id}/history`)).json() }) }
    catch (err) { setError(failure(err)) }
    finally { setBusy(false) }
  }
  const active = terms.find(t => t.status === 'ACTIVE')
  return <Stack spacing={2.5}>
    <Divider />
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography component="h2" variant="h5">Organization & terms</Typography>
      <Button onClick={reload} disabled={busy}>Reload settings & terms</Button>
    </Stack>
    {error && <Alert severity="error">{error}</Alert>}
    {notice && <Alert severity="success">{notice}</Alert>}
    {settings ? <Box component="form" key={settings.version} onSubmit={saveSettings}>
      <Stack spacing={2}>
        <TextField label="Organization name" name="organizationName" defaultValue={settings.organizationName} required slotProps={{ htmlInput: { maxLength: 150 } }} />
        <Typography variant="body2" color="text.secondary">Default points for new activities. Existing activities retain their saved values. Excused and absent remain 0. Manual awards and term totals are available on the Point ledger page.</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
          {pointFields.map(([key, label]) => <TextField key={key} label={label} name={key} type="number" defaultValue={settings[key]} required slotProps={{ htmlInput: { min: 0, max: 1000, step: 1 } }} />)}
        </Box>
        <Button type="submit" variant="outlined" disabled={busy}>Save organization settings</Button>
      </Stack>
    </Box> : !error && <Typography role="status">Loading settings…</Typography>}
    <Divider />
    <Typography variant="h6">Academic terms</Typography>
    <Alert severity="info">{active ? `Active term: ${active.name}` : 'No active term. Create a term and activate it when ready.'} Only one term can be active. Close it before activating another.</Alert>
    <Button variant="contained" disabled={busy || !settings} onClick={() => { setFormError(''); setEditor({ correction: false }) }}>Create term</Button>
    {settings && terms.length === 0 && <Typography>No academic terms yet.</Typography>}
    {terms.map(term => <Stack key={term.id} spacing={1.5} sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 2 }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}><Typography variant="h6">{term.name}</Typography><Chip label={term.status} color={term.status === 'ACTIVE' ? 'primary' : 'default'} size="small" /></Stack>
      <Typography variant="body2">{term.startDate} — {term.endDate}</Typography>
      <Stack direction="row" useFlexGap sx={{ flexWrap: 'wrap' }} spacing={1}>
        <Button disabled={busy} onClick={() => { setFormError(''); setEditor({ term, correction: term.status === 'CLOSED' }) }}>{term.status === 'CLOSED' ? 'Correct closed term' : 'Edit term'}</Button>
        <Button disabled={busy} onClick={() => showHistory(term)}>Term history</Button>
        {term.status !== 'CLOSED' && <Button disabled={busy || (term.status === 'DRAFT' && !!active)} onClick={() => { setFormError(''); setConfirmation({ term, action: term.status === 'ACTIVE' ? 'close' : 'activate' }) }}>{term.status === 'ACTIVE' ? 'Close term' : 'Activate term'}</Button>}
      </Stack>
    </Stack>)}
    <Dialog open={!!editor} onClose={() => { if (!busy) setEditor(null) }} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={saveTerm}>
        <DialogTitle>{editor?.correction ? 'Correct closed term' : editor?.term ? 'Edit term' : 'Create term'}</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
          {formError && <Alert severity="error">{formError}</Alert>}
          {editor?.correction && <Alert severity="info">Only the term details change. The term stays closed; this correction and its reason are recorded in history.</Alert>}
          <TextField label="Term name" name="name" defaultValue={editor?.term?.name ?? ''} required slotProps={{ htmlInput: { maxLength: 100 } }} />
          <TextField label="Start date" name="startDate" type="date" defaultValue={editor?.term?.startDate ?? ''} required slotProps={{ inputLabel: { shrink: true } }} />
          <TextField label="End date" name="endDate" type="date" defaultValue={editor?.term?.endDate ?? ''} required slotProps={{ inputLabel: { shrink: true } }} />
          {editor?.correction && <TextField label="Correction reason" name="reason" required multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} />}
        </Stack></DialogContent>
        <DialogActions><Button disabled={busy} onClick={() => setEditor(null)}>Cancel</Button><Button type="submit" disabled={busy} variant="contained">Save term</Button></DialogActions>
      </Box>
    </Dialog>
    <Dialog open={!!confirmation} onClose={() => { if (!busy) setConfirmation(null) }}>
      <DialogTitle>{confirmation?.action === 'close' ? 'Close term?' : 'Activate term?'}</DialogTitle>
      <DialogContent><Stack spacing={2}>{formError && <Alert severity="error">{formError}</Alert>}<Typography>{confirmation?.term.name}</Typography><Typography>{confirmation?.action === 'close' ? 'This locks ordinary edits. Any attendance drafts must be completed or cancelled first. Closed terms cannot be reopened; corrections require a reason.' : 'This becomes the active academic term. No previous records will be deleted.'}</Typography></Stack></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setConfirmation(null)}>Cancel</Button><Button disabled={busy} onClick={transition} variant="contained">Confirm</Button></DialogActions>
    </Dialog>
    <Dialog open={!!history} onClose={() => setHistory(null)} fullWidth maxWidth="sm">
      <DialogTitle>Term history: {history?.name}</DialogTitle>
      <DialogContent><Stack spacing={2}>{history?.entries.map(entry => <Box key={entry.id} sx={{ borderBottom: 1, borderColor: 'divider', pb: 2 }}>
        <Typography sx={{ fontWeight: 700 }}>{entry.action} · {entry.status}</Typography>
        <Typography>{entry.name}: {entry.startDate} — {entry.endDate}</Typography>
        <Typography>{entry.reason}</Typography>
        <Typography variant="body2" color="text.secondary">{entry.changedBy} · {new Date(entry.changedAt).toLocaleString()} · version {entry.termVersion}</Typography>
      </Box>)}</Stack></DialogContent>
      <DialogActions><Button onClick={() => setHistory(null)}>Close</Button></DialogActions>
    </Dialog>
  </Stack>
}
