import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { ApiError, apiRequest } from '../api/auth'
import { useSearchParams } from 'react-router'

interface Term { id: string; name: string; status: string }
export interface Ranked { rank: number; memberId: string; memberCode: string; name: string; total: number }
interface Result { term: Term; eligibilityBasis: string; eligibleCount: number; members: Ranked[] }
export function Leaderboard({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [query] = useSearchParams()
  const requestedTerm = query.get('termId')
  const [terms, setTerms] = useState<Term[]>([])
  const [termId, setTermId] = useState('')
  const [view, setView] = useState(query.get('view') === 'all' ? 'all' : 'top10')
  const [data, setData] = useState<Result | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [revision, setRevision] = useState(0)
  const report = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 401) onSessionExpired()
    return err instanceof ApiError ? err.message : 'Could not load rankings. Check the backend and refresh.'
  }, [onSessionExpired])
  useEffect(() => {
    let live = true
    apiRequest('/api/v1/terms').then(r => r.json()).then((rows: Term[]) => {
      if (live) { setTerms(rows); setTermId(rows.find(t => t.id === requestedTerm)?.id ?? rows.find(t => t.status === 'ACTIVE')?.id ?? rows[0]?.id ?? ''); if (!rows.length) setLoading(false) }
    }).catch(err => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [report, requestedTerm])
  useEffect(() => {
    if (!termId) return
    let live = true
    apiRequest(`/api/v1/leaderboard?termId=${termId}&view=${view}`).then(r => r.json()).then((result: Result) => { if (live) { setData(result); setLoading(false) } })
      .catch(err => { if (live) { setError(report(err)); setLoading(false) } })
    return () => { live = false }
  }, [termId, view, revision, report])
  function reset() { setData(null); setLoading(true); setError('') }
  return <Stack spacing={2}>
    <Typography color="text.secondary">Term totals come directly from the point ledger, including deductions and reversals. Equal totals share the same rank (1, 1, 3).</Typography>
    {error && <Alert severity="error">{error}</Alert>}
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField select label="Leaderboard term" value={termId} sx={{ minWidth: 250 }} onChange={e => { reset(); setTermId(e.target.value) }}>{terms.map(t => <MenuItem value={t.id} key={t.id}>{t.name} · {t.status}</MenuItem>)}</TextField>
      <TextField select label="Show rankings" value={view} sx={{ minWidth: 200 }} onChange={e => { reset(); setView(e.target.value) }}><MenuItem value="top10">Top ten, including ties</MenuItem><MenuItem value="all">Full list</MenuItem></TextField>
      <Button disabled={loading || !termId} onClick={() => { reset(); setRevision(n => n + 1) }}>Refresh rankings</Button>
    </Stack>
    {loading && <Typography role="status">Loading rankings…</Typography>}
    {!loading && !termId && <Alert severity="info">Create an academic term in Settings first.</Alert>}
    {data && <><Typography variant="body2">{data.eligibilityBasis} Showing {data.members.length} of {data.eligibleCount} eligible members. Zero-point members are included.</Typography>
      {view === 'top10' && <Typography variant="body2" color="text.secondary">Ties at rank 10 are included, so this view may contain more than ten members.</Typography>}
      <TableContainer component={Paper} variant="outlined"><Table aria-label="Member leaderboard"><TableHead><TableRow><TableCell>Rank</TableCell><TableCell>Member</TableCell><TableCell>Member ID</TableCell><TableCell align="right">Points</TableCell></TableRow></TableHead><TableBody>{data.members.map(m => <TableRow key={m.memberId}><TableCell>{m.rank}</TableCell><TableCell component="th" scope="row">{m.name}</TableCell><TableCell>{m.memberCode}</TableCell><TableCell align="right">{m.total}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
      {!data.members.length && <Typography>No eligible members for this term.</Typography>}
    </>}
  </Stack>
}
