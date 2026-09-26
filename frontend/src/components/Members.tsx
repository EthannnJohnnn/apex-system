import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'

type Category = 'MEMBER' | 'OFFICER' | 'EXECUTIVE' | 'PRESIDENT'
interface Member {
  id: string; memberCode: string; name: string; position: string; category: Category
  eligible: boolean; active: boolean; notes: string; version: number
}
interface History { id: string; eligible: boolean; reason: string; changedBy: string; effectiveAt: string }
const categories: Record<Category, string> = { MEMBER: 'Member', OFFICER: 'Officer', EXECUTIVE: 'Executive Committee', PRESIDENT: 'President' }
const empty = { memberCode: '', name: '', position: 'Member', category: 'MEMBER' as Category, eligible: true, notes: '', reason: '' }

export function Members({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [members, setMembers] = useState<Member[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('active')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Member | null>(null)
  const [form, setForm] = useState(empty)
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const [statusTarget, setStatusTarget] = useState<Member | null>(null)
  const [historyTarget, setHistoryTarget] = useState<Member | null>(null)
  const [history, setHistory] = useState<History[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')

  useEffect(() => {
    let active = true
    apiRequest('/api/v1/members').then(r => r.json()).then((data: Member[]) => {
      if (active) setMembers(data)
    }).catch((e: unknown) => {
      if (!active) return
      if (e instanceof ApiError && e.status === 401) onSessionExpired()
      else setError('Cannot load members. Check the backend and retry.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [onSessionExpired])

  function failure(e: unknown): string {
    if (e instanceof ApiError && e.status === 401) onSessionExpired()
    return e instanceof ApiError ? e.message : 'Connection lost. The result could not be confirmed. Reload members before retrying.'
  }
  async function reload() {
    setLoading(true); setError('')
    try { setMembers(await (await apiRequest('/api/v1/members')).json()) }
    catch (e) { setError(failure(e)) }
    finally { setLoading(false) }
  }
  function start(member: Member | null) {
    setEditing(member); setForm(member ? { ...member, reason: '' } : empty)
    setFormError(''); setOpen(true)
  }
  function replace(member: Member) {
    setMembers(old => [...old.filter(m => m.id !== member.id), member].sort((a, b) => a.name.localeCompare(b.name)))
  }
  const defaultEligible = form.category === 'MEMBER' || form.category === 'OFFICER'
  const reasonRequired = editing ? form.eligible !== editing.eligible : form.eligible !== defaultEligible
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setFormError(''); setNotice('')
    try {
      const response = await apiRequest('/api/v1/members' + (editing ? '/' + editing.id : ''), editing ? 'PUT' : 'POST',
        { ...form, version: editing?.version })
      replace(await response.json()); setOpen(false); setNotice(editing ? 'Member updated.' : 'Member saved.')
    } catch (e) { setFormError(failure(e)) }
    finally { setBusy(false) }
  }
  async function changeStatus() {
    if (!statusTarget) return
    setBusy(true); setError(''); setNotice('')
    try {
      replace(await (await apiRequest('/api/v1/members/' + statusTarget.id + '/status', 'POST',
        { active: !statusTarget.active, version: statusTarget.version })).json())
      setNotice(statusTarget.active ? 'Member deactivated. History is preserved.' : 'Member reactivated.')
    } catch (e) { setError(failure(e)) }
    finally { setBusy(false); setStatusTarget(null) }
  }
  async function showHistory(member: Member) {
    setHistoryTarget(member); setHistory([]); setHistoryError(''); setHistoryLoading(true)
    try { setHistory(await (await apiRequest('/api/v1/members/' + member.id + '/eligibility-history')).json()) }
    catch (e) { setHistoryError(failure(e)) }
    finally { setHistoryLoading(false) }
  }
  const query = search.trim().toLowerCase()
  const visible = members.filter(m => (filter === 'all' || m.active === (filter === 'active')) &&
    [m.name, m.memberCode, m.position].some(value => value.toLowerCase().includes(query)))

  return <Box component="section" aria-label="Members" sx={{ borderTop: '1px solid', borderColor: 'divider', pt: 3 }}>
    <Stack spacing={2}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
        <Typography variant="h5" component="h2">Members</Typography>
        <Button variant="contained" onClick={() => start(null)}>Add member</Button>
      </Stack>
      <Typography color="text.secondary">Member records, not accounts. Deactivation preserves history.</Typography>
      {notice && <Alert severity="success" role="status">{notice}</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <TextField label="Search name, ID or position" value={search} onChange={e => setSearch(e.target.value)} fullWidth />
        <TextField select label="Show" value={filter} onChange={e => setFilter(e.target.value)} sx={{ minWidth: 155 }}>
          <MenuItem value="active">Active</MenuItem><MenuItem value="inactive">Inactive</MenuItem><MenuItem value="all">All members</MenuItem>
        </TextField>
        <Button onClick={reload} disabled={loading}>Reload</Button>
      </Stack>
      {loading ? <Typography role="status">Loading members…</Typography> : <>
        <Typography variant="body2" color="text.secondary">{visible.length} member{visible.length === 1 ? '' : 's'}</Typography>
        {!visible.length && !error && <Alert severity="info">No members found. Add a member or change your search.</Alert>}
        {visible.map(member => <Box key={member.id} sx={{ p: 2, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 2 }}>
            <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
              <Typography variant="h6">{member.name}</Typography>
              <Typography color="text.secondary">{member.memberCode} · {member.position}</Typography>
              <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap', mt: 1 }}>
                <Chip size="small" label={categories[member.category]} />
                <Chip size="small" variant="outlined" label={member.eligible ? 'Points eligible' : 'Not eligible'} />
                {!member.active && <Chip size="small" label="Inactive" />}
              </Stack>
            </Box>
            <Stack direction="row" sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
              <Button onClick={() => start(member)} aria-label={'Edit ' + member.name}>Edit</Button>
              <Button onClick={() => showHistory(member)} aria-label={'Eligibility history for ' + member.name}>History</Button>
              <Button onClick={() => { setError(''); setStatusTarget(member) }}>{member.active ? 'Deactivate' : 'Reactivate'}</Button>
            </Stack>
          </Stack>
        </Box>)}
      </>}
    </Stack>
    <Dialog open={open} onClose={() => { if (!busy) setOpen(false) }} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={save}>
        <DialogTitle>{editing ? 'Edit member' : 'Add member'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField autoFocus label="Full name" required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} slotProps={{ htmlInput: { maxLength: 150 } }} />
            <TextField label="Student / Member ID" required value={form.memberCode} onChange={e => setForm({ ...form, memberCode: e.target.value })} helperText="Unique, including inactive records. Saved in uppercase." slotProps={{ htmlInput: { maxLength: 64 } }} />
            <TextField select label="Position category" value={form.category} onChange={e => {
              const category = e.target.value as Category
              setForm({ ...form, category, eligible: category === 'MEMBER' || category === 'OFFICER' })
            }}>{Object.entries(categories).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}</TextField>
            <TextField label="Position / title" required value={form.position} onChange={e => setForm({ ...form, position: e.target.value })} slotProps={{ htmlInput: { maxLength: 100 } }} />
            <FormControlLabel control={<Checkbox checked={form.eligible} disabled={form.category === 'PRESIDENT'} onChange={e => setForm({ ...form, eligible: e.target.checked })} />} label="Eligible to earn points" />
            <Typography variant="body2" color="text.secondary">Executives are excluded by default but may opt in. The president is always excluded. Eligibility changes apply to future activities only.</Typography>
            {reasonRequired && <TextField label="Reason for eligibility change" required multiline value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} slotProps={{ htmlInput: { maxLength: 500 } }} />}
            <TextField label="Notes (optional)" multiline minRows={2} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} slotProps={{ htmlInput: { maxLength: 2000 } }} />
          </Stack>
        </DialogContent>
        <DialogActions><Button disabled={busy} onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" variant="contained" disabled={busy}>{busy ? 'Saving…' : 'Save member'}</Button></DialogActions>
      </Box>
    </Dialog>
    <Dialog open={!!statusTarget} onClose={() => { if (!busy) setStatusTarget(null) }} fullWidth maxWidth="xs">
      <DialogTitle>{statusTarget?.active ? 'Deactivate member?' : 'Reactivate member?'}</DialogTitle>
      <DialogContent><Typography>{statusTarget?.name} will be {statusTarget?.active ? 'removed from the active list' : 'returned to the active list'}. Records and eligibility history stay saved.</Typography></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setStatusTarget(null)}>Cancel</Button><Button disabled={busy} onClick={changeStatus}>Confirm</Button></DialogActions>
    </Dialog>
    <Dialog open={!!historyTarget} onClose={() => setHistoryTarget(null)} fullWidth maxWidth="sm">
      <DialogTitle>Eligibility history · {historyTarget?.name}</DialogTitle>
      <DialogContent><Stack spacing={2}>
        {historyLoading && <Typography role="status">Loading history…</Typography>}
        {historyError && <Alert severity="error">{historyError}</Alert>}
        {history.map(item => <Box key={item.id} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 2, overflowWrap: 'anywhere' }}>
          <Typography sx={{ fontWeight: 700 }}>{item.eligible ? 'Eligible' : 'Not eligible'}</Typography>
          <Typography>{item.reason}</Typography>
          <Typography variant="caption" color="text.secondary">{new Date(item.effectiveAt).toLocaleString()} · {item.changedBy}</Typography>
        </Box>)}
      </Stack></DialogContent>
      <DialogActions><Button onClick={() => setHistoryTarget(null)}>Close</Button></DialogActions>
    </Dialog>
  </Box>
}
