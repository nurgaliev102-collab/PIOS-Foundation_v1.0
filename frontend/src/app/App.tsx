import { useRoutes } from 'react-router-dom'
import { routes } from './routes'

/**
 * Application shell — Sprint 0: Frontend Foundation.
 *
 * Composes routing only. No providers, state management, or business
 * logic are added here until a real feature actually needs one —
 * consistent with this sprint's own "no business functionality" scope.
 */
function App() {
  return useRoutes(routes)
}

export default App
