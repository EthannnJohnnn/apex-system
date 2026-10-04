import { useEffect, useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import DownloadOutlined from '@mui/icons-material/DownloadOutlined'
import { ApiError, apiRequest } from '../api/auth'

interface Term { id: string; name: string; status: string }

export function ExcelExport({ kind, initialTermId, disabled, onSessionExpired }: {
  kind: 'members' | 'events'; initialTermId?: string; disabled?: boolean; onSessionExpired: () => void
}) {
  const [open, setOpen] = useState(false)
  const [terms, setTerms] = useState<Term[]>([])
  const [termId, setTermId] = useState('')
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!open) return
    let live = true
    apiRequest('/api/v1/terms').then(r => r.json()).then((data: Term[]) => {
      if (!live) return
      setTerms(data)
      setTermId(data.find(t => t.id === initialTermId)?.id ?? data.find(t => t.status === 'ACTIVE')?.id ?? data[0]?.id ?? '')
    }).catch((e: unknown) => {
      if (!live) return
      if (e instanceof ApiError && e.status === 401) onSessionExpired()
      else setError('Cannot load terms. Check the backend and retry.')
    }).finally(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [open, initialTermId, attempt, onSessionExpired])

  async function download() {
    setBusy(true); setError(''); setNotice('')
    try {
      const response = await apiRequest(`/api/v1/exports/${kind}?termId=${encodeURIComponent(termId)}`)
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      const termName = (terms.find(t => t.id === termId)?.name ?? termId).replace(/[^\p{L}\p{N}_-]+/gu, '-').slice(0, 80)
      link.download = `apex-${kind}-${termName}.xlsx`
      document.body.appendChild(link)
      link.click(); link.remove()
      // Give the browser time to start reading the download before releasing it.
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
      setNotice('Excel download started. Check your Downloads folder.')
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onSessionExpired()
      else setError(e instanceof ApiError ? e.message : 'Export failed. Check the backend and try again.')
    } finally { setBusy(false) }
  }

  return <>
    <Button startIcon={<DownloadOutlined />} disabled={disabled} onClick={() => { setError(''); setNotice(''); setTerms([]); setTermId(''); setLoading(true); setOpen(true) }}>Export Excel</Button>
    <Dialog open={open} onClose={() => { if (!busy) setOpen(false) }} fullWidth maxWidth="sm" aria-labelledby={`export-${kind}-title`}>
      <DialogTitle id={`export-${kind}-title`}>Export {kind === 'members' ? 'members' : 'events & attendance'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography>{kind === 'members'
            ? 'All members, including inactive members, with points and deductions for the selected term. Includes a separate point history sheet. Search filters do not limit the export.'
            : 'All meetings and events in the selected term, with event details and a separate attendance sheet. Only saved data is exported; drafts do not award points.'}</Typography>
          {loading ? <Typography role="status">Loading terms…</Typography> : terms.length > 0 ?
            <TextField select label="Export term" value={termId} disabled={busy} onChange={e => { setTermId(e.target.value); setNotice('') }}>
              {terms.map(t => <MenuItem key={t.id} value={t.id}>{t.name} ({t.status})</MenuItem>)}
            </TextField> : !error && <Alert severity="info">Create a term in Settings before exporting.</Alert>}
          {error && <Alert severity="error" action={!terms.length && <Button disabled={loading} onClick={() => { setLoading(true); setError(''); setAttempt(a => a + 1) }}>Retry</Button>}>{error}</Alert>}
          {notice && <Alert severity="success" role="status">{notice}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setOpen(false)}>Close</Button><Button variant="contained" disabled={busy || loading || !termId} onClick={() => void download()}>{busy ? 'Exporting…' : 'Download Excel'}</Button></DialogActions>
    </Dialog>
  </>
}
