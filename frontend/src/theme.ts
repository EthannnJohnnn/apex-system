import { createTheme } from '@mui/material/styles'

export const apexTheme = createTheme({
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
