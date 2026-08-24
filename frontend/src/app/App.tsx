import { useRoutes } from 'react-router-dom'
import { routes } from './routes'
import { PushPermissionPrompt } from '../features/push/PushPermissionPrompt'

/** Application shell with the explicit, opt-in Web Push control. */
function App() {
  const routedElement = useRoutes(routes)
  return (
    <>
      {routedElement}
      <PushPermissionPrompt />
    </>
  )
}

export default App
