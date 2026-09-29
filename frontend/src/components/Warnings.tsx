import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'
import type { Activity } from './Activities'

interface Term { id: string; name: string; status: string }
interface Member { id: string; name: string; memberCode: string; active: boolean; eligible: boolean }
interface Warning { id: string; termId: string; memberId: string; memberName: string; incident: string; severity: 'MINOR' | 'MAJOR' | null; activityId: string | null; assignedRole: string; deduction: number; reason: string; status: string; version: number; createdAt: string }
interface View { warning: Warning; history: { action: string; reason: string; actor: string; recordedAt: string; version: number }[] }
interface Pending { path: string; body: object }
type Action = { kind: 'create' } | { kind: 'resolve' | 'cancel'; warning: Warning }

export function Warnings({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [terms, setTerms] = useState<Term[]>([])
  const [termId, setTermId] = useState('')
  const [members, setMembers] = useState<Member[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [records, setRecords] = useState<Warning[]>([])
  const [view, setView] = useState<View | null>(null)
  const [filter, setFilter] = useState('')
  const [status, setStatus] = useState('ALL')
  const [action, setAction] = useState<Action | null>(null)
  const [memberId, setMemberId] = useState('')
  const [activityId, setActivityId] = useState('')
  const [roleNoShow, setRoleNoShow] = useState(false)
  const [severity, setSeverity] = useState('MINOR')
  const [deduct, setDeduct] = useState(false)
  const [pending, setPending] = useState<Pending | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const term = terms.find(t => t.id === termId)
  const activity = activities.find(a => a.id === activityId)
  const fixedDeduction = activity?.kind === 'MEETING' ? 3 : 5
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return err instanceof ApiError ? err.message : 'Connection interrupted. Retry the same request, or reload and check history before starting another.'
  }, [onSessionExpired])
  const load = useCallback(async (id: string) => {
    const [r, a, m] = await Promise.all([
      apiRequest('/api/v1/warnings?termId=' + id).then(r => r.json()) as Promise<Warning[]>,
      apiRequest('/api/v1/activities?termId=' + id).then(r => r.json()) as Promise<Activity[]>,
      apiRequest('/api/v1/members').then(r => r.json()) as Promise<Member[]>,
    ])
    return { records: r, activities: a, members: m }
  }, [])
  useEffect(() => {
    let live = true
    apiRequest('/api/v1/terms').then(r => r.json()).then((data: Term[]) => {
      if (live) { setTerms(data); setTermId(data.find(t => t.status === 'ACTIVE')?.id ?? data[0]?.id ?? ''); if (!data.length) setLoading(false) }
    }).catch(err => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [report])
  useEffect(() => {
    if (!termId) return
    let live = true
    load(termId).then(data => { if (live) { setRecords(data.records); setActivities(data.activities); setMembers(data.members); setLoading(false) } })
      .catch(err => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [termId, load, report])
  async function reload() {
    setBusy(true); setError('')
    try {
      const latest: Term[] = await (await apiRequest('/api/v1/terms')).json()
      setTerms(latest)
      if (termId) { const data = await load(termId); setRecords(data.records); setActivities(data.activities); setMembers(data.members) }
    } catch (err) { setError(report(err)) } finally { setBusy(false) }
  }
  async function history(id: string) {
    setBusy(true); setError('')
    try { setView(await (await apiRequest('/api/v1/warnings/' + id)).json()) } catch (err) { setError(report(err)) } finally { setBusy(false) }
  }
  function open(next: Action) {
    setAction(next); setPending(null); setFormError(''); setNotice(''); setSeverity('MINOR'); setDeduct(false); setRoleNoShow(false); setActivityId(''); setMemberId('')
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!action || !term) return
    const values = new FormData(event.currentTarget)
    const reason = String(values.get('reason') ?? '')
    const request: Pending = pending ?? (action.kind === 'create' ? {
      path: '/api/v1/warnings', body: { requestId: crypto.randomUUID(), termId, memberId, incident: String(values.get('incident') ?? ''), severity: severity === 'NONE' ? null : severity,
        activityId: roleNoShow ? activityId : null, assignedRole: roleNoShow ? String(values.get('assignedRole') ?? '') : '',
        deduction: deduct ? (roleNoShow ? fixedDeduction : Number(values.get('deduction'))) : 0, reason },
    } : { path: `/api/v1/warnings/${action.warning.id}/${action.kind === 'cancel' && term.status === 'CLOSED' ? 'closed-cancel' : action.kind}`,
      body: { requestId: crypto.randomUUID(), version: action.warning.version, reason } })
    setPending(request); setBusy(true); setFormError('')
    try {
      const saved: View = await (await apiRequest(request.path, 'POST', request.body)).json()
      setAction(null); setPending(null); setView(saved); setNotice('Incident saved. Its history and any linked points are updated.')
      const data = await load(termId); setRecords(data.records); setActivities(data.activities); setMembers(data.members)
    } catch (err) {
      if (err instanceof ApiError && err.status >= 400 && err.status < 500) setPending(null)
      setFormError(report(err)); setError(report(err))
    } finally { setBusy(false) }
  }
  const visible = records.filter(w => (status === 'ALL' || w.status === status) && `${w.memberName} ${w.incident}`.toLowerCase().includes(filter.toLowerCase()))
  return <Stack spacing={2}>
    <Typography color="text.secondary">Record Minor/Major warnings or a deduction-only incident. Resolving a warning keeps its deduction; cancelling reverses it once.</Typography>
    {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="success">{notice}</Alert>}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField select label="Academic term" value={termId} disabled={busy || loading} sx={{ minWidth: 240 }} onChange={e => { setLoading(true); setRecords([]); setView(null); setNotice(''); setError(''); setTermId(e.target.value) }}>{terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} · {t.status}</MenuItem>)}</TextField>
      <Button disabled={busy || loading} onClick={() => void reload()}>Reload</Button>
      <Button variant="contained" disabled={busy || loading || term?.status !== 'ACTIVE'} onClick={() => open({ kind: 'create' })}>Record incident</Button>
    </Stack>
    {loading && <Typography role="status">Loading incidents…</Typography>}
    {!loading && !term && <Alert severity="info">Create and activate an academic term in Settings first.</Alert>}
    {term && term.status !== 'ACTIVE' && <Alert severity="info">{term.status === 'CLOSED' ? 'Closed term: new records and resolutions are locked. A reasoned closed-term cancellation can reverse an incorrect deduction.' : 'Activate this term before recording incidents.'}</Alert>}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField label="Search member or incident" value={filter} onChange={e => setFilter(e.target.value)} fullWidth /><TextField select label="Status" value={status} onChange={e => setStatus(e.target.value)} sx={{ minWidth: 180 }}>{['ALL', 'OPEN', 'RESOLVED', 'CANCELLED'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField></Stack>
    {!loading && <Typography variant="body2">{visible.length} incident{visible.length === 1 ? '' : 's'}</Typography>}
    {visible.map(w => <Paper key={w.id} variant="outlined" sx={{ p: 2 }}><Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}><Typography sx={{ fontWeight: 700 }}>{w.memberName} · {w.incident}</Typography><Chip size="small" label={w.severity ?? 'DEDUCTION ONLY'} /><Chip size="small" variant="outlined" label={w.status} /></Stack>
      <Typography>{w.reason}</Typography>
      {w.activityId && <Typography variant="body2">Assigned-role no-show · {activities.find(a => a.id === w.activityId)?.title ?? w.activityId} · Role: {w.assignedRole}</Typography>}
      <Typography variant="body2" color="text.secondary">{w.deduction ? `Deduction: −${w.deduction} points${w.status === 'CANCELLED' ? ' · reversed' : ''}` : 'No point deduction'} · {new Date(w.createdAt).toLocaleString()}</Typography>
      <Stack direction="row" spacing={1}><Button disabled={busy} onClick={() => void history(w.id)}>History</Button>{term?.status === 'ACTIVE' && w.status === 'OPEN' && w.severity && <Button disabled={busy} onClick={() => open({ kind: 'resolve', warning: w })}>Resolve</Button>}{term?.status !== 'DRAFT' && w.status !== 'CANCELLED' && <Button disabled={busy} onClick={() => open({ kind: 'cancel', warning: w })}>{term?.status === 'CLOSED' ? 'Cancel in closed term' : 'Cancel incident'}</Button>}</Stack>
    </Stack></Paper>)}
    <Dialog open={!!action} onClose={() => { if (!busy && !pending) setAction(null) }} maxWidth="sm" fullWidth>
      <Box component="form" onSubmit={submit}><DialogTitle>{action?.kind === 'create' ? 'Record warning or deduction' : action?.kind === 'resolve' ? 'Resolve warning' : 'Cancel incident'}</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {formError && <Alert severity="error">{formError}</Alert>}
        {pending && <Alert severity="info">The submitted details are held for a safe retry. If unsure whether the save succeeded, close and reload to check the history.</Alert>}
        {action?.kind === 'create' ? <>
          <TextField select required label="Member" value={memberId} onChange={e => setMemberId(e.target.value)} disabled={!!pending}>{members.filter(m => m.active).map(m => <MenuItem key={m.id} value={m.id}>{m.name} · {m.memberCode}</MenuItem>)}</TextField>
          <TextField required name="incident" label="Incident reference" helperText="Use one consistent reference per incident, e.g. MEETING-SEP29-NOSHOW. Reusing it for this member is blocked, even after cancellation." slotProps={{ htmlInput: { maxLength: 180 } }} disabled={!!pending} />
          <TextField select label="Warning severity" value={severity} onChange={e => { setSeverity(e.target.value); if (e.target.value === 'NONE') setDeduct(true) }} disabled={!!pending}><MenuItem value="MINOR">Minor</MenuItem><MenuItem value="MAJOR">Major</MenuItem><MenuItem value="NONE">None — deduction only</MenuItem></TextField>
          <FormControlLabel control={<Checkbox checked={roleNoShow} onChange={e => setRoleNoShow(e.target.checked)} disabled={!!pending} />} label="Assigned-role no-show" />
          {roleNoShow && <><TextField select required label="Finalized activity" value={activityId} onChange={e => setActivityId(e.target.value)} disabled={!!pending}>{activities.filter(a => a.status === 'FINALIZED').map(a => <MenuItem key={a.id} value={a.id}>{a.title} · {a.kind}</MenuItem>)}</TextField><TextField required name="assignedRole" label="Assigned role" helperText="Confirm the member was assigned this role. Apex requires their finalized attendance to be ABSENT." slotProps={{ htmlInput: { maxLength: 150 } }} disabled={!!pending} /></>}
          <FormControlLabel control={<Checkbox checked={deduct} disabled={!!pending || severity === 'NONE'} onChange={e => setDeduct(e.target.checked)} />} label="Include a point deduction" />
          {deduct && (roleNoShow ? <Alert severity="info">Meeting: −3 points. Event: −5 points. {activity ? `This ${activity.kind.toLowerCase()}: −${fixedDeduction}.` : 'Choose the activity above.'} The same activity/member no-show cannot be recorded twice.</Alert> : <TextField required name="deduction" label="Points to deduct" type="number" defaultValue={1} slotProps={{ htmlInput: { min: 1, max: 1000, step: 1 } }} disabled={!!pending} />)}
        </> : <Alert severity={action?.kind === 'cancel' ? 'warning' : 'info'}>{action?.kind === 'cancel' ? `${term?.status === 'CLOSED' ? 'Closed-term correction. ' : ''}Cancellation preserves the record and reverses its linked deduction once.` : 'Resolution records that the warning has been handled. Its deduction remains in the ledger.'}</Alert>}
        <TextField required name="reason" label="Reason" multiline minRows={2} slotProps={{ htmlInput: { maxLength: 500 } }} disabled={!!pending} />
      </Stack></DialogContent><DialogActions><Button disabled={busy} onClick={() => { setAction(null); setPending(null); void reload() }}>Close and reload</Button><Button type="submit" variant="contained" disabled={busy}>{busy ? 'Saving…' : pending ? 'Retry same request' : 'Confirm'}</Button></DialogActions></Box>
    </Dialog>
    <Dialog open={!!view} onClose={() => setView(null)} maxWidth="sm" fullWidth><DialogTitle>Incident history</DialogTitle><DialogContent><Stack spacing={2}><Typography sx={{ fontWeight: 700 }}>{view?.warning.memberName} · {view?.warning.incident}</Typography>{view?.history.map(h => <Paper variant="outlined" sx={{ p: 2 }} key={h.version}><Typography sx={{ fontWeight: 600 }}>{h.action}</Typography><Typography>{h.reason}</Typography><Typography variant="caption" color="text.secondary">{h.actor} · {new Date(h.recordedAt).toLocaleString()} · Version {h.version}</Typography></Paper>)}</Stack></DialogContent><DialogActions><Button onClick={() => setView(null)}>Close</Button></DialogActions></Dialog>
  </Stack>
}
