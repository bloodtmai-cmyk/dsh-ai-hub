import { useEffect, useState } from 'react'
import { Layout } from './components/Layout'
import { Loading } from './components/States'
import { api, clearCsrf } from './lib/api'
import { LoginPage } from './pages/LoginPage'
import { pageSlot, type PageId } from './platform/registry'

export default function App() {
  const [workcode, setWorkcode] = useState<string | null>(null)
  const [checking, setChecking] = useState(true)
  const [page, setPage] = useState<PageId>('dashboard')

  useEffect(() => {
    api<{ workcode: string }>('/api/admin/session')
      .then((session) => setWorkcode(session.workcode))
      .catch(() => setWorkcode(null))
      .finally(() => setChecking(false))
  }, [])

  async function logout() {
    await api<void>('/api/admin/session', { method: 'DELETE' })
    clearCsrf()
    setWorkcode(null)
  }

  if (checking) return <div className="boot-state"><Loading /></div>
  if (!workcode) return <LoginPage onLogin={setWorkcode} />

  const content = pageSlot(page).render({ navigate: setPage })

  return <Layout page={page} workcode={workcode} onNavigate={setPage} onLogout={logout}>{content}</Layout>
}
