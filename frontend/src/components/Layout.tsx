import {
  Boxes,
  LogOut,
  ShieldCheck,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { platformNavigationSections, platformPageSlots, type PageId } from '../platform/registry'

export type { PageId } from '../platform/registry'

export function Layout({ page, workcode, onNavigate, onLogout, children }: {
  page: PageId
  workcode: string
  onNavigate: (page: PageId) => void
  onLogout: () => void
  children: ReactNode
}) {
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true"><Boxes size={22} /></span>
          <div><strong>AI Hub</strong><span>能力治理中心</span></div>
        </div>
        <nav aria-label="主导航">
          {platformNavigationSections.map((section) => (
            <section className="nav-group" aria-labelledby={`nav-${section.id}`} key={section.id}>
              <h2 id={`nav-${section.id}`}>{section.label}</h2>
              {platformPageSlots.filter((slot) => slot.section === section.id).map(({ id, label, icon: Icon }) => (
                <button key={id} className={page === id ? 'nav-item nav-item--active' : 'nav-item'} onClick={() => onNavigate(id)} aria-current={page === id ? 'page' : undefined}>
                  <Icon size={18} aria-hidden="true" /><span>{label}</span>
                </button>
              ))}
            </section>
          ))}
        </nav>
        <div className="sidebar__footer">
          <div className="admin-identity"><ShieldCheck size={17} aria-hidden="true" /><div><span>管理员</span><strong>{workcode}</strong></div></div>
          <button className="icon-button" type="button" onClick={onLogout} title="退出登录" aria-label="退出登录"><LogOut size={18} /></button>
        </div>
      </aside>
      <main className="main" id="main-content">{children}</main>
    </div>
  )
}
