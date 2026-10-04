import { useState, type ReactNode } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router'
import { Alert, Box, Button, Dialog, DialogContent, DialogTitle, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, Paper, Stack, Typography } from '@mui/material'
import AccountCircleOutlined from '@mui/icons-material/AccountCircleOutlined'
import CloseOutlined from '@mui/icons-material/CloseOutlined'
import { ConnectionStatus } from './ConnectionStatus'
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
import { Warnings } from './Warnings'
import { Leaderboard } from './Leaderboard'
import LeaderboardOutlined from '@mui/icons-material/LeaderboardOutlined'

const pages = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined /> },
  { path: '/members', label: 'Members', icon: <PeopleOutline /> },
  { path: '/activities', label: 'Attendance', icon: <EventOutlined /> },
  { path: '/points', label: 'Point ledger', icon: <ReceiptLongOutlined /> },
  { path: '/leaderboard', label: 'Leaderboard', icon: <LeaderboardOutlined /> },
  { path: '/warnings', label: 'Warnings', icon: <FlagOutlined /> },
  { path: '/settings', label: 'Settings', icon: <SettingsOutlined /> },
]

export function Workspace({ username, busy, logout, onSessionExpired, account, message }: {
  username: string; busy: boolean; logout: () => void; onSessionExpired: () => void; account: ReactNode; message: string
}) {
  const [open, setOpen] = useState(false)
  const [accountAnchor, setAccountAnchor] = useState<HTMLElement | null>(null)
  const [accountOpen, setAccountOpen] = useState(false)
  const location = useLocation()
  const title = pages.find(p => p.path === location.pathname)?.label ?? 'Dashboard'
  const sidebar = <Stack sx={{ height: '100%' }}>
    <Box sx={{ px: 3, py: 2 }}><ApexLogo /></Box>
    <Divider />
    <List component="nav" aria-label="Main navigation" sx={{ px: 1.5, py: 2, flex: 1, display: 'flex', flexDirection: 'column' }}>
      {pages.map(page => <ListItemButton key={page.path} component={NavLink} to={page.path} end={page.path === '/'} onClick={() => setOpen(false)}
        sx={{ borderRadius: 1.5, mb: .5, flexGrow: 0, mt: page.path === '/settings' ? 'auto' : 0, '&.active': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } } }}>
        <ListItemIcon sx={{ minWidth: 36 }}>{page.icon}</ListItemIcon><ListItemText primary={page.label} slotProps={{ primary: { sx: { fontSize: 14, fontWeight: 600 } } }} />
      </ListItemButton>)}
    </List>
  </Stack>
  return <Box sx={{ display: 'flex', minHeight: '100dvh' }}>
    <Box component="a" href="#main-content" sx={{ position: 'fixed', top: -100, left: 16, zIndex: 1500, p: 1, bgcolor: 'background.paper', '&:focus': { top: 8 } }} onClick={e => { e.preventDefault(); document.getElementById('main-content')?.focus() }}>Skip to content</Box>
    <Drawer variant="permanent" sx={{ width: 252, flexShrink: 0, display: { xs: 'none', md: 'block' }, '& .MuiDrawer-paper': { width: 252, boxSizing: 'border-box' } }}>{sidebar}</Drawer>
    <Drawer open={open} onClose={() => setOpen(false)} sx={{ display: { md: 'none' }, '& .MuiDrawer-paper': { width: 252 } }}>{sidebar}</Drawer>
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Box component="header" sx={{ px: { xs: 2, lg: 4 }, py: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 1 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><IconButton aria-label="Open navigation" onClick={() => setOpen(true)} sx={{ display: { md: 'none' } }}><MenuOutlined /></IconButton><Typography variant="body2" color="text.secondary">Apex / {title}</Typography></Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}><Appearance /><Button id="account-button" startIcon={<AccountCircleOutlined />} aria-haspopup="menu" aria-controls={accountAnchor ? 'account-menu' : undefined} aria-expanded={!!accountAnchor} onClick={e => setAccountAnchor(e.currentTarget)}>Account</Button></Stack>
        </Stack>
      </Box>
      <Box component="main" id="main-content" tabIndex={-1} sx={{ p: { xs: 2, lg: 4 }, maxWidth: 1500, mx: 'auto' }}>
        <Stack spacing={3}>
          <Typography component="h1" variant="h4">{title}</Typography>
          {message && <Alert severity="error">{message}</Alert>}
          <Routes>
            <Route path="/" element={<Dashboard onSessionExpired={onSessionExpired} />} />
            <Route path="/members" element={<Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}><Members onSessionExpired={onSessionExpired} /></Paper>} />
            <Route path="/points" element={<Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}><Points onSessionExpired={onSessionExpired} /></Paper>} />
            <Route path="/settings" element={<Paper variant="outlined" sx={{ p: 3 }}><Organization onSessionExpired={onSessionExpired} /></Paper>} />
            <Route path="/activities" element={<Activities onSessionExpired={onSessionExpired} />} />
            <Route path="/warnings" element={<Warnings onSessionExpired={onSessionExpired} />} />
            <Route path="/leaderboard" element={<Leaderboard onSessionExpired={onSessionExpired} />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <Stack component="footer" spacing={1} sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}><ConnectionStatus /></Stack>
        </Stack>
      </Box>
    </Box>
    <Menu id="account-menu" anchorEl={accountAnchor} open={!!accountAnchor} onClose={() => setAccountAnchor(null)} slotProps={{ list: { 'aria-labelledby': 'account-button' } }}>
      <MenuItem disabled>Signed in as {username}</MenuItem>
      <MenuItem onClick={() => { setAccountAnchor(null); setAccountOpen(true) }}>Account & password</MenuItem>
      <MenuItem disabled={busy} onClick={() => { setAccountAnchor(null); logout() }}><ListItemIcon><LogoutOutlined fontSize="small" /></ListItemIcon>Sign out</MenuItem>
    </Menu>
    <Dialog open={accountOpen} onClose={() => { if (!busy) setAccountOpen(false) }} fullWidth maxWidth="sm" aria-labelledby="account-title">
      <DialogTitle id="account-title">Your account<IconButton aria-label="Close account" disabled={busy} onClick={() => setAccountOpen(false)} sx={{ position: 'absolute', right: 8, top: 8 }}><CloseOutlined /></IconButton></DialogTitle>
      <DialogContent>{account}</DialogContent>
    </Dialog>
  </Box>
}
