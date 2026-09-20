import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider } from '@mui/material/styles'
import App from './App'
import { apexTheme } from './theme'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={apexTheme} defaultMode="system" noSsr>
      <CssBaseline />
      <App />
    </ThemeProvider>
  </StrictMode>,
)
