import { Box, Button, Container, Stack, Typography } from '@mui/material'
import { useColorScheme } from '@mui/material/styles'
import { ConnectionStatus } from './components/ConnectionStatus'

const modes = ['light', 'dark', 'system'] as const
const plannedAreas = ['Members', 'Attendance', 'Points', 'Warnings']

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
          gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1.35fr) minmax(280px, 0.65fr)' },
          alignItems: 'center',
          gap: { xs: 6, md: 10 },
          py: { xs: 7, md: 10 },
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
            sx={{ mt: 1.5, maxWidth: 620, fontSize: { xs: '2.7rem', sm: '3.5rem', md: '4.25rem' }, lineHeight: 1.08, fontWeight: 700, letterSpacing: '-0.035em' }}
          >
            A clear view of every contribution.
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mt: 3, maxWidth: 500, fontSize: '1.1rem', lineHeight: 1.6 }}>
            Apex will bring member records, attendance, points, and warnings into one focused workspace for the organization president.
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 5, pt: 3, borderTop: '1px solid', borderColor: 'divider', maxWidth: 500 }}>
            Runs on one laptop. Members are records, not accounts.
          </Typography>
        </Box>

        <Box
          component="section"
          aria-labelledby="system-status-title"
          sx={{ p: { xs: 3, sm: 4 }, border: '1px solid', borderColor: 'divider', borderRadius: 2, bgcolor: 'background.paper' }}
        >
          <Typography variant="overline" color="primary" sx={{ fontWeight: 700, letterSpacing: '0.14em' }}>
            Live check
          </Typography>
          <Typography id="system-status-title" component="h2" variant="h5" sx={{ mt: 0.5, fontWeight: 700 }}>
            System status
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 2.5 }}>
            React checks that the Apex backend and database are ready.
          </Typography>
          <ConnectionStatus />

          <Typography component="h3" variant="h6" sx={{ mt: 4, fontWeight: 700 }}>
            President's tools
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            These areas will appear as we build Apex.
          </Typography>
          <Box sx={{ mt: 3 }}>
            {plannedAreas.map((area, index) => (
              <Box
                key={area}
                sx={{ display: 'grid', gridTemplateColumns: '32px 1fr', gap: 2, py: 2, borderTop: '1px solid', borderColor: 'divider' }}
              >
                <Typography variant="caption" color="primary" sx={{ fontWeight: 700 }}>
                  {String(index + 1).padStart(2, '0')}
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600 }}>
                  {area}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>
      </Container>

      <Box component="footer" sx={{ borderTop: '1px solid', borderColor: 'divider' }}>
        <Container maxWidth="lg" sx={{ py: 2.5 }}>
          <Typography variant="caption" color="text.secondary">
            Apex connection preview — login and member records are not active yet.
          </Typography>
        </Container>
      </Box>
    </Box>
  )
}

export default App
