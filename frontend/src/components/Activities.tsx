import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'

type Status = 'PRESENT' | 'LATE' | 'EXCUSED' | 'ABSENT'
const statuses: Status[] = ['PRESENT', 'LATE', 'EXCUSED', 'ABSENT']
interface Term { id: string; name: string; status: string }
interface Member { id: string; name: string; memberCode: string; active: boolean }
export interface Activity { id: string; termId: string; title: string; kind: 'MEETING' | 'EVENT'; scheduledAt: string; location: string; description: string; presentPoints: number; latePoints: number; status: string; version: number }
interface Attendee { memberId: string; name: string; memberCode: string; status: Status | null; eligibleSnapshot: boolean | null; points: number }
interface History { id: string; memberId: string | null; action: string; oldStatus: string | null; newStatus: string | null; oldPoints: number | null; newPoints: number | null; reason: string; actor: string; recordedAt: string }
interface View { activity: Activity; termStatus: string; attendees: Attendee[]; history: History[] }
interface Request { path: string; method: string; body: object }
type DialogState = { type: 'create' | 'edit' | 'finalize' | 'cancel' | 'correct'; member?: Attendee }
const formatTime = (value: string) => new Date(value).toLocaleString('en-PH', { timeZone: 'Asia/Manila' })
const localInput = (value: string) => new Date(new Date(value).getTime() + 8 * 3600000).toISOString().slice(0, 16)

