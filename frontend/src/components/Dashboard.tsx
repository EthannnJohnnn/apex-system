import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Alert, Box, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'
import { ConnectionStatus } from './ConnectionStatus'

interface Member { id: string; name: string; active: boolean; eligible: boolean }
interface Term { id: string; name: string; status: string }
interface Entry { id: string; memberId: string; amount: number; reason: string; recordedAt: string }
interface Snapshot { members: Member[]; term?: Term; entries: Entry[]; netPoints: number }
const panel = { p: 3, borderRadius: 2 }

export function Dashboard({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Snapshot | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    const [members, terms]: [Member[], Term[]] = await Promise.all([
      apiRequest('/api/v1/members').then(r => r.json()), apiRequest('/api/v1/terms').then(r => r.json()),
    ])
    const term = terms.find(t => t.status === 'ACTIVE')
    const ledger: { entries: Entry[] } = term ? await (await apiRequest('/api/v1/points?termId=' + term.id)).json() : { entries: [] }
    return { members, term, entries: ledger.entries, netPoints: ledger.entries.reduce((sum, e) => sum + e.amount, 0) }
  }, [])
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return 'Could not load the dashboard. Check the backend and try Refresh overview.'
  }, [onSessionExpired])
  useEffect(() => {
    let live = true
    load().then(result => { if (live) setData(result) }).catch((err: unknown) => { if (live) setError(report(err)) }).finally(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [load, report])
  async function refresh() {
    setLoading(true); setError('')
    try { setData(await load()) } catch (err) { setError(report(err)) } finally { setLoading(false) }
  }
  const active = data?.members.filter(m => m.active).length ?? 0
  const eligible = data?.members.filter(m => m.active && m.eligible).length ?? 0
  const groups = data ? [{ label: 'Active · eligible', value: eligible }, { label: 'Active · ineligible', value: active - eligible }, { label: 'Inactive', value: data.members.length - active }] : []
  return <Stack spacing={3}>
    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}><Typography color="text.secondary">Your organization at a glance.</Typography><Button disabled={loading} onClick={refresh}>Refresh overview</Button></Stack>
    {error && <Alert severity="error">{error} {data && 'Previously loaded values remain visible.'}</Alert>}
    {loading && <Typography role="status">Loading overview…</Typography>}
    {data && <>
      <Chip label={data.term ? `Active term · ${data.term.name}` : 'No active academic term'} sx={{ alignSelf: 'flex-start' }} />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', lg: 'repeat(4, 1fr)' }, gap: 2 }}>
        {[['Total members', data.members.length], ['Active members', active], ['Active & eligible', eligible], ['Term net points', data.term ? data.netPoints : '—']].map(([label, value]) => <Paper variant="outlined" key={label} sx={panel}><Typography color="text.secondary" variant="body2">{label}</Typography><Typography variant="h3" sx={{ mt: 1, fontWeight: 600 }}>{value}</Typography></Paper>)}
      </Box>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 3 }}>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Member overview</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>Current membership, grouped by status</Typography>
          {data.members.length === 0 ? <Typography>No members yet. Add your first member to see this chart.</Typography> : <Stack spacing={2.5} component="ul" sx={{ p: 0, listStyle: 'none' }}>{groups.map(group => <Box component="li" key={group.label}><Stack direction="row" sx={{ justifyContent: 'space-between', mb: .75 }}><Typography variant="body2">{group.label}</Typography><Typography variant="body2">{group.value}</Typography></Stack><Box aria-hidden="true" sx={{ height: 12, bgcolor: 'action.hover', borderRadius: 1, overflow: 'hidden' }}><Box sx={{ height: '100%', width: `${group.value / data.members.length * 100}%`, bgcolor: 'primary.main' }} /></Box></Box>)}</Stack>}
          <Button component={Link} to="/members" sx={{ mt: 2 }}>Manage members →</Button>
        </Paper>
        <Paper variant="outlined" sx={panel}><Typography component="h2" variant="h6">Attendance trends</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Meetings and events · planned for Stage 13</Typography><Box sx={{ minHeight: 170, display: 'grid', placeItems: 'center', border: '1px dashed', borderColor: 'divider', borderRadius: 1, mt: 3, p: 3 }}><Typography color="text.secondary" sx={{ textAlign: 'center' }}>Attendance graphs will appear after the attendance feature is built. No sample results are shown.</Typography></Box></Paper>
      </Box>
      <Paper variant="outlined" sx={panel}><Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}><Typography component="h2" variant="h6">Recent point transactions</Typography><Button component={Link} to="/points">View ledger →</Button></Stack>
        {!data.term ? <Typography color="text.secondary" sx={{ mt: 2 }}>Activate a term in Settings to see its point activity.</Typography> : data.entries.length === 0 ? <Typography color="text.secondary" sx={{ mt: 2 }}>No point transactions in this term yet.</Typography> : data.entries.slice(0, 5).map(entry => <Stack key={entry.id} direction="row" spacing={2} sx={{ py: 2, borderBottom: 1, borderColor: 'divider', justifyContent: 'space-between' }}><Box sx={{ minWidth: 0 }}><Typography sx={{ fontWeight: 600 }}>{data.members.find(m => m.id === entry.memberId)?.name ?? 'Member'}</Typography><Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{entry.reason}</Typography><Typography variant="caption" color="text.secondary">{new Date(entry.recordedAt).toLocaleString()}</Typography></Box><Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>{entry.amount > 0 ? '+' : ''}{entry.amount} pts</Typography></Stack>)}
      </Paper>
    </>}
    <ConnectionStatus />
  </Stack>
}
