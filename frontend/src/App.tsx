import { Box, Button, Container, Stack, Typography } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'
import { PresidentAccess } from './components/PresidentAccess'

const modes = ['light', 'dark', 'system'] as const

function App() {
  const { mode, systemMode, setMode } = useColorScheme()
  const selectedMode = mode ?? 'system'
  const effectiveMode = mode === 'system' ? systemMode : mode
  const logoSrc = effectiveMode === 'dark'
    ? '/brand/apex-logo-red-dark.svg'
    : '/brand/apex-logo-red-light.svg'

  return (
    <Box sx={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', borderTop: '4px solid', borderColor: 'primary.main' }}>
      <Box component="header" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
        <Container
          maxWidth="lg"
          sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2, py: 1.5 }}
        >
          <Box
            component="img"
            src={logoSrc}
            alt="Apex"
            sx={{ display: 'block', width: { xs: 165, sm: 210 }, height: 'auto', ml: -2 }}
          />
          <Stack direction="row" spacing={1} role="group" aria-label="Theme preference">
            {modes.map((choice) => (
              <Button
                key={choice}
                size="small"
                variant={selectedMode === choice ? 'contained' : 'outlined'}
                onClick={() => setMode(choice)}
                sx={{ textTransform: 'capitalize', minWidth: 70 }}
              >
                {choice}
              </Button>
            ))}
          </Stack>
        </Container>
      </Box>

      <Container
        component="main"
        maxWidth="lg"
        sx={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr)',
          alignItems: 'center',
          gap: 3,
          py: { xs: 3, md: 4 },
        }}
      >
        <Box>
          <Box sx={{ width: 48, height: 4, bgcolor: 'primary.main', mb: 3 }} />
          <Typography variant="overline" color="primary" sx={{ fontWeight: 700, letterSpacing: '0.14em' }}>
            Local / president only
          </Typography>
          <Typography
            component="h1"
            variant="h2"
            sx={{ mt: 1.5, fontSize: { xs: '2rem', md: '2.75rem' }, lineHeight: 1.08, fontWeight: 700, letterSpacing: '-0.035em' }}
          >
            A clear view of every contribution.
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Runs on one laptop. Members are records, not accounts.
          </Typography>
        </Box>

        <Box
          component="section"
          aria-label="President account"
          sx={{ p: { xs: 3, sm: 4 }, border: '1px solid', borderColor: 'divider', borderRadius: 2, bgcolor: 'background.paper' }}
        >
          <PresidentAccess />
        </Box>
      </Container>

      <Box component="footer" sx={{ borderTop: '1px solid', borderColor: 'divider' }}>
        <Container maxWidth="lg" sx={{ py: 2.5 }}>
          <Typography variant="caption" color="text.secondary">
            Apex · Local president workspace
          </Typography>
        </Container>
      </Box>
    </Box>
  )
}

export default App
