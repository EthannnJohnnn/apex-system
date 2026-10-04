import { createTheme } from '@mui/material/styles'

export const apexTheme = createTheme({
  typography: {
    fontFamily: 'Outfit, system-ui, sans-serif',
    h4: { fontSize: '1.5rem', fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1.25 },
    h5: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.3 },
    h6: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#B4232D', contrastText: '#FFFFFF' },
        background: { default: '#F7F6F5', paper: '#FFFFFF' },
        text: { primary: '#17212E', secondary: '#52606D' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#F16B6B', contrastText: '#111316' },
        background: { default: '#111316', paper: '#1B1F23' },
        text: { primary: '#F1F5F9', secondary: '#B5BDC7' },
      },
    },
  },
})
