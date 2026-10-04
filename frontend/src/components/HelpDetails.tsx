import type { ReactNode } from 'react'
import { Box } from '@mui/material'

/** Optional explanations stay keyboard-accessible without crowding the task. */
export function HelpDetails({ label, children }: { label: string; children: ReactNode }) {
  return <Box component="details" sx={{ color: 'text.secondary', fontSize: 14, '& > summary': { cursor: 'pointer', width: 'fit-content', borderRadius: 1, '&:hover': { color: 'text.primary' }, '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: 3 } } }}>
    <summary>{label}</summary>
    <Box sx={{ pt: 1, maxWidth: 720 }}>{children}</Box>
  </Box>
}