export function Activities({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [terms, setTerms] = useState<Term[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [termId, setTermId] = useState('')
  const [activities, setActivities] = useState<Activity[]>([])
  const [view, setView] = useState<View | null>(null)
  const [marks, setMarks] = useState<Record<string, Status | ''>>({})
  const [checked, setChecked] = useState<string[]>([])
  const [bulk, setBulk] = useState<Status>('PRESENT')
  const [roster, setRoster] = useState<string[]>([])
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [pending, setPending] = useState<Request | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [historyOpen, setHistoryOpen] = useState(false)
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return err instanceof ApiError ? err.message : 'Connection interrupted. Retry the same request, or reload and check the activity before starting another action.'
  }, [onSessionExpired])
  function show(result: View) {
    setView(result); setMarks(Object.fromEntries(result.attendees.map(m => [m.memberId, m.status ?? '']))); setChecked([])
  }
  useEffect(() => {
    let live = true
    Promise.all([apiRequest('/api/v1/terms').then(r => r.json()), apiRequest('/api/v1/members').then(r => r.json())]).then(([t, m]: [Term[], Member[]]) => {
      if (live) { setTerms(t); setMembers(m); setTermId(t.find(item => item.status === 'ACTIVE')?.id ?? t[0]?.id ?? ''); setLoading(false) }
    }).catch((err: unknown) => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [report])
  useEffect(() => {
    if (!termId) return
    let live = true
    apiRequest('/api/v1/activities?termId=' + termId).then(r => r.json()).then((rows: Activity[]) => { if (live) setActivities(rows) }).catch((err: unknown) => { if (live) setError(report(err)) })
    return () => { live = false }
  }, [termId, report])
  async function reload() {
    setBusy(true); setError(''); setNotice('')
    try {
      const [t, m]: [Term[], Member[]] = await Promise.all([apiRequest('/api/v1/terms').then(r => r.json()), apiRequest('/api/v1/members').then(r => r.json())])
      setTerms(t); setMembers(m)
      const id = termId || t.find(item => item.status === 'ACTIVE')?.id || t[0]?.id || ''
      setTermId(id)
      if (id) setActivities(await (await apiRequest('/api/v1/activities?termId=' + id)).json())
      if (view) show(await (await apiRequest('/api/v1/activities/' + view.activity.id)).json())
      setPending(null); setDialog(null); setLoading(false)
    } catch (err) { setError(report(err)) } finally { setBusy(false) }
  }
  async function openActivity(id: string) {
    setBusy(true); setError(''); setNotice(''); setHistoryOpen(false)
    try { show(await (await apiRequest('/api/v1/activities/' + id)).json()) } catch (err) { setError(report(err)) } finally { setBusy(false) }
  }
  async function send(request: Request) {
    setBusy(true); setError(''); setNotice(''); setPending(request)
    try {
      const result: View = await (await apiRequest(request.path, request.method, request.body)).json()
      show(result); setDialog(null); setPending(null)
      setActivities(rows => [result.activity, ...rows.filter(a => a.id !== result.activity.id)].sort((a,b) => b.scheduledAt.localeCompare(a.scheduledAt)))
      setNotice('Saved successfully. Attendance and any point changes are stored together.')
    } catch (err) {
      setError(report(err))
      if (err instanceof ApiError && err.status < 500) setPending(null)
    } finally { setBusy(false) }
  }
  const activity = view?.activity
  const dirty = !!view && view.attendees.some(m => (marks[m.memberId] ?? '') !== (m.status ?? ''))
  const locked = busy || !!pending
  const canDraft = activity?.status === 'DRAFT' && view?.termStatus === 'ACTIVE'
  function openDialog(type: DialogState['type'], member?: Attendee) {
    setError(''); setNotice(''); setDialog({ type, member })
    if (type === 'create') setRoster(members.filter(m => m.active).map(m => m.id))
    if (type === 'edit') setRoster(view?.attendees.map(m => m.memberId) ?? [])
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!dialog) return
    const values = new FormData(event.currentTarget)
    const base = '/api/v1/activities'
    const common = { requestId: crypto.randomUUID(), version: activity?.version, reason: String(values.get('reason') ?? '') }
    if (dialog.type === 'create' || dialog.type === 'edit') {
      const body = { ...common, termId: dialog.type === 'create' ? termId : activity?.termId, title: String(values.get('title')), kind: String(values.get('kind')), scheduledAt: String(values.get('schedule')) + ':00+08:00', location: String(values.get('location')), description: String(values.get('description')), memberIds: roster }
      void send({ path: base + (dialog.type === 'edit' ? '/' + activity?.id : ''), method: dialog.type === 'edit' ? 'PUT' : 'POST', body })
    } else {
      const suffix = dialog.type === 'correct' ? view?.termStatus === 'CLOSED' ? 'closed-corrections' : 'corrections' : dialog.type
      void send({ path: `${base}/${activity?.id}/${suffix}`, method: 'POST', body: { ...common, memberId: dialog.member?.memberId, status: values.get('status') } })
    }
  }
  const rosterOptions = members.filter(m => m.active || (dialog?.type === 'edit' && view?.attendees.some(a => a.memberId === m.id)))
  return <Stack spacing={3}>
    <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}><Typography color="text.secondary">Create an activity, save attendance, then finalize its points.</Typography><Button disabled={busy} onClick={reload}>Reload activities</Button></Stack>
    {error && !dialog && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="success">{notice}</Alert>}
    {pending && !dialog && <Alert severity="warning" action={<Button disabled={busy} onClick={() => void send(pending)}>Retry same request</Button>}>The save result is not confirmed. Other actions are locked until you retry or reload.</Alert>}
    {loading && <Typography role="status">Loading activities…</Typography>}
    {!loading && terms.length === 0 && <Alert severity="info">Create and activate an academic term in Settings first.</Alert>}
    {terms.length > 0 && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField select label="Activity term" value={termId} disabled={locked || dirty} onChange={e => { setTermId(e.target.value); setActivities([]); setView(null); setError(''); setNotice('') }} sx={{ flex: 1 }}>{terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} ({t.status})</MenuItem>)}</TextField><Button variant="contained" disabled={locked || dirty || terms.find(t => t.id === termId)?.status !== 'ACTIVE'} onClick={() => openDialog('create')}>Create activity</Button></Stack>}
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', xl: '280px minmax(0,1fr)' }, gap: 3 }}>
      <Stack spacing={1} aria-label="Activity list">{activities.length === 0 && termId && <Typography color="text.secondary">No activities in this term yet.</Typography>}{activities.map(a => <Button key={a.id} variant={activity?.id === a.id ? 'contained' : 'outlined'} disabled={locked || dirty} onClick={() => void openActivity(a.id)} sx={{ justifyContent: 'flex-start', textAlign: 'left', textTransform: 'none', p: 2 }}><Box>{a.title}<Typography variant="caption" component="div">{a.kind} · {a.status}<br />{formatTime(a.scheduledAt)}</Typography></Box></Button>)}</Stack>
      {view && activity ? <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, minWidth: 0 }}><Stack spacing={2}>
        <Box><Chip size="small" label={`${activity.kind} · ${activity.status}`} /><Typography component="h2" variant="h5" sx={{ mt: 1, overflowWrap: 'anywhere' }}>{activity.title}</Typography><Typography>{formatTime(activity.scheduledAt)} · Asia/Manila</Typography>{activity.location && <Typography color="text.secondary">{activity.location}</Typography>}{activity.description && <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{activity.description}</Typography>}</Box>
        <Alert severity="info">Saved scoring: Present +{activity.presentPoints}, Late +{activity.latePoints}, Excused / Absent 0. Only eligible attendees earn points. Eligibility is checked at the scheduled time and frozen on finalization.</Alert>
        <Typography variant="body2" color="text.secondary">15-minute grace period: arrivals through {formatTime(new Date(new Date(activity.scheduledAt).getTime() + 15 * 60000).toISOString())} may be marked Present. The president confirms each status; a very late arrival is never automatically marked Absent.</Typography>
        {canDraft && <Stack direction="row" spacing={1}><Button disabled={locked || dirty} onClick={() => openDialog('edit')}>Edit details / roster</Button><Button disabled={locked || dirty} onClick={() => openDialog('cancel')}>Cancel draft</Button></Stack>}
        {canDraft && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}><FormControlLabel control={<Checkbox disabled={locked} checked={checked.length === view.attendees.length} indeterminate={checked.length > 0 && checked.length < view.attendees.length} onChange={e => setChecked(e.target.checked ? view.attendees.map(m => m.memberId) : [])} />} label="Select all" /><TextField select size="small" label="Bulk status" value={bulk} disabled={locked} onChange={e => setBulk(e.target.value as Status)} sx={{ minWidth: 140 }}>{statuses.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField><Button disabled={locked || checked.length === 0} onClick={() => setMarks(previous => ({ ...previous, ...Object.fromEntries(checked.map(id => [id, bulk])) }))}>Apply to selected ({checked.length})</Button></Stack>}
        {view.attendees.map(member => <Stack key={member.memberId} direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ py: 1.5, borderBottom: 1, borderColor: 'divider', alignItems: { sm: 'center' } }}>
          {canDraft && <Checkbox slotProps={{ input: { 'aria-label': `Select ${member.name}` } }} disabled={locked} checked={checked.includes(member.memberId)} onChange={e => setChecked(ids => e.target.checked ? [...ids, member.memberId] : ids.filter(id => id !== member.memberId))} />}
          <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 600 }}>{member.name}</Typography><Typography variant="caption" color="text.secondary">{member.memberCode}{member.eligibleSnapshot !== null && ` · ${member.eligibleSnapshot ? 'Eligible at activity' : 'Not eligible at activity'} · ${member.points} pts`}</Typography></Box>
          {canDraft ? <TextField select size="small" label={`Attendance: ${member.name}`} value={marks[member.memberId] ?? ''} disabled={locked} onChange={e => setMarks(previous => ({ ...previous, [member.memberId]: e.target.value as Status | '' }))} sx={{ minWidth: 170 }}><MenuItem value="">Unmarked</MenuItem>{statuses.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField> : <Chip label={member.status ?? 'Unmarked'} />}
          {activity.status === 'FINALIZED' && <Button disabled={locked} onClick={() => openDialog('correct', member)}>{view.termStatus === 'CLOSED' ? 'Correct closed term' : 'Correct'}</Button>}
        </Stack>)}
        {dirty && <Alert severity="warning">Unsaved attendance. Save the draft before switching activities or finalizing. Leaving this page discards unsaved changes.</Alert>}
        {canDraft && <Stack direction="row" spacing={1}><Button variant="outlined" disabled={locked || !dirty} onClick={() => void send({ path: `/api/v1/activities/${activity.id}/attendance`, method: 'PUT', body: { requestId: crypto.randomUUID(), version: activity.version, marks: view.attendees.map(m => ({ memberId: m.memberId, status: marks[m.memberId] || null })) } })}>Save draft</Button><Button variant="contained" disabled={locked || dirty || view.attendees.some(m => !m.status)} onClick={() => openDialog('finalize')}>Finalize attendance</Button></Stack>}
        <Button onClick={() => setHistoryOpen(!historyOpen)}>{historyOpen ? 'Hide history' : 'Show history'}</Button>
        {historyOpen && view.history.map(h => <Box key={h.id} sx={{ borderLeft: 3, borderColor: 'divider', pl: 2 }}><Typography variant="body2" sx={{ fontWeight: 600 }}>{h.action} {h.memberId ? `· ${view.attendees.find(m => m.memberId === h.memberId)?.name ?? members.find(m => m.id === h.memberId)?.name ?? 'Former attendee'}` : ''}</Typography>{h.newStatus && <Typography variant="body2">{h.oldStatus ?? 'Unmarked'} → {h.newStatus} · {h.oldPoints ?? 0} → {h.newPoints ?? 0} pts</Typography>}<Typography variant="body2">{h.reason}</Typography><Typography variant="caption" color="text.secondary">{h.actor} · {formatTime(h.recordedAt)}</Typography></Box>)}
      </Stack></Paper> : <Paper variant="outlined" sx={{ p: 4 }}><Typography color="text.secondary">Choose an activity to open its attendance checklist.</Typography></Paper>}
    </Box>
    <Dialog open={!!dialog} onClose={() => { if (!locked) setDialog(null) }} fullWidth maxWidth="sm"><Box component="form" onSubmit={submit}>
      <DialogTitle>{dialog?.type === 'create' ? 'Create activity' : dialog?.type === 'edit' ? 'Edit activity draft' : dialog?.type === 'finalize' ? 'Finalize attendance?' : dialog?.type === 'cancel' ? 'Cancel attendance draft?' : 'Correct attendance'}</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}{pending && <Alert severity="warning">Save result unconfirmed. Retry preserves the original request. To discard the form, close it and reload the activity before starting another action.</Alert>}
        {(dialog?.type === 'create' || dialog?.type === 'edit') ? <>
          <TextField name="title" label="Activity title" required defaultValue={dialog.type === 'edit' ? activity?.title : ''} disabled={locked} slotProps={{ htmlInput: { maxLength: 150 } }} />
          <TextField name="kind" label="Activity type" select defaultValue={dialog.type === 'edit' ? activity?.kind : 'MEETING'} disabled={locked || dialog.type === 'edit'}>{['MEETING','EVENT'].map(k => <MenuItem key={k} value={k}>{k}</MenuItem>)}</TextField>
          {dialog.type === 'edit' && <input type="hidden" name="kind" value={activity?.kind} />}
          <TextField name="schedule" label="Schedule (Asia/Manila)" type="datetime-local" required defaultValue={dialog.type === 'edit' && activity ? localInput(activity.scheduledAt) : ''} disabled={locked} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField name="location" label="Location (optional)" defaultValue={dialog.type === 'edit' ? activity?.location : ''} disabled={locked} slotProps={{ htmlInput: { maxLength: 200 } }} />
          <TextField name="description" label="Description (optional)" multiline minRows={2} defaultValue={dialog.type === 'edit' ? activity?.description : ''} disabled={locked} slotProps={{ htmlInput: { maxLength: 2000 } }} />
          <Typography variant="body2">Point values are copied from Settings on creation and kept when editing. Expected attendees:</Typography>
          {rosterOptions.length === 0 && <Alert severity="info">Add active members first.</Alert>}
          <Box sx={{ maxHeight: 240, overflow: 'auto' }}>{rosterOptions.map(m => <FormControlLabel key={m.id} sx={{ display: 'flex' }} control={<Checkbox disabled={locked} checked={roster.includes(m.id)} onChange={e => setRoster(ids => e.target.checked ? [...ids, m.id] : ids.filter(id => id !== m.id))} />} label={`${m.name} (${m.memberCode})${m.active ? '' : ' · inactive'}`} />)}</Box>
        </> : dialog?.type === 'finalize' ? <Alert severity="warning">Finalize {activity?.title}? Points will be recorded once for every eligible attendee. Further changes require a correction reason. Check the saved checklist before continuing.</Alert> : <>
          <Typography>{dialog?.member?.name ?? activity?.title}</Typography>
          {dialog?.type === 'correct' && <><Alert severity="warning">{view?.termStatus === 'CLOSED' ? 'This is a CLOSED-term correction. ' : ''}The original attendance and any point entries stay in history. Eligibility and scoring saved for this activity are retained.</Alert><TextField name="status" label="Correct attendance status" select required defaultValue={dialog.member?.status ?? ''} disabled={locked}>{statuses.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}</TextField></>}
          <TextField name="reason" label="Reason" required multiline minRows={2} disabled={locked} slotProps={{ htmlInput: { maxLength: 500 } }} />
        </>}
      </Stack></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setDialog(null)}>Close</Button>{pending ? <Button disabled={busy} onClick={() => void send(pending)}>Retry same request</Button> : <Button type="submit" variant="contained" disabled={busy || ((dialog?.type === 'create' || dialog?.type === 'edit') && roster.length === 0)}>{busy ? 'Saving…' : dialog?.type === 'finalize' ? 'Confirm finalization' : 'Save'}</Button>}</DialogActions>
    </Box></Dialog>
  </Stack>
}
