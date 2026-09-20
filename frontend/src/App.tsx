import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'

const modes = ['light', 'dark', 'system'] as const

function App() {
  const { mode, setMode } = useColorScheme()
  const selectedMode = mode ?? 'system'

  return (
    <Box sx={{ maxWidth: 960, minHeight: '100vh', mx: 'auto', p: { xs: 3, md: 6 } }}>
      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}
      >
        <Typography component="h1" variant="h5" sx={{ fontWeight: 800 }}>
          Apex
        </Typography>
        <Stack direction="row" spacing={1} role="group" aria-label="Theme preference">
          {modes.map((choice) => (
            <Button
              key={choice}
              size="small"
              variant={selectedMode === choice ? 'contained' : 'outlined'}
              onClick={() => setMode(choice)}
            >
              {choice}
            </Button>
          ))}
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ mt: 6, p: { xs: 3, md: 5 }, borderRadius: 2 }}>
        <Typography variant="overline" color="primary" sx={{ fontWeight: 700 }}>
          President workspace
        </Typography>
        <Typography component="h2" variant="h4" sx={{ mt: 1, fontWeight: 700 }}>
          Track participation in one place.
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 2 }}>
          Members, attendance, and points will appear here as we build Apex.
        </Typography>
      </Paper>
    </Box>
  )
}

export default App
