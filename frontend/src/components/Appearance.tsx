import { Box, ToggleButton, ToggleButtonGroup } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'

export function ApexLogo() {
  const { mode, systemMode } = useColorScheme()
  const dark = (mode === 'system' ? systemMode : mode) === 'dark'
  return <Box component="img" src={`/brand/apex-logo-red-${dark ? 'dark' : 'light'}.svg`} alt="Apex" sx={{ width: 160, display: 'block' }} />
}

export function Appearance() {
  const { mode, setMode } = useColorScheme()
  return <ToggleButtonGroup size="small" exclusive value={mode ?? 'system'} aria-label="Theme preference"
    onChange={(_, value: 'light' | 'dark' | 'system' | null) => { if (value) setMode(value) }}>
    {(['light', 'dark', 'system'] as const).map(value => <ToggleButton key={value} value={value} sx={{ textTransform: 'capitalize' }}>{value}</ToggleButton>)}
  </ToggleButtonGroup>
}
