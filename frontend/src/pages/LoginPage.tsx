import { ArrowRight, Boxes, LockKeyhole } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { api, ensureCsrf } from '../lib/api'

export function LoginPage({ onLogin }: { onLogin: (workcode: string) => void }) {
  const [workcode, setWorkcode] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await ensureCsrf()
      const session = await api<{ workcode: string }>('/api/admin/session', {
        method: 'POST',
        body: JSON.stringify({ workcode, password }),
      })
      onLogin(session.workcode)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <header className="login-header">
        <div className="login-brand"><span className="login-brand__mark" aria-hidden="true"><Boxes size={24} /></span><span aria-hidden="true" /><div><strong>AI Hub</strong><small>能力治理中心</small></div></div>
      </header>
      <section className="login-content">
        <form className="login-panel" onSubmit={submit}>
          <div className="login-panel__heading"><LockKeyhole size={20} aria-hidden="true" /><div><h1>管理员登录</h1><p>请使用平台管理员账号</p></div></div>
          {error && <div className="notice notice--error" role="alert">{error}</div>}
          <label>工号<input value={workcode} onChange={(event) => setWorkcode(event.target.value)} inputMode="numeric" autoComplete="username" required autoFocus /></label>
          <label>密码<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
          <button className="primary-button login-button" disabled={submitting}>
            <span>{submitting ? '登录中' : '登录'}</span><ArrowRight size={17} aria-hidden="true" />
          </button>
        </form>
      </section>
      <footer className="login-footer">自托管能力控制面</footer>
    </main>
  )
}
