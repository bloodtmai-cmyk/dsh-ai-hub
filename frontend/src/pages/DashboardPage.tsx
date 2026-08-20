import { ArrowUpRight, Blocks, KeyRound, MessageSquareText, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { PageId } from '../platform/registry'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { Capability, ConversationSummary, KeyApplication, Page } from '../lib/types'

export function DashboardPage({ onNavigate }: { onNavigate: (page: PageId) => void }) {
  const [capabilities, setCapabilities] = useState<Page<Capability> | null>(null)
  const [conversations, setConversations] = useState<Page<ConversationSummary> | null>(null)
  const [keys, setKeys] = useState<Page<KeyApplication> | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([
      api<Page<Capability>>('/api/admin/capabilities?excludeStatus=DISABLED&size=5'),
      api<Page<ConversationSummary>>('/api/admin/conversations?size=5'),
      api<Page<KeyApplication>>('/api/admin/key-applications?status=PENDING&size=5'),
    ]).then(([capabilityPage, conversationPage, keyPage]) => {
      setCapabilities(capabilityPage)
      setConversations(conversationPage)
      setKeys(keyPage)
    }).catch((reason) => setError(reason.message))
  }, [])

  return (
    <div className="page page--dashboard">
      <header className="page-header"><div><h1>工作台</h1><p className="page-description">今天需要关注的能力、申请和审计动态</p></div><span className="live-indicator"><i />服务在线</span></header>
      {error && <ErrorNotice message={error} />}
      {!capabilities || !conversations || !keys ? <Loading /> : <>
        <section className="summary-strip" aria-label="关键指标">
          <button className="summary-item summary-item--primary" onClick={() => onNavigate('capabilities')}>
            <span className="summary-item__label"><Blocks aria-hidden="true" />已登记能力</span>
            <strong>{capabilities.totalElements}</strong>
            <span className="summary-item__scope">不含已停用 <ArrowUpRight aria-hidden="true" /></span>
          </button>
          <button className="summary-item" onClick={() => onNavigate('conversations')}>
            <span className="summary-item__label"><MessageSquareText aria-hidden="true" />对话审计回合</span>
            <strong>{conversations.totalElements}</strong>
            <ArrowUpRight className="summary-item__arrow" aria-hidden="true" />
          </button>
          <button className="summary-item" onClick={() => onNavigate('keys')}>
            <span className="summary-item__label"><KeyRound aria-hidden="true" />待审批 API Key</span>
            <strong>{keys.totalElements}</strong>
            <ArrowUpRight className="summary-item__arrow" aria-hidden="true" />
          </button>
          <button className="summary-item" onClick={() => onNavigate('audit')}>
            <span className="summary-item__label"><ShieldCheck aria-hidden="true" />授权策略</span>
            <strong className="summary-item__text">默认拒绝</strong>
            <ArrowUpRight className="summary-item__arrow" aria-hidden="true" />
          </button>
        </section>
        <div className="dashboard-columns">
          <section className="section-block dashboard-stream dashboard-stream--wide">
            <div className="section-heading"><h2>最近对话审计</h2><button className="text-button" onClick={() => onNavigate('conversations')}>查看全部</button></div>
            {conversations.content.length === 0 ? <Empty /> : <div className="table-wrap"><table><thead><tr><th>工号</th><th>模型</th><th>时间</th><th>Token</th><th>状态</th></tr></thead><tbody>
              {conversations.content.map((item) => <tr key={item.id}><td className="mono">{item.workcode}</td><td>{item.model}</td><td>{formatDate(item.startedAt)}</td><td>{item.inputTokens + item.outputTokens}</td><td><StatusBadge value={item.status} /></td></tr>)}
            </tbody></table></div>}
          </section>
          <section className="section-block dashboard-stream">
            <div className="section-heading"><h2>待审批 API Key</h2><button className="text-button" onClick={() => onNavigate('keys')}>进入审批</button></div>
            {keys.content.length === 0 ? <Empty text="没有待审批申请" /> : <div className="table-wrap"><table><thead><tr><th>工号</th><th>用途</th><th>提交时间</th></tr></thead><tbody>
              {keys.content.map((item) => <tr key={item.id}><td className="mono">{item.workcode}</td><td>{item.purpose}</td><td>{formatDate(item.submittedAt)}</td></tr>)}
            </tbody></table></div>}
          </section>
        </div>
      </>}
    </div>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
