import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Alert, Box, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import { attendanceCategories, attendanceSummary, termLink } from '../dashboard'
import type { Activity } from './Activities'
import type { Ranked } from './Leaderboard'
import { HelpDetails } from './HelpDetails'

interface Term { id: string; name: string; status: string }
interface Entry { id: string; memberName: string; amount: number; kind: string; recordedAt: string }
interface Trend { activity: Activity; present: number; late: number; excused: number; absent: number }
interface Snapshot { term: Term | null; counts: { totalMembers: number; activeMembers: number; activeEligible: number; netPoints: number; openWarnings: number }; attendance: { recent: Trend[]; upcoming: Activity[] }; leaders: Ranked[]; changes: Entry[] }
const panel = { p: 2.5, borderRadius: 2, minWidth: 0 }
const formatTime = (value: string) => new Date(value).toLocaleString('en-PH', { timeZone: 'Asia/Manila', month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })

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
    return 'Dashboard unavailable. Check the backend, then refresh.'
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
    try {
      const [overview, rows] = await Promise.all([load(), apiRequest('/api/v1/terms').then(r => r.json())])
      setData(overview); setTerms(rows)
    } catch (err) { setError(report(err)) } finally { setLoading(false) }
  }
  const active = data?.counts.activeMembers ?? 0
  const eligible = data?.counts.activeEligible ?? 0
  return <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { lg: 'center' } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
          <TextField select size="small" label="Academic term" disabled={loading} value={termId} sx={{ minWidth: 220, maxWidth: { sm: 340 } }} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }} onChange={e => { setData(null); setLoading(true); setError(''); setTermId(e.target.value) }}><MenuItem value="">{!termId && data?.term ? data.term.name : 'Current active term'}</MenuItem>{terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} · {t.status}</MenuItem>)}</TextField>
          {data?.term && <Chip size="small" variant="outlined" label={data.term.status} />}
        </Stack>
        <Stack direction="row" spacing={1}><Button startIcon={<RefreshOutlined />} disabled={loading} onClick={refresh}>Refresh</Button><Button variant="contained" component={Link} to={termLink('/activities', data?.term?.id)}>{data?.term?.status === 'ACTIVE' ? 'Take attendance' : 'View attendance'}</Button></Stack>
      </Stack>
    {error && <Alert severity="error">{error} {data && 'Previously loaded values remain visible.'}</Alert>}
    {loading && <Typography role="status">Loading overview…</Typography>}
    {data && <>
      {!data.term && <Alert severity="info" action={<Button component={Link} to="/settings">Settings</Button>}>Choose a historical term above, or activate a term in Settings to see attendance and points.</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' }, gap: 2 }}>
        {[
          { label: 'Active members', value: active, scope: 'Now' },
          { label: 'Points eligible', value: eligible, scope: 'Now' },
          { label: 'Net points', value: data.term ? data.counts.netPoints : '—', scope: 'Selected term' },
          { label: 'Open warnings', value: data.term ? data.counts.openWarnings : '—', scope: 'Selected term' },
        ].map(card => <Paper variant="outlined" key={card.label} sx={panel}><Typography component="h2" variant="body2" color="text.secondary">{card.label}</Typography><Typography sx={{ my: .5, fontSize: 28, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{card.value}</Typography><Typography variant="caption" color="text.secondary">{card.scope}</Typography></Paper>)}
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1.65fr) minmax(0, 1fr)' }, gap: 3 }}>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Attendance</Typography><Typography variant="caption" color="text.secondary">Latest 6 finalized activities</Typography>
          <HelpDetails label="How it's calculated"><Typography variant="body2">Rate = (Present + Late) ÷ all expected attendees, including Excused and Absent.</Typography></HelpDetails>
          {data.attendance.recent.length === 0 ? <Typography color="text.secondary" sx={{ py: 4 }}>No finalized attendance yet.</Typography> : <Stack component="ul" spacing={2} sx={{ p: 0, my: 2, listStyle: 'none' }}>{data.attendance.recent.map(row => {
            const summary = attendanceSummary(row)
            return <Box component="li" key={row.activity.id}>
              <Stack direction="row" spacing={1} sx={{ justifyContent: 'space-between', alignItems: 'baseline' }}><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{row.activity.title}</Typography><Typography variant="body2" sx={{ whiteSpace: 'nowrap', fontWeight: 700 }}>{summary.percent === null ? '—' : `${summary.percent}%`}</Typography></Stack>
              <Typography variant="caption" color="text.secondary">{summary.attended}/{summary.total} attended · {formatTime(row.activity.scheduledAt)}</Typography>
              <Box aria-hidden="true" sx={{ display: 'flex', height: 14, bgcolor: 'action.hover', borderRadius: 1, overflow: 'hidden', my: 1 }}>{attendanceCategories.map(category => <Box key={category.key} sx={{ width: `${summary.total ? row[category.key] / summary.total * 100 : 0}%`, bgcolor: category.color }} />)}</Box>
              <Stack direction="row" sx={{ flexWrap: 'wrap', gap: '4px 16px' }}>{attendanceCategories.map(category => <Stack key={category.key} direction="row" spacing={.75} sx={{ alignItems: 'center' }}><Box aria-hidden="true" sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: category.color }} /><Typography variant="caption">{category.label} {row[category.key]}</Typography></Stack>)}</Stack>
            </Box>
          })}</Stack>}
          <Button component={Link} to={termLink('/activities', data.term?.id)}>All activities →</Button>
        </Paper>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Up next</Typography><Typography variant="caption" color="text.secondary">Scheduled drafts · Asia/Manila</Typography>{data.attendance.upcoming.length === 0 ? <Typography color="text.secondary" sx={{ py: 4 }}>No upcoming activities.</Typography> : data.attendance.upcoming.map(a => <Box key={a.id} sx={{ py: 2, borderBottom: 1, borderColor: 'divider' }}><Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{a.title}</Typography><Typography variant="caption" color="text.secondary">{a.kind === 'MEETING' ? 'Meeting' : 'Event'} · {formatTime(a.scheduledAt)}</Typography>{a.location && <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{a.location}</Typography>}<Button component={Link} to={termLink('/activities', a.termId, a.id)} aria-label={`Open attendance for ${a.title}`} sx={{ mt: .5 }}>Attendance →</Button></Box>)}</Paper>
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, minmax(0, 1fr))' }, gap: 3 }}>
      <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Leading members</Typography><Typography variant="caption" color="text.secondary">Top 10 ranks · Ties included</Typography>{data.leaders.length === 0 ? <Typography sx={{ mt: 2 }}>No eligible members.</Typography> : data.leaders.map(m => <Stack key={m.memberId} direction="row" spacing={2} sx={{ justifyContent: 'space-between', py: 1, borderBottom: 1, borderColor: 'divider' }}><Typography sx={{ overflowWrap: 'anywhere', minWidth: 0 }}>#{m.rank} · {m.name}</Typography><Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>{m.total} pts</Typography></Stack>)}<Button component={Link} to={data.term ? `/leaderboard?termId=${data.term.id}&view=all` : '/leaderboard'} sx={{ mt: 1 }}>Full leaderboard →</Button></Paper>
      <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Recent points</Typography>
        {!data.term ? <Typography color="text.secondary" sx={{ mt: 2 }}>Activate a term in Settings to see its point activity.</Typography> : data.changes.length === 0 ? <Typography color="text.secondary" sx={{ mt: 2 }}>No point transactions in this term yet.</Typography> : data.changes.slice(0, 5).map(entry => <Stack key={entry.id} direction="row" spacing={2} sx={{ py: 2, borderBottom: 1, borderColor: 'divider', justifyContent: 'space-between' }}><Box sx={{ minWidth: 0 }}><Typography sx={{ fontWeight: 600 }}>{entry.memberName}</Typography><Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{entry.kind}</Typography><Typography variant="caption" color="text.secondary">{new Date(entry.recordedAt).toLocaleString()}</Typography></Box><Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>{entry.amount > 0 ? '+' : ''}{entry.amount} pts</Typography></Stack>)}
        <Button component={Link} to={termLink('/points', data.term?.id)} sx={{ mt: 1 }}>View ledger →</Button>
      </Paper>
      </Box>
    </>}
  </Stack>
}
