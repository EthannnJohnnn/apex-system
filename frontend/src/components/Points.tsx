import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'

interface Term { id: string; name: string; status: 'DRAFT' | 'ACTIVE' | 'CLOSED' }
interface Total { memberId: string; memberCode: string; name: string; active: boolean; eligible: boolean; total: number }
interface Entry { id: string; sequence: number; memberId: string; amount: number; kind: string; reversesId: string | null; replacesId: string | null; reason: string; actor: string; recordedAt: string }
interface Ledger { term: Term; totals: Total[]; entries: Entry[] }
interface Action { requestId: string; termId: string; memberId: string; amount: number; reason: string }
interface Editor { requestId: string; member: Total; source?: Entry }

export function Points({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [terms, setTerms] = useState<Term[]>([])
  const [selected, setSelected] = useState('')
  const [ledger, setLedger] = useState<Ledger | null>(null)
  const [filter, setFilter] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editor, setEditor] = useState<Editor | null>(null)
  const [pending, setPending] = useState<Action | null>(null)
  const [formError, setFormError] = useState('')
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return err instanceof ApiError ? err.message : 'Connection interrupted. The save may have reached Apex. Retry the same request or reload and check history before starting another.'
  }, [onSessionExpired])
  const load = useCallback(async (termId: string) => {
    const data: Ledger = await (await apiRequest('/api/v1/points?termId=' + termId)).json()
    setLedger(data)
  }, [])
  useEffect(() => {
    let live = true
    apiRequest('/api/v1/terms').then(r => r.json()).then((data: Term[]) => {
      if (live) { setTerms(data); setSelected(data.find(t => t.status === 'ACTIVE')?.id ?? data[0]?.id ?? ''); setLoading(false) }
    }).catch((err: unknown) => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [report])
  useEffect(() => {
    if (!selected) return
    let live = true
    apiRequest('/api/v1/points?termId=' + selected).then(r => r.json()).then((data: Ledger) => { if (live) setLedger(data) })
      .catch((err: unknown) => { if (live) setError(report(err)) })
    return () => { live = false }
  }, [selected, report])
  async function reload() {
    setBusy(true); setError(''); setNotice('')
    try {
      const data: Term[] = await (await apiRequest('/api/v1/terms')).json()
      setTerms(data)
      const id = data.some(t => t.id === selected) ? selected : data.find(t => t.status === 'ACTIVE')?.id ?? data[0]?.id ?? ''
      setSelected(id)
      if (id) await load(id)
      else setLedger(null)
    } catch (err) { setError(report(err)) }
    finally { setBusy(false) }
  }
  function open(member: Total, source?: Entry) {
    setEditor({ requestId: crypto.randomUUID(), member, source }); setPending(null); setFormError(''); setNotice('')
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor || !ledger) return
    const values = new FormData(event.currentTarget)
    const amount = Number(values.get('amount')) * (!editor.source && values.get('kind') === 'DEDUCTION' ? -1 : 1)
    const body = pending ?? { requestId: editor.requestId, termId: ledger.term.id, memberId: editor.member.memberId, amount, reason: String(values.get('reason')) }
    setPending(body); setBusy(true); setFormError(''); setError('')
    try {
      const suffix = editor.source ? `/${editor.source.id}/${ledger.term.status === 'CLOSED' ? 'closed-corrections' : 'corrections'}` : ''
      await apiRequest('/api/v1/points' + suffix, 'POST', body)
      setEditor(null); setPending(null); setNotice('Points saved. The ledger history is preserved.')
      try { await load(ledger.term.id) }
      catch { setError('Saved successfully, but totals could not refresh. Click Reload ledger; do not submit again.') }
    } catch (err) {
      setFormError(report(err))
      if (err instanceof ApiError && err.status < 500) setPending(null)
    } finally { setBusy(false) }
  }
  const current = ledger?.term.id === selected ? ledger : null
  const reversed = new Set(current?.entries.map(e => e.reversesId).filter(Boolean))
  const members = new Map(current?.totals.map(m => [m.memberId, m]))
  const entries = current?.entries.filter(e => !filter || e.memberId === filter) ?? []
  return <Stack spacing={2.5}>
    <Divider />
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography component="h2" variant="h5">Point ledger</Typography>
      <Button onClick={reload} disabled={busy}>Reload ledger</Button>
    </Stack>
    <Typography variant="body2" color="text.secondary">Every total comes from its history. Awards add points; deductions subtract points. Corrections preserve the original entry.</Typography>
    {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="success">{notice}</Alert>}
    {loading && <Typography role="status">Loading terms…</Typography>}
    {!loading && terms.length === 0 && <Alert severity="info">Create and activate an academic term above, then click Reload ledger.</Alert>}
    {terms.length > 0 && <TextField select label="Ledger term" value={selected} disabled={busy} onChange={e => { setSelected(e.target.value); setFilter(''); setError(''); setNotice('') }}>
      {terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} ({t.status})</MenuItem>)}
    </TextField>}
    {selected && !current && !error && <Typography role="status">Loading ledger…</Typography>}
    {current && <>
      {current.term.status !== 'ACTIVE' && <Alert severity="info">{current.term.status === 'CLOSED' ? 'Closed term: no new awards or deductions. Use an explicit closed-term correction with a reason to fix history.' : 'Draft term: activate it before recording points.'}</Alert>}
      <Typography component="h3" variant="h6">Member totals</Typography>
      {current.totals.length === 0 && <Typography>Add members before recording points.</Typography>}
      {current.totals.map(member => <Stack key={member.memberId} direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, border: 1, borderColor: 'divider', p: 2, borderRadius: 1 }}>
        <Box><Typography sx={{ fontWeight: 700 }}>{member.name} — {member.total} points</Typography><Typography variant="body2">{member.memberCode} · {member.active ? 'Active' : 'Inactive'} · {member.eligible ? 'Points eligible' : 'Ineligible'}</Typography></Box>
        <Button disabled={busy || current.term.status !== 'ACTIVE' || !member.active || !member.eligible} onClick={() => open(member)}>Award / deduct</Button>
      </Stack>)}
      <Typography component="h3" variant="h6">Transaction history</Typography>
      <TextField select label="History member" value={filter} onChange={e => setFilter(e.target.value)}>
        <MenuItem value="">All members</MenuItem>{current.totals.map(m => <MenuItem key={m.memberId} value={m.memberId}>{m.name} ({m.memberCode})</MenuItem>)}
      </TextField>
      {entries.length === 0 && <Typography>No point transactions in this view. Totals start at zero for each term.</Typography>}
      {entries.map(entry => <Stack key={entry.id} spacing={1} sx={{ border: 1, borderColor: 'divider', p: 2, borderRadius: 1 }}>
        <Typography sx={{ fontWeight: 700 }}>#{entry.sequence} · {members.get(entry.memberId)?.name} · {entry.amount > 0 ? '+' : ''}{entry.amount} · {entry.kind}{reversed.has(entry.id) ? ' (reversed)' : ''}</Typography>
        <Typography>{entry.reason}</Typography>
        <Typography variant="body2" color="text.secondary">{entry.actor} · {new Date(entry.recordedAt).toLocaleString()}</Typography>
        {(entry.reversesId || entry.replacesId) && <Typography variant="body2">{entry.reversesId ? 'Reverses' : 'Replaces'} entry #{current.entries.find(e => e.id === (entry.reversesId ?? entry.replacesId))?.sequence}</Typography>}
        {entry.kind !== 'REVERSAL' && !reversed.has(entry.id) && current.term.status !== 'DRAFT' && <Button disabled={busy} onClick={() => open(members.get(entry.memberId)!,entry)}>{current.term.status === 'CLOSED' ? 'Correct closed-term entry' : 'Correct entry'}</Button>}
      </Stack>)}
    </>}
    <Dialog open={!!editor} onClose={() => { if (!busy && !pending) setEditor(null) }} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={submit}>
        <DialogTitle>{editor?.source ? 'Correct point entry' : 'Award or deduct points'}</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
          <Typography>{editor?.member.name} · {current?.term.name}</Typography>
          {formError && <Alert severity="error">{formError}</Alert>}
          {pending && <Alert severity="info">The original request is retained for safe retry. If you close this form, reload and check history before starting a new action.</Alert>}
          {editor?.source ? <Alert severity="warning">{current?.term.status === 'CLOSED' ? 'You are correcting a CLOSED term. ' : ''}Original: {editor.source.amount} points. Enter the intended replacement amount, not the difference. Use 0 to cancel. The original, its reversal, and the replacement will remain visible.</Alert> : <TextField name="kind" label="Action" select defaultValue="AWARD" disabled={!!pending}><MenuItem value="AWARD">Award</MenuItem><MenuItem value="DEDUCTION">Deduction</MenuItem></TextField>}
          <TextField name="amount" label={editor?.source ? 'Replacement points (negative for deduction)' : 'Points'} type="number" defaultValue={editor?.source?.amount ?? 1} required disabled={!!pending} slotProps={{ htmlInput: { min: editor?.source ? -1000 : 1, max: 1000, step: 1 } }} />
          <TextField name="reason" label="Reason" multiline minRows={2} required disabled={!!pending} slotProps={{ htmlInput: { maxLength: 500 } }} />
        </Stack></DialogContent>
        <DialogActions><Button disabled={busy} onClick={() => { setEditor(null); setPending(null) }}>Cancel</Button><Button type="submit" variant="contained" disabled={busy}>{busy ? 'Saving…' : pending ? 'Retry same request' : 'Save points'}</Button></DialogActions>
      </Box>
    </Dialog>
  </Stack>
}
