import { useState, type ReactNode } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router'
import { Alert, Box, Button, Chip, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Paper, Stack, Typography } from '@mui/material'
import DashboardOutlined from '@mui/icons-material/DashboardOutlined'
import PeopleOutline from '@mui/icons-material/PeopleOutlineOutlined'
import EventOutlined from '@mui/icons-material/EventOutlined'
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined'
import FlagOutlined from '@mui/icons-material/FlagOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import MenuOutlined from '@mui/icons-material/MenuOutlined'
import LogoutOutlined from '@mui/icons-material/LogoutOutlined'
import { ApexLogo, Appearance } from './Appearance'
import { Dashboard } from './Dashboard'
import { Members } from './Members'
import { Organization } from './Organization'
import { Points } from './Points'
import { Activities } from './Activities'

const pages = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined /> },
  { path: '/members', label: 'Members', icon: <PeopleOutline /> },
  { path: '/activities', label: 'Activities & attendance', icon: <EventOutlined /> },
  { path: '/points', label: 'Point ledger', icon: <ReceiptLongOutlined /> },
  { path: '/warnings', label: 'Warnings', icon: <FlagOutlined /> },
  { path: '/settings', label: 'Settings', icon: <SettingsOutlined /> },
]

function Upcoming({ stage, description }: { stage: number; description: string }) {
  return <Stack spacing={2}><Chip label={`Planned · Stage ${stage}`} sx={{ alignSelf: 'flex-start' }} /><Typography variant="h5" component="h2">This workspace is coming next</Typography><Typography color="text.secondary">{description}</Typography><Alert severity="info">No records are being collected here yet. Your existing members and points are unchanged.</Alert></Stack>
}

export function Workspace({ username, busy, logout, onSessionExpired, account, message }: {
  username: string; busy: boolean; logout: () => void; onSessionExpired: () => void; account: ReactNode; message: string
}) {
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const title = pages.find(p => p.path === location.pathname)?.label ?? 'Dashboard'
  const sidebar = <Stack sx={{ height: '100%' }}>
    <Box sx={{ px: 3, py: 2 }}><ApexLogo /><Typography variant="overline" color="text.secondary">President workspace</Typography></Box>
    <Divider />
    <List component="nav" aria-label="Main navigation" sx={{ px: 1.5, py: 2 }}>
      {pages.map(page => <ListItemButton key={page.path} component={NavLink} to={page.path} end={page.path === '/'} onClick={() => setOpen(false)}
        sx={{ borderRadius: 1.5, mb: .5, '&.active': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } } }}>
        <ListItemIcon sx={{ minWidth: 36 }}>{page.icon}</ListItemIcon><ListItemText primary={page.label} slotProps={{ primary: { sx: { fontSize: 14, fontWeight: 600 } } }} />
      </ListItemButton>)}
    </List>
    <Box sx={{ mt: 'auto', p: 3 }}><Chip size="small" label="Laptop only" variant="outlined" /><Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>One organization.<br />One focused workspace.</Typography></Box>
  </Stack>
  return <Box sx={{ display: 'flex', minHeight: '100dvh' }}>
    <Box component="a" href="#main-content" sx={{ position: 'fixed', top: -100, left: 16, zIndex: 1500, p: 1, bgcolor: 'background.paper', '&:focus': { top: 8 } }} onClick={e => { e.preventDefault(); document.getElementById('main-content')?.focus() }}>Skip to content</Box>
    <Drawer variant="permanent" sx={{ width: 252, flexShrink: 0, display: { xs: 'none', md: 'block' }, '& .MuiDrawer-paper': { width: 252, boxSizing: 'border-box' } }}>{sidebar}</Drawer>
    <Drawer open={open} onClose={() => setOpen(false)} sx={{ display: { md: 'none' }, '& .MuiDrawer-paper': { width: 252 } }}>{sidebar}</Drawer>
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Box component="header" sx={{ px: { xs: 2, lg: 4 }, py: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><IconButton aria-label="Open navigation" onClick={() => setOpen(true)} sx={{ display: { md: 'none' } }}><MenuOutlined /></IconButton><Typography variant="body2" color="text.secondary">Apex / {title}</Typography></Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}><Appearance /><Button startIcon={<LogoutOutlined />} disabled={busy} onClick={logout}>Sign out</Button></Stack>
        </Stack>
      </Box>
      <Box component="main" id="main-content" tabIndex={-1} sx={{ p: { xs: 2, lg: 4 }, maxWidth: 1500, mx: 'auto' }}>
        <Stack spacing={3}>
          <Box><Typography variant="overline" color="primary">{location.pathname === '/' ? `Welcome back, ${username}` : 'Organization management'}</Typography><Typography component="h1" variant="h4" sx={{ fontWeight: 700 }}>{title}</Typography></Box>
          {message && <Alert severity="error">{message}</Alert>}
          <Routes>
            <Route path="/" element={<Dashboard onSessionExpired={onSessionExpired} />} />
            <Route path="/members" element={<Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}><Members onSessionExpired={onSessionExpired} /></Paper>} />
            <Route path="/points" element={<Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}><Points onSessionExpired={onSessionExpired} /></Paper>} />
            <Route path="/settings" element={<Stack spacing={3}><Paper variant="outlined" sx={{ p: 3 }}><Organization onSessionExpired={onSessionExpired} /></Paper><Paper variant="outlined" sx={{ p: 3 }}>{account}</Paper></Stack>} />
            <Route path="/activities" element={<Activities onSessionExpired={onSessionExpired} />} />
            <Route path="/warnings" element={<Paper variant="outlined" sx={{ p: 3 }}><Upcoming stage={14} description="Record warnings and track their resolution with an auditable history." /></Paper>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <Typography component="footer" variant="caption" color="text.secondary">Apex · Local president workspace · Members are records, not accounts.</Typography>
        </Stack>
      </Box>
    </Box>
  </Box>
}
