import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import { initNewRelic } from './services/newrelic'

// Bootstrap RUM as early as possible so we capture the initial page-load timing.
// No-op if NR env vars are not configured — see services/newrelic.ts.
initNewRelic()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
