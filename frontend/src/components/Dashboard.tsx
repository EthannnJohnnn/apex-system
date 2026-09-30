import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Alert, Box, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'
import { ConnectionStatus } from './ConnectionStatus'
import type { Activity } from './Activities'
import type { Ranked } from './Leaderboard'

interface Term { id: string; name: string; status: string }
interface Entry { id: string; memberName: string; amount: number; kind: string; recordedAt: string }
interface Trend { activity: Activity; present: number; late: number; excused: number; absent: number }
interface Snapshot { term: Term | null; counts: { totalMembers: number; activeMembers: number; activeEligible: number; netPoints: number; openWarnings: number }; attendance: { recent: Trend[]; upcoming: Activity[] }; leaders: Ranked[]; changes: Entry[] }
const panel = { p: 3, borderRadius: 2 }

export function Dashboard({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Snapshot | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [terms, setTerms] = useState<Term[]>([])
  const [termId, setTermId] = useState('')
  const load = useCallback(async () => {
    return await (await apiRequest('/api/v1/dashboard' + (termId ? '?termId=' + termId : ''))).json() as Snapshot
  }, [termId])
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return 'Could not load the dashboard. Check the backend and try Refresh overview.'
  }, [onSessionExpired])
  useEffect(() => {
    let live = true
    apiRequest('/api/v1/terms').then(r => r.json()).then((rows: Term[]) => { if (live) setTerms(rows) }).catch(err => { if (live) setError(report(err)) })
    return () => { live = false }
  }, [report])
  useEffect(() => {
    let live = true
    load().then(result => { if (live) setData(result) }).catch((err: unknown) => { if (live) setError(report(err)) }).finally(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [load, report])
  async function refresh() {
    setLoading(true); setError('')
    try { setData(await load()) } catch (err) { setError(report(err)) } finally { setLoading(false) }
  }
  const active = data?.counts.activeMembers ?? 0
  const eligible = data?.counts.activeEligible ?? 0
  const groups = data ? [{ label: 'Active · eligible', value: eligible }, { label: 'Active · ineligible', value: active - eligible }, { label: 'Inactive', value: data.counts.totalMembers - active }] : []
  return <Stack spacing={3}>
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}><Typography color="text.secondary">Your organization at a glance.</Typography><Button disabled={loading} onClick={refresh}>Refresh overview</Button></Stack>
    <TextField select label="Dashboard term" disabled={loading} value={termId} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }} onChange={e => { setData(null); setLoading(true); setError(''); setTermId(e.target.value) }}><MenuItem value="">Current active term</MenuItem>{terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} · {t.status}</MenuItem>)}</TextField>
    {error && <Alert severity="error">{error} {data && 'Previously loaded values remain visible.'}</Alert>}
    {loading && <Typography role="status">Loading overview…</Typography>}
    {data && <>
      <Chip label={data.term ? `${data.term.status} · ${data.term.name}` : 'No active academic term'} sx={{ alignSelf: 'flex-start' }} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', lg: 'repeat(5, minmax(0, 1fr))' }, gap: 2 }}>
        {[['Current members', data.counts.totalMembers], ['Active members', active], ['Active & eligible', eligible], ['Term net points', data.term ? data.counts.netPoints : '—'], ['Open warnings in term', data.term ? data.counts.openWarnings : '—']].map(([label, value]) => <Paper variant="outlined" key={label} sx={panel}><Typography color="text.secondary" variant="body2">{label}</Typography><Typography variant="h3" sx={{ mt: 1, fontWeight: 600 }}>{value}</Typography></Paper>)}
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 3 }}>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Member overview</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>Current membership, grouped by status</Typography>
          {data.counts.totalMembers === 0 ? <Typography>No members yet. Add your first member to see this chart.</Typography> : <Stack spacing={2.5} component="ul" sx={{ p: 0, listStyle: 'none' }}>{groups.map(group => <Box component="li" key={group.label}><Stack direction="row" sx={{ justifyContent: 'space-between', mb: .75 }}><Typography variant="body2">{group.label}</Typography><Typography variant="body2">{group.value}</Typography></Stack><Box aria-hidden="true" sx={{ height: 12, bgcolor: 'action.hover', borderRadius: 1, overflow: 'hidden' }}><Box sx={{ height: '100%', width: `${group.value / data.counts.totalMembers * 100}%`, bgcolor: 'primary.main' }} /></Box></Box>)}</Stack>}
          <Button component={Link} to="/members" sx={{ mt: 2 }}>Manage members →</Button>
        </Paper>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Attendance trends</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Latest six finalized activities · selected term · Present + Late / all expected attendees</Typography>
          {data.attendance.recent.length === 0 ? <Typography color="text.secondary" sx={{ py: 4 }}>Finalize an activity to see real attendance results here.</Typography> : <Stack component="ul" spacing={2} sx={{ p: 0, listStyle: 'none' }}>{data.attendance.recent.map(row => { const total = row.present + row.late + row.excused + row.absent; return <Box component="li" key={row.activity.id}><Typography variant="body2" sx={{ fontWeight: 600 }}>{row.activity.title} · {row.present + row.late}/{total}</Typography><Typography variant="caption" color="text.secondary">Present {row.present} · Late {row.late} · Excused {row.excused} · Absent {row.absent}</Typography><Box aria-hidden="true" sx={{ height: 10, bgcolor: 'action.hover', borderRadius: 1, overflow: 'hidden', mt: .5 }}><Box sx={{ height: '100%', width: `${total ? (row.present + row.late) / total * 100 : 0}%`, bgcolor: 'primary.main' }} /></Box></Box> })}</Stack>}
          <Button component={Link} to="/activities">Manage attendance →</Button>
        </Paper>
      </Box>
      <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Upcoming activities</Typography>{data.attendance.upcoming.length === 0 ? <Typography color="text.secondary" sx={{ mt: 1 }}>No upcoming drafts in the selected term.</Typography> : data.attendance.upcoming.map(a => <Box key={a.id} sx={{ mt: 2 }}><Typography sx={{ fontWeight: 600 }}>{a.title}</Typography><Typography variant="body2" color="text.secondary">{new Date(a.scheduledAt).toLocaleString('en-PH', { timeZone: 'Asia/Manila' })} · Asia/Manila · {a.kind}</Typography></Box>)}</Paper>
      <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Leading members</Typography><Typography variant="body2" color="text.secondary">Top ten ranks, including ties · Selected term</Typography>{data.leaders.length === 0 ? <Typography sx={{ mt: 2 }}>No eligible members to rank.</Typography> : data.leaders.map(m => <Stack key={m.memberId} direction="row" sx={{ justifyContent: 'space-between', py: 1 }}><Typography>#{m.rank} · {m.name}</Typography><Typography sx={{ fontWeight: 700 }}>{m.total} pts</Typography></Stack>)}<Button component={Link} to={data.term ? `/leaderboard?termId=${data.term.id}&view=all` : '/leaderboard'}>Open full leaderboard →</Button></Paper>
      <Paper variant="outlined" sx={panel}><Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}><Typography component="h2" variant="h6">Recent point changes</Typography><Button component={Link} to="/points">View ledger →</Button></Stack><Typography variant="body2" color="text.secondary">Private incident reasons are kept out of this summary.</Typography>
        {!data.term ? <Typography color="text.secondary" sx={{ mt: 2 }}>Activate a term in Settings to see its point activity.</Typography> : data.changes.length === 0 ? <Typography color="text.secondary" sx={{ mt: 2 }}>No point transactions in this term yet.</Typography> : data.changes.slice(0, 5).map(entry => <Stack key={entry.id} direction="row" spacing={2} sx={{ py: 2, borderBottom: 1, borderColor: 'divider', justifyContent: 'space-between' }}><Box sx={{ minWidth: 0 }}><Typography sx={{ fontWeight: 600 }}>{entry.memberName}</Typography><Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{entry.kind}</Typography><Typography variant="caption" color="text.secondary">{new Date(entry.recordedAt).toLocaleString()}</Typography></Box><Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>{entry.amount > 0 ? '+' : ''}{entry.amount} pts</Typography></Stack>)}
      </Paper>
    </>}
    <ConnectionStatus />
  </Stack>
}
